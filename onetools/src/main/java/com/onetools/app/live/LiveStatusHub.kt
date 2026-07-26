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
import com.onetools.app.live.adapter.AdapterOutcome
import com.onetools.app.live.adapter.NotificationSnippet
import com.onetools.app.live.adapter.VendorAdapterRegistry
import com.onetools.app.live.capsule.CapsuleDisplayMode
import com.onetools.app.live.capsule.CapsuleLifecycle
import com.onetools.app.live.capsule.CapsuleSession
import com.onetools.app.live.capsule.OneCapsuleOverlay
import com.onetools.app.live.capsule.OneCapsuleStore
import com.onetools.app.live.capsule.OneCapsuleTemplates

/**
 * 发布国内软件实时状态：
 * 1) Ongoing 通知（API 36+ Live Update 芯片）
 * 2) One Capsule 悬浮岛（轻提醒 / 展开卡）
 */
object LiveStatusHub {
    const val CHANNEL_ID = "onetools_live_status_v1"
    const val NOTIFICATION_ID = 71

    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    @Volatile
    private var lastChip: String? = null

    fun lastChipText(): String? = lastChip

    fun publish(context: Context, source: LiveStatusSource, chipText: String, detail: String) {
        val pkg = source.packages.firstOrNull().orEmpty()
        val outcome = VendorAdapterRegistry.parse(
            NotificationSnippet(
                packageName = pkg,
                key = "manual-${source.id}",
                title = chipText,
                text = detail,
                isOngoing = true,
            ),
        )
        val session = when (outcome) {
            is AdapterOutcome.Accepted -> outcome.session
            AdapterOutcome.Ignored -> OneCapsuleTemplates.fromNotification(
                source = source,
                title = chipText,
                text = detail,
                chipFallback = chipText,
            )
        }
        publishSession(context, session, expand = false)
    }

    fun publishSession(
        context: Context,
        session: CapsuleSession,
        expand: Boolean = false,
    ) {
        val app = context.applicationContext
        ensureChannel(app)
        lastChip = session.pillText()
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(
            NOTIFICATION_ID,
            buildNotification(app, session.source, session.pillText().take(7), session.subtitle),
        )
        val prefs = LiveStatusPrefs(app)
        CapsuleLifecycle.attach(app)
        OneCapsuleStore.upsert(session, expand = expand)
        if (prefs.masterEnabled && prefs.capsuleEnabled) {
            OneCapsuleOverlay.get(app).start()
        } else {
            OneCapsuleOverlay.get(app).stop()
            OneCapsuleStore.setMode(CapsuleDisplayMode.HIDDEN)
        }
    }

    fun publishDemoMeituan(context: Context, expand: Boolean = false) {
        publishSession(context, OneCapsuleTemplates.meituanDelivering(), expand)
    }

    fun publishDemoDidi(context: Context, expand: Boolean = true) {
        publishSession(context, OneCapsuleTemplates.didiOnTrip(), expand)
    }

    fun publishDemoMulti(context: Context) {
        val app = context.applicationContext
        val prefs = LiveStatusPrefs(app)
        prefs.masterEnabled = true
        prefs.capsuleEnabled = true
        OneCapsuleStore.clear()
        OneCapsuleStore.upsert(OneCapsuleTemplates.meituanDelivering(), expand = false)
        OneCapsuleStore.upsert(OneCapsuleTemplates.didiOnTrip(), expand = false)
        OneCapsuleStore.upsert(OneCapsuleTemplates.cainiaoParcel(), expand = false)
        lastChip = OneCapsuleStore.snapshot().active?.pillText()
        ensureChannel(app)
        app.getSystemService(NotificationManager::class.java)?.notify(
            NOTIFICATION_ID,
            buildNotification(
                app,
                LiveStatusSource.MEITUAN,
                lastChip?.take(7) ?: "实时",
                "多任务演示：左右滑动切换",
            ),
        )
        OneCapsuleOverlay.get(app).start()
    }

    fun clear(context: Context) {
        lastChip = null
        val app = context.applicationContext
        app.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        OneCapsuleStore.clear()
        OneCapsuleOverlay.get(app).stop()
    }

    fun refreshCapsuleVisibility(context: Context) {
        val app = context.applicationContext
        val prefs = LiveStatusPrefs(app)
        if (prefs.masterEnabled && prefs.capsuleEnabled && OneCapsuleStore.snapshot().sessions.isNotEmpty()) {
            OneCapsuleStore.setMode(
                if (OneCapsuleStore.snapshot().mode == CapsuleDisplayMode.HIDDEN) {
                    CapsuleDisplayMode.PILL
                } else {
                    OneCapsuleStore.snapshot().mode
                },
            )
            OneCapsuleOverlay.get(app).start()
        } else if (!prefs.masterEnabled || !prefs.capsuleEnabled) {
            OneCapsuleOverlay.get(app).stop()
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
        val title = source.labelZh
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
        val flat = Settings.Secure.getString(
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
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ContextCompat.startActivity(context, intent, null)
    }
}
