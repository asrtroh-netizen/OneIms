package com.oneims.app

import android.app.Application
import com.oneims.app.core.DiagFileLogger
import com.oneims.app.core.OemDeviceCompat

/**
 * 进程入口：挂详细日志与未捕获异常落盘，便于小米等 OEM 闪退取证。
 */
class OneImsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagFileLogger.init(this)
        DiagFileLogger.installCrashHandler()
        DiagFileLogger.breadcrumb(
            "Application.onCreate oem=${OemDeviceCompat.summaryLine()}",
        )
    }
}
