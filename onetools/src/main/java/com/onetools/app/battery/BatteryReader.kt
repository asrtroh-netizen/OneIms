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
    val isCharging: Boolean,
    val isPlugged: Boolean,
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

        val isPlugged = plugged != 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            (isPlugged && status == BatteryManager.BATTERY_STATUS_FULL)

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
            isCharging = isCharging,
            isPlugged = isPlugged,
        )
    }

    fun healthLabel(code: Int): String = when (code) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
        BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "故障"
        BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
        else -> "未知"
    }

    private fun statusLabel(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
        BatteryManager.BATTERY_STATUS_FULL -> "已充满"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未在充电"
        else -> "未知"
    }

    private fun pluggedLabel(plugged: Int): String = when (plugged) {
        0 -> "未插电"
        BatteryManager.BATTERY_PLUGGED_AC -> "交流电"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线"
        BatteryManager.BATTERY_PLUGGED_DOCK -> "底座"
        else -> "其他"
    }
}
