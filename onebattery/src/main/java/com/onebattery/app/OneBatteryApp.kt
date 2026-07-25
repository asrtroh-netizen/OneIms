package com.onebattery.app

import android.app.Application
import com.onebattery.app.battery.BatteryPrefs
import com.onebattery.app.battery.BatteryWidgetTick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OneBatteryApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        BatteryWidgetTick.register(this)
        appScope.launch {
            BatteryPrefs(this@OneBatteryApp).applyPixelDesignIfUnset()
        }
    }
}
