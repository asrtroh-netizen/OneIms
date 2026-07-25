package com.oneims.app.core

import android.content.Context
import android.os.PersistableBundle
import com.oneims.app.R
import com.oneims.app.model.ConfigResult
import java.util.Locale

/**
 * Pixel IMS 公开功能的独立兼容实现。
 *
 * 这里只对齐公开可观察的 CarrierConfig 行为，不复制 GPL 源码。常用能力继续留在
 * [ImsController]；本模块收敛显示选项、漫游选项、完整导出与受约束专家编辑，
 * 避免把低频高级逻辑堆回主业务控制器。
 */
data class PixelImsOptions(
    val wfcRoamingEnabled: Boolean = false,
    val showWfcMode: Boolean = false,
    val showWfcRoamingMode: Boolean = false,
    val supportWifiOnly: Boolean = false,
    val allowAddingApns: Boolean = false,
    val showVowifiIcon: Boolean = false,
    val alwaysShowDataRatIcon: Boolean = false,
    val show4gForLteIcon: Boolean = false,
    val hideLtePlusIcon: Boolean = false,
    val showImsStatus: Boolean = false,
    val ssOverCdma: Boolean = false,
    val enhanced4g: Boolean = false,
)

internal object ExpertConfigPolicy {
    private const val MAX_KEY_LENGTH = 160
    private const val MAX_VALUE_LENGTH = 2_048
    private val keyPattern = Regex("[a-z0-9_.]+")
    private val blockedFragments = listOf(
        "allowed_network_types",
        "preferred_network",
        "network_selection",
        "emergency",
        "satellite",
        "carrier_certificate",
    )

    fun validateKey(key: String): Boolean {
        val normalized = key.trim().lowercase(Locale.ROOT)
        return normalized.length in 1..MAX_KEY_LENGTH &&
            keyPattern.matches(normalized) &&
            blockedFragments.none(normalized::contains) &&
            normalized !in CarrierConfigKeys.specializedManagerKeys
    }

    fun validateValue(rawValue: String): Boolean =
        rawValue.length <= MAX_VALUE_LENGTH &&
            rawValue.none { char -> char.code < 0x20 }
}

object PixelImsCompat {

    fun readOptions(context: Context, subId: Int): PixelImsOptions {
        val config = SystemApiBroker.getCarrierConfig(context, subId) ?: return PixelImsOptions()
        return PixelImsOptions(
            wfcRoamingEnabled = config.getBoolean(CarrierConfigKeys.WFC_DEFAULT_ROAMING_ENABLED),
            showWfcMode = config.getBoolean(CarrierConfigKeys.EDITABLE_WFC_MODE),
            showWfcRoamingMode = config.getBoolean(CarrierConfigKeys.EDITABLE_WFC_ROAMING_MODE),
            supportWifiOnly = config.getBoolean(CarrierConfigKeys.WFC_SUPPORTS_WIFI_ONLY),
            allowAddingApns = config.getBoolean(CarrierConfigKeys.ALLOW_ADDING_APNS),
            showVowifiIcon =
                config.getBoolean(CarrierConfigKeys.SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR),
            alwaysShowDataRatIcon =
                config.getBoolean(CarrierConfigKeys.ALWAYS_SHOW_DATA_RAT_ICON),
            show4gForLteIcon =
                config.getBoolean(CarrierConfigKeys.SHOW_4G_FOR_LTE_DATA_ICON),
            hideLtePlusIcon =
                config.getBoolean(CarrierConfigKeys.HIDE_LTE_PLUS_DATA_ICON),
            showImsStatus =
                config.getBoolean(CarrierConfigKeys.SHOW_IMS_REGISTRATION_STATUS),
            ssOverCdma = config.getBoolean(CarrierConfigKeys.SUPPORT_SS_OVER_CDMA),
            enhanced4g =
                config.getBoolean(CarrierConfigKeys.EDITABLE_ENHANCED_4G_LTE) &&
                    config.getBoolean(CarrierConfigKeys.ENHANCED_4G_LTE_ON_BY_DEFAULT) &&
                    !config.getBoolean(CarrierConfigKeys.HIDE_ENHANCED_4G_LTE),
        )
    }

