package com.oneims.app.onekuku

import android.content.Context
import android.util.Log
import com.oneims.app.core.ConfigStore
import com.oneims.app.core.ImsController
import com.oneims.app.core.PixelImsCompat
import com.oneims.app.core.PixelImsOptions
import com.oneims.app.core.VoWifiNameFormatManager
import com.oneims.app.model.ConfigResult
import com.oneims.app.model.WfcMode

/**
 * 按快照恢复通话配置：单项失败不阻断后续，末尾汇总。
 * 顺序：身份 → IMS → WFC → 5G NR → VoWiFi 名称 → 高级选项 → extras。
 * 信号格 / 5G 显示增强已迁出，不再恢复。
 */
object OneKukuRestoreManager {
    private const val TAG = "OneIMS-Restore"
    private const val ITEM_MAX_ATTEMPTS = 2

    private fun retryStep(block: () -> ConfigResult): Boolean {
        var ok = false
        repeat(ITEM_MAX_ATTEMPTS) { attempt ->
            val result = runCatching(block).getOrElse {
                ConfigResult(false, it.message ?: "error")
            }
            ok = result.success
            if (ok) return true
            Log.w(TAG, "step retry attempt=${attempt + 1} msg=${result.message}")
        }
        return ok
    }

    fun restoreAll(context: Context, subId: Int): OneKukuCommandResult {
        OneKukuHiddenRunner.markExecuting()
        val wake = OneKukuHiddenRunner.wake()
        if (!wake.success) {
            OneKukuHiddenRunner.markFailed(wake.message)
            return wake
        }
        val sims = ImsController.listSims(context)
        when (val resolved = OneKukuSnapshotStore.resolveForSelectedSim(context, subId, sims)) {
            is SnapshotMatchResult.NoSnapshot -> {
                val msg = "no snapshot"
                Log.w(TAG, msg)
                OneKukuSleepController.sleepIfEnabled(context)
                return OneKukuCommandResult(false, OneKukuRunnerState.SLEEPING, msg)
            }
            is SnapshotMatchResult.NoMatchingSim -> {
                Log.w(TAG, OneKukuSnapshotStore.MSG_NO_MATCHING_SIM)
                OneKukuSleepController.sleepIfEnabled(context)
                return OneKukuCommandResult(
                    false,
                    OneKukuRunnerState.SLEEPING,
                    OneKukuSnapshotStore.MSG_NO_MATCHING_SIM,
                )
            }
            is SnapshotMatchResult.Matched -> {
                val snapshot = resolved.snapshot
                val writeSubId = resolved.writeSubId
                // 明确排除 APN：一键恢复通话不自动恢复 APN。
                val detail = linkedMapOf<String, Boolean>()
                val failures = mutableListOf<String>()

                fun runNamed(name: String, block: () -> ConfigResult): Boolean {
                    val ok = retryStep {
                        val result = block()
                        if (!result.success) {
                            failures += "$name: ${result.message}"
                            Log.w(TAG, "item failed name=$name msg=${result.message}")
                        }
                        result
                    }
                    return ok
                }

                detail["identity"] = runNamed("identity") {
                    restoreIdentity(context, writeSubId, snapshot)
                }
                detail["ims"] = runNamed("ims") {
                    restoreIms(context, writeSubId, snapshot)
                }
                detail["wfc"] = runNamed("wfc") {
                    restoreWfc(context, writeSubId, snapshot)
                }
                detail["nr5g"] = runNamed("nr5g") {
                    restoreFiveG(context, writeSubId, snapshot)
                }
                detail["vowifi_name"] = runNamed("vowifi_name") {
                    restoreVoWifiName(context, writeSubId, snapshot)
                }
                detail["advanced"] = runNamed("advanced") {
                    restoreAdvanced(context, writeSubId, snapshot)
                }
                detail["extras"] = runNamed("extras") {
                    restoreExtras(context, writeSubId, snapshot)
                }
                detail["verify"] = runNamed("verify") {
                    val result = verify(context, writeSubId)
                    ConfigResult(result.success, result.message)
                }

                val successCount = detail.values.count { it }
                val total = detail.size
                val allOk = successCount == total
                val partial = successCount > 0 && !allOk
                val status = when {
                    allOk -> "success"
                    partial -> "partial"
                    else -> "failed"
                }
                OneKukuSnapshotStore.updateRestoreStatus(
                    context = context,
                    subId = writeSubId,
                    status = status,
                    verifiedAt = System.currentTimeMillis(),
                )
                OneKukuSleepController.sleepIfEnabled(context)
                val message = buildString {
                    append("restore $status ($successCount/$total)")
                    if (failures.isNotEmpty()) {
                        append(" · ")
                        append(failures.joinToString("; "))
                    }
                }
                Log.i(TAG, message)
                return OneKukuCommandResult(
                    success = allOk || partial,
                    state = OneKukuRunnerState.SLEEPING,
                    message = message,
                    detail = detail,
                )
            }
        }
    }

