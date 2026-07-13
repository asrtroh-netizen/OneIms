package com.oneims.app.model

import androidx.annotation.StringRes
import com.oneims.app.R

/**
 * VoWiFi（WiFi Calling）呼叫模式。
 * 取值与 AOSP `ImsMmTelManager.WIFI_MODE_*` 常量严格对齐，切勿改动数值。
 * 展示名走字符串资源 [labelRes]，支持中/英；取值 [value] 不受影响。
 */
enum class WfcMode(val value: Int, @StringRes val labelRes: Int) {
    WIFI_ONLY(0, R.string.wfc_wifi_only),
    CELLULAR_PREFERRED(1, R.string.wfc_cellular_preferred),
    WIFI_PREFERRED(2, R.string.wfc_wifi_preferred);

    companion object {
        fun of(value: Int): WfcMode = entries.firstOrNull { it.value == value } ?: CELLULAR_PREFERRED
    }
}

/** 单个 SIM 卡（订阅）的精简信息。 */
data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierId: Int,
    val displayName: String,
    val carrierName: String,
    val mcc: String,
    val mnc: String,
)

/** 一次配置写入操作的结果。 */
data class ConfigResult(
    val success: Boolean,
    val message: String,
    val detail: Map<String, Boolean> = emptyMap(),
)

/** IMS 注册态诊断结果。 */
data class ImsStatus(
    val volteRegistered: Boolean,
    val vowifiRegistered: Boolean,
    val rawText: String,
)

/**
 * 基本通信健康报告（最高优先级保障：电话 / 数据 / 短信）。
 * OneIms 铁律：任何操作都不得让这三项从「正常」变「异常」。
 */
data class HealthReport(
    val simReady: Boolean,        // SIM 就绪
    val dataCapable: Boolean,     // 数据（上网）能力
    val voiceCapable: Boolean,    // 语音（打电话）能力
    val networkType: String,      // 当前数据网络类型
    val detail: String,
) {
    /** 三项核心能力是否都健康。 */
    val allHealthy: Boolean get() = simReady && dataCapable && voiceCapable
}

/**
 * ePDG（VoWiFi 网关）连通性自检结果。
 * 区分「配置没开」还是「运营商没放通 ePDG」这两类完全不同的问题。
 */
sealed class EpdgResult {
    data class Reachable(val host: String, val ip: String) : EpdgResult()
    data class DnsFail(val host: String) : EpdgResult()
    data class PortUnreachable(val host: String, val ip: String) : EpdgResult()
    data class Unavailable(val reason: String) : EpdgResult()
}

/**
 * 应用内检查更新结果（对接 GitHub Release，对齐 carrier-ims「应用维护」）。
 * @property hasUpdate 远端版本是否比当前新
 * @property currentVersion 当前 App versionName
 * @property latestVersion 远端最新 tag（已去 v 前缀展示）
 * @property downloadUrl 最新 Release 里 .apk 资产的直链（无则空串）
 * @property releaseNotes Release 说明正文（可能为空）
 * @property message 面向用户的一句话结论（成功/无更新/失败原因）
 */
data class UpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val message: String,
)
