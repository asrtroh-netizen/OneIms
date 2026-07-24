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

/**
 * Foreground notification showing live up/down rates (Pixel Meter–style display).
 */
class SpeedMonitorService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null
    private var sampler: PhysicalSpeedSampler? = null
    private var lastRx = -1L
    private var lastTx = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        ensureChannel()
        val notification = buildNotification(getString(R.string.meter_starting))
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
        job?.cancel()
        job = scope.launch {
            while (isActive) {
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
                val text = getString(
                    R.string.meter_notification_body,
                    SpeedFormat.formatRate(down),
                    SpeedFormat.formatRate(up),
                )
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification(text))
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        sampler?.stop()
        sampler = null
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

    private fun buildNotification(content: String): Notification {
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_onetools)
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
