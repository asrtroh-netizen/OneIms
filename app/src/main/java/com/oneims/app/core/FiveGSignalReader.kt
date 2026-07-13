package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager

/**
 * 「5G 显示增强」需要的原始信号快照。只读采集，不写任何配置，不影响 IMS/CarrierConfig/APN。
 *
 * 字段的真实性边界（如实披露，不为了凑参数而假装能读到）：
 *   - [dataNetworkType]：[TelephonyManager.getDataNetworkType] 同步可读，与 [SafetyGuard] 现有
 *     体检逻辑同源，可靠。
 *   - [serviceStateText]：[TelephonyManager.getServiceState] 同步可读的 `toString()`；不同 Android
 *     版本/厂商的字段格式不完全一致，只作为 [resolveSimpleFiveGDisplay] 里 "NR_SA/NR_NSA 字符串
 *     兜底匹配" 的最佳努力来源，不保证每台设备都命中。
 *   - [overrideNetworkType]：[TelephonyDisplayInfo] 的 NSA/Advanced 判定官方只通过
 *     `TelephonyCallback.DisplayInfoListener` 异步回调获取，没有同步 getter。本项目现有架构里
 *     "体检/诊断" 都是一次性同步读取（对齐 [SafetyGuard.healthCheck]），为了保持"先实现简单稳定
 *     版本、不为这个功能大改测速/监听模块"的既定范围，这里暂不注册常驻监听器，固定返回
 *     [TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE]——即 NSA/Advanced 的精确识别目前主要依赖
 *     上面的 [serviceStateText] 字符串兜底。这是当前版本已知的能力边界，非隐藏缺陷。
 *   - [downlinkMbps]/[uplinkMbps]：项目当前没有任何实时测速数据源，固定传 null，交给
 *     [resolveSimpleFiveGDisplay] 按 0 处理（自然退化为 SA/NSA/Advanced 基础展示，不崩溃）。
 */
data class FiveGSignal(
    val dataNetworkType: Int,
    val overrideNetworkType: Int,
    val serviceStateText: String?,
    val downlinkMbps: Double?,
    val uplinkMbps: Double?,
)

object FiveGSignalReader {

    @SuppressLint("MissingPermission")
    fun read(context: Context, subId: Int): FiveGSignal {
        val base = context.getSystemService(TelephonyManager::class.java)
        val tm = runCatching { base?.createForSubscriptionId(subId) }.getOrNull() ?: base

        val dataNetworkType = runCatching { tm?.dataNetworkType }
            .getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
            ?: TelephonyManager.NETWORK_TYPE_UNKNOWN

        val serviceStateText = runCatching { tm?.serviceState?.toString() }.getOrNull()

        return FiveGSignal(
            dataNetworkType = dataNetworkType,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
            serviceStateText = serviceStateText,
            downlinkMbps = null,
            uplinkMbps = null,
        )
    }
}
