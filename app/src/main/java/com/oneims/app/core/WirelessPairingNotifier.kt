package com.oneims.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.oneims.app.R

/**
 * Shizuku 式无线调试配对：下拉状态栏通知里直接填六位码。
 */
object WirelessPairingNotifier {

    const val CHANNEL_ID = "oneims_wireless_pairing"
    const val NOTIFICATION_ID = 71_001
    const val ACTION_SUBMIT_CODE = "com.oneims.app.action.SUBMIT_WIRELESS_PAIRING_CODE"
    const val KEY_REMOTE_INPUT = "wireless_pairing_code"
    const val EXTRA_PAIRING_CODE = "pairing_code"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.wireless_pairing_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.wireless_pairing_channel_desc)
            },
        )
    }

    fun showPairingPrompt(context: Context) {
        val app = context.applicationContext
        ensureChannel(app)
        val nm = app.getSystemService(NotificationManager::class.java) ?: return

        val wirelessIntent = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val openIntent = if (wirelessIntent.resolveActivity(app.packageManager) != null) {
            wirelessIntent
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openWireless = PendingIntent.getActivity(
            app,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val replyIntent = Intent(app, WirelessPairingCodeReceiver::class.java)
            .setAction(ACTION_SUBMIT_CODE)
        val replyPending = PendingIntent.getBroadcast(
            app,
            1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val remoteInput = RemoteInput.Builder(KEY_REMOTE_INPUT)
            .setLabel(app.getString(R.string.wireless_pairing_input_label))
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            app.getString(R.string.wireless_pairing_reply_action),
            replyPending,
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(app.getString(R.string.wireless_pairing_title))
            .setContentText(app.getString(R.string.wireless_pairing_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(app.getString(R.string.wireless_pairing_big_text)),
            )
            .setContentIntent(openWireless)
            .addAction(replyAction)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun showResult(context: Context, ok: Boolean, detail: String) {
        val app = context.applicationContext
        ensureChannel(app)
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(
                app.getString(
                    if (ok) R.string.wireless_pairing_result_ok_title
                    else R.string.wireless_pairing_result_fail_title,
                ),
            )
            .setContentText(detail)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val nm = context.applicationContext.getSystemService(NotificationManager::class.java)
        nm?.cancel(NOTIFICATION_ID)
    }
}
