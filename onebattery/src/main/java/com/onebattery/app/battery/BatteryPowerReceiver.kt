package com.onebattery.app.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts / stops clean-room charge tracking when power is connected.
 */
class BatteryPowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            -> {
                BatteryChargeService.start(context.applicationContext)
                BatteryWidgetUpdater.updateAll(context.applicationContext)
            }
        }
    }
}
