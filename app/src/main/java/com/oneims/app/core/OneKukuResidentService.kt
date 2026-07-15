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
import com.oneims.app.onekuku.OneKukuHiddenRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * OneKuku 通道常驻保活：激活成功后以前台服务顶在后台，
 * 周期性检查 binder；掉了且已配对 + Wi‑Fi 可用时静默重连（不刷「激活中」）。
 */
class OneKukuResidentService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private val reconnectMutex = Mutex()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        startResidentLoop()
        Log.i(TAG, "resident service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        Log.i(TAG, "resident service destroyed")
        super.onDestroy()
    }

    private fun startResidentLoop() {
        loopJob = scope.launch {
            while (isActive) {
                delay(INTERVAL_MS)
                ensureChannelQuietly()
            }
        }
    }

    private suspend fun ensureChannelQuietly() {
        if (OneKukuManager.isReady()) {
            // 已就绪时把 UI 相位从「激活中」纠回，避免进程内相位残留。
            val phase = OneKukuActivationUi.phase
            if (phase == OneKukuActivationPhase.CONNECTING ||
                phase == OneKukuActivationPhase.STARTING ||
                phase == OneKukuActivationPhase.WAITING_PAIR ||
                phase == OneKukuActivationPhase.PAIRING ||
                phase == OneKukuActivationPhase.ACTIVE
            ) {
                OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
            }
            return
        }
        if (!OneKukuEmbeddedAdbActivator.hasPairedOnce(applicationContext)) return
        if (!OneKukuAdbMdns.isWifiClientConnected(applicationContext)) return

        reconnectMutex.withLock {
            if (OneKukuManager.isReady()) return@withLock
            Log.i(TAG, "channel lost; silent reconnect")
            OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)
            val wake = OneKukuHiddenRunner.wake()
            if (wake.success && OneKukuManager.isReady()) {
                OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
                Log.i(TAG, "silent wake ok")
                return@withLock
            }
            // 静默直连：不改 CONNECTING，避免首页闪「激活中」
            when (val outcome = OneKukuMiniAdbClient.activateExistingOrNeedPair(applicationContext)) {
                is OneKukuMiniAdbClient.Outcome.Success -> {
                    OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
                    Log.i(TAG, "silent activate ok")
                }
                else -> Log.w(TAG, "silent activate skip: $outcome")
            }
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL,
                getString(com.oneims.app.R.string.onekuku_resident_channel),
                NotificationManager.IMPORTANCE_MIN,
            )
            ch.description = getString(com.oneims.app.R.string.onekuku_resident_channel_desc)
            nm.createNotificationChannel(ch)
        }
        val notif: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(com.oneims.app.R.string.onekuku_resident_notif_title))
            .setContentText(getString(com.oneims.app.R.string.onekuku_resident_notif_text))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    companion object {
        private const val TAG = "OneIMS-Resident"
        private const val CHANNEL = "oneims_onekuku_resident"
        private const val NOTIF_ID = 1016
        private const val INTERVAL_MS = 20_000L

        fun start(context: Context) {
            val i = Intent(context, OneKukuResidentService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OneKukuResidentService::class.java))
        }
    }
}
