package com.onetools.app.updates

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.onetools.app.MainActivity
import com.onetools.app.R

object UpdateCheckNotifier {
    private const val CHANNEL = "one_update_check"
    private const val NOTIF_ID = 7201

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.updates_auto_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun notifyUpdates(context: Context, titles: List<String>) {
        if (titles.isEmpty()) return
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATES, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            7201,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = titles.take(5).joinToString(" · ")
        val notif = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_onetools)
            .setContentTitle(context.getString(R.string.updates_auto_notif_title, titles.size))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
    }

    const val EXTRA_OPEN_UPDATES = "com.onetools.app.extra.OPEN_UPDATES"
}
