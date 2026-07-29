package com.oneims.app.core

import android.app.Notification
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
 * OneKuku 配对通知：下拉状态栏 RemoteInput 填六位码。
 * 稳态对齐 V15：由 [OneKukuPairingHostService] 以前台服务扛住，避免国产 OEM 清普通 ongoing。
 */
object OneKukuPairingNotification {

    /** 换 channel id：旧渠道若被 OEM 降到 NONE，用户无需手清也能重新 HIGH。 */
    const val CHANNEL_ID = "onekuku_pairing_fgs"
    const val NOTIFICATION_ID = 71_001
    const val ACTION_SUBMIT_CODE = "com.oneims.app.action.SUBMIT_WIRELESS_PAIRING_CODE"
    const val KEY_REMOTE_INPUT = "wireless_pairing_code"
    const val EXTRA_PAIRING_CODE = "pairing_code"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // 清理旧无 FGS 渠道（若存在），减少「有时有有时无」的双渠道混乱。
        runCatching { nm.deleteNotificationChannel("onekuku_pairing") }
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.onekuku_pair_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.onekuku_pair_channel_desc)
                setSound(null, null)
                setShowBadge(false)
                if (Build.VERSION.SDK_INT >= 29) {
                    setAllowBubbles(false)
                }
            },
        )
    }

    fun buildWaitingNotification(context: Context): Notification {
        val app = context.applicationContext
        ensureChannel(app)

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

        val builder = NotificationCompat.Builder(app, CHANNEL_ID)
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
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= 34) {
            builder.setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE,
            )
        }
        return builder.build()
    }

    fun showWaiting(context: Context) {
        // V15 同款：先起 FGS 再贴通知，国产机不易被清。
        OneKukuPairingHostService.startHolding(context)
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
        OneKukuPairingHostService.stopHolding(context)
    }

    /**
     * 失败时保留失败文案 + RemoteInput 重试，**不要**立刻盖回「等待配对」
     * （否则用户只看到还在等待，以为填码没生效）。
     */
    fun showFailure(context: Context, reason: String) {
        val app = context.applicationContext
        ensureChannel(app)
        val nm = app.getSystemService(NotificationManager::class.java) ?: return

        val replyIntent = Intent(app, WirelessPairingCodeReceiver::class.java)
            .setAction(ACTION_SUBMIT_CODE)
        val replyPending = PendingIntent.getBroadcast(
            app,
            2,
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

        val detail = app.getString(R.string.onekuku_pair_text_fail, reason)
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(app.getString(R.string.onekuku_pair_title_fail))
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .addAction(replyAction)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        // 同 ID 更新 FGS 通知内容；勿再 startHolding（会盖回「等待配对」）。
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        OneKukuPairingHostService.stopHolding(context)
        context.applicationContext.getSystemService(NotificationManager::class.java)
            ?.cancel(NOTIFICATION_ID)
    }

    fun notifyPlain(context: Context, notification: Notification) {
        ensureChannel(context)
        context.applicationContext.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification)
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
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build(),
        )
    }
}
