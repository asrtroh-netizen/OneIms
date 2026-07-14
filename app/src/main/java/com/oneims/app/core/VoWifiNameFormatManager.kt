package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.PersistableBundle
import android.os.SystemClock
import android.provider.Settings
import com.oneims.app.R

data class VoWifiNameSelection(
    val formatIndex: Int?,
    val customCarrierName: String,
)

internal data class VoWifiFormatValues(
    val index: Int,
    val dataIndex: Int,
    val flightModeIndex: Int,
    val useRootLocale: Boolean,
)

internal data class VoWifiCarrierValues(
    val overrideEnabled: Boolean,
    val displayName: String,
)

internal object VoWifiNameFormatPolicy {
    const val MIN_INDEX = 0
    const val MAX_INDEX = 12
    private const val MAX_CARRIER_NAME_LENGTH = 64

    fun requireValidIndex(index: Int) {
        require(index in MIN_INDEX..MAX_INDEX) {
            "VoWiFi format index must be between $MIN_INDEX and $MAX_INDEX"
        }
    }

    fun normalizeCarrierName(raw: String): String {
        val normalized = raw.trim()
        require(normalized.length <= MAX_CARRIER_NAME_LENGTH) {
            "Carrier display name is too long"
        }
        require(normalized.none(Char::isISOControl)) {
            "Carrier display name contains control characters"
        }
        return normalized
    }

    fun preview(
        formatIndex: Int?,
        systemCarrierName: String,
        customCarrierName: String,
    ): String {
        val systemCarrier = systemCarrierName.trim().ifBlank { "Carrier" }
        val carrier = customCarrierName.trim()
            .ifBlank { systemCarrier }
        return when (formatIndex) {
            null -> systemCarrier
            0 -> carrier
            1 -> "$carrier Wi-Fi Calling"
            2 -> "WLAN Call"
            3 -> "$carrier WLAN Call"
            4 -> "$carrier Wi-Fi"
            5 -> "WiFi Calling | $carrier"
            6 -> "$carrier VoWifi"
            7 -> "Wi-Fi Calling"
            8 -> "Wi-Fi"
            9 -> "WiFi Calling"
            10 -> "VoWifi"
            11 -> "$carrier WiFi Calling"
            12 -> "WiFi Call"
            else -> error("Unsupported VoWiFi format index: $formatIndex")
        }
    }

    fun formatMatches(current: VoWifiFormatValues, expected: VoWifiFormatValues): Boolean =
        current == expected

    fun carrierMatches(current: VoWifiCarrierValues, expected: VoWifiCarrierValues): Boolean =
        current == expected
}

/**
 * VoWiFi SPN 名称格式的唯一写入入口。
 *
 * CarrierConfig 不支持逐 key 删除，因此“跟随系统”与清空自定义名称都使用每卡、每次开机
 * 首次写入前捕获的有效值回写。只有当前值仍匹配本模块 pending/confirmed 值时才恢复，
 * 避免覆盖系统或其他模块在稍后作出的决定。
 */
object VoWifiNameFormatManager {
    private const val PREFS = "oneims_vowifi_name_baselines"
    private const val KEY_BOOT_EPOCH = "boot_epoch"
    private const val KEY_HAS_BASELINE = "has_baseline"

    private const val KEY_BASE_INDEX = "base_index"
    private const val KEY_BASE_DATA_INDEX = "base_data_index"
    private const val KEY_BASE_FLIGHT_INDEX = "base_flight_index"
    private const val KEY_BASE_ROOT_LOCALE = "base_root_locale"
    private const val KEY_BASE_CARRIER_OVERRIDE = "base_carrier_override"
    private const val KEY_BASE_CARRIER_NAME = "base_carrier_name"

    private const val KEY_PENDING_INDEX = "pending_index"
    private const val KEY_PENDING_DATA_INDEX = "pending_data_index"
    private const val KEY_PENDING_FLIGHT_INDEX = "pending_flight_index"
    private const val KEY_PENDING_ROOT_LOCALE = "pending_root_locale"
    private const val KEY_LAST_INDEX = "last_index"
    private const val KEY_LAST_DATA_INDEX = "last_data_index"
    private const val KEY_LAST_FLIGHT_INDEX = "last_flight_index"
    private const val KEY_LAST_ROOT_LOCALE = "last_root_locale"

    private const val KEY_PENDING_CARRIER = "pending_carrier"
    private const val KEY_PENDING_CARRIER_OVERRIDE = "pending_carrier_override"
    private const val KEY_PENDING_CARRIER_NAME = "pending_carrier_name"
    private const val KEY_LAST_CARRIER = "last_carrier"
    private const val KEY_LAST_CARRIER_OVERRIDE = "last_carrier_override"
    private const val KEY_LAST_CARRIER_NAME = "last_carrier_name"

