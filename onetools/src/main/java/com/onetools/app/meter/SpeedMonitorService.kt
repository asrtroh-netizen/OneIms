package com.onetools.app.meter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
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

/**
 * Foreground notification + optional overlay showing live rates.
 * Display modes inspired by Pixel Meter (Apache-2.0); implementation is first-party.
 */
class SpeedMonitorService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null
    private var sampler: PhysicalSpeedSampler? = null
    private var overlay: MeterOverlayController? = null
    private var lastRx = -1L
    private var lastTx = -1L
    private var prefs = MeterPrefsSnapshot()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_APPLY_PREFS -> {
                refreshPrefs()
                applyOverlayState(lastFormatted)
                return START_STICKY
            }
            else -> startMonitoring()
        }
        return START_STICKY
    }

    private var lastFormatted: String = ""

    private fun refreshPrefs() {
        prefs = runBlocking { MeterSettings(applicationContext).snapshot() }
    }

    private fun startMonitoring() {
        isRunning = true
        refreshPrefs()
        ensureChannel()
        val notification = buildNotification(getString(R.string.meter_starting), 0, 0)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (sampler == null) {
            val cm = getSystemService(ConnectivityManager::class.java)
            sampler = PhysicalSpeedSampler(cm).also { it.start() }
        }
        if (overlay == null) {
            overlay = MeterOverlayController(this) {
                // Double-tap: hide overlay until user re-enables in settings / tile.
                runBlocking { MeterSettings(applicationContext).setOverlayEnabled(false) }
                refreshPrefs()
                applyOverlayState(lastFormatted)
            }
        }
        applyOverlayState(getString(R.string.meter_starting))

        job?.cancel()
        job = scope.launch {
            while (isActive) {
                refreshPrefs()
                val totals = sampler?.readTotals() ?: PhysicalSpeedSampler.TrafficTotals(0, 0)
                val down: Long
                val up: Long
                if (lastRx < 0 || lastTx < 0) {
                    down = 0
                    up = 0
                } else {
                    down = (totals.rxBytes - lastRx).coerceAtLeast(0)
                    up = (totals.txBytes - lastTx).coerceAtLeast(0)
                }
                lastRx = totals.rxBytes
                lastTx = totals.txBytes
                val text = MeterRateFormatter.format(prefs, down, up)
                lastFormatted = text
                if (prefs.notificationEnabled) {
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID, buildNotification(text, down, up))
                }
                applyOverlayState(text)
                delay(prefs.sampleIntervalMs)
            }
        }
    }

    private fun applyOverlayState(text: String) {
        val o = overlay ?: return
        if (prefs.overlayEnabled && o.canDraw()) {
            o.show(text, prefs)
            o.update(text)
        } else {
            o.hide()
        }
    }

    override fun onDestroy() {
        isRunning = false
        job?.cancel()
        sampler?.stop()
        sampler = null
        overlay?.hide()
        overlay = null
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.meter_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(content: String, down: Long = 0, up: Long = 0): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, SpeedMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = androidx.core.graphics.drawable.IconCompat.createWithBitmap(
            MeterDynamicIcon.create(down, up, prefs.displayMode),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(getString(R.string.meter_notification_title))
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.meter_stop), stop)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "onetools_meter"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.onetools.app.meter.STOP"
        const val ACTION_APPLY_PREFS = "com.onetools.app.meter.APPLY_PREFS"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SpeedMonitorService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, SpeedMonitorService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
