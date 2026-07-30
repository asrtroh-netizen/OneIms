package com.oneims.app.core

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 对齐 Shizuku V15 `AdbPairingService`：用 specialUse FGS 扛住六位码通知。
 * 纯 [NotificationManager.notify] 在一加/小米等国产机上易被折叠/清掉，表现为「通知栏不稳定」。
 */
class OneKukuPairingHostService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundSafe()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val notification = OneKukuPairingNotification.buildWaitingNotification(this)
                startAsForeground(notification)
            }
        }
        return START_STICKY
    }

    private fun startAsForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    OneKukuPairingNotification.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(OneKukuPairingNotification.NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "startForeground failed, fall back to plain notify", e)
            // Android 12+ 后台限制：仍尽量贴通知，避免完全丢码入口。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                OneKukuPairingNotification.notifyPlain(this, notification)
            }
        }
    }

    private fun stopForegroundSafe() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    companion object {
        private const val TAG = "OneIMS-PairHost"
        const val ACTION_HOLD = "com.oneims.app.action.HOLD_PAIRING_NOTIFICATION"
        const val ACTION_STOP = "com.oneims.app.action.STOP_PAIRING_HOST"

        @Volatile
        private var autoPairCode: String? = null

        /** V15 同款：通知监听器抓到六位码后注入，立刻走配对广播。 */
        fun setAutoPairCode(code: String) {
            val digits = code.filter { it.isDigit() }.takeLast(6)
            if (digits.length != 6) return
            autoPairCode = digits
            Log.i(TAG, "autoPairCode armed")
        }

        fun consumeAutoPairCode(): String? {
            val code = autoPairCode
            autoPairCode = null
            return code
        }

        fun startHolding(context: Context) {
            val app = context.applicationContext
            val pendingAuto = consumeAutoPairCode()
            if (pendingAuto != null) {
                Log.i(TAG, "auto-submitting pairing code from listener")
                app.sendBroadcast(
                    Intent(OneKukuPairingNotification.ACTION_SUBMIT_CODE)
                        .setPackage(app.packageName)
                        .putExtra(OneKukuPairingNotification.EXTRA_PAIRING_CODE, pendingAuto),
                )
                return
            }
            val intent = Intent(app, OneKukuPairingHostService::class.java).setAction(ACTION_HOLD)
            runCatching {
                ContextCompat.startForegroundService(app, intent)
            }.onFailure {
                Log.w(TAG, "startForegroundService failed, plain notify", it)
                OneKukuPairingNotification.notifyPlain(
                    app,
                    OneKukuPairingNotification.buildWaitingNotification(app),
                )
            }
        }

        fun stopHolding(context: Context) {
            val app = context.applicationContext
            runCatching {
                app.startService(
                    Intent(app, OneKukuPairingHostService::class.java).setAction(ACTION_STOP),
                )
            }
            runCatching {
                app.stopService(Intent(app, OneKukuPairingHostService::class.java))
            }
        }
    }
}
