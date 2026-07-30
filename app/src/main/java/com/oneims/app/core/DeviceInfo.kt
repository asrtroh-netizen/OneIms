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

    /** 供单测注入的 SoC 指纹；生产路径用 [socFingerprint]。 */
    data class SocFingerprint(
        val manufacturer: String,
        val model: String,
        val hardware: String,
        val board: String,
    ) {
        fun joined(): String =
            listOf(manufacturer, model, hardware, board)
                .joinToString(" ")
                .lowercase()
    }

    fun socFingerprint(): SocFingerprint {
        val manufacturer: String
        val model: String
        if (Build.VERSION.SDK_INT >= 31) {
            manufacturer = Build.SOC_MANUFACTURER.orEmpty()
            model = Build.SOC_MODEL.orEmpty()
        } else {
            manufacturer = ""
            model = ""
        }
        return SocFingerprint(
            manufacturer = manufacturer,
            model = model,
            hardware = Build.HARDWARE.orEmpty(),
            board = Build.BOARD.orEmpty(),
        )
    }

    /** 是否 Google Tensor 芯片（Pixel 6 起的机型才支持这套 IMS 强开）。 */
    fun isTensor(fp: SocFingerprint = socFingerprint()): Boolean {
        val soc = fp.joined()
        val hw = fp.hardware.lowercase()
        return soc.contains("google") || soc.contains("tensor") ||
            hw.contains("gs1") || hw.contains("gs2") || hw.contains("zuma")
    }

    /**
     * 是否联发科 / 天玑系 SoC。
     * 社区反馈：此类机型上 VoWiFi 强开常「改不了 / 不起作用」，软件仍可开门，但需诚实提示。
     */
    fun isMediaTek(fp: SocFingerprint = socFingerprint()): Boolean {
        val soc = fp.joined()
        return soc.contains("mediatek") ||
            soc.contains("mtsoc") ||
            Regex("""\bmtk\b""").containsMatchIn(soc) ||
            Regex("""\bmt[0-9]{3,4}\b""").containsMatchIn(soc) ||
            soc.contains("dimensity") ||
            soc.contains("天玑") ||
            soc.contains("helio")
    }

    /**
     * 是否高通 / 骁龙系 SoC（国内大量 VoWiFi 用户机型）。
     * 仅用于兼容提示与首页适配；不硬拦写入。
     */
    fun isQualcomm(fp: SocFingerprint = socFingerprint()): Boolean {
        val soc = fp.joined()
        return soc.contains("qualcomm") ||
            soc.contains("qcom") ||
            soc.contains("snapdragon") ||
            Regex("""\bsm\d{4}\b""").containsMatchIn(soc) ||
            Regex("""\bsdm\d{3,4}\b""").containsMatchIn(soc) ||
            Regex("""\bmsm\d+\b""").containsMatchIn(soc)
    }

    /** 人类可读 SoC 摘要（排障用）。 */
    fun socLabel(fp: SocFingerprint = socFingerprint()): String {
        val primary = listOf(fp.manufacturer, fp.model)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (primary.isNotEmpty()) return primary
        return listOf(fp.hardware, fp.board)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { "unknown" }
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

    /**
     * 是否为官方主推的 VoWiFi 强开路径（Tensor）。
     * 仅用于兼容提示 / 文案；**不**再硬拦写入——软件侧开门，OEM 是否生效无法左右。
     */
    fun supportsVowifiForceEnable(fp: SocFingerprint = socFingerprint()): Boolean = isTensor(fp)

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
            append(context.getString(R.string.dev_soc, socLabel())).append('\n')
            if (isMediaTek()) {
                append(context.getString(R.string.dev_mediatek_vowifi_note)).append('\n')
            }
            append(context.getString(R.string.dev_sim_count, snap.simCount)).append('\n')
            append(context.getString(R.string.dev_delegate, snap.delegateLabel)).append('\n')
            append(context.getString(R.string.dev_last_strategy, snap.strategyLabel))
        }
    }
}
