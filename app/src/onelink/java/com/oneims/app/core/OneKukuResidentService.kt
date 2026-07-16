package com.oneims.app.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * OneLink 桩：无 OneKuku 常驻保活服务（Manifest 已 remove）。
 */
class OneKukuResidentService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) = Unit
        fun stop(context: Context) = Unit
    }
}