    fun preview(
        formatIndex: Int?,
        systemCarrierName: String,
        customCarrierName: String,
    ): String = VoWifiNameFormatPolicy.preview(
        formatIndex = formatIndex,
        systemCarrierName = systemCarrierName,
        customCarrierName = customCarrierName,
    )

    @Synchronized
    fun readSelection(context: Context, subId: Int): VoWifiNameSelection {
        requireValidSubId(subId)
        if (!hasCurrentBaseline(context, subId)) {
            clearOwnership(context, subId)
            return VoWifiNameSelection(null, "")
        }
        val currentFormat = readCurrentFormat(context, subId)
        val ownedFormat = readPendingFormat(context, subId) ?: readLastFormat(context, subId)
        if (currentFormat == null || ownedFormat == null ||
            !VoWifiNameFormatPolicy.formatMatches(currentFormat, ownedFormat)
        ) {
            clearOwnership(context, subId)
            return VoWifiNameSelection(null, "")
        }

        val ownedCarrier = readPendingCarrier(context, subId) ?: readLastCarrier(context, subId)
        val currentCarrier = readCurrentCarrier(context, subId)
        val customCarrier = if (
            ownedCarrier != null &&
            currentCarrier != null &&
            VoWifiNameFormatPolicy.carrierMatches(currentCarrier, ownedCarrier) &&
            ownedCarrier.overrideEnabled
        ) {
            ownedCarrier.displayName
        } else {
            ""
        }
        return VoWifiNameSelection(ownedFormat.index, customCarrier)
    }

    @Synchronized
    fun apply(
        context: Context,
        subId: Int,
        formatIndex: Int?,
        customCarrierName: String,
    ): String {
        requireValidSubId(subId)
        if (formatIndex == null) {
            val restored = restoreBaseline(context, subId)
            return context.getString(
                if (restored) {
                    R.string.vowifi_name_follow_system_success
                } else {
                    R.string.vowifi_name_follow_system_no_override
                },
            )
        }

        VoWifiNameFormatPolicy.requireValidIndex(formatIndex)
        val normalizedCarrierName =
            VoWifiNameFormatPolicy.normalizeCarrierName(customCarrierName)
        captureBaselineOnce(context, subId)

        val desiredFormat = VoWifiFormatValues(
            index = formatIndex,
            dataIndex = formatIndex,
            flightModeIndex = formatIndex,
            useRootLocale = true,
        )
        val desiredCarrier = desiredCarrierValues(
            context = context,
            subId = subId,
            normalizedCarrierName = normalizedCarrierName,
        )
        val ownedCarrier = desiredCarrier.takeIf { normalizedCarrierName.isNotEmpty() }
        recordPending(context, subId, desiredFormat, ownedCarrier)

        val overrides = desiredFormat.toBundle().apply {
            desiredCarrier?.let { carrier ->
                putBoolean(CarrierConfigKeys.CARRIER_NAME_OVERRIDE, carrier.overrideEnabled)
                putString(CarrierConfigKeys.CARRIER_NAME_STRING, carrier.displayName)
            }
        }
        run {
            val write = CarrierConfigOverrideWriter.applyPersistentOverride(
                context, subId, overrides, reason = "VoWifiNameFormat",
            )
            check(write.success) { write.message }
        }

        check(readCurrentFormat(context, subId) == desiredFormat) {
            "VoWiFi format CarrierConfig readback mismatch for subId=$subId"
        }
        if (desiredCarrier != null) {
            check(readCurrentCarrier(context, subId) == desiredCarrier) {
                "Carrier name CarrierConfig readback mismatch for subId=$subId"
            }
        }
        confirmApplied(context, subId, desiredFormat, ownedCarrier)
        return context.getString(R.string.vowifi_name_apply_success)
    }

    private fun desiredCarrierValues(
        context: Context,
        subId: Int,
        normalizedCarrierName: String,
    ): VoWifiCarrierValues? {
        if (normalizedCarrierName.isNotEmpty()) {
            return VoWifiCarrierValues(true, normalizedCarrierName)
        }
        val owned = readPendingCarrier(context, subId) ?: readLastCarrier(context, subId)
            ?: return null
        val current = readCurrentCarrier(context, subId)
        if (current == null || !VoWifiNameFormatPolicy.carrierMatches(current, owned)) {
            clearCarrierOwnership(context, subId)
            return null
        }
        return readBaselineCarrier(context, subId)
    }

