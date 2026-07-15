package com.oneims.app.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SubscriptionManager
import android.util.Log
import com.oneims.app.R
import com.oneims.app.onekuku.OneKukuBootRestoreCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 开机/解锁/SIM/Wi‑Fi 变化后调度 OneKuku 自动检查与恢复。
 *
 * 必须以前台服务拉起：Android 12+ 从 BootReceiver 普通 startService 常被
 * `Background start not allowed` 拦住，导致「重启后要点半天才就绪」。
 */
class OneKukuBootRestoreService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var subListener: SubscriptionManager.OnSubscriptionsChangedListener? = null
    private var running = false
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureForeground()
        val sm = getSystemService(SubscriptionManager::class.java)
        if (sm != null) {
            val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                override fun onSubscriptionsChanged() {
                    scheduleRun(debounceMs = 3_000L)
                }
            }
            subListener = listener
            runCatching {
                sm.addOnSubscriptionsChangedListener(listener)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService 后必须尽快进前台，不能等 debounce。
        ensureForeground()
        scheduleRun(debounceMs = intent?.getLongExtra(EXTRA_DEBOUNCE_MS, 0L) ?: 0L)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        subListener?.let { listener ->
            runCatching {
                getSystemService(SubscriptionManager::class.java)
                    ?.removeOnSubscriptionsChangedListener(listener)
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        foregroundStarted = true
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL,
                getString(R.string.onekuku_boot_fg_channel),
                NotificationManager.IMPORTANCE_MIN,
            )
            ch.description = getString(R.string.onekuku_boot_fg_channel_desc)
            nm.createNotificationChannel(ch)
        }
        val notif: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.onekuku_boot_fg_title))
            .setContentText(getString(R.string.onekuku_boot_fg_text))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun scheduleRun(debounceMs: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (running) return@postDelayed
            running = true
            scope.launch {
                try {
                    OneKukuBootRestoreCoordinator.run(applicationContext)
                } catch (error: Throwable) {
                    Log.w(TAG, "boot restore failed: ${error.message}")
                } finally {
                    running = false
                    runCatching {
                        if (Build.VERSION.SDK_INT >= 24) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } else {
                            @Suppress("DEPRECATION")
                            stopForeground(true)
                        }
                    }
                    stopSelf()
                }
            }
        }, debounceMs.coerceAtLeast(0L))
    }

    companion object {
        private const val TAG = "OneIMS-OneKuku"
        private const val CHANNEL = "oneims_boot_restore_fg"
        private const val NOTIF_ID = 1003
        const val EXTRA_DEBOUNCE_MS = "debounce_ms"

        fun enqueue(context: Context, debounceMs: Long = 0L) {
            val intent = Intent(context, OneKukuBootRestoreService::class.java)
                .putExtra(EXTRA_DEBOUNCE_MS, debounceMs)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "enqueue boot restore: ${it.message}") }
        }
    }
}
