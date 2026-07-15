package com.oneims.app.core

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
    @Volatile
    var phase: OneKukuActivationPhase = OneKukuActivationPhase.IDLE
        private set

    @Volatile
    var lastFailureReason: String? = null
        private set

    /** 配对成功后自动继续「一键恢复通话」。 */
    @Volatile
    var pendingRestoreAfterPair: Boolean = false

    fun setPhase(value: OneKukuActivationPhase, failure: String? = null) {
        phase = value
        if (value == OneKukuActivationPhase.FAILED) {
            lastFailureReason = failure
        } else if (value != OneKukuActivationPhase.IDLE) {
            lastFailureReason = null
        }
    }

    fun reset() {
        phase = OneKukuActivationPhase.IDLE
        lastFailureReason = null
    }
}