    @Synchronized
    private fun restoreBaseline(context: Context, subId: Int): Boolean {
        val baselineFormat = readBaselineFormat(context, subId) ?: return false
        val currentFormat = readCurrentFormat(context, subId)
        val ownedFormat = readPendingFormat(context, subId) ?: readLastFormat(context, subId)
        val restoreFormat = currentFormat != null &&
            ownedFormat != null &&
            VoWifiNameFormatPolicy.formatMatches(currentFormat, ownedFormat)

        val baselineCarrier = readBaselineCarrier(context, subId)
        val currentCarrier = readCurrentCarrier(context, subId)
        val ownedCarrier = readPendingCarrier(context, subId) ?: readLastCarrier(context, subId)
        val restoreCarrier = baselineCarrier != null &&
            currentCarrier != null &&
            ownedCarrier != null &&
            VoWifiNameFormatPolicy.carrierMatches(currentCarrier, ownedCarrier)

        if (!restoreFormat && !restoreCarrier) {
            clearOwnership(context, subId)
            return false
        }

        val overrides = PersistableBundle().apply {
            if (restoreFormat) putAll(baselineFormat.toBundle())
            if (restoreCarrier) {
                putBoolean(
                    CarrierConfigKeys.CARRIER_NAME_OVERRIDE,
                    checkNotNull(baselineCarrier).overrideEnabled,
                )
                putString(
                    CarrierConfigKeys.CARRIER_NAME_STRING,
                    baselineCarrier.displayName,
                )
            }
        }
        run {
            val write = CarrierConfigOverrideWriter.applyPersistentOverride(
                context, subId, overrides, reason = "VoWifiNameFormat",
            )
            check(write.success) { write.message }
        }
        if (restoreFormat) {
            check(readCurrentFormat(context, subId) == baselineFormat) {
                "VoWiFi format baseline readback mismatch for subId=$subId"
            }
        }
        if (restoreCarrier) {
            check(readCurrentCarrier(context, subId) == baselineCarrier) {
                "Carrier name baseline readback mismatch for subId=$subId"
            }
        }
        clearOwnership(context, subId)
        return true
    }

    @SuppressLint("ApplySharedPref")
    private fun captureBaselineOnce(context: Context, subId: Int) {
        if (hasCurrentBaseline(context, subId)) return
        clearOwnership(context, subId)
        val format = checkNotNull(readCurrentFormat(context, subId)) {
            "VoWiFi CarrierConfig is unavailable for subId=$subId"
        }
        val carrier = checkNotNull(readCurrentCarrier(context, subId)) {
            "Carrier name CarrierConfig is unavailable for subId=$subId"
        }
        val saved = prefs(context).edit()
            .putBoolean(key(KEY_HAS_BASELINE, subId), true)
            .putString(key(KEY_BOOT_EPOCH, subId), currentBootEpoch(context))
            .putInt(key(KEY_BASE_INDEX, subId), format.index)
            .putInt(key(KEY_BASE_DATA_INDEX, subId), format.dataIndex)
            .putInt(key(KEY_BASE_FLIGHT_INDEX, subId), format.flightModeIndex)
            .putBoolean(key(KEY_BASE_ROOT_LOCALE, subId), format.useRootLocale)
            .putBoolean(key(KEY_BASE_CARRIER_OVERRIDE, subId), carrier.overrideEnabled)
            .putString(key(KEY_BASE_CARRIER_NAME, subId), carrier.displayName)
            .commit()
        check(saved) { "Failed to persist VoWiFi name baseline" }
    }

    @SuppressLint("ApplySharedPref")
    private fun recordPending(
        context: Context,
        subId: Int,
        format: VoWifiFormatValues,
        carrier: VoWifiCarrierValues?,
    ) {
        val editor = prefs(context).edit()
            .putInt(key(KEY_PENDING_INDEX, subId), format.index)
            .putInt(key(KEY_PENDING_DATA_INDEX, subId), format.dataIndex)
            .putInt(key(KEY_PENDING_FLIGHT_INDEX, subId), format.flightModeIndex)
            .putBoolean(key(KEY_PENDING_ROOT_LOCALE, subId), format.useRootLocale)
        if (carrier == null) {
            editor
                .remove(key(KEY_PENDING_CARRIER, subId))
                .remove(key(KEY_PENDING_CARRIER_OVERRIDE, subId))
                .remove(key(KEY_PENDING_CARRIER_NAME, subId))
        } else {
            editor
                .putBoolean(key(KEY_PENDING_CARRIER, subId), true)
                .putBoolean(
                    key(KEY_PENDING_CARRIER_OVERRIDE, subId),
                    carrier.overrideEnabled,
                )
                .putString(key(KEY_PENDING_CARRIER_NAME, subId), carrier.displayName)
        }
        check(editor.commit()) { "Failed to persist pending VoWiFi name ownership" }
    }

