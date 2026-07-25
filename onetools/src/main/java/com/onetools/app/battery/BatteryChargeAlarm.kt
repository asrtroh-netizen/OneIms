package com.onetools.app.battery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.onetools.app.MainActivity
import com.onetools.app.R

object BatteryChargeAlarm {
    private const val CHANNEL_ID = "one_battery_alarm"
    private const val NOTIF_ID = 7102

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
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_onetools)
            .setContentTitle(context.getString(R.string.battery_alarm_title))
            .setContentText(
                context.getString(R.string.battery_alarm_body, percent, threshold),
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
        }
    }
}
