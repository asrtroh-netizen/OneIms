package com.oneims.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * OneKuku 激活流程 UI 相位（首页总控卡 / detailOverride 共用）。
 */
enum class OneKukuActivationPhase {
    IDLE,
    WAITING_PAIR,
    PAIRING,
    CONNECTING,
    STARTING,
    ACTIVE,
    FAILED,
}

object OneKukuActivationUi {
    private val phaseFlow = MutableStateFlow(OneKukuActivationPhase.IDLE)

    val phaseState: StateFlow<OneKukuActivationPhase> = phaseFlow.asStateFlow()

    val phase: OneKukuActivationPhase
        get() = phaseFlow.value

    @Volatile
    var lastFailureReason: String? = null
        private set

    /** 配对成功后自动继续「一键恢复通话」。 */
    @Volatile
    var pendingRestoreAfterPair: Boolean = false

    fun setPhase(value: OneKukuActivationPhase, failure: String? = null) {
        if (value == OneKukuActivationPhase.FAILED) {
            lastFailureReason = failure
        } else if (value != OneKukuActivationPhase.IDLE) {
            lastFailureReason = null
        }
        phaseFlow.value = value
    }

    fun reset() {
        lastFailureReason = null
        phaseFlow.value = OneKukuActivationPhase.IDLE
    }
}
