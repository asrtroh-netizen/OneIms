package com.onetools.app.special.display

import android.content.Context
import android.os.PersistableBundle
import com.onetools.app.special.broker.SpecialBroker
import com.onetools.app.special.broker.SpecialErrors

/**
 * 信号格显示样式（对齐 OneIMS SignalBarSystemStyleManager 的核心写入语义）。
 * R1：格子模式独立写 inflate / LTE / parameters；不耦合能力页阈值开关。
 */
object SignalBarController {
    fun apply(
        context: Context,
        subId: Int,
        mode: SpecialFeatureStore.SignalBarMode,
    ): String {
        require(subId >= 0) { "Invalid subscription id" }
        if (SpecialBroker.getCarrierConfig(context, subId) == null) {
            error("当前系统暂不支持修改状态栏信号格样式")
        }
        return runCatching {
            SpecialFeatureStore.setSignalBarMode(context, subId, mode)
            ensureBaseline(context, subId)
            when (mode) {
                SpecialFeatureStore.SignalBarMode.AUTO -> {
                    val baseline = SpecialFeatureStore.readSignalBaseline(context, subId)
                        ?: return@runCatching "已保存「自动适配」；无需清除系统覆盖"
                    writePreset(context, subId, baseline.inflate, baseline.lte, baseline.nr, baseline.parameters)
                    "已恢复信号格显示样式为首次写入前的系统默认"
                }
                SpecialFeatureStore.SignalBarMode.FOUR_BARS -> {
                    writePreset(
                        context,
                        subId,
                        inflate = false,
                        lte = intArrayOf(-128, -118, -108, -98),
                        nr = intArrayOf(-110, -90, -80, -65),
                        parameters = 1,
                    )
                    check(verifyInflate(context, subId, expectInflate = false)) {
                        "Signal bar style readback mismatch"
                    }
                    "已应用固定 4 格信号显示样式"
                }
                SpecialFeatureStore.SignalBarMode.FIVE_BARS -> {
                    writePreset(
                        context,
                        subId,
                        inflate = true,
                        lte = intArrayOf(-125, -115, -105, -95),
                        nr = intArrayOf(-115, -105, -95, -85),
                        parameters = 1,
                    )
                    check(verifyInflate(context, subId, expectInflate = true)) {
                        "Signal bar style readback mismatch"
                    }
                    "已应用固定 5 格信号显示样式"
                }
            }
        }.getOrElse { error ->
            throw IllegalStateException(
                "信号格显示样式写入或回读失败：${SpecialErrors.describe(error)}",
                error,
            )
        }
    }

    private fun ensureBaseline(context: Context, subId: Int) {
        if (SpecialFeatureStore.hasSignalBaseline(context, subId)) return
        val bundle = SpecialBroker.getCarrierConfig(context, subId) ?: return
        val inflate = bundle.getBoolean(DisplayKeys.INFLATE_SIGNAL_STRENGTH_BOOL, false)
        val lte = bundle.getIntArray(DisplayKeys.LTE_RSRP_THRESHOLDS_INT_ARRAY)
            ?: intArrayOf(-128, -118, -108, -98)
        val nr = bundle.getIntArray(DisplayKeys.NR_SSRSRP_THRESHOLDS_INT_ARRAY)
            ?: intArrayOf(-110, -90, -80, -65)
        val parameters = bundle.getInt(DisplayKeys.PARAMETERS_USE_FOR_NR_SIGNAL_BAR_INT, 1)
        SpecialFeatureStore.saveSignalBaseline(context, subId, inflate, lte, nr, parameters)
    }

    private fun writePreset(
        context: Context,
        subId: Int,
        inflate: Boolean,
        lte: IntArray,
        nr: IntArray,
        parameters: Int,
    ) {
        val overrides = PersistableBundle().apply {
            putBoolean(DisplayKeys.INFLATE_SIGNAL_STRENGTH_BOOL, inflate)
            putIntArray(DisplayKeys.LTE_RSRP_THRESHOLDS_INT_ARRAY, lte)
            putIntArray(DisplayKeys.NR_SSRSRP_THRESHOLDS_INT_ARRAY, nr)
            putInt(DisplayKeys.PARAMETERS_USE_FOR_NR_SIGNAL_BAR_INT, parameters)
        }
        SpecialBroker.applyOverridesResilient(context, subId, overrides)
    }

    private fun verifyInflate(context: Context, subId: Int, expectInflate: Boolean): Boolean {
        val bundle = SpecialBroker.getCarrierConfig(context, subId) ?: return false
        return bundle.getBoolean(DisplayKeys.INFLATE_SIGNAL_STRENGTH_BOOL, false) == expectInflate
    }
}
