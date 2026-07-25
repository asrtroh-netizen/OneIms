package com.onebattery.app.battery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.onebattery.app.MainActivity
import com.onebattery.app.R

enum class BatteryAlarmKind {
    CHARGE,
    LOW,
    TEMP_HIGH,
    TEMP_LOW,
}

object BatteryChargeAlarm {
    private const val CHANNEL_ID = "one_battery_alarm"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.battery_alarm_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
        mgr.createNotificationChannel(ch)
    }

    fun notifyThreshold(context: Context, percent: Int, threshold: Int) {
        notify(
            context,
            BatteryAlarmKind.CHARGE,
            context.getString(R.string.battery_alarm_title),
            context.getString(R.string.battery_alarm_body, percent, threshold),
        )
    }

    fun notifyLow(context: Context, percent: Int, threshold: Int) {
        notify(
            context,
            BatteryAlarmKind.LOW,
            context.getString(R.string.battery_low_alarm_title),
            context.getString(R.string.battery_low_alarm_body, percent, threshold),
        )
    }

    fun notifyTempHigh(context: Context, tempC: Float, threshold: Float) {
        notify(
            context,
            BatteryAlarmKind.TEMP_HIGH,
            context.getString(R.string.battery_temp_high_alarm_title),
            context.getString(R.string.battery_temp_high_alarm_body, tempC, threshold),
        )
    }

    fun notifyTempLow(context: Context, tempC: Float, threshold: Float) {
        notify(
            context,
            BatteryAlarmKind.TEMP_LOW,
            context.getString(R.string.battery_temp_low_alarm_title),
            context.getString(R.string.battery_temp_low_alarm_body, tempC, threshold),
        )
    }

    private fun notify(
        context: Context,
        kind: BatteryAlarmKind,
        title: String,
        body: String,
    ) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            kind.ordinal,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notifId(kind), notif)
        }
    }

    private fun notifId(kind: BatteryAlarmKind): Int = when (kind) {
        BatteryAlarmKind.CHARGE -> 7102
        BatteryAlarmKind.LOW -> 7103
        BatteryAlarmKind.TEMP_HIGH -> 7104
        BatteryAlarmKind.TEMP_LOW -> 7105
    }
}
