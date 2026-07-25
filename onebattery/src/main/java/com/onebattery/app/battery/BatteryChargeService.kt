package com.onebattery.app.battery

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.onebattery.app.MainActivity
import com.onebattery.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Foreground battery monitor — charge sessions, discharge curves, deep-sleep estimate,
 * per-app drain (AccuBattery-shaped, clean-room).
 */
class BatteryChargeService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    private var mode: Mode = Mode.IDLE

    private var chargeSessionId: String? = null
    private var chargeStartedAt: Long = 0L
    private var chargeStartPercent: Int = -1
    private var chargeStartMah: Int = -1
    private var alarmFired = false

    private var dischargeSessionId: String? = null
    private var dischargeStartedAt: Long = 0L
    private var dischargeStartPercent: Int = -1
    private var dischargeStartMah: Int = -1
    private var dischargeScreenOffMs: Long = 0L
    private var dischargeDeepSleepMs: Long = 0L

    private var lastPercent: Int = -1
    private var lastMah: Int = -1
    private var lastSampleAt: Long = 0L
    private var recentMahPerHour: Double = 0.0

    private enum class Mode { IDLE, CHARGE, DISCHARGE }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                finalizeAll()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> ensureRunning()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        finalizeAll()
        super.onDestroy()
    }

    private fun ensureRunning() {
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
        if (!prefs.trackingEnabled) {
            stopSelf()
            return
        }
        if (snap.isPlugged) {
            if (mode == Mode.DISCHARGE) {
                finalizeDischarge()
            }
            if (mode != Mode.CHARGE) {
                beginCharge(snap)
            }
            tickCharge(snap, prefs)
        } else {
            if (mode == Mode.CHARGE) {
                finalizeCharge()
            }
            if (mode != Mode.DISCHARGE) {
                beginDischarge(snap)
            }
            tickDischarge(snap, prefs)
        }
    }

    private fun beginCharge(snap: BatterySnapshot) {
        mode = Mode.CHARGE
        chargeSessionId = UUID.randomUUID().toString()
        chargeStartedAt = System.currentTimeMillis()
        chargeStartPercent = snap.percent
        chargeStartMah = snap.chargeCounterMah
        alarmFired = false
        lastPercent = -1
        lastMah = -1
    }

    private fun beginDischarge(snap: BatterySnapshot) {
        mode = Mode.DISCHARGE
        dischargeSessionId = UUID.randomUUID().toString()
        dischargeStartedAt = System.currentTimeMillis()
        dischargeStartPercent = snap.percent
        dischargeStartMah = snap.chargeCounterMah
        dischargeScreenOffMs = 0L
        dischargeDeepSleepMs = 0L
        lastPercent = snap.percent
        lastMah = snap.chargeCounterMah
        lastSampleAt = System.currentTimeMillis()
        val sid = dischargeSessionId ?: return
        runBlocking {
            BatterySessionStore(applicationContext).addSample(
                BatterySampleEntity(
                    sessionId = sid,
                    at = lastSampleAt,
                    percent = snap.percent,
                    chargeMah = snap.chargeCounterMah,
                    screenOn = isScreenOn(),
                ),
            )
        }
    }

    private fun tickCharge(snap: BatterySnapshot, prefs: BatteryPrefsSnapshot) {
        notifyLive(
            getString(R.string.battery_tracking_live, snap.percent, snap.currentNowMa),
        )
        if (
            prefs.chargeAlarmEnabled &&
            !alarmFired &&
            snap.percent >= prefs.chargeAlarmPercent
        ) {
            alarmFired = true
            BatteryChargeAlarm.notifyThreshold(
                this,
                snap.percent,
                prefs.chargeAlarmPercent,
            )
        }
    }

    private fun tickDischarge(snap: BatterySnapshot, prefs: BatteryPrefsSnapshot) {
        val now = System.currentTimeMillis()
        val sid = dischargeSessionId ?: return
        val elapsedMs = (now - lastSampleAt).coerceAtLeast(1L)
        val screenOn = isScreenOn()
        if (!screenOn) {
            dischargeScreenOffMs += elapsedMs
        }
        val mah = BatteryDrainMath.pickMahDelta(
            prevMah = lastMah,
            nowMah = snap.chargeCounterMah,
            prevPct = lastPercent,
            nowPct = snap.percent,
            designMah = prefs.designCapacityMah,
        )
        val elapsedH = elapsedMs.toDouble() / 3_600_000.0
        // Deep-sleep heuristic: screen off + almost no drain this interval
        if (!screenOn && mah < 2.0 && snap.percent == lastPercent) {
            dischargeDeepSleepMs += elapsedMs
        }
        if (mah > 0 && elapsedH > 0) {
            val rate = mah / elapsedH
            recentMahPerHour = if (recentMahPerHour <= 0) rate else (recentMahPerHour * 0.7 + rate * 0.3)
            val fg = ForegroundAppProbe.current(this)
            val pkg = fg?.packageName ?: "unknown"
            val label = fg?.label ?: getString(R.string.battery_drain_unknown_app)
            runBlocking {
                BatteryAppDrainStore(applicationContext).attribute(pkg, label, mah, screenOn)
            }
        }
        runBlocking {
            BatterySessionStore(applicationContext).addSample(
                BatterySampleEntity(
                    sessionId = sid,
                    at = now,
                    percent = snap.percent,
                    chargeMah = snap.chargeCounterMah,
                    screenOn = screenOn,
                ),
            )
        }
        lastPercent = snap.percent
        lastMah = snap.chargeCounterMah
        lastSampleAt = now
        val remain = BatteryDrainMath.remainingMinutes(
            snap.percent,
            prefs.designCapacityMah,
            recentMahPerHour,
        )
        notifyLive(
            getString(
                R.string.battery_drain_live,
                snap.percent,
                remain ?: 0,
            ),
        )
    }

    private fun isScreenOn(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return true
        return pm.isInteractive
    }

    private fun finalizeAll() {
        finalizeCharge()
        finalizeDischarge()
        mode = Mode.IDLE
    }

    private fun finalizeCharge() {
        val id = chargeSessionId ?: return
        chargeSessionId = null
        val snap = BatteryReader.read(this) ?: return
        val estimate = BatteryCapacityEstimator.estimateFullMah(
            startPercent = chargeStartPercent,
            endPercent = snap.percent,
            startChargeMah = chargeStartMah,
            endChargeMah = snap.chargeCounterMah,
        ) ?: 0
        runBlocking {
            BatterySessionStore(applicationContext).upsert(
                BatterySessionEntity(
                    id = id,
                    kind = "CHARGE",
                    startedAt = chargeStartedAt,
                    endedAt = System.currentTimeMillis(),
                    startPercent = chargeStartPercent,
                    endPercent = snap.percent,
                    startChargeMah = chargeStartMah,
                    endChargeMah = snap.chargeCounterMah,
                    estimatedFullMah = estimate,
                ),
            )
        }
    }

    private fun finalizeDischarge() {
        val id = dischargeSessionId ?: return
        dischargeSessionId = null
        val snap = BatteryReader.read(this) ?: return
        val ended = System.currentTimeMillis()
        val totalMs = (ended - dischargeStartedAt).coerceAtLeast(1L)
        val deepPct = ((dischargeDeepSleepMs * 100L) / totalMs).toInt().coerceIn(0, 100)
        runBlocking {
            BatterySessionStore(applicationContext).upsert(
                BatterySessionEntity(
                    id = id,
                    kind = "DISCHARGE",
                    startedAt = dischargeStartedAt,
                    endedAt = ended,
                    startPercent = dischargeStartPercent,
                    endPercent = snap.percent,
                    startChargeMah = dischargeStartMah,
                    endChargeMah = snap.chargeCounterMah,
                    estimatedFullMah = 0,
                    deepSleepPercent = deepPct,
                    screenOffMs = dischargeScreenOffMs,
                    deepSleepMs = dischargeDeepSleepMs,
                ),
            )
        }
        lastPercent = -1
        lastMah = -1
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

    private fun notifyLive(text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotif(text))
    }

    private fun buildNotif(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
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
        const val ACTION_STOP = "com.onebattery.app.battery.STOP"

        fun start(context: Context) {
            val i = Intent(context, BatteryChargeService::class.java)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BatteryChargeService::class.java))
        }
    }
}
