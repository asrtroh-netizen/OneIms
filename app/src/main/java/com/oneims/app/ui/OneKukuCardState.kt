package com.oneims.app.ui

import com.oneims.app.R
import com.oneims.app.onekuku.OneKukuRunnerState

/**
 * 首页顶部通道总控卡状态（三态，对齐 V15 Hero）。
 *
 * 未激活 → 激活中 → 就绪
 * 内部 runner 仍可有 SLEEPING（退后台书签），对外一律显示就绪，不拆桥。
 */
enum class OneKukuCardState {
    /** 未激活 */
    INACTIVE,

    /** 激活中（授权 / 配对 / 连接 / 启动） */
    ACTIVATING,

    /** 就绪（特权桥已授权；含后台待命） */
    READY,
}

/**
 * 将底层运行/授权/执行标志与激活相位收敛为卡片三态。
 */
object OneKukuCardPolicy {
    fun resolve(
        serviceReady: Boolean,
        @Suppress("UNUSED_PARAMETER") isExecuting: Boolean,
        @Suppress("UNUSED_PARAMETER") channelSleeping: Boolean,
        @Suppress("UNUSED_PARAMETER") taskComplete: Boolean = false,
    ): OneKukuCardState =
        // 执行中 / 后台待命对外都展示「就绪」；激活中由 fromActivationPhase 覆盖。
        if (serviceReady) OneKukuCardState.READY else OneKukuCardState.INACTIVE

    fun fromActivationPhase(phase: com.oneims.app.core.OneKukuActivationPhase): OneKukuCardState? =
        when (phase) {
            com.oneims.app.core.OneKukuActivationPhase.WAITING_PAIR,
            com.oneims.app.core.OneKukuActivationPhase.PAIRING,
            com.oneims.app.core.OneKukuActivationPhase.CONNECTING,
            com.oneims.app.core.OneKukuActivationPhase.STARTING,
            -> OneKukuCardState.ACTIVATING
            com.oneims.app.core.OneKukuActivationPhase.FAILED ->
                OneKukuCardState.INACTIVE
            com.oneims.app.core.OneKukuActivationPhase.ACTIVE,
            com.oneims.app.core.OneKukuActivationPhase.IDLE,
            -> null
        }

    /** 进度阶段点亮数（1–3）：未激活 → 激活中 → 就绪。 */
    fun litStageCount(state: OneKukuCardState): Int = when (state) {
        OneKukuCardState.INACTIVE -> 1
        OneKukuCardState.ACTIVATING -> 2
        OneKukuCardState.READY -> 3
    }

    /** 三段进度条标签（固定顺序）。 */
    fun stageLabelRes(): List<Int> = listOf(
        R.string.onekuku_stage_inactive,
        R.string.onekuku_stage_activate,
        R.string.onekuku_stage_ready,
    )

    /** 内部 runner 是否处于后台待命（不再映射为卡片第四态）。 */
    fun isChannelSleeping(runnerState: OneKukuRunnerState): Boolean =
        runnerState == OneKukuRunnerState.SLEEPING

    fun isBusy(state: OneKukuCardState): Boolean =
        state == OneKukuCardState.ACTIVATING

    fun isAlert(state: OneKukuCardState): Boolean =
        state == OneKukuCardState.INACTIVE

    fun isSettled(state: OneKukuCardState): Boolean =
        state == OneKukuCardState.READY
}
