package com.oneims.app.core

import android.content.Context

/** OneLink：Wi‑Fi 续跑由 BootReceiver NETWORK_STATE 路径处理；此处 no-op。 */
object OneKukuWifiReadyMonitor {
    fun ensureRegistered(context: Context) = Unit
    fun unregister(context: Context) = Unit
}
