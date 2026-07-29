package com.oneims.app.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.oneims.app.R
import com.oneims.app.core.privilege.PrivilegeBridges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 对齐 V15 `WatchdogService`：binder 死后限次静默重拉 OneBridge（仅 ADB 通道）。
 */
class OneKukuWatchdogService : Service() {

    companion object {
        private const val TAG = "OneIMS-Watchdog"
        private const val ACTION_START = "com.oneims.app.onekuku.watchdog.START"
        private const val ACTION_STOP = "com.oneims.app.onekuku.watchdog.STOP"
        private const val MAX_RESTART_ATTEMPTS = 5
        private const val STABLE_WINDOW_MILLIS = 300_000L
        private const val RESTART_IN_FLIGHT_RESET_MILLIS = 15_000L
        private const val NOTIFICATION_ID = 71_010
        private const val CHANNEL_ID = "onekuku_watchdog"

        fun start(context: Context) {
            if (ChannelLine.usesShizuku) return
            if (!ConfigStore.isOneKukuWatchdogEnabled(context)) return
            val intent = Intent(context, OneKukuWatchdogService::class.java).setAction(ACTION_START)
            runCatching {
                context.startForegroundService(intent)
            }.onFailure {
                Log.w(TAG, "startForegroundService failed", it)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OneKukuWatchdogService::class.java).setAction(ACTION_STOP)
            runCatching { context.stopService(intent) }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var restartAttempts = 0
    private var isRestartInFlight = false
    private var watchdogEnabledForThisSession = false
    private var listenersRegistered = false
    private var stableResetJob: Job? = null
    private var restartInFlightResetJob: Job? = null

    private val binderReceivedListener: () -> Unit = {
        Log.i(TAG, "binder received")
        isRestartInFlight = false
        restartInFlightResetJob?.cancel()
        scheduleStableReset()
    }

    private val binderDeadListener: () -> Unit = binderDead@{
        Log.w(TAG, "binder death")
        stableResetJob?.cancel()
        if (!watchdogEnabledForThisSession) {
            stopSelf()
            return@binderDead
        }
        if (!ConfigStore.isOneKukuWatchdogEnabled(this)) {
            stopSelf()
            return@binderDead
        }
        if (isRestartInFlight) {
            Log.i(TAG, "restart already in flight")
            return@binderDead
        }
        if (restartAttempts >= MAX_RESTART_ATTEMPTS) {
            Log.w(TAG, "restart limit reached")
            stopSelf()
            return@binderDead
        }
        restartAttempts += 1
        isRestartInFlight = true
        serviceScope.launch(Dispatchers.IO) {
            Log.i(TAG, "silent activate attempt=$restartAttempts/$MAX_RESTART_ATTEMPTS")
            runCatching {
                OneKukuMiniAdbClient.activateExistingOrNeedPair(applicationContext)
            }.onFailure {
                Log.w(TAG, "silent activate failed", it)
            }
            scheduleRestartInFlightReset()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        watchdogEnabledForThisSession = ConfigStore.isOneKukuWatchdogEnabled(this)
        if (!watchdogEnabledForThisSession) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!listenersRegistered) {
            val bridge = PrivilegeBridges.current
            bridge.addBinderReceivedListener(binderReceivedListener, sticky = true)
            bridge.addBinderDeadListener(binderDeadListener)
            listenersRegistered = true
        }
        if (OneKukuManager.isRunning()) {
            binderReceivedListener()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stableResetJob?.cancel()
        restartInFlightResetJob?.cancel()
        serviceScope.cancel()
        if (listenersRegistered) {
            val bridge = PrivilegeBridges.current
            runCatching { bridge.removeBinderReceivedListener(binderReceivedListener) }
            runCatching { bridge.removeBinderDeadListener(binderDeadListener) }
            listenersRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleStableReset() {
        stableResetJob?.cancel()
        stableResetJob = serviceScope.launch {
            delay(STABLE_WINDOW_MILLIS)
            if (OneKukuManager.isRunning()) {
                restartAttempts = 0
                Log.i(TAG, "reset restart attempts after stable window")
            }
        }
    }

    private fun scheduleRestartInFlightReset() {
        restartInFlightResetJob?.cancel()
        restartInFlightResetJob = serviceScope.launch {
            delay(RESTART_IN_FLIGHT_RESET_MILLIS)
            if (!OneKukuManager.isRunning()) {
                isRestartInFlight = false
                Log.w(TAG, "cleared restart in-flight after timeout")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.onekuku_watchdog_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.onekuku_watchdog_channel_desc)
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.onekuku_watchdog_title))
            .setContentText(getString(R.string.onekuku_watchdog_text))
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
