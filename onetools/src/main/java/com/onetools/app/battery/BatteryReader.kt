package com.onetools.app.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

/**
 * Clean-room battery snapshot using public Android battery APIs.
 * Field set inspired by Battery Info (Apache-2.0); implementation is original.
 * See `onetools/NOTICE`.
 */
data class BatterySnapshot(
    val percent: Int,
    val healthCode: Int,
    val healthLabel: String,
    val temperatureC: Float,
    val voltageMv: Int,
    val statusLabel: String,
    val pluggedLabel: String,
    val technology: String,
    val cycleCount: Int,
    val chargeCounterMah: Int,
    val currentNowMa: Int,
    val chargingTimeRemainingMs: Long,
)

object BatteryReader {
    fun read(context: Context): BatterySnapshot? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val bm = context.getSystemService(BatteryManager::class.java)

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1).coerceAtLeast(1)
        val percent = ((level * 100f) / scale).toInt().coerceIn(0, 100)

        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "—"

        val cycles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
        } else {
            -1
        }

        val chargeCounterUa = runCatching {
            bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        }.getOrDefault(Long.MIN_VALUE)
        val chargeMah = if (chargeCounterUa > 0) (chargeCounterUa / 1000).toInt() else -1

        val currentUa = runCatching {
            bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrDefault(0L)
        val currentMa = (kotlin.math.abs(currentUa) / 1000).toInt()

        val chargeRemain = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { bm.computeChargeTimeRemaining() }.getOrDefault(-1L)
        } else {
            -1L
        }

        return BatterySnapshot(
            percent = percent,
            healthCode = health,
            healthLabel = healthLabel(health),
            temperatureC = tempTenths / 10f,
            voltageMv = voltage,
            statusLabel = statusLabel(status),
            pluggedLabel = pluggedLabel(plugged),
            technology = tech,
            cycleCount = cycles,
            chargeCounterMah = chargeMah,
            currentNowMa = currentMa,
            chargingTimeRemainingMs = chargeRemain,
        )
    }

    fun healthLabel(code: Int): String = when (code) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> "Unknown"
    }

    private fun statusLabel(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        else -> "Unknown"
    }

    private fun pluggedLabel(plugged: Int): String = when (plugged) {
        0 -> "Unplugged"
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
        else -> "Other"
    }
}
