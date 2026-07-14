package com.oneims.app.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SubscriptionManager
import android.util.Log
import com.oneims.app.onekuku.OneKukuBootRestoreCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 开机/解锁/SIM 变化后调度 OneKuku 自动检查与恢复（后台协程，不做 APN/切卡）。
 */
class OneKukuBootRestoreService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var subListener: SubscriptionManager.OnSubscriptionsChangedListener? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scheduleRun(debounceMs = intent?.getLongExtra(EXTRA_DEBOUNCE_MS, 0L) ?: 0L)
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
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
                    stopSelf()
                }
            }
        }, debounceMs.coerceAtLeast(0L))
    }

    companion object {
        private const val TAG = "OneIMS-OneKuku"
        const val EXTRA_DEBOUNCE_MS = "debounce_ms"

        fun enqueue(context: Context, debounceMs: Long = 0L) {
            val intent = Intent(context, OneKukuBootRestoreService::class.java)
                .putExtra(EXTRA_DEBOUNCE_MS, debounceMs)
            // 开机/解锁广播场景允许后台 startService；避免 startForegroundService 未调 startForeground ANR/崩。
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "enqueue boot restore: ${it.message}") }
        }
    }
}
