package com.onetools.app.meter

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.meterStore by preferencesDataStore("one_meter_settings")

enum class MeterDisplayMode {
    BOTH,
    TOTAL,
    DOWN,
    UP,
}

data class MeterPrefsSnapshot(
    val displayMode: MeterDisplayMode = MeterDisplayMode.BOTH,
    val prefix: String = "",
    val overlayEnabled: Boolean = false,
    val notificationEnabled: Boolean = true,
)

class MeterSettings(private val context: Context) {
    private val modeKey = stringPreferencesKey("display_mode")
    private val prefixKey = stringPreferencesKey("prefix")
    private val overlayKey = booleanPreferencesKey("overlay")
    private val notifKey = booleanPreferencesKey("notification")

    val snapshotFlow: Flow<MeterPrefsSnapshot> = context.meterStore.data.map { p ->
        MeterPrefsSnapshot(
            displayMode = runCatching {
                MeterDisplayMode.valueOf(p[modeKey] ?: MeterDisplayMode.BOTH.name)
            }.getOrDefault(MeterDisplayMode.BOTH),
            prefix = p[prefixKey].orEmpty(),
            overlayEnabled = p[overlayKey] ?: false,
            notificationEnabled = p[notifKey] ?: true,
        )
    }

    suspend fun snapshot(): MeterPrefsSnapshot = snapshotFlow.first()

    suspend fun setDisplayMode(mode: MeterDisplayMode) {
        context.meterStore.edit { it[modeKey] = mode.name }
    }

    suspend fun setPrefix(prefix: String) {
        context.meterStore.edit { it[prefixKey] = prefix }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.meterStore.edit { it[overlayKey] = enabled }
    }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.meterStore.edit { it[notifKey] = enabled }
    }
}

object MeterRateFormatter {
    fun format(prefs: MeterPrefsSnapshot, down: Long, up: Long): String {
        val body = when (prefs.displayMode) {
            MeterDisplayMode.BOTH ->
                "↓ ${SpeedFormat.formatRate(down)} · ↑ ${SpeedFormat.formatRate(up)}"
            MeterDisplayMode.TOTAL ->
                SpeedFormat.formatRate(down + up)
            MeterDisplayMode.DOWN ->
                "↓ ${SpeedFormat.formatRate(down)}"
            MeterDisplayMode.UP ->
                "↑ ${SpeedFormat.formatRate(up)}"
        }
        val prefix = prefs.prefix.trim()
        return if (prefix.isEmpty()) body else "$prefix $body"
    }
}
