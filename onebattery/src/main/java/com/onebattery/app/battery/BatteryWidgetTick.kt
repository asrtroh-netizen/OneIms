package com.onebattery.app.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock

/**
 * Dynamic [ACTION_BATTERY_CHANGED] listener (cannot be declared in the manifest).
 * Throttled so widgets do not refresh on every sticky tick.
 */
object BatteryWidgetTick {
    @Volatile
    private var registered = false

    @Volatile
    private var lastPercent = -1

    @Volatile
    private var lastAt = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val percent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
            val now = SystemClock.elapsedRealtime()
            if (percent == lastPercent && now - lastAt < 60_000L) return
            lastPercent = percent
            lastAt = now
            BatteryWidgetUpdater.updateAll(context.applicationContext)
        }
    }

    fun register(context: Context) {
        if (registered) return
        val app = context.applicationContext
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
        registered = true
        BatteryWidgetUpdater.updateAll(app)
    }
}
