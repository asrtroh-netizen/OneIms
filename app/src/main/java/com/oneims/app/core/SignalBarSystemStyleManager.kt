package com.oneims.app.core

import android.content.Context
import com.oneims.app.R

/**
 * 独家页「信号格显示样式」入口。
 *
 * 本地只更新格子模式偏好；系统写入时与能力页阈值开关组合，但两侧 prefs 互不派生、互不覆盖。
 * 只对传入的 [subId]（应为 UI 的 selectedSubId）操作。
 */
object SignalBarSystemStyleManager {

    fun applyAuto(context: Context, subId: Int): String =
        apply(context, subId, ConfigStore.SignalBarDisplayMode.AUTO)

    fun applyFourBars(context: Context, subId: Int): String =
        apply(context, subId, ConfigStore.SignalBarDisplayMode.FOUR_BARS)

    fun applyFiveBars(context: Context, subId: Int): String =
        apply(context, subId, ConfigStore.SignalBarDisplayMode.FIVE_BARS)

    fun clearOverride(context: Context, subId: Int): String = applyAuto(context, subId)

    /**
     * 保存格子模式 → 与当前阈值开关组合写系统 → 回读 inflate → 返回用户可读结果。
     */
    @Synchronized
    fun apply(
        context: Context,
        subId: Int,
        mode: ConfigStore.SignalBarDisplayMode,
    ): String {
        requireValidSubId(subId)
        if (SystemApiBroker.getCarrierConfig(context, subId) == null) {
            throw IllegalStateException(
                context.getString(R.string.signal_bar_style_unsupported),
            )
        }
        return runCatching {
            ConfigStore.setSignalBarDisplayMode(context, subId, mode)
            val adjustmentEnabled = ConfigStore.signalStrengthAdjustmentEnabled(context, subId)
            val systemChanged = SystemDisplayOverrideManager.applyIndependentSignalPreferences(
                context = context,
                subId = subId,
                adjustmentEnabled = adjustmentEnabled,
                barMode = mode,
            )
            if (mode != ConfigStore.SignalBarDisplayMode.AUTO) {
                check(verifyStyle(context, subId, mode)) {
                    "Signal bar style readback mismatch for subId=$subId mode=$mode"
                }
            }
            when {
                mode == ConfigStore.SignalBarDisplayMode.AUTO && systemChanged ->
                    context.getString(R.string.signal_bar_style_system_cleared)
                mode == ConfigStore.SignalBarDisplayMode.AUTO ->
                    context.getString(R.string.signal_bar_style_system_cleared_noop)
                else ->
                    context.getString(R.string.signal_bar_style_system_applied)
            }
        }.getOrElse { error ->
            if (error is IllegalStateException &&
                error.message == context.getString(R.string.signal_bar_style_unsupported)
            ) {
                throw error
            }
            throw IllegalStateException(
                context.getString(
                    R.string.signal_bar_style_system_failed,
                    OperationErrors.describe(error),
                ),
                error,
            )
        }
    }

    /**
     * 仅按 inflate 推断格子样式；阈值字段由能力页独立控制，不再要求整包等于四/五格预设。
     */
    fun readCurrentStyle(
        context: Context,
        subId: Int,
    ): ConfigStore.SignalBarDisplayMode? {
        requireValidSubId(subId)
        val current = SystemDisplayOverrideManager.peekSignalPreset(context, subId) ?: return null
        return when {
            current.inflateSignalStrength -> ConfigStore.SignalBarDisplayMode.FIVE_BARS
            else -> ConfigStore.SignalBarDisplayMode.FOUR_BARS
        }
    }

    fun verifyStyle(
        context: Context,
        subId: Int,
        expectedStyle: ConfigStore.SignalBarDisplayMode,
    ): Boolean {
        requireValidSubId(subId)
        if (expectedStyle == ConfigStore.SignalBarDisplayMode.AUTO) return false
        val current = SystemDisplayOverrideManager.peekSignalPreset(context, subId) ?: return false
        return when (expectedStyle) {
            ConfigStore.SignalBarDisplayMode.FOUR_BARS -> !current.inflateSignalStrength
            ConfigStore.SignalBarDisplayMode.FIVE_BARS -> current.inflateSignalStrength
            ConfigStore.SignalBarDisplayMode.AUTO -> false
        }
    }

    private fun requireValidSubId(subId: Int) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
    }
}