    @SuppressLint("ApplySharedPref")
    private fun confirmApplied(
        context: Context,
        subId: Int,
        format: VoWifiFormatValues,
        carrier: VoWifiCarrierValues?,
    ) {
        val editor = prefs(context).edit()
            .putInt(key(KEY_LAST_INDEX, subId), format.index)
            .putInt(key(KEY_LAST_DATA_INDEX, subId), format.dataIndex)
            .putInt(key(KEY_LAST_FLIGHT_INDEX, subId), format.flightModeIndex)
            .putBoolean(key(KEY_LAST_ROOT_LOCALE, subId), format.useRootLocale)
            .remove(key(KEY_PENDING_INDEX, subId))
            .remove(key(KEY_PENDING_DATA_INDEX, subId))
            .remove(key(KEY_PENDING_FLIGHT_INDEX, subId))
            .remove(key(KEY_PENDING_ROOT_LOCALE, subId))
        if (carrier == null || !carrier.overrideEnabled) {
            editor
                .remove(key(KEY_LAST_CARRIER, subId))
                .remove(key(KEY_LAST_CARRIER_OVERRIDE, subId))
                .remove(key(KEY_LAST_CARRIER_NAME, subId))
        } else {
            editor
                .putBoolean(key(KEY_LAST_CARRIER, subId), true)
                .putBoolean(key(KEY_LAST_CARRIER_OVERRIDE, subId), true)
                .putString(key(KEY_LAST_CARRIER_NAME, subId), carrier.displayName)
        }
        editor
            .remove(key(KEY_PENDING_CARRIER, subId))
            .remove(key(KEY_PENDING_CARRIER_OVERRIDE, subId))
            .remove(key(KEY_PENDING_CARRIER_NAME, subId))
        check(editor.commit()) { "Failed to confirm VoWiFi name ownership" }
    }

    private fun readCurrentFormat(context: Context, subId: Int): VoWifiFormatValues? {
        val config = SystemApiBroker.getCarrierConfig(context, subId) ?: return null
        val index = config.getInt(CarrierConfigKeys.WFC_SPN_FORMAT_IDX, 0)
        return VoWifiFormatValues(
            index = index,
            dataIndex = config.getInt(CarrierConfigKeys.WFC_DATA_SPN_FORMAT_IDX, index),
            flightModeIndex = config.getInt(
                CarrierConfigKeys.WFC_FLIGHT_MODE_SPN_FORMAT_IDX,
                index,
            ),
            useRootLocale = config.getBoolean(
                CarrierConfigKeys.WFC_SPN_USE_ROOT_LOCALE,
                false,
            ),
        )
    }

    private fun readCurrentCarrier(context: Context, subId: Int): VoWifiCarrierValues? {
        val config = SystemApiBroker.getCarrierConfig(context, subId) ?: return null
        return VoWifiCarrierValues(
            overrideEnabled = config.getBoolean(CarrierConfigKeys.CARRIER_NAME_OVERRIDE, false),
            displayName = config.getString(CarrierConfigKeys.CARRIER_NAME_STRING).orEmpty(),
        )
    }

    private fun readBaselineFormat(context: Context, subId: Int): VoWifiFormatValues? {
        if (!hasCurrentBaseline(context, subId)) return null
        val p = prefs(context)
        return VoWifiFormatValues(
            index = p.getInt(key(KEY_BASE_INDEX, subId), 0),
            dataIndex = p.getInt(key(KEY_BASE_DATA_INDEX, subId), 0),
            flightModeIndex = p.getInt(key(KEY_BASE_FLIGHT_INDEX, subId), 0),
            useRootLocale = p.getBoolean(key(KEY_BASE_ROOT_LOCALE, subId), false),
        )
    }

    private fun readBaselineCarrier(context: Context, subId: Int): VoWifiCarrierValues? {
        if (!hasCurrentBaseline(context, subId)) return null
        val p = prefs(context)
        return VoWifiCarrierValues(
            overrideEnabled = p.getBoolean(key(KEY_BASE_CARRIER_OVERRIDE, subId), false),
            displayName = p.getString(key(KEY_BASE_CARRIER_NAME, subId), "").orEmpty(),
        )
    }

