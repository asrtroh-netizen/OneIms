package com.onetools.app.battery

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.batteryPrefsStore by preferencesDataStore("one_battery_prefs")

data class BatteryPrefsSnapshot(
    val designCapacityMah: Int,
    val chargeAlarmEnabled: Boolean,
    val chargeAlarmPercent: Int,
    val trackingEnabled: Boolean,
    /** True once the user (or a chip) explicitly set design capacity. */
    val designCapacityUserSet: Boolean,
)

class BatteryPrefs(private val context: Context) {
    private val designKey = intPreferencesKey("design_capacity_mah")
    private val designUserSetKey = booleanPreferencesKey("design_capacity_user_set")
    private val alarmEnabledKey = booleanPreferencesKey("charge_alarm_enabled")
    private val alarmPercentKey = intPreferencesKey("charge_alarm_percent")
    private val trackingKey = booleanPreferencesKey("tracking_enabled")

    val snapshotFlow: Flow<BatteryPrefsSnapshot> = context.batteryPrefsStore.data.map { p ->
        BatteryPrefsSnapshot(
            designCapacityMah = p[designKey] ?: DEFAULT_DESIGN_MAH,
            chargeAlarmEnabled = p[alarmEnabledKey] ?: true,
            chargeAlarmPercent = (p[alarmPercentKey] ?: DEFAULT_ALARM_PERCENT)
                .coerceIn(MIN_ALARM, MAX_ALARM),
            trackingEnabled = p[trackingKey] ?: true,
            designCapacityUserSet = p[designUserSetKey] ?: false,
        )
    }

    suspend fun snapshot(): BatteryPrefsSnapshot = snapshotFlow.first()

    suspend fun setDesignCapacityMah(value: Int, userSet: Boolean = true) {
        context.batteryPrefsStore.edit {
            it[designKey] = value.coerceIn(500, 20_000)
            it[designUserSetKey] = userSet
        }
    }

    /**
     * When the user has never set design capacity, apply Pixel preset for this device.
     * @return applied preset label, or null if skipped / unknown device.
     */
    suspend fun applyPixelDesignIfUnset(): String? {
        val snap = snapshot()
        if (snap.designCapacityUserSet) return null
        val preset = PixelDesignCapacity.match() ?: return null
        setDesignCapacityMah(preset.mah, userSet = false)
        return preset.label
    }

    /** Force-apply current device Pixel preset (marks as user-set so it sticks). */
    suspend fun applyDetectedPixelPreset(): PixelDesignCapacity.Preset? {
        val preset = PixelDesignCapacity.match() ?: return null
        setDesignCapacityMah(preset.mah, userSet = true)
        return preset
    }

    suspend fun setChargeAlarmEnabled(value: Boolean) {
        context.batteryPrefsStore.edit { it[alarmEnabledKey] = value }
    }

    suspend fun setChargeAlarmPercent(value: Int) {
        context.batteryPrefsStore.edit {
            it[alarmPercentKey] = value.coerceIn(MIN_ALARM, MAX_ALARM)
        }
    }

    suspend fun setTrackingEnabled(value: Boolean) {
        context.batteryPrefsStore.edit { it[trackingKey] = value }
    }

    companion object {
        /** Fallback when device is not a known Pixel. */
        const val DEFAULT_DESIGN_MAH = 4500
        const val DEFAULT_ALARM_PERCENT = 80
        const val MIN_ALARM = 50
        const val MAX_ALARM = 100
    }
}
