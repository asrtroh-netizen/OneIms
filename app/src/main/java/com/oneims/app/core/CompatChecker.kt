package com.oneims.app.core

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import com.oneims.app.R
import com.oneims.app.shizuku.ShizukuManager

/** 本机支持度结论。展示名走字符串资源 [labelRes]，支持中/英。 */
enum class SupportLevel(@StringRes val labelRes: Int) {
    FULL(R.string.support_full),
    DEGRADED(R.string.support_degraded),
    UNSUPPORTED(R.string.support_unsupported),
    NEED_SHIZUKU(R.string.support_need_shizuku),
}

data class CompatReport(
    val level: SupportLevel,
    val lines: List<String>,
) {
    fun asText(): String = lines.joinToString("\n")
}

/**
 * 本机兼容性体检（只读探测，绝不改任何配置）。
 *
 * 逐项核查后给「本机支持度」结论，帮用户在真正动手前就知道自己这台机能不能用、走哪条路，
 * 这是「不同机器一定要适配」的第一道关。全部结论文案走字符串资源，随系统语言切中/英。
 */
object CompatChecker {

    fun run(context: Context): CompatReport {
        val lines = mutableListOf<String>()

        val tensor = DeviceInfo.isTensor()
        lines += context.getString(
            R.string.compat_tensor,
            context.getString(if (tensor) R.string.compat_yes else R.string.compat_tensor_no),
        )

        val sdkOk = Build.VERSION.SDK_INT >= 31
        lines += context.getString(
            R.string.compat_os, Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
            if (sdkOk) context.getString(R.string.compat_yes) else context.getString(R.string.compat_os_bad),
        )

        val shizukuRunning = ShizukuManager.isRunning()
        val shizukuGranted = ShizukuManager.isGranted()
        lines += context.getString(
            R.string.compat_shizuku,
            context.getString(
                when {
                    shizukuGranted -> R.string.compat_shizuku_granted
                    shizukuRunning -> R.string.compat_shizuku_running
                    else -> R.string.compat_shizuku_off
                },
            ),
        )

        val delegate = SystemApiBroker.supportsDelegate()
        lines += context.getString(
            R.string.compat_strategy,
            context.getString(if (delegate) R.string.compat_strategy_delegate else R.string.compat_strategy_direct),
        )

        val simCount = DeviceInfo.simCount(context)
        lines += context.getString(
            R.string.compat_sim, simCount,
            if (simCount > 0) context.getString(R.string.compat_yes) else context.getString(R.string.compat_sim_no),
        )

        val level = when {
            !tensor -> SupportLevel.UNSUPPORTED
            !sdkOk -> SupportLevel.UNSUPPORTED
            !shizukuGranted -> SupportLevel.NEED_SHIZUKU
            delegate -> SupportLevel.FULL
            else -> SupportLevel.DEGRADED
        }
        lines += context.getString(R.string.compat_conclusion, context.getString(level.labelRes))
        lines += context.getString(
            when (level) {
                SupportLevel.FULL -> R.string.compat_advice_full
                SupportLevel.DEGRADED -> R.string.compat_advice_degraded
                SupportLevel.NEED_SHIZUKU -> R.string.compat_advice_need_shizuku
                SupportLevel.UNSUPPORTED -> R.string.compat_advice_unsupported
            },
        )
        return CompatReport(level, lines)
    }
}
