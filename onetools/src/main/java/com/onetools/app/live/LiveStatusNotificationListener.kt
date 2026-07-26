package com.onetools.app.live

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.onetools.app.live.adapter.AdapterOutcome
import com.onetools.app.live.adapter.NotificationSnippet
import com.onetools.app.live.adapter.VendorAdapterRegistry
import com.onetools.app.live.capsule.CapsuleLifecycle

/**
 * 白名单通知 → 厂商适配器 → One Capsule 会话。
 */
class LiveStatusNotificationListener : NotificationListenerService() {
    private val prefs by lazy { LiveStatusPrefs(this) }

    override fun onListenerConnected() {
        CapsuleLifecycle.attach(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val snap = prefs.snapshot()
        if (!snap.masterEnabled) return
        val source = LiveStatusSource.fromPackage(sbn.packageName) ?: return
        if (source !in snap.enabledSources) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (
            extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            )?.toString()
        val outcome = VendorAdapterRegistry.parse(
            NotificationSnippet(
                packageName = sbn.packageName.orEmpty(),
                key = sbn.key,
                title = title,
                text = text,
                isOngoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0,
            ),
        )
        when (outcome) {
            is AdapterOutcome.Ignored -> {
                Log.d(TAG, "ignore pkg=${sbn.packageName}")
            }
            is AdapterOutcome.Accepted -> {
                Log.d(TAG, "accept source=${source.id} conf=${outcome.confidence} id=${outcome.session.id}")
                CapsuleLifecycle.attach(this)
                LiveStatusHub.publishSession(this, outcome.session, expand = false)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val source = LiveStatusSource.fromPackage(sbn.packageName) ?: return
        CapsuleLifecycle.onNotificationRemoved("live-${source.id}-${sbn.key}")
        // 适配器 id 前缀 live-{source}-{key}
        val still = runCatching { activeNotifications }.getOrNull().orEmpty().any { active ->
            val s = LiveStatusSource.fromPackage(active.packageName)
            s != null && prefs.isSourceEnabled(s) && prefs.masterEnabled &&
                active.key != sbn.key
        }
        if (!still) {
            Log.d(TAG, "clear after remove source=${source.id}")
            LiveStatusHub.clear(this)
        } else {
            LiveStatusHub.refreshCapsuleVisibility(this)
        }
    }

    companion object {
        private const val TAG = "OneTools-LiveStatus"
    }
}
