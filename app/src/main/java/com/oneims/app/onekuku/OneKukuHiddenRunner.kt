package com.oneims.app.onekuku

import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * 后台唤醒/状态机：平时休眠，不展示终端。
 */
object OneKukuHiddenRunner {
    private const val TAG = "OneIMS-OneKuku"

    private val state = AtomicReference(OneKukuRunnerState.SLEEPING)
    @Volatile
    private var bridge: OneKukuPrivilegeBridge? = null

    fun installBridge(privilegeBridge: OneKukuPrivilegeBridge) {
        bridge = privilegeBridge
        if (privilegeBridge.isActivated()) {
            state.set(OneKukuRunnerState.SLEEPING)
        } else {
            state.set(OneKukuRunnerState.INACTIVE)
        }
    }

    fun currentState(): OneKukuRunnerState = state.get()

    fun markExecuting() {
        state.set(OneKukuRunnerState.EXECUTING)
        Log.i(TAG, "state=EXECUTING")
    }

    fun markSleeping() {
        state.set(OneKukuRunnerState.SLEEPING)
        Log.i(TAG, "state=SLEEPING")
    }

    fun markFailed(reason: String) {
        state.set(OneKukuRunnerState.FAILED)
        Log.w(TAG, "state=FAILED reason=$reason")
    }

    fun refreshFromBridge() {
        val b = bridge
        if (b == null) {
            state.set(OneKukuRunnerState.INACTIVE)
            return
        }
        if (!b.isActivated()) {
            state.set(OneKukuRunnerState.INACTIVE)
        } else if (state.get() != OneKukuRunnerState.EXECUTING) {
            state.set(OneKukuRunnerState.SLEEPING)
        }
    }

    /**
     * 唤醒核心：不弹终端；成功后进入 ACTIVE（随即通常转入 EXECUTING/SLEEPING）。
     */
    fun wake(): OneKukuCommandResult {
        val b = bridge
            ?: return OneKukuCommandResult(
                success = false,
                state = OneKukuRunnerState.INACTIVE,
                message = "OneKuku privilege bridge missing",
            )
        if (b.isActivated()) {
            state.set(OneKukuRunnerState.ACTIVE)
            Log.i(TAG, "wake: already activated")
            return OneKukuCommandResult(true, OneKukuRunnerState.ACTIVE, "OneKuku active")
        }
        state.set(OneKukuRunnerState.STARTING)
        Log.i(TAG, "wake: starting")
        val ok = runCatching { b.requestWake() }.getOrDefault(false)
        return if (ok || b.isActivated()) {
            state.set(OneKukuRunnerState.ACTIVE)
            Log.i(TAG, "wake: activated")
            OneKukuCommandResult(true, OneKukuRunnerState.ACTIVE, "OneKuku activated")
        } else {
            state.set(OneKukuRunnerState.INACTIVE)
            Log.w(TAG, "wake: inactive")
            OneKukuCommandResult(false, OneKukuRunnerState.INACTIVE, "OneKuku inactive")
        }
    }
}
