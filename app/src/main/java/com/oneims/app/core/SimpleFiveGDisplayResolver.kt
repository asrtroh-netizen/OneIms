package com.oneims.app.core

import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager

/**
 * 「5G 显示增强」的持久化配置。四个阈值继续只驱动 OneIms 内部展示；系统图标配置由
 * [SystemDisplayOverrideManager] 在用户明确点击应用时尝试写入当前 selectedSubId。
 * 阈值单位均为 Mbps，UI 层负责夹在 1..10000 之间，非法输入回退默认值。
 */
data class SimpleFiveGDisplayConfig(
    val enabled: Boolean = false,
    val mode: String = Mode.CN_SPEED,
    val plusDlThresholdMbps: Int = 300,
    val fiveGaDlThresholdMbps: Int = 1000,
    val uplinkEnhancedThresholdMbps: Int = 50,
    val superUplinkThresholdMbps: Int = 300,
    val systemIconConfigString: String = DEFAULT_SYSTEM_ICON_CONFIG,
) {
    /** 显示方式取值集中放这里，避免 UI 层与持久化层各写一份字符串常量而漂移。 */
    object Mode {
        const val CONSERVATIVE = "CONSERVATIVE"
        const val CN_SPEED = "CN_SPEED"
        const val COOL = "COOL"
        const val CUSTOM = "CUSTOM"
    }

    companion object {
        const val DEFAULT_SYSTEM_ICON_CONFIG =
            "connected_mmwave:5G_PLUS,connected:5G_PLUS,not_restricted_rrc_idle:5G"
    }
}

/**
 * 一次解析结果。[enabled] 为 false 时表示「本次不生效」（开关关闭，或识别不到 5G），
 * 调用方此时应保持原有展示不变，不要用空字符串覆盖已有内容。
 */
data class SimpleFiveGDisplayResult(
    val enabled: Boolean,
    val title: String,
    val networkType: String,
    val speedLine: String,
)

/** 解析器只消费本地化标签，避免 core 纯函数把中文硬编码带进英语界面。 */
data class SimpleFiveGDisplayLabels(
    val unknownOperator: String,
    val uplinkEnhanced: String,
    val superUplink: String,
    val coolFiveGPlus: String,
    val coolFiveGa: String,
)

/**
 * 「5G 显示增强」的唯一判定入口——纯函数，不依赖 Context/Telephony 运行时状态，方便单测。
 * 所有页面（首页/排障页等）想要更直观的 5G 文案，都应该调这一个函数，不要各自重复判断逻辑。
 *
 * 实时信号（[dataNetworkType]/[overrideNetworkType]/[serviceStateText]/[downlinkMbps]/[uplinkMbps]）
 * 由调用方负责采集（见 [FiveGSignalReader]），本函数只做“给定信号 → 该显示什么文案”的映射，
 * 不主动读取 Telephony API，避免解析逻辑与系统调用耦合。
 *
 * 速率数据缺失（[downlinkMbps]/[uplinkMbps] 传 null）时按 0 处理，即只会落在“未达阈值”的分支，
 * 结果自然退化为 SA/NSA/Advanced 的基础展示，不会抛异常、不会显示错误文案。
 */
fun resolveSimpleFiveGDisplay(
    config: SimpleFiveGDisplayConfig,
    operatorName: String?,
    dataNetworkType: Int,
    overrideNetworkType: Int,
    serviceStateText: String?,
    downlinkMbps: Double?,
    uplinkMbps: Double?,
    labels: SimpleFiveGDisplayLabels,
): SimpleFiveGDisplayResult {
    val ss = serviceStateText.orEmpty()
    val op = operatorName?.takeIf { it.isNotBlank() } ?: labels.unknownOperator

    val isSa = dataNetworkType == TelephonyManager.NETWORK_TYPE_NR ||
        ss.contains("NR_SA", ignoreCase = true)

    val isNsa = overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
        ss.contains("NR_NSA", ignoreCase = true)

    val isAdvanced = overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED

    val isFiveG = isSa || isNsa || isAdvanced

    if (!config.enabled || !isFiveG) {
        return SimpleFiveGDisplayResult(
            enabled = false,
            title = "",
            networkType = "",
            speedLine = "",
        )
    }

    val dl = downlinkMbps ?: 0.0
    val ul = uplinkMbps ?: 0.0
    val thresholds = if (config.mode == SimpleFiveGDisplayConfig.Mode.CUSTOM) {
        config
    } else {
        SimpleFiveGDisplayConfig()
    }

    val networkType = when {
        isAdvanced -> "$op · 5G Advanced"
        isSa -> "$op · 5G SA"
        isNsa -> "$op · 5G NSA"
        else -> "$op · 5G"
    }

    val speedLine = buildString {
        if (dl > 0) append("DL ${dl.toInt()} Mbps")
        if (ul > 0) {
            if (isNotEmpty()) append(" · ")
            append("UL ${ul.toInt()} Mbps")
        }

        val ulText = when {
            ul >= thresholds.superUplinkThresholdMbps -> labels.superUplink
            ul >= thresholds.uplinkEnhancedThresholdMbps -> labels.uplinkEnhanced
            else -> ""
        }

        if (ulText.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(ulText)
        }
    }

    val title = when (config.mode) {
        SimpleFiveGDisplayConfig.Mode.CONSERVATIVE -> when {
            isAdvanced -> "5G Advanced"
            isSa -> "5G SA"
            isNsa -> "5G NSA"
            else -> "5G"
        }

        SimpleFiveGDisplayConfig.Mode.COOL -> when {
            isAdvanced -> labels.coolFiveGa
            isSa && dl >= thresholds.fiveGaDlThresholdMbps -> labels.coolFiveGa
            isNsa && dl >= thresholds.fiveGaDlThresholdMbps -> labels.coolFiveGPlus
            dl >= thresholds.plusDlThresholdMbps -> labels.coolFiveGPlus
            isSa -> "5G SA"
            isNsa -> "5G NSA"
            else -> "5G"
        }

        // CUSTOM 使用用户阈值；CN_SPEED 使用默认阈值。共同走同一套状态映射，
        // 避免切回内地速率模式后继续误用隐藏的自定义值。
        // 说明：系统状态栏 CarrierConfig 通常只有 5G / 5G_PLUS；「5G-A」主要是应用内文案。
        // NR Advanced 网络本身即按 5G-A 展示，不再强依赖下行是否冲过阈值。
        SimpleFiveGDisplayConfig.Mode.CUSTOM,
        SimpleFiveGDisplayConfig.Mode.CN_SPEED,
        -> when {
            isAdvanced -> "5G-A"
            isSa && dl >= thresholds.fiveGaDlThresholdMbps -> "5G-A"
            isNsa && dl >= thresholds.fiveGaDlThresholdMbps -> "5G+"
            dl >= thresholds.plusDlThresholdMbps -> "5G+"
            isSa -> "5G SA"
            isNsa -> "5G NSA"
            else -> "5G"
        }

        else -> when {
            isSa -> "5G SA"
            isNsa -> "5G NSA"
            isAdvanced -> "5G Advanced"
            else -> "5G"
        }
    }

    return SimpleFiveGDisplayResult(
        enabled = true,
        title = title,
        networkType = networkType,
        speedLine = speedLine,
    )
}
