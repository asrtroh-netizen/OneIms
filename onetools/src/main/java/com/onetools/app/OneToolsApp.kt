package com.onetools.app

import android.app.Application
import com.onetools.app.updates.UpdateCheckNotifier
import com.onetools.app.updates.UpdateCheckScheduler

class OneToolsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UpdateCheckNotifier.ensureChannel(this)
        UpdateCheckScheduler.resync(this)
    }
}
