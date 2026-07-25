package com.onetools.app

import android.app.Application
import com.onetools.app.battery.BatteryPrefs
import com.onetools.app.battery.BatteryWidgetTick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OneToolsApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        BatteryWidgetTick.register(this)
        appScope.launch {
            BatteryPrefs(this@OneToolsApp).applyPixelDesignIfUnset()
        }
    }
}
