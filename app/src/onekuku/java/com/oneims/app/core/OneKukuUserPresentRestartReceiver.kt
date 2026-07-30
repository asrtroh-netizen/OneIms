package com.oneims.app.core

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 对齐 V15 `UserPresentRestartReceiver`：解锁后立即 + 5s + 15s 强制续跑开机激活。
 *
 * 延后 enqueue 必须 [goAsync] 撑住 PendingResult，否则 15s 后主线程回调时
 * 会踩 `Broadcast already finished`（dropbox 已见 com.oneims.app）。
 */
class OneKukuUserPresentRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (Intent.ACTION_USER_PRESENT != intent?.action) return
        if (ChannelLine.usesShizuku) return
        if (!ConfigStore.isOneKukuBootAutoCheck(context)) {
            setEnabled(context, false)
            return
        }

        setEnabled(context, false)
        val app = context.applicationContext
        val pending = goAsync()
        Log.i(TAG, "USER_PRESENT: force boot restore (immediate + delayed)")
        OneKukuBootRestoreService.enqueue(app, debounceMs = 0L)
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            OneKukuBootRestoreService.enqueue(app, debounceMs = 0L)
        }, 5_000L)
        handler.postDelayed({
            try {
                OneKukuBootRestoreService.enqueue(app, debounceMs = 0L)
            } finally {
                runCatching { pending.finish() }
            }
        }, 15_000L)
    }

    companion object {
        private const val TAG = "OneIMS-UserPresent"

        fun setEnabled(context: Context, enabled: Boolean) {
            val component = ComponentName(context, OneKukuUserPresentRestartReceiver::class.java)
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                component,
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
