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
/** 首页设备详情卡用的结构化快照（只读）。 */
data class DeviceSnapshot(
    val versionName: String,
    val versionCode: Int,
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val tensorLabel: String,
    val simCount: Int,
    val delegateLabel: String,
    val strategyLabel: String,
) {
    fun modelTitle(): String = "$manufacturer $model".trim()
}

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

    /** 结构化快照，供首页设备详情卡绑定。 */
    @SuppressLint("MissingPermission")
    fun snapshot(context: Context): DeviceSnapshot {
        val patch = runCatching { Build.VERSION.SECURITY_PATCH }
            .getOrDefault(context.getString(R.string.dev_unknown))
        val tensor = context.getString(if (isTensor()) R.string.dev_tensor_yes else R.string.dev_tensor_no)
        val delegate = context.getString(
            if (SystemApiBroker.supportsDelegate()) R.string.dev_delegate_yes else R.string.dev_delegate_no,
        )
        val strategy = if (SystemApiBroker.lastStrategy == "unused") {
            context.getString(R.string.strategy_unused)
        } else {
            SystemApiBroker.lastStrategy
        }
        return DeviceSnapshot(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = patch,
            tensorLabel = tensor,
            simCount = simCount(context),
            delegateLabel = delegate,
            strategyLabel = strategy,
        )
    }

    /** 汇总为可直接展示的多行文本（随系统语言切中/英）。 */
    @SuppressLint("MissingPermission")
    fun summary(context: Context): String {
        val snap = snapshot(context)
        return buildString {
            append(
                context.getString(
                    R.string.dev_app_version,
                    snap.versionName,
                    snap.versionCode,
                ),
            ).append('\n')
            append(context.getString(R.string.dev_model, snap.manufacturer, snap.model)).append('\n')
            append(context.getString(R.string.dev_device, snap.device)).append('\n')
            append(context.getString(R.string.dev_android, snap.androidRelease, snap.sdkInt)).append('\n')
            append(context.getString(R.string.dev_patch, snap.securityPatch)).append('\n')
            append(context.getString(R.string.dev_tensor, snap.tensorLabel)).append('\n')
            append(context.getString(R.string.dev_sim_count, snap.simCount)).append('\n')
            append(context.getString(R.string.dev_delegate, snap.delegateLabel)).append('\n')
            append(context.getString(R.string.dev_last_strategy, snap.strategyLabel))
        }
    }
}
