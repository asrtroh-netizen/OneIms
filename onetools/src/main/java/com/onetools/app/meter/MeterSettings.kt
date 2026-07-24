package com.onetools.app.meter

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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

enum class MeterOverlayTheme {
    INK, // deep ink glass — default, beats flat gray
    GLASS, // brighter frosted
    LIME, // high-contrast lime on ink (not purple)
    SLATE, // soft slate
}

data class MeterPrefsSnapshot(
    val displayMode: MeterDisplayMode = MeterDisplayMode.BOTH,
    val prefix: String = "",
    val overlayEnabled: Boolean = false,
    val notificationEnabled: Boolean = true,
    val overlayTheme: MeterOverlayTheme = MeterOverlayTheme.INK,
    val overlayTextSp: Float = 14f,
    val overlayCornerDp: Float = 18f,
    val overlayAlpha: Float = 0.88f,
    val overlayPadHDp: Float = 14f,
    val overlayPadVDp: Float = 10f,
)

class MeterSettings(private val context: Context) {
    private val modeKey = stringPreferencesKey("display_mode")
    private val prefixKey = stringPreferencesKey("prefix")
    private val overlayKey = booleanPreferencesKey("overlay")
    private val notifKey = booleanPreferencesKey("notification")
    private val themeKey = stringPreferencesKey("overlay_theme")
    private val textSpKey = floatPreferencesKey("overlay_text_sp")
    private val cornerKey = floatPreferencesKey("overlay_corner_dp")
    private val alphaKey = floatPreferencesKey("overlay_alpha")
    private val padHKey = floatPreferencesKey("overlay_pad_h")
    private val padVKey = floatPreferencesKey("overlay_pad_v")

    val snapshotFlow: Flow<MeterPrefsSnapshot> = context.meterStore.data.map { p ->
        MeterPrefsSnapshot(
            displayMode = runCatching {
                MeterDisplayMode.valueOf(p[modeKey] ?: MeterDisplayMode.BOTH.name)
            }.getOrDefault(MeterDisplayMode.BOTH),
            prefix = p[prefixKey].orEmpty(),
            overlayEnabled = p[overlayKey] ?: false,
            notificationEnabled = p[notifKey] ?: true,
            overlayTheme = runCatching {
                MeterOverlayTheme.valueOf(p[themeKey] ?: MeterOverlayTheme.INK.name)
            }.getOrDefault(MeterOverlayTheme.INK),
            overlayTextSp = p[textSpKey] ?: 14f,
            overlayCornerDp = p[cornerKey] ?: 18f,
            overlayAlpha = (p[alphaKey] ?: 0.88f).coerceIn(0.35f, 1f),
            overlayPadHDp = p[padHKey] ?: 14f,
            overlayPadVDp = p[padVKey] ?: 10f,
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

    suspend fun setOverlayTheme(theme: MeterOverlayTheme) {
        context.meterStore.edit { it[themeKey] = theme.name }
    }

    suspend fun setOverlayTextSp(v: Float) {
        context.meterStore.edit { it[textSpKey] = v.coerceIn(10f, 22f) }
    }

    suspend fun setOverlayCornerDp(v: Float) {
        context.meterStore.edit { it[cornerKey] = v.coerceIn(4f, 28f) }
    }

    suspend fun setOverlayAlpha(v: Float) {
        context.meterStore.edit { it[alphaKey] = v.coerceIn(0.35f, 1f) }
    }

    suspend fun setOverlayPadding(hDp: Float, vDp: Float) {
        context.meterStore.edit {
            it[padHKey] = hDp.coerceIn(8f, 28f)
            it[padVKey] = vDp.coerceIn(6f, 20f)
        }
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
