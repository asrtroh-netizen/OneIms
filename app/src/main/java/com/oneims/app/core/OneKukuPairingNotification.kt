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
 * OneKuku 配对通知：下拉状态栏 RemoteInput 填六位码（参考 Shizuku / 冰箱体验）。
 * 用户无需切回 OneIMS。
 */
object OneKukuPairingNotification {

    const val CHANNEL_ID = "onekuku_pairing"
    const val NOTIFICATION_ID = 71_001
    const val ACTION_SUBMIT_CODE = "com.oneims.app.action.SUBMIT_WIRELESS_PAIRING_CODE"
    const val KEY_REMOTE_INPUT = "wireless_pairing_code"
    const val EXTRA_PAIRING_CODE = "pairing_code"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.onekuku_pair_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.onekuku_pair_channel_desc)
            },
        )
    }

    fun showWaiting(context: Context) {
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
            .setLabel(app.getString(R.string.onekuku_pair_input_hint))
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            app.getString(R.string.onekuku_pair_action),
            replyPending,
        ).addRemoteInput(remoteInput).build()

        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(app.getString(R.string.onekuku_pair_title_waiting))
                .setContentText(app.getString(R.string.onekuku_pair_text_waiting))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(app.getString(R.string.onekuku_pair_text_waiting)),
                )
                .setContentIntent(openWireless)
                .addAction(replyAction)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build(),
        )
    }

    fun showPairingInProgress(context: Context) {
        postSimple(
            context,
            title = context.getString(R.string.onekuku_pair_title_progress),
            text = context.getString(R.string.onekuku_pair_text_progress),
            ongoing = true,
        )
    }

    fun showSuccess(context: Context) {
        postSimple(
            context,
            title = context.getString(R.string.onekuku_pair_title_ok),
            text = context.getString(R.string.onekuku_pair_text_ok),
            ongoing = false,
            autoCancel = true,
        )
    }

    fun showFailure(context: Context, reason: String) {
        postSimple(
            context,
            title = context.getString(R.string.onekuku_pair_title_fail),
            text = context.getString(R.string.onekuku_pair_text_fail, reason),
            ongoing = false,
            autoCancel = true,
        )
        // 失败后再次挂上等待通知，方便重试且无需回 App
        showWaiting(context)
    }

    fun cancel(context: Context) {
        context.applicationContext.getSystemService(NotificationManager::class.java)
            ?.cancel(NOTIFICATION_ID)
    }

    private fun postSimple(
        context: Context,
        title: String,
        text: String,
        ongoing: Boolean,
        autoCancel: Boolean = false,
    ) {
        val app = context.applicationContext
        ensureChannel(app)
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(ongoing)
                .setAutoCancel(autoCancel)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }
}
