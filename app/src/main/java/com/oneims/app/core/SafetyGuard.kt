package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyManager
import com.oneims.app.R
import com.oneims.app.model.ConfigResult
import com.oneims.app.model.HealthReport

/**
 * OneIms 安全护栏 —— 最高优先级铁律的守护者。
 *
 * 用户铁律：电话 / 数据上网 / 短信 = 权重最高，绝不能被本工具搞挂；VoWiFi 其次。
 * 本类提供两把「保命锁」：
 *   1. [healthCheck]：核查 SIM / 数据 / 语音三项核心能力是否健康；
 *   2. [restoreDefaults]：一键清空所有 CarrierConfig 覆盖，恢复运营商默认（应急回滚）。
 *
 * 配合 ImsController：任何写入操作，若「写后健康检查」发现三项核心能力从正常变异常，
 * 立即自动 restoreDefaults 回滚，宁可不开 IMS，也绝不断了基本通信。
 */
object SafetyGuard {

    /** 采集基本通信健康状态。只读，不改任何配置。 */
    @SuppressLint("MissingPermission")
    fun healthCheck(context: Context, subId: Int): HealthReport {
        val base = context.getSystemService(TelephonyManager::class.java)
        val tm = runCatching { base?.createForSubscriptionId(subId) }.getOrNull() ?: base

        val simReady = runCatching { base?.simState == TelephonyManager.SIM_STATE_READY }
            .getOrDefault(false)

        val voiceCapable = runCatching { base?.isVoiceCapable == true }.getOrDefault(false)

        val dataEnabled = runCatching {
            // isDataEnabled 在 API 26+ 可用
            TelephonyManager::class.java.getMethod("isDataEnabled").invoke(tm) as Boolean
        }.getOrDefault(false)

        val dataState = runCatching { tm?.dataState ?: TelephonyManager.DATA_UNKNOWN }
            .getOrDefault(TelephonyManager.DATA_UNKNOWN)
        val dataConnectedOrEnabled =
            dataEnabled || dataState == TelephonyManager.DATA_CONNECTED

        val netType = currentNetworkTypeLabel(context, subId, tm)

        val yes = context.getString(R.string.yes)
        val no = context.getString(R.string.no)
        val detail = buildString {
            append(context.getString(R.string.health_sim_ready, if (simReady) yes else no)).append('\n')
            append(context.getString(R.string.health_voice, if (voiceCapable) yes else no)).append('\n')
            append(context.getString(R.string.health_data, if (dataConnectedOrEnabled) yes else no)).append('\n')
            append(context.getString(R.string.health_net_type, netType))
        }

        return HealthReport(
            simReady = simReady,
            dataCapable = dataConnectedOrEnabled,
            voiceCapable = voiceCapable,
            networkType = netType,
            detail = detail,
        )
    }

    /**
     * 一键还原：清空指定 SIM 的所有 CarrierConfig 覆盖，恢复运营商默认。
     * 用 overrideConfig(subId, null, false) 实现，是本工具的应急保命锁。
     */
    fun restoreDefaults(context: Context, subId: Int): ConfigResult {
        return try {
            // overrideConfig(bundle=null) 即清空覆盖；内部会选择 Instrumentation 或 root 直调。
            SystemApiBroker.overrideConfig(context, subId, null, false)
            val displayOwnershipCleanup = runCatching {
                SystemDisplayOverrideManager.onAllOverridesCleared(context, subId)
            }
            // 即使本地显示 ownership 清理异常，也不能留下会被守护稍后重放的核心配置。
            ConfigStore.clearAppliedProfiles(context)
            ConfigStore.setFiveGDisplayConfig(context, SimpleFiveGDisplayConfig())
            ConfigStore.setSignalStrengthAdjustmentEnabled(context, subId, false)
            ConfigStore.setSignalBarDisplayMode(
                context,
                subId,
                ConfigStore.SignalBarDisplayMode.AUTO,
            )
            displayOwnershipCleanup.getOrThrow()
            ConfigResult(true, context.getString(R.string.msg_restore_ok))
        } catch (e: Throwable) {
            ConfigResult(
                false,
                context.getString(R.string.msg_restore_failed, OperationErrors.describe(e)),
            )
        }
    }

    /**
     * 只做「网络类型名 + 5G 显示增强」这一小步，不含 [healthCheck] 里 SIM 就绪/语音/数据状态那些
     * 检测；轻量状态展示只需调用这个函数，不要为了拿网络类型顺带跑一整套体检。
     */
    @SuppressLint("MissingPermission")
    fun currentNetworkTypeLabel(context: Context, subId: Int, tm: TelephonyManager? = null): String {
        val telephony = tm ?: run {
            val base = context.getSystemService(TelephonyManager::class.java)
            runCatching { base?.createForSubscriptionId(subId) }.getOrNull() ?: base
        }
        return runCatching {
            val baseName = networkTypeName(context, telephony?.dataNetworkType ?: 0)
            enhanceFiveGDisplay(context, subId, baseName)
        }.getOrDefault(context.getString(R.string.net_unknown))
    }

    /**
     * 「5G 显示增强」（实验功能）接入点：仅当用户在实验功能页开启该开关时才生效，
     * 关闭时原样返回 [baseName]，不改变体检展示的既有行为。只读取信号、只影响这一行文案，
     * 不写 CarrierConfig、不碰 IMS/VoLTE/VoWiFi/VoNR 逻辑、不影响状态栏。
     */
    private fun enhanceFiveGDisplay(context: Context, subId: Int, baseName: String): String {
        val config = ConfigStore.fiveGDisplayConfig(context)
        if (!config.enabled) return baseName
        val signal = FiveGSignalReader.read(context, subId)
        val result = resolveSimpleFiveGDisplay(
            config = config,
            operatorName = null,
            dataNetworkType = signal.dataNetworkType,
            overrideNetworkType = signal.overrideNetworkType,
            serviceStateText = signal.serviceStateText,
            downlinkMbps = signal.downlinkMbps,
            uplinkMbps = signal.uplinkMbps,
            labels = SimpleFiveGDisplayLabels(
                unknownOperator = context.getString(R.string.five_g_unknown_operator),
                uplinkEnhanced = context.getString(R.string.five_g_uplink_enhanced),
                superUplink = context.getString(R.string.five_g_super_uplink),
                coolFiveGPlus = context.getString(R.string.five_g_cool_plus),
                coolFiveGa = context.getString(R.string.five_g_cool_a),
            ),
        )
        return if (result.enabled && result.title.isNotBlank()) result.title else baseName
    }

    private fun networkTypeName(context: Context, type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> context.getString(R.string.net_5g)
        TelephonyManager.NETWORK_TYPE_LTE -> context.getString(R.string.net_4g)
        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_HSDPA -> context.getString(R.string.net_3g)
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_GPRS -> context.getString(R.string.net_2g)
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> context.getString(R.string.net_none)
        else -> context.getString(R.string.net_other, type)
    }
}
