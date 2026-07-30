package com.oneims.app.core

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.oneims.app.R
import com.oneims.app.model.ConfigResult

enum class ReapplyTrigger(
    val storedValue: String,
    @StringRes val labelRes: Int,
) {
    MANUAL("manual", R.string.reapply_trigger_manual),
    QUICK_SETTINGS_TILE("quick_settings_tile", R.string.reapply_trigger_quick_tile),
    /** 特权桥（OneBridge 或 Shizuku 回落）binder 就绪。 */
    BRIDGE_READY("bridge_ready", R.string.reapply_trigger_bridge_ready),
    /** @deprecated 历史存储值；[fromStored] 会映射到 [BRIDGE_READY]。 */
    @Deprecated("Use BRIDGE_READY")
    SHIZUKU_READY("shizuku_ready", R.string.reapply_trigger_bridge_ready),
    IMS_NOT_REGISTERED("ims_not_registered", R.string.reapply_trigger_ims_not_registered),
    BOOT("boot", R.string.reapply_trigger_boot),
    ;

    companion object {
        fun fromStored(value: String?): ReapplyTrigger = when (value) {
            "shizuku_ready", "bridge_ready" -> BRIDGE_READY
            else -> entries.firstOrNull { trigger -> trigger.storedValue == value } ?: MANUAL
        }
    }
}

/**
 * 所有重应用入口都经由这里记录触发原因和结果，便于前台解释“为什么重应用”并追溯失败。
 *
 * 恢复契约：凡用户曾成功写入且已持久化为重放源的配置，均应在此与手动入口走同一写入面。
 * 「应用高级选项」与核心 [lastApplied] 解耦——有高级 prefs 就必须重放，不得因核心缺失/失败而静默跳过。
 */
object ReapplyManager {
    private const val TAG = "OneIMS-Reapply"

    fun reapply(
        context: Context,
        trigger: ReapplyTrigger,
        targetSubId: Int? = null,
    ): ConfigResult {
        val parts = mutableListOf<String>()
        var attempted = false
        var allOk = true

        fun note(label: String, result: ConfigResult) {
            attempted = true
            if (!result.success) allOk = false
            parts += "$label: ${result.message}"
        }

        val applied = ConfigStore.lastApplied(context)
        if (applied != null) {
            note("core", ImsController.reapplyLast(context, targetSubId))
        } else if (targetSubId != null) {
            val ui = ConfigStore.capabilityUiState(context, targetSubId)
            if (ui != null) {
                note(
                    "core",
                    ImsController.applyAll(
                        context,
                        targetSubId,
                        ui.volte,
                        ui.vowifi,
                        ui.vonr,
                        ui.wfcMode,
                    ),
                )
            }
        }

        // 高级选项按 subId 各存一份：开机/守护对每张有记录的卡分别重放，禁止用单槽全局值串卡。
        val advancedSubIds = when {
            targetSubId != null && targetSubId >= 0 -> {
                listOf(targetSubId).filter {
                    ConfigStore.lastAdvancedOptions(context, it) != null
                }
            }
            else -> ConfigStore.listAdvancedOptionSubIds(context)
        }
        Log.i(TAG, "advanced reapply subIds=$advancedSubIds trigger=$trigger")
        for (advancedSubId in advancedSubIds) {
            val advancedOptions = ConfigStore.lastAdvancedOptions(context, advancedSubId)
                ?: continue
            note(
                "advanced@$advancedSubId",
                PixelImsCompat.applyOptions(context, advancedSubId, advancedOptions),
            )
        }

        val subIds = resolveTargetSubIds(context, targetSubId, applied?.subId)
        for (subId in subIds) {
            reapplyPerSimPersisted(context, subId, ::note)
        }

        // 5G 显示增强 / 信号格样式已迁出 OneIMS，不再开机重放。
        // 能力页「5G信号强度调整」阈值仍按卡重放（见 reapplyPerSimPersisted）。

        val result = if (!attempted) {
            ConfigResult(false, context.getString(R.string.msg_no_history))
        } else {
            ConfigResult(
                success = allOk,
                message = parts.joinToString("\n").ifBlank {
                    context.getString(R.string.msg_none)
                },
            )
        }
        ConfigStore.saveReapplyStatus(
            context,
            ConfigStore.ReapplyStatus(
                timestampMillis = System.currentTimeMillis(),
                success = result.success,
                trigger = trigger,
                message = result.message,
            ),
        )
        return result
    }

    /**
     * 是否存在任何可开机重放的持久源（核心 / 高级 / 按卡能力 / 信号阈值）。
     * 供开机编排在「无 lastApplied」时仍进入重放，而不是整段跳过。
     * 信号格样式 / 5G 显示增强 / 切卡已迁出，不再计入重放源。
     */
    fun hasPersistedReapplySource(context: Context): Boolean {
        if (ConfigStore.lastApplied(context) != null) return true
        if (ConfigStore.hasAnyAdvancedOptions(context)) return true
        return ImsController.listSims(context).any { sim ->
            val caps = ConfigStore.capabilityUiState(context, sim.subscriptionId)
            caps != null && (
                caps.vilte || caps.ut || caps.crossSim || caps.nr5g ||
                    ConfigStore.signalStrengthAdjustmentEnabled(context, sim.subscriptionId)
                )
        }
    }

    private fun reapplyPerSimPersisted(
        context: Context,
        subId: Int,
        note: (String, ConfigResult) -> Unit,
    ) {
        val caps = ConfigStore.capabilityUiState(context, subId)
        if (caps != null && (caps.vilte || caps.ut || caps.crossSim)) {
            note(
                "extras@$subId",
                ImsController.applyCarrierExtras(
                    context,
                    subId,
                    caps.vilte,
                    caps.ut,
                    caps.crossSim,
                ),
            )
        }
        if (caps?.nr5g == true) {
            note("nr5g@$subId", ImsController.apply5g(context, subId, true))
        }

        val signalOn = ConfigStore.signalStrengthAdjustmentEnabled(context, subId)
        if (signalOn) {
            val signalResult = runCatching {
                val message = SystemDisplayOverrideManager.applySignalStrengthAdjustment(
                    context = context,
                    subId = subId,
                    enabled = caps?.nr5g == true,
                    preferenceEnabled = true,
                )
                ConfigResult(true, message)
            }.getOrElse { error ->
                ConfigResult(false, error.message ?: "signal failed")
            }
            note("signal@$subId", signalResult)
        }
    }

    private fun resolveWriteSubId(
        context: Context,
        targetSubId: Int?,
        fallbackSubId: Int?,
    ): Int? {
        if (targetSubId != null && targetSubId >= 0) return targetSubId
        if (fallbackSubId != null && fallbackSubId >= 0) return fallbackSubId
        val selected = ConfigStore.getSelectedSubId(context)
        if (selected >= 0) return selected
        return ImsController.listSims(context).firstOrNull()?.subscriptionId
    }

    private fun resolveTargetSubIds(
        context: Context,
        targetSubId: Int?,
        fallbackSubId: Int?,
    ): List<Int> {
        if (targetSubId != null && targetSubId >= 0) return listOf(targetSubId)
        val sims = ImsController.listSims(context).map { it.subscriptionId }
        if (sims.isNotEmpty()) return sims
        val single = resolveWriteSubId(context, null, fallbackSubId)
        return listOfNotNull(single)
    }
}
