package com.onetools.app.meter

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val Context.meterStore by preferencesDataStore("one_meter_settings")

enum class MeterDisplayMode {
    BOTH,
    TOTAL,
    DOWN,
    UP,
}

/** Order of up/down segments when mode is BOTH. */
enum class MeterSpeedOrder {
    DOWN_THEN_UP,
    UP_THEN_DOWN,
}

enum class MeterOverlayTheme {
    INK,
    GLASS,
    LIME,
    SLATE,
}

data class MeterPrefsSnapshot(
    val displayMode: MeterDisplayMode = MeterDisplayMode.BOTH,
    val speedOrder: MeterSpeedOrder = MeterSpeedOrder.DOWN_THEN_UP,
    val prefix: String = "",
    val overlayEnabled: Boolean = false,
    val notificationEnabled: Boolean = true,
    val overlayTheme: MeterOverlayTheme = MeterOverlayTheme.INK,
    val overlayTextSp: Float = 14f,
    val overlayCornerDp: Float = 18f,
    val overlayAlpha: Float = 0.88f,
    val overlayPadHDp: Float = 14f,
    val overlayPadVDp: Float = 10f,
    val sampleIntervalMs: Long = 1000L,
    val overlayX: Int = 48,
    val overlayY: Int = 180,
)

class MeterSettings(private val context: Context) {
    private val modeKey = stringPreferencesKey("display_mode")
    private val orderKey = stringPreferencesKey("speed_order")
    private val prefixKey = stringPreferencesKey("prefix")
    private val overlayKey = booleanPreferencesKey("overlay")
    private val notifKey = booleanPreferencesKey("notification")
    private val themeKey = stringPreferencesKey("overlay_theme")
    private val textSpKey = floatPreferencesKey("overlay_text_sp")
    private val cornerKey = floatPreferencesKey("overlay_corner_dp")
    private val alphaKey = floatPreferencesKey("overlay_alpha")
    private val padHKey = floatPreferencesKey("overlay_pad_h")
    private val padVKey = floatPreferencesKey("overlay_pad_v")
    private val intervalKey = longPreferencesKey("sample_interval_ms")
    private val overlayXKey = intPreferencesKey("overlay_x")
    private val overlayYKey = intPreferencesKey("overlay_y")

    val snapshotFlow: Flow<MeterPrefsSnapshot> = context.meterStore.data.map { p ->
        MeterPrefsSnapshot(
            displayMode = runCatching {
                MeterDisplayMode.valueOf(p[modeKey] ?: MeterDisplayMode.BOTH.name)
            }.getOrDefault(MeterDisplayMode.BOTH),
            speedOrder = runCatching {
                MeterSpeedOrder.valueOf(p[orderKey] ?: MeterSpeedOrder.DOWN_THEN_UP.name)
            }.getOrDefault(MeterSpeedOrder.DOWN_THEN_UP),
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
            sampleIntervalMs = (p[intervalKey] ?: 1000L).coerceIn(500L, 3000L),
            overlayX = p[overlayXKey] ?: 48,
            overlayY = p[overlayYKey] ?: 180,
        )
    }

    suspend fun snapshot(): MeterPrefsSnapshot = snapshotFlow.first()

    suspend fun setDisplayMode(mode: MeterDisplayMode) {
        context.meterStore.edit { it[modeKey] = mode.name }
    }

    suspend fun setSpeedOrder(order: MeterSpeedOrder) {
        context.meterStore.edit { it[orderKey] = order.name }
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

    suspend fun setSampleIntervalMs(ms: Long) {
        context.meterStore.edit { it[intervalKey] = ms.coerceIn(500L, 3000L) }
    }

    fun saveOverlayPositionAsync(x: Int, y: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            context.meterStore.edit {
                it[overlayXKey] = x
                it[overlayYKey] = y
            }
        }
    }
}

object MeterRateFormatter {
    fun format(prefs: MeterPrefsSnapshot, down: Long, up: Long): String {
        val downPart = "↓ ${SpeedFormat.formatRate(down)}"
        val upPart = "↑ ${SpeedFormat.formatRate(up)}"
        val body = when (prefs.displayMode) {
            MeterDisplayMode.BOTH -> when (prefs.speedOrder) {
                MeterSpeedOrder.DOWN_THEN_UP -> "$downPart · $upPart"
                MeterSpeedOrder.UP_THEN_DOWN -> "$upPart · $downPart"
            }
            MeterDisplayMode.TOTAL -> SpeedFormat.formatRate(down + up)
            MeterDisplayMode.DOWN -> downPart
            MeterDisplayMode.UP -> upPart
        }
        val prefix = prefs.prefix.trim()
        return if (prefix.isEmpty()) body else "$prefix $body"
    }
}
