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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground notification + optional overlay showing live rates.
 * Display modes inspired by Pixel Meter (Apache-2.0); implementation is first-party.
 */
class SpeedMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var sampler: PhysicalSpeedSampler? = null
    private var overlay: MeterOverlayController? = null
    private var lastRx = -1L
    private var lastTx = -1L
    private var prefs = MeterPrefsSnapshot()
    private var lastFormatted: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_APPLY_PREFS -> {
                scope.launch {
                    refreshPrefs()
                    applyOverlayState(lastFormatted)
                }
                return START_STICKY
            }
            ACTION_DOCK_OEM -> {
                scope.launch {
                    refreshPrefs()
                    ensureOverlay()
                    val (x, y) = MeterOverlayController.oemSlotXy(this@SpeedMonitorService)
                    MeterSettings(applicationContext).setOverlayPosition(x, y)
                    MeterSettings(applicationContext).setOverlayEnabled(true)
                    refreshPrefs()
                    applyOverlayState(lastFormatted.ifEmpty { getString(R.string.meter_starting) })
                    overlay?.moveToOemStatusSlot()
                }
                return START_STICKY
            }
            else -> startMonitoring()
        }
        return START_STICKY
    }

    private suspend fun refreshPrefs() {
        prefs = MeterSettings(applicationContext).snapshot()
    }

    private fun ensureOverlay() {
        if (overlay != null) return
        overlay = MeterOverlayController(this) {
            scope.launch {
                MeterSettings(applicationContext).setOverlayEnabled(false)
                refreshPrefs()
                applyOverlayState(lastFormatted)
            }
        }
    }

    private fun startMonitoring() {
        isRunning = true
        scope.launch {
            // Product: 通知栏网速是主路径 — 启动监测时强制打开常驻通知。
            MeterSettings(applicationContext).setNotificationEnabled(true)
            refreshPrefs()
            withContext(Dispatchers.Main) {
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
            }
            if (sampler == null) {
                val cm = getSystemService(ConnectivityManager::class.java)
                sampler = PhysicalSpeedSampler(cm).also { it.start() }
            }
            ensureOverlay()
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
                    // FGS 常驻通知始终刷新速率（关通知开关也不停更，避免「开了没网速」）。
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID, buildNotification(text, down, up))
                    applyOverlayState(text)
                    delay(prefs.sampleIntervalMs)
                }
            }
        }
    }

    private fun applyOverlayState(text: String) {
        // Controllers already marshal to main; call from any thread.
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
        scope.cancel()
        sampler?.stop()
        sampler = null
        overlay?.hide()
        overlay = null
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        // New channel id so importance bump applies (Android freezes old channel importance).
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.meter_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.meter_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(content: String, down: Long = 0, up: Long = 0): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_METER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, SpeedMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val iconBitmap = MeterDynamicIcon.create(down, up, prefs.displayMode)
        val icon = androidx.core.graphics.drawable.IconCompat.createWithBitmap(iconBitmap)
        val chip = MeterChipFormat.format(prefs, down, up)
        // Title = live speed so shade / collapsed row shows rates at a glance.
        val title = content.ifBlank { getString(R.string.meter_starting) }
        val body = getString(R.string.meter_notification_hint)
        // Android 16+ (API 36): status-bar chip via promoted ongoing + shortCriticalText.
        if (Build.VERSION.SDK_INT >= 36 && prefs.statusBarChipEnabled) {
            val nm = getSystemService(NotificationManager::class.java)
            val allowPromoted = nm?.canPostPromotedNotifications() == true
            val platform = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.graphics.drawable.Icon.createWithBitmap(iconBitmap))
                .setContentTitle(title)
                .setContentText(body)
                .setSubText(chip)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(open)
                .addAction(
                    Notification.Action.Builder(null, getString(R.string.meter_stop), stop).build(),
                )
                .setShortCriticalText(chip)
            if (allowPromoted) {
                platform.setFlag(Notification.FLAG_PROMOTED_ONGOING, true)
            }
            return platform.build()
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(chip)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
            .addAction(0, getString(R.string.meter_stop), stop)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "onetools_meter_speed_v2"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.onetools.app.meter.STOP"
        const val ACTION_APPLY_PREFS = "com.onetools.app.meter.APPLY_PREFS"
        const val ACTION_DOCK_OEM = "com.onetools.app.meter.DOCK_OEM"

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
            ContextCompat.startForegroundService(
                context,
                Intent(context, SpeedMonitorService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