    fun applyOptions(
        context: Context,
        subId: Int,
        options: PixelImsOptions,
    ): ConfigResult {
        return try {
            val before = SafetyGuard.healthCheck(context, subId)
            val overrides = PersistableBundle().apply {
                putBoolean(
                    CarrierConfigKeys.WFC_DEFAULT_ROAMING_ENABLED,
                    options.wfcRoamingEnabled,
                )
                putBoolean(CarrierConfigKeys.EDITABLE_WFC_MODE, options.showWfcMode)
                putBoolean(
                    CarrierConfigKeys.EDITABLE_WFC_ROAMING_MODE,
                    options.showWfcRoamingMode,
                )
                putBoolean(CarrierConfigKeys.WFC_SUPPORTS_WIFI_ONLY, options.supportWifiOnly)
                putBoolean(CarrierConfigKeys.ALLOW_ADDING_APNS, options.allowAddingApns)
                putBoolean(
                    CarrierConfigKeys.SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR,
                    options.showVowifiIcon,
                )
                putBoolean(
                    CarrierConfigKeys.ALWAYS_SHOW_DATA_RAT_ICON,
                    options.alwaysShowDataRatIcon,
                )
                putBoolean(
                    CarrierConfigKeys.SHOW_4G_FOR_LTE_DATA_ICON,
                    options.show4gForLteIcon,
                )
                putBoolean(CarrierConfigKeys.HIDE_LTE_PLUS_DATA_ICON, options.hideLtePlusIcon)
                putBoolean(
                    CarrierConfigKeys.SHOW_IMS_REGISTRATION_STATUS,
                    options.showImsStatus,
                )
                putBoolean(CarrierConfigKeys.SUPPORT_SS_OVER_CDMA, options.ssOverCdma)
                putBoolean(CarrierConfigKeys.EDITABLE_ENHANCED_4G_LTE, options.enhanced4g)
                putBoolean(CarrierConfigKeys.ENHANCED_4G_LTE_ON_BY_DEFAULT, options.enhanced4g)
                putBoolean(CarrierConfigKeys.HIDE_ENHANCED_4G_LTE, !options.enhanced4g)
            }
            run {
                val write = CarrierConfigOverrideWriter.applyPersistentOverride(
                    context, subId, overrides, reason = "PixelImsCompat",
                )
                check(write.success) { write.message }
            }
            rollbackIfCommunicationDegraded(context, subId, before)?.let { return it }
            ConfigStore.saveAdvancedOptions(context, options, subId)
            ConfigResult(true, context.getString(R.string.msg_pixel_ims_options_applied))
        } catch (error: Throwable) {
            ConfigResult(
                false,
                context.getString(
                    R.string.msg_pixel_ims_options_failed,
                    OperationErrors.describe(error),
                ),
            )
        }
    }

    /**
     * 专家编辑只允许修改当前 CarrierConfig 中已存在、类型可识别且不命中通信红线的键。
     * 这保留 Pixel IMS 的专家能力，同时避免任意构造网络选择/紧急通信相关配置。
     */
    @Suppress("DEPRECATION")
    fun applyExpertValue(
        context: Context,
        subId: Int,
        keyInput: String,
        rawValue: String,
    ): ConfigResult {
        val key = keyInput.trim().lowercase(Locale.ROOT)
        if (!ExpertConfigPolicy.validateKey(key) || !ExpertConfigPolicy.validateValue(rawValue)) {
            return ConfigResult(false, context.getString(R.string.msg_expert_invalid_input))
        }
        val current = SystemApiBroker.getCarrierConfig(context, subId)
            ?: return ConfigResult(false, context.getString(R.string.config_dump_failed))
        if (!current.containsKey(key)) {
            return ConfigResult(false, context.getString(R.string.msg_expert_unknown_key, key))
        }

        return try {
            val before = SafetyGuard.healthCheck(context, subId)
            val overrides = PersistableBundle()
            putTypedValue(overrides, key, current.get(key), rawValue)
            run {
                val write = CarrierConfigOverrideWriter.applyPersistentOverride(
                    context, subId, overrides, reason = "PixelImsCompat",
                )
                check(write.success) { write.message }
            }
            rollbackIfCommunicationDegraded(context, subId, before)?.let { return it }
            ConfigResult(true, context.getString(R.string.msg_expert_applied, key))
        } catch (error: Throwable) {
            ConfigResult(
                false,
                context.getString(
                    R.string.msg_expert_failed,
                    OperationErrors.describe(error),
                ),
            )
        }
    }

