package com.oneims.app.core

import android.content.Context
import com.oneims.app.R

/**
 * 独家页「信号格显示样式」的系统全局尝试入口。
 *
 * 只对传入的 [subId]（应为 UI 的 selectedSubId）做 CarrierConfig override / 基线恢复，
 * 不默认卡1、不默认数据卡、不默认 slot0。UI 层负责展示「当前目标」。
 *
 * 底层复用 [SystemDisplayOverrideManager] 的四键预设与 ownership，避免与能力页
 * 「5G信号强度调整」各造一套 baseline。
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
     * 保存本地选择 → 对 [subId] 写入/清除系统配置 → 回读验证 → 返回用户可读结果。
     * 失败抛 [IllegalStateException]，不假成功；本地选择在模式切换时已由 UI 保留。
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
            val preset = signalBarSystemPreset(mode)
            val systemChanged = if (preset == null) {
                SystemDisplayOverrideManager.restoreSignalBarOverride(context, subId)
            } else {
                SystemDisplayOverrideManager.applySignalStrengthPreset(context, subId, preset)
            }
            ConfigStore.setSignalBarDisplayMode(context, subId, mode)
            if (preset != null) {
                check(verifyStyle(context, subId, mode)) {
                    "Signal bar style readback mismatch for subId=$subId mode=$mode"
                }
            }
            when {
                preset == null && systemChanged ->
                    context.getString(R.string.signal_bar_style_system_cleared)
                preset == null ->
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
     * 回读当前卡生效的信号预设，映射为样式；无法匹配四/五格预设时返回 null
     *（可能是运营商默认或外部改动）。
     */
    fun readCurrentStyle(
        context: Context,
        subId: Int,
    ): ConfigStore.SignalBarDisplayMode? {
        requireValidSubId(subId)
        val current = SystemDisplayOverrideManager.peekSignalPreset(context, subId) ?: return null
        return when {
            SystemDisplayOwnershipPolicy.signalPresetsEqual(current, fourBarSignalPreset()) ->
                ConfigStore.SignalBarDisplayMode.FOUR_BARS
            SystemDisplayOwnershipPolicy.signalPresetsEqual(current, fiveBarSignalPreset()) ->
                ConfigStore.SignalBarDisplayMode.FIVE_BARS
            else -> null
        }
    }

    fun verifyStyle(
        context: Context,
        subId: Int,
        expectedStyle: ConfigStore.SignalBarDisplayMode,
    ): Boolean {
        requireValidSubId(subId)
        val expected = signalBarSystemPreset(expectedStyle) ?: return false
        return SystemDisplayOverrideManager.verifySignalBarConfig(context, subId, expected)
    }

    private fun requireValidSubId(subId: Int) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
    }
}
