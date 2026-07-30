package com.oneims.app.core

import android.content.Context

/**
 * OneKuku：迷你版宿主 server 已清除；保留空操作 API，避免调用点大面积改名。
 */
object OneKukuHostServerBootstrap {
    fun ensureRunning(context: Context): Boolean = true

    fun isHostServerAlive(): Boolean = false
}
