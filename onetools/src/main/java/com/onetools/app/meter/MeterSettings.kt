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
    /** One 深空底 #111318 + 白字 — 家族默认 */
    ONE_DARK,
    /** One 浅雾底 #F9F9FF */
    ONE_MIST,
    /** 深底 + 白 primary 描边高亮 */
    ONE_WHITE,
    /** 岩灰备选 */
    SLATE,
}

enum class MeterRateUnit {
    BYTES_PER_SEC,
    BITS_PER_SEC,
}

data class MeterPrefsSnapshot(
    val displayMode: MeterDisplayMode = MeterDisplayMode.BOTH,
    val speedOrder: MeterSpeedOrder = MeterSpeedOrder.DOWN_THEN_UP,
    val rateUnit: MeterRateUnit = MeterRateUnit.BYTES_PER_SEC,
    val prefix: String = "",
    val overlayEnabled: Boolean = false,
    val notificationEnabled: Boolean = true,
    /** Android 16+ status-bar chip via promoted ongoing notification (OEM-like). */
    /** Deprecated: Live Update chip removed; kept for DataStore read compat. */
    val statusBarChipEnabled: Boolean = false,
    val overlayTheme: MeterOverlayTheme = MeterOverlayTheme.ONE_DARK,
    val overlayTextSp: Float = 14f,
    val overlayCornerDp: Float = 20f,
    val overlayAlpha: Float = 0.92f,
    val overlayPadHDp: Float = 14f,
    val overlayPadVDp: Float = 10f,
    val sampleIntervalMs: Long = 1000L,
    val overlayX: Int = 48,
    val overlayY: Int = 180,
)

class MeterSettings(private val context: Context) {
    private val modeKey = stringPreferencesKey("display_mode")
    private val orderKey = stringPreferencesKey("speed_order")
    private val unitKey = stringPreferencesKey("rate_unit")
    private val prefixKey = stringPreferencesKey("prefix")
    private val overlayKey = booleanPreferencesKey("overlay")
    private val notifKey = booleanPreferencesKey("notification")
    private val chipKey = booleanPreferencesKey("status_bar_chip")
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
            rateUnit = runCatching {
                MeterRateUnit.valueOf(p[unitKey] ?: MeterRateUnit.BYTES_PER_SEC.name)
            }.getOrDefault(MeterRateUnit.BYTES_PER_SEC),
            prefix = p[prefixKey].orEmpty(),
            overlayEnabled = p[overlayKey] ?: false,
            notificationEnabled = p[notifKey] ?: true,
            statusBarChipEnabled = false,
            overlayTheme = runCatching {
                when (val raw = p[themeKey]) {
                    "INK", null -> MeterOverlayTheme.ONE_DARK
                    "GLASS" -> MeterOverlayTheme.ONE_MIST
                    "LIME" -> MeterOverlayTheme.ONE_WHITE
                    else -> MeterOverlayTheme.valueOf(raw)
                }
            }.getOrDefault(MeterOverlayTheme.ONE_DARK),
            overlayTextSp = p[textSpKey] ?: 14f,
            overlayCornerDp = p[cornerKey] ?: 20f,
            overlayAlpha = (p[alphaKey] ?: 0.92f).coerceIn(0.35f, 1f),
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

    suspend fun setRateUnit(unit: MeterRateUnit) {
        context.meterStore.edit { it[unitKey] = unit.name }
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

    suspend fun setStatusBarChipEnabled(enabled: Boolean) {
        context.meterStore.edit { it[chipKey] = enabled }
    }

    suspend fun setOverlayPosition(x: Int, y: Int) {
        context.meterStore.edit {
            it[overlayXKey] = x
            it[overlayYKey] = y
        }
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
        val downPart = "↓ ${SpeedFormat.formatRate(down, prefs.rateUnit)}"
        val upPart = "↑ ${SpeedFormat.formatRate(up, prefs.rateUnit)}"
        val body = when (prefs.displayMode) {
            MeterDisplayMode.BOTH -> when (prefs.speedOrder) {
                MeterSpeedOrder.DOWN_THEN_UP -> "$downPart · $upPart"
                MeterSpeedOrder.UP_THEN_DOWN -> "$upPart · $downPart"
            }
            MeterDisplayMode.TOTAL -> SpeedFormat.formatRate(down + up, prefs.rateUnit)
            MeterDisplayMode.DOWN -> downPart
            MeterDisplayMode.UP -> upPart
        }
        val prefix = prefs.prefix.trim()
        return if (prefix.isEmpty()) body else "$prefix $body"
    }
}
