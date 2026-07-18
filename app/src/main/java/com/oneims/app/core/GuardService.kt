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
import com.oneims.app.core.privilege.PrivilegeBridges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * IMS 掉线守护前台服务。
 *
 * 两条自愈触发：
 *   1) **特权桥 binder 到达**（OneBridge 或 Shizuku 回落）→ 立刻重应用上次配置
 *      —— 开机后通道就绪即可自动恢复临时覆盖（非 system app 无法 persistent）；
 *   2) **定时巡检**（默认 120s）→ 若检测到 IMS 未注册，则重应用上次配置，治「间歇掉线」。
 *   另：BootReceiver 在存在 lastApplied 时也会拉起本服务；开机编排里也会主动 BOOT 重应用。
 */
class GuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    @Volatile
    private var lastBridgeReadyReapplyAtMs: Long = 0L

    private val binderReceivedListener: () -> Unit = {
        scope.launch {
            val now = System.currentTimeMillis()
            // 同一 binder 的粘性/重投通知做冷却，避免独立版通道周期投递打满 CarrierConfig。
            if (now - lastBridgeReadyReapplyAtMs < BRIDGE_READY_DEBOUNCE_MS) {
                return@launch
            }
            lastBridgeReadyReapplyAtMs = now
            runCatching {
                ReapplyManager.reapply(
                    applicationContext,
                    ReapplyTrigger.BRIDGE_READY,
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        runCatching {
            PrivilegeBridges.current.addBinderReceivedListener(
                binderReceivedListener,
                sticky = true,
            )
        }
        startGuardLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 被系统杀掉后尽量重启，保持守护在线
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching {
            PrivilegeBridges.current.removeBinderReceivedListener(binderReceivedListener)
        }
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
        private const val BRIDGE_READY_DEBOUNCE_MS = 60_000L

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
