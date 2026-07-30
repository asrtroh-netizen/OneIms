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
        Log.i(TAG, "USER_PRESENT: force boot restore (immediate + delayed)")
        OneKukuBootRestoreService.enqueue(app, debounceMs = 0L)
        Handler(Looper.getMainLooper()).postDelayed({
            OneKukuBootRestoreService.enqueue(app, debounceMs = 0L)
        }, 5_000L)
        Handler(Looper.getMainLooper()).postDelayed({
            OneKukuBootRestoreService.enqueue(app, debounceMs = 0L)
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
