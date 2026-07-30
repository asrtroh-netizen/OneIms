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
     * 经 [CarrierConfigOverrideWriter] 清空 persistent override，是本工具的应急保命锁。
     */
    fun restoreDefaults(context: Context, subId: Int): ConfigResult {
        return try {
            val clear = CarrierConfigOverrideWriter.clearPersistentOverride(
                context = context,
                subId = subId,
                keys = emptyList(),
                reason = "restoreDefaults",
            )
            if (!clear.success) {
                return ConfigResult(false, clear.message)
            }
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
     * 只做网络类型名；不含体检里的 SIM/语音/数据探测。
     * 5G 显示增强已迁出 OneIMS，不再改写展示文案。
     */
    @SuppressLint("MissingPermission")
    fun currentNetworkTypeLabel(context: Context, subId: Int, tm: TelephonyManager? = null): String {
        val telephony = tm ?: run {
            val base = context.getSystemService(TelephonyManager::class.java)
            runCatching { base?.createForSubscriptionId(subId) }.getOrNull() ?: base
        }
        return runCatching {
            networkTypeName(context, telephony?.dataNetworkType ?: 0)
        }.getOrDefault(context.getString(R.string.net_unknown))
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
