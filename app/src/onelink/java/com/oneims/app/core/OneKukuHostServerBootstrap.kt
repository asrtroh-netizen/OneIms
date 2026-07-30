package com.oneims.app.core

import android.content.Context

/** OneLink：无宿主内嵌 server，冷启引导为空操作。 */
object OneKukuHostServerBootstrap {
    fun ensureRunning(context: Context): Boolean = true

    fun isHostServerAlive(): Boolean = false
}
