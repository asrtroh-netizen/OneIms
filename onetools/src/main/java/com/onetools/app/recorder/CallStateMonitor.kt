package com.onetools.app.recorder

import android.content.Context
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

enum class CallPhase { IDLE, RINGING, OFFHOOK }

/** Clean-room call state observer (no proprietary dialer hooks). */
class CallStateMonitor(
    private val context: Context,
    private val onPhase: (CallPhase) -> Unit,
) {
    private val tm = context.getSystemService<TelephonyManager>()
    private val registered = AtomicBoolean(false)
    private var callback: TelephonyCallback? = null

    fun start(executor: Executor) {
        if (!registered.compareAndSet(false, true)) return
        val manager = tm ?: run {
            registered.set(false)
            return
        }
        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                onPhase(
                    when (state) {
                        TelephonyManager.CALL_STATE_RINGING -> CallPhase.RINGING
                        TelephonyManager.CALL_STATE_OFFHOOK -> CallPhase.OFFHOOK
                        else -> CallPhase.IDLE
                    },
                )
            }
        }
        callback = cb
        manager.registerTelephonyCallback(executor, cb)
    }

    fun stop() {
        if (!registered.compareAndSet(true, false)) return
        val cb = callback ?: return
        runCatching { tm?.unregisterTelephonyCallback(cb) }
        callback = null
    }
}
