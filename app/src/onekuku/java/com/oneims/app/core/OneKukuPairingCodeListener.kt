package com.oneims.app.core

import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * 对齐 V15 `AdbPairingNotificationListener`：从系统设置通知抓六位配对码并自动填入。
 * 需用户在系统设置里对本 App 开启「通知使用权」。
 */
@RequiresApi(Build.VERSION_CODES.R)
class OneKukuPairingCodeListener : NotificationListenerService() {

    private val pairingCodeRegex = Regex("(\\d{6})")

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!ConfigStore.isOneKukuAutoPairingEnabled(this)) return
        if (sbn?.packageName != "com.android.settings") return

        val notification = sbn.notification
        val title = notification.extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = notification.extras.getCharSequence("android.text")?.toString().orEmpty()
        val match = pairingCodeRegex.find(title) ?: pairingCodeRegex.find(text) ?: return
        val code = match.value
        Log.i(TAG, "auto-detected pairing code from Settings notification")
        OneKukuPairingHostService.setAutoPairCode(code)
        // 若正在等待配对，立刻提交；否则下次 startHolding 会消费。
        applicationContext.sendBroadcast(
            Intent(OneKukuPairingNotification.ACTION_SUBMIT_CODE)
                .setPackage(packageName)
                .putExtra(OneKukuPairingNotification.EXTRA_PAIRING_CODE, code),
        )
    }

    companion object {
        private const val TAG = "OneIMS-PairListener"
    }
}
