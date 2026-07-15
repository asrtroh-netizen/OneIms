package com.oneims.app.core

import android.content.Context
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
 */
object ReapplyManager {
    fun reapply(
        context: Context,
        trigger: ReapplyTrigger,
        targetSubId: Int? = null,
    ): ConfigResult {
        val coreResult = ImsController.reapplyLast(context, targetSubId)
        val advancedOptions = ConfigStore.lastAdvancedOptions(context)
        val applied = ConfigStore.lastApplied(context)
        val effectiveSubId = targetSubId ?: applied?.subId
        val result = if (
            coreResult.success &&
            advancedOptions != null &&
            applied != null &&
            effectiveSubId != null &&
            effectiveSubId == applied.subId
        ) {
            val advancedResult = PixelImsCompat.applyOptions(
                context,
                effectiveSubId,
                advancedOptions,
            )
            ConfigResult(
                success = advancedResult.success,
                message = context.getString(
                    R.string.reapply_combined_result,
                    coreResult.message,
                    advancedResult.message,
                ),
            )
        } else {
            coreResult
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
}
