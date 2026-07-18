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
        deadListeners.forEach { it() }
    }

    private val receivedListeners = CopyOnWriteArrayList<() -> Unit>()
    private val deadListeners = CopyOnWriteArrayList<() -> Unit>()

    fun onReceived(received: IBinder?) {
        val previous = binder
        // 周期重投同一远端 binder 时不重复通知，避免 Guard 全量 reapply 导致发热。
        // BinderProxy.equals 按远端句柄比较，可识别「新包装、同一远端」。
        if (received != null && previous != null && previous.pingBinder() && previous == received) {
            Log.d(TAG, "OneBridge binder resent (same remote); skip listeners")
            return
        }
        if (previous != null) {
            runCatching { previous.unlinkToDeath(death, 0) }
        }
        binder = received
        if (received != null) {
            runCatching { received.linkToDeath(death, 0) }
            Log.i(TAG, "OneBridge binder received")
            receivedListeners.forEach { it() }
        } else {
            Log.w(TAG, "OneBridge binder cleared")
            deadListeners.forEach { it() }
        }
    }

    fun get(): IBinder? {
        val b = binder
        return if (b != null && b.pingBinder()) b else null
    }

    fun addReceivedListener(listener: () -> Unit, sticky: Boolean = true) {
        receivedListeners.add(listener)
        if (sticky && get() != null) {
            listener()
        }
    }

    fun removeReceivedListener(listener: () -> Unit) {
        receivedListeners.remove(listener)
    }

    fun addDeadListener(listener: () -> Unit) {
        deadListeners.add(listener)
    }

    fun removeDeadListener(listener: () -> Unit) {
        deadListeners.remove(listener)
    }
}