    private fun readPendingFormat(context: Context, subId: Int): VoWifiFormatValues? =
        readStoredFormat(context, subId, pending = true)

    private fun readLastFormat(context: Context, subId: Int): VoWifiFormatValues? =
        readStoredFormat(context, subId, pending = false)

    private fun readStoredFormat(
        context: Context,
        subId: Int,
        pending: Boolean,
    ): VoWifiFormatValues? {
        val p = prefs(context)
        val indexKey = key(if (pending) KEY_PENDING_INDEX else KEY_LAST_INDEX, subId)
        if (!p.contains(indexKey)) return null
        return VoWifiFormatValues(
            index = p.getInt(indexKey, 0),
            dataIndex = p.getInt(
                key(if (pending) KEY_PENDING_DATA_INDEX else KEY_LAST_DATA_INDEX, subId),
                0,
            ),
            flightModeIndex = p.getInt(
                key(
                    if (pending) KEY_PENDING_FLIGHT_INDEX else KEY_LAST_FLIGHT_INDEX,
                    subId,
                ),
                0,
            ),
            useRootLocale = p.getBoolean(
                key(
                    if (pending) KEY_PENDING_ROOT_LOCALE else KEY_LAST_ROOT_LOCALE,
                    subId,
                ),
                false,
            ),
        )
    }

    private fun readPendingCarrier(context: Context, subId: Int): VoWifiCarrierValues? =
        readStoredCarrier(context, subId, pending = true)

    private fun readLastCarrier(context: Context, subId: Int): VoWifiCarrierValues? =
        readStoredCarrier(context, subId, pending = false)

    private fun readStoredCarrier(
        context: Context,
        subId: Int,
        pending: Boolean,
    ): VoWifiCarrierValues? {
        val p = prefs(context)
        val marker = key(if (pending) KEY_PENDING_CARRIER else KEY_LAST_CARRIER, subId)
        if (!p.getBoolean(marker, false)) return null
        return VoWifiCarrierValues(
            overrideEnabled = p.getBoolean(
                key(
                    if (pending) KEY_PENDING_CARRIER_OVERRIDE else KEY_LAST_CARRIER_OVERRIDE,
                    subId,
                ),
                false,
            ),
            displayName = p.getString(
                key(if (pending) KEY_PENDING_CARRIER_NAME else KEY_LAST_CARRIER_NAME, subId),
                "",
            ).orEmpty(),
        )
    }

    private fun VoWifiFormatValues.toBundle() = PersistableBundle().apply {
        putInt(CarrierConfigKeys.WFC_SPN_FORMAT_IDX, index)
        putInt(CarrierConfigKeys.WFC_DATA_SPN_FORMAT_IDX, dataIndex)
        putInt(CarrierConfigKeys.WFC_FLIGHT_MODE_SPN_FORMAT_IDX, flightModeIndex)
        putBoolean(CarrierConfigKeys.WFC_SPN_USE_ROOT_LOCALE, useRootLocale)
    }

    private fun hasCurrentBaseline(context: Context, subId: Int): Boolean {
        val p = prefs(context)
        return p.getBoolean(key(KEY_HAS_BASELINE, subId), false) &&
            p.getString(key(KEY_BOOT_EPOCH, subId), null) == currentBootEpoch(context)
    }

    private fun clearCarrierOwnership(context: Context, subId: Int) {
        prefs(context).edit()
            .remove(key(KEY_PENDING_CARRIER, subId))
            .remove(key(KEY_PENDING_CARRIER_OVERRIDE, subId))
            .remove(key(KEY_PENDING_CARRIER_NAME, subId))
            .remove(key(KEY_LAST_CARRIER, subId))
            .remove(key(KEY_LAST_CARRIER_OVERRIDE, subId))
            .remove(key(KEY_LAST_CARRIER_NAME, subId))
            .apply()
    }

    @SuppressLint("ApplySharedPref")
    private fun clearOwnership(context: Context, subId: Int) {
        val suffix = "_$subId"
        val editor = prefs(context).edit()
        prefs(context).all.keys
            .filter { it.endsWith(suffix) }
            .forEach(editor::remove)
        check(editor.commit()) { "Failed to clear VoWiFi name ownership" }
    }

    private fun currentBootEpoch(context: Context): String {
        val bootCount = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        }.getOrDefault(-1)
        if (bootCount >= 0) return "count:$bootCount"
        val originMinutes =
            (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000L
        return "origin:$originMinutes"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(prefix: String, subId: Int) = "${prefix}_$subId"

    private fun requireValidSubId(subId: Int) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
    }
}