    fun restoreIms(context: Context, subId: Int): OneKukuCommandResult =
        step("ims", context) { restoreIms(context, subId, requireSnapshot(context, subId)) }

    fun restoreWfc(context: Context, subId: Int): OneKukuCommandResult =
        step("wfc", context) { restoreWfc(context, subId, requireSnapshot(context, subId)) }

    fun restoreFiveG(context: Context, subId: Int): OneKukuCommandResult =
        step("nr5g", context) { restoreFiveG(context, subId, requireSnapshot(context, subId)) }

    fun restoreVoWifiName(context: Context, subId: Int): OneKukuCommandResult =
        step("vowifi_name", context) { restoreVoWifiName(context, subId, requireSnapshot(context, subId)) }

    fun restoreIdentity(context: Context, subId: Int): OneKukuCommandResult =
        step("identity", context) { restoreIdentity(context, subId, requireSnapshot(context, subId)) }

    fun verify(context: Context, subId: Int): OneKukuCommandResult {
        val sims = ImsController.listSims(context)
        val ok = when (val resolved = OneKukuSnapshotStore.resolveForSelectedSim(context, subId, sims)) {
            is SnapshotMatchResult.Matched ->
                OneKukuBootRestoreCoordinator.isSnapshotCarrierVerified(
                    context = context,
                    subId = resolved.writeSubId,
                    snapshot = resolved.snapshot,
                )
            is SnapshotMatchResult.NoMatchingSim,
            is SnapshotMatchResult.NoSnapshot -> false
        }
        return OneKukuCommandResult(
            success = ok,
            state = OneKukuHiddenRunner.currentState(),
            message = if (ok) "verify ok" else "verify failed",
            detail = mapOf("verify" to ok),
        )
    }

    private fun step(
        name: String,
        context: Context,
        block: () -> ConfigResult,
    ): OneKukuCommandResult {
        OneKukuHiddenRunner.markExecuting()
        val wake = OneKukuHiddenRunner.wake()
        if (!wake.success) return wake
        val result = runCatching(block).getOrElse {
            ConfigResult(false, it.message ?: "error")
        }
        Log.i(TAG, "step=$name success=${result.success} msg=${result.message}")
        OneKukuSleepController.sleepIfEnabled(context)
        return OneKukuCommandResult(
            success = result.success,
            state = OneKukuRunnerState.SLEEPING,
            message = result.message,
            detail = mapOf(name to result.success),
        )
    }

    private fun requireSnapshot(context: Context, subId: Int): OneKukuSnapshot {
        val sims = ImsController.listSims(context)
        return when (val resolved = OneKukuSnapshotStore.resolveForSelectedSim(context, subId, sims)) {
            is SnapshotMatchResult.Matched -> resolved.snapshot
            is SnapshotMatchResult.NoMatchingSim ->
                error(OneKukuSnapshotStore.MSG_NO_MATCHING_SIM)
            is SnapshotMatchResult.NoSnapshot ->
                error("snapshot missing")
        }
    }

    private fun restoreIdentity(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): ConfigResult {
        val carrier = snapshot.entry("identity", "carrierName") ?: return ConfigResult(true, "skip")
        val ua = snapshot.entry("identity", "imsUserAgent").orEmpty()
        if (carrier.isBlank() && ua.isBlank()) return ConfigResult(true, "skip")
        // UA 可能已打码；打码值跳过以免写坏
        val safeUa = if (ua.contains('…') || ua == "***") "" else ua
        return ImsController.applyIdentityOverride(context, subId, carrier, safeUa)
    }

    private fun restoreIms(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): ConfigResult {
        val volte = snapshot.bool("ims", "volte", true)
        val vowifi = snapshot.bool("ims", "vowifi", true)
        val vonr = snapshot.bool("ims", "vonr", false)
        val wfc = WfcMode.of(
            snapshot.entry("wfc", "mode")?.toIntOrNull()
                ?: snapshot.entry("ims", "wfcMode")?.toIntOrNull()
                ?: 1,
        )
        return ImsController.applyAll(context, subId, volte, vowifi, vonr, wfc)
    }

