package com.onetools.app.battery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.onetools.app.MainActivity
import com.onetools.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Foreground sampler while charging — records a charge session and fires optional % alarm.
 */
class BatteryChargeService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    private var sessionId: String? = null
    private var startedAt: Long = 0L
    private var startPercent: Int = -1
    private var startMah: Int = -1
    private var alarmFired = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                finalizeSession()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startTracking()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        finalizeSession()
        super.onDestroy()
    }

    private fun startTracking() {
        val prefs = runBlocking { BatteryPrefs(applicationContext).snapshot() }
        if (!prefs.trackingEnabled) {
            stopSelf()
            return
        }
        ensureChannel()
        val notif = buildNotif(getString(R.string.battery_tracking_starting))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
        if (sessionId == null) {
            val snap = BatteryReader.read(this) ?: run {
                stopSelf()
                return
            }
            if (!snap.isPlugged) {
                stopSelf()
                return
            }
            sessionId = UUID.randomUUID().toString()
            startedAt = System.currentTimeMillis()
            startPercent = snap.percent
            startMah = snap.chargeCounterMah
            alarmFired = false
        }
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                tick()
                delay(15_000L)
            }
        }
    }

    private fun tick() {
        val snap = BatteryReader.read(this) ?: return
        val prefs = runBlocking { BatteryPrefs(applicationContext).snapshot() }
        val mgr = getSystemService(NotificationManager::class.java)
        mgr?.notify(
            NOTIF_ID,
            buildNotif(
                getString(
                    R.string.battery_tracking_live,
                    snap.percent,
                    snap.currentNowMa,
                ),
            ),
        )
        if (
            prefs.chargeAlarmEnabled &&
            !alarmFired &&
            snap.percent >= prefs.chargeAlarmPercent &&
            snap.isPlugged
        ) {
            alarmFired = true
            BatteryChargeAlarm.notifyThreshold(
                this,
                snap.percent,
                prefs.chargeAlarmPercent,
            )
        }
        if (!snap.isPlugged) {
            finalizeSession()
            stopSelf()
        }
    }

    private fun finalizeSession() {
        val id = sessionId ?: return
        sessionId = null
        val snap = BatteryReader.read(this) ?: return
        val endPct = snap.percent
        val endMah = snap.chargeCounterMah
        val estimate = BatteryCapacityEstimator.estimateFullMah(
            startPercent = startPercent,
            endPercent = endPct,
            startChargeMah = startMah,
            endChargeMah = endMah,
        ) ?: 0
        val entity = BatterySessionEntity(
            id = id,
            kind = "CHARGE",
            startedAt = startedAt,
            endedAt = System.currentTimeMillis(),
            startPercent = startPercent,
            endPercent = endPct,
            startChargeMah = startMah,
            endChargeMah = endMah,
            estimatedFullMah = estimate,
        )
        runBlocking {
            BatterySessionStore(applicationContext).upsert(entity)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.battery_tracking_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun buildNotif(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_onetools)
            .setContentTitle(getString(R.string.battery_tracking_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "one_battery_track"
        private const val NOTIF_ID = 7101
        const val ACTION_STOP = "com.onetools.app.battery.STOP"

        fun start(context: Context) {
            val i = Intent(context, BatteryChargeService::class.java)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BatteryChargeService::class.java))
        }
    }
}
