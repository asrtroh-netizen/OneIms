package com.oneims.app.core

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * IMS 掉线守护前台服务。
 *
 * 两条自愈触发：
 *   1) **Shizuku binder 到达**（Shizuku 服务启动/重启的瞬间）→ 立刻重应用上次配置
 *      —— 对应「开机后启动 Shizuku 即自动恢复」，也是新补丁下 persistent=false 的主要补救；
 *   2) **定时巡检**（默认 120s）→ 若检测到 IMS 未注册，则重应用上次配置，治「间歇掉线」。
 *
 * 铁律：重应用复用 [ReapplyManager] → 内部走安全护栏，异常自动回滚，绝不搞挂基本通信。
 */
class GuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    private val binderListener = Shizuku.OnBinderReceivedListener {
        // Shizuku 就绪的瞬间，重应用上次配置
        scope.launch {
            runCatching {
                ReapplyManager.reapply(
                    applicationContext,
                    ReapplyTrigger.SHIZUKU_READY,
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderListener) }
        startGuardLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 被系统杀掉后尽量重启，保持守护在线
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeBinderReceivedListener(binderListener) }
        loopJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startGuardLoop() {
        loopJob = scope.launch {
            while (isActive) {
                delay(INTERVAL_MS)
                if (!ConfigStore.isGuardEnabled(applicationContext)) continue
                val saved = ConfigStore.lastApplied(applicationContext) ?: continue
                // 仅在「明确检测到 IMS 未注册」时才重应用，避免无谓写入
                if (isImsDown(applicationContext, saved.subId)) {
                    runCatching {
                        ReapplyManager.reapply(
                            applicationContext,
                            ReapplyTrigger.IMS_NOT_REGISTERED,
                        )
                    }
                }
            }
        }
    }

    /** 明确读到未注册返回 true；读不到（未知）返回 false，避免误触发。 */
    @SuppressLint("MissingPermission")
    private fun isImsDown(context: Context, subId: Int): Boolean {
        return runCatching {
            val tm = context.getSystemService(TelephonyManager::class.java)
                ?.createForSubscriptionId(subId)
            val registered = TelephonyManager::class.java.getMethod("isImsRegistered")
                .invoke(tm) as Boolean
            registered.not()
        }.getOrDefault(false)
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL, getString(com.oneims.app.R.string.guard_channel), NotificationManager.IMPORTANCE_MIN,
            )
            ch.description = getString(com.oneims.app.R.string.guard_channel_desc)
            nm.createNotificationChannel(ch)
        }
        val notif: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(com.oneims.app.R.string.guard_notif_title))
            .setContentText(getString(com.oneims.app.R.string.guard_notif_text))
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
        private const val CHANNEL = "oneims_guard"
        private const val NOTIF_ID = 1001
        private const val INTERVAL_MS = 120_000L // 巡检间隔 2 分钟

        fun start(context: Context) {
            val i = Intent(context, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuardService::class.java))
        }
    }
}
