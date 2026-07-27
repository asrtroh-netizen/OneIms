package com.onetools.app.live

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
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
 * 发布国内软件实时状态到 One Capsule 悬浮岛。
 * 刻意不在状态栏旁再挂 Live Update 小胶囊（除 Meter 网速外），避免与时间挤在一起。
 */
object LiveStatusHub {
    const val CHANNEL_ID = "onetools_live_status_v1"
    const val NOTIFICATION_ID = 71

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
        // Real app status is rendered by One Capsule; do not create a second status chip.
        app.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
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