    @Suppress("DEPRECATION")
    fun dumpFullConfig(context: Context, subId: Int): String {
        val config = SystemApiBroker.getCarrierConfig(context, subId)
            ?: return context.getString(R.string.config_dump_failed)
        val lines = config.keySet()
            .sorted()
            .map { key -> "$key = ${formatValue(config.get(key))}" }
        return buildString {
            append(context.getString(R.string.config_full_dump_title, subId))
            append('\n')
            append(lines.joinToString("\n"))
            append('\n')
            append(context.getString(R.string.config_full_dump_footer, lines.size))
        }
    }

    private fun putTypedValue(
        bundle: PersistableBundle,
        key: String,
        current: Any?,
        rawValue: String,
    ) {
        when (current) {
            is Boolean -> bundle.putBoolean(key, parseBoolean(rawValue))
            is Int -> bundle.putInt(key, rawValue.trim().toInt())
            is Long -> bundle.putLong(key, rawValue.trim().toLong())
            is Double -> bundle.putDouble(key, parseFiniteDouble(rawValue))
            is String -> bundle.putString(key, rawValue)
            is BooleanArray -> bundle.putBooleanArray(
                key,
                parseList(rawValue).map(::parseBoolean).toBooleanArray(),
            )
            is IntArray -> bundle.putIntArray(
                key,
                parseList(rawValue).map(String::toInt).toIntArray(),
            )
            is LongArray -> bundle.putLongArray(
                key,
                parseList(rawValue).map(String::toLong).toLongArray(),
            )
            is DoubleArray -> bundle.putDoubleArray(
                key,
                parseList(rawValue).map(::parseFiniteDouble).toDoubleArray(),
            )
            is Array<*> -> {
                check(current.all { item -> item is String }) {
                    "Unsupported array value type for $key"
                }
                bundle.putStringArray(key, parseList(rawValue).toTypedArray())
            }
            else -> error("Unsupported CarrierConfig value type for $key")
        }
    }

    private fun parseBoolean(value: String): Boolean =
        when (value.trim().lowercase(Locale.ROOT)) {
            "true", "1" -> true
            "false", "0" -> false
            else -> error("Boolean value must be true/false or 1/0")
        }

    private fun parseList(value: String): List<String> =
        value.split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)

    private fun parseFiniteDouble(value: String): Double =
        value.trim().toDouble().also { parsed ->
            require(parsed.isFinite()) { "Floating-point value must be finite" }
        }

    private fun rollbackIfCommunicationDegraded(
        context: Context,
        subId: Int,
        before: com.oneims.app.model.HealthReport,
    ): ConfigResult? {
        val after = SafetyGuard.healthCheck(context, subId)
        if (!before.allHealthy || after.allHealthy) return null
        val rollback = SafetyGuard.restoreDefaults(context, subId)
        return ConfigResult(
            false,
            if (rollback.success) {
                context.getString(R.string.msg_health_rollback)
            } else {
                context.getString(
                    R.string.msg_write_rollback_failed,
                    context.getString(R.string.msg_health_rollback),
                    rollback.message,
                )
            },
        )
    }

    private fun formatValue(value: Any?): String = when (value) {
        null -> "(null)"
        is BooleanArray -> value.contentToString()
        is IntArray -> value.contentToString()
        is LongArray -> value.contentToString()
        is DoubleArray -> value.contentToString()
        is Array<*> -> value.contentDeepToString()
        else -> value.toString()
    }
}