    private fun restoreWfc(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): ConfigResult {
        val wfc = WfcMode.of(
            snapshot.entry("wfc", "mode")?.toIntOrNull()
                ?: snapshot.entry("ims", "wfcMode")?.toIntOrNull()
                ?: 1,
        )
        return ImsController.setWfcMode(context, subId, wfc)
    }

    private fun restoreFiveG(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): ConfigResult {
        val enabled = snapshot.bool("nr5g", "enabled", false)
        if (!enabled) return ConfigResult(true, "skip")
        return ImsController.apply5g(context, subId, true)
    }

    private fun restoreVoWifiName(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): ConfigResult {
        val index = snapshot.entry("vowifi_name", "formatIndex")?.toIntOrNull()
            ?: return ConfigResult(true, "skip")
        val custom = snapshot.entry("vowifi_name", "customCarrier").orEmpty()
        return runCatching {
            val message = VoWifiNameFormatManager.apply(
                context = context,
                subId = subId,
                formatIndex = index,
                customCarrierName = custom,
            )
            ConfigResult(true, message)
        }.getOrElse { ConfigResult(false, it.message ?: "vowifi name failed") }
    }

    private fun restoreAdvanced(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): ConfigResult {
        val fromSnapshot = snapshotAdvancedOptions(snapshot)
        if (fromSnapshot != null) {
            return PixelImsCompat.applyOptions(context, subId, fromSnapshot)
        }
        // 无本卡快照时，仅回放本卡 per-subId prefs，禁止串写其它卡的高级选项。
        val options = ConfigStore.lastAdvancedOptions(context, subId)
            ?: return ConfigResult(true, "skip")
        return PixelImsCompat.applyOptions(context, subId, options)
    }

    private fun restoreExtras(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): ConfigResult {
        val hasExtras = snapshot.entries.any { it.configGroup == "extras" }
        if (!hasExtras) {
            val caps = ConfigStore.capabilityUiState(context, subId)
            if (caps == null || (!caps.vilte && !caps.ut && !caps.crossSim)) {
                return ConfigResult(true, "skip")
            }
            return ImsController.applyCarrierExtras(
                context,
                subId,
                caps.vilte,
                caps.ut,
                caps.crossSim,
            )
        }
        val vilte = snapshot.bool("extras", "vilte", false)
        val ut = snapshot.bool("extras", "ut", false)
        val crossSim = snapshot.bool("extras", "cross_sim", false)
        if (!vilte && !ut && !crossSim) return ConfigResult(true, "skip")
        return ImsController.applyCarrierExtras(context, subId, vilte, ut, crossSim)
    }

    private fun snapshotAdvancedOptions(snapshot: OneKukuSnapshot): PixelImsOptions? {
        if (snapshot.entries.none { it.configGroup == "advanced" }) return null
        return PixelImsOptions(
            wfcRoamingEnabled = snapshot.bool("advanced", "wfc_roaming", false),
            showWfcMode = snapshot.bool("advanced", "show_wfc_mode", false),
            showWfcRoamingMode = snapshot.bool("advanced", "show_wfc_roaming_mode", false),
            supportWifiOnly = snapshot.bool("advanced", "wifi_only", false),
            allowAddingApns = snapshot.bool("advanced", "allow_apn_add", false),
            showVowifiIcon = snapshot.bool("advanced", "vowifi_icon", false),
            alwaysShowDataRatIcon = snapshot.bool("advanced", "data_rat_icon", false),
            show4gForLteIcon = snapshot.bool("advanced", "4g_for_lte", false),
            hideLtePlusIcon = snapshot.bool("advanced", "hide_lte_plus", false),
            showImsStatus = snapshot.bool("advanced", "show_ims_status", false),
            ssOverCdma = snapshot.bool("advanced", "ss_over_cdma", false),
            enhanced4g = snapshot.bool("advanced", "enhanced_4g", false),
        )
    }

    private fun OneKukuSnapshot.entry(type: String, key: String): String? =
        entries.firstOrNull { it.configGroup == type && it.configKey == key }?.configValue

    private fun OneKukuSnapshot.bool(type: String, key: String, default: Boolean): Boolean =
        entry(type, key)?.toBooleanStrictOrNull() ?: default
}
