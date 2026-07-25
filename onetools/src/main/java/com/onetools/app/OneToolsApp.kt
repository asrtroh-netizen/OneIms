package com.onetools.app

import android.app.Application
import com.onetools.app.battery.BatteryPrefs
import com.onetools.app.battery.BatteryWidgetTick
import com.onetools.app.updates.UpdateCheckNotifier
import com.onetools.app.updates.UpdateCheckScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OneToolsApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        BatteryWidgetTick.register(this)
        UpdateCheckNotifier.ensureChannel(this)
        UpdateCheckScheduler.resync(this)
        appScope.launch {
            BatteryPrefs(this@OneToolsApp).applyPixelDesignIfUnset()
        }
    }
}
