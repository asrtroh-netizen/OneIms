package com.onetools.app.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.onetools.app.MainActivity
import com.onetools.app.R

/**
 * 把解析后的国内软件状态发布为：
 * 1) Ongoing 通知（API 36+ 请求 Live Update 芯片）
 * 2) 顶栏灵动岛胶囊悬浮窗（观感可控，需悬浮窗权限）
 */
object LiveStatusHub {
    const val CHANNEL_ID = "onetools_live_status_v1"
    const val NOTIFICATION_ID = 71

    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    @Volatile
    private var lastChip: String? = null

    fun lastChipText(): String? = lastChip

    fun publish(context: Context, source: LiveStatusSource, chipText: String, detail: String) {
        val app = context.applicationContext
        ensureChannel(app)
        lastChip = chipText
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(app, source, chipText, detail))
        val prefs = LiveStatusPrefs(app)
        if (prefs.masterEnabled && prefs.capsuleEnabled) {
            LiveStatusCapsuleOverlay.get(app).update(chipText)
        }
    }

    fun clear(context: Context) {
        lastChip = null
        val app = context.applicationContext
        app.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        LiveStatusCapsuleOverlay.get(app).hide()
    }

    fun refreshCapsuleVisibility(context: Context) {
        val app = context.applicationContext
        val prefs = LiveStatusPrefs(app)
        val chip = lastChip
        if (prefs.masterEnabled && prefs.capsuleEnabled && !chip.isNullOrBlank()) {
            LiveStatusCapsuleOverlay.get(app).show(chip)
        } else {
            LiveStatusCapsuleOverlay.get(app).hide()
        }
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ContextCompat.startActivity(context, intent, null)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.live_status_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.live_status_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(
        context: Context,
        source: LiveStatusSource,
        chipText: String,
        detail: String,
    ): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = when (source) {
            LiveStatusSource.MEITUAN -> context.getString(R.string.live_source_meituan)
            LiveStatusSource.DIDI -> context.getString(R.string.live_source_didi)
            LiveStatusSource.CAINIAO -> context.getString(R.string.live_source_cainiao)
        }
        val content = detail.ifBlank { chipText }
        if (Build.VERSION.SDK_INT >= 36) {
            val builder = Notification.Builder(context, CHANNEL_ID)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_live_status)
                .setShortCriticalText(chipText.take(7))
            builder.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
            runCatching {
                Notification.Builder::class.java
                    .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                    .invoke(builder, true)
            }
            return builder.build()
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setSilent(true)
            .setShowWhen(false)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_live_status)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    fun isNotificationAccessEnabled(context: Context): Boolean {
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val pkg = context.packageName
        val listener = LiveStatusNotificationListener::class.java.name
        return flat.split(':').any { entry ->
            entry.equals("$pkg/$listener", ignoreCase = true) ||
                (entry.startsWith("$pkg/") && entry.contains("LiveStatusNotificationListener"))
        }
    }

    fun openNotificationAccessSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ContextCompat.startActivity(context, intent, null)
    }
}
