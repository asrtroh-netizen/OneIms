package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import com.oneims.app.BuildConfig
import com.oneims.app.R

/**
 * 设备与系统信息 + 兼容性报告。
 *
 * 目的：各代 Pixel（6~10 / a 系 / Fold / Tablet）与不同 Android 版本行为差异大，
 * 把「型号 / 系统 / 补丁 / 芯片 / SIM 数 / 走哪条绕过策略」一屏透出，既方便用户，也利于排障。
 */
object DeviceInfo {

    /** 是否 Google Tensor 芯片（Pixel 6 起的机型才支持这套 IMS 强开）。 */
    fun isTensor(): Boolean {
        val soc = if (Build.VERSION.SDK_INT >= 31) {
            (Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL).lowercase()
        } else {
            (Build.HARDWARE + " " + Build.BOARD).lowercase()
        }
        return soc.contains("google") || soc.contains("tensor") ||
            Build.HARDWARE.lowercase().let { it.contains("gs1") || it.contains("gs2") || it.contains("zuma") }
    }

    @SuppressLint("MissingPermission")
    fun simCount(context: Context): Int = runCatching {
        context.getSystemService(SubscriptionManager::class.java)
            ?.activeSubscriptionInfoCount ?: 0
    }.getOrDefault(0)

    /** 汇总为可直接展示的多行文本（随系统语言切中/英）。 */
    @SuppressLint("MissingPermission")
    fun summary(context: Context): String {
        val patch = runCatching { Build.VERSION.SECURITY_PATCH }
            .getOrDefault(context.getString(R.string.dev_unknown))
        val tensor = context.getString(if (isTensor()) R.string.dev_tensor_yes else R.string.dev_tensor_no)
        val delegate = context.getString(
            if (SystemApiBroker.supportsDelegate()) R.string.dev_delegate_yes else R.string.dev_delegate_no,
        )
        // lastStrategy 内部 token "unused" 本地化展示，其余（delegate/shizuku-direct）为技术名原样透出
        val strategy = if (SystemApiBroker.lastStrategy == "unused") {
            context.getString(R.string.strategy_unused)
        } else {
            SystemApiBroker.lastStrategy
        }
        return buildString {
            append(
                context.getString(
                    R.string.dev_app_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                ),
            ).append('\n')
            append(context.getString(R.string.dev_model, Build.MANUFACTURER, Build.MODEL)).append('\n')
            append(context.getString(R.string.dev_device, Build.DEVICE)).append('\n')
            append(context.getString(R.string.dev_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT)).append('\n')
            append(context.getString(R.string.dev_patch, patch)).append('\n')
            append(context.getString(R.string.dev_tensor, tensor)).append('\n')
            append(context.getString(R.string.dev_sim_count, simCount(context))).append('\n')
            append(context.getString(R.string.dev_delegate, delegate)).append('\n')
            append(context.getString(R.string.dev_last_strategy, strategy))
        }
    }
}
