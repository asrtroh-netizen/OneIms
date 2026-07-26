package com.onetools.app.live

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 只处理白名单国内 App 的进度类通知，转成 Live Update 芯片。
 */
class LiveStatusNotificationListener : NotificationListenerService() {
    private val prefs by lazy { LiveStatusPrefs(this) }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val snap = prefs.snapshot()
        if (!snap.masterEnabled) return
        val source = LiveStatusSource.fromPackage(sbn.packageName) ?: return
        if (source !in snap.enabledSources) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        val chip = LiveStatusParser.toChipText(source, title, text)
        val detail = listOfNotNull(title?.toString(), text?.toString())
            .joinToString(" · ")
            .ifBlank { chip }
        Log.d(TAG, "chip source=${source.id} pkg=${sbn.packageName} chip=$chip")
        LiveStatusHub.publish(this, source, chip, detail.take(120))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val source = LiveStatusSource.fromPackage(sbn.packageName) ?: return
        // 简单策略：移除后若没有其它活跃白名单通知则清空芯片。
        val still = runCatching { activeNotifications }.getOrNull().orEmpty().any { active ->
            val s = LiveStatusSource.fromPackage(active.packageName)
            s != null && prefs.isSourceEnabled(s) && prefs.masterEnabled &&
                active.key != sbn.key
        }
        if (!still) {
            Log.d(TAG, "clear chip after remove source=${source.id}")
            LiveStatusHub.clear(this)
        }
    }

    companion object {
        private const val TAG = "OneTools-LiveStatus"
    }
}
