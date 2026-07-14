package com.oneims.app.core.privilege

import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 持有 OneBridge shell 进程投递来的 binder。
 */
object BridgeBinderHolder {
    private const val TAG = "OneBridgeClient"

    @Volatile
    private var binder: IBinder? = null

    private val death = DeathRecipient {
        Log.w(TAG, "OneBridge binder died")
        binder = null
        listeners.forEach { it() }
    }

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun onReceived(received: IBinder?) {
        val previous = binder
        if (previous != null) {
            runCatching { previous.unlinkToDeath(death, 0) }
        }
        binder = received
        if (received != null) {
            runCatching { received.linkToDeath(death, 0) }
            Log.i(TAG, "OneBridge binder received")
        }
        listeners.forEach { it() }
    }

    fun get(): IBinder? {
        val b = binder
        return if (b != null && b.pingBinder()) b else null
    }

    fun addDeadListener(listener: () -> Unit) {
        listeners.add(listener)
    }
}
