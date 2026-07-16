package com.oneims.app.ui

import com.oneims.app.R
import com.oneims.app.onekuku.OneKukuRunnerState

/**
 * 首页顶部通道总控卡状态（四态）。
 *
 * 未激活 → 激活中 → 就绪 ↔ 休眠
 * （关 App / 退后台 → 休眠；再打开 → 就绪；特权桥进程仍在则无需重配对）
 */
enum class OneKukuCardState {
    /** 未激活 */
    INACTIVE,

    /** 激活中（授权 / 配对 / 连接 / 启动） */
    ACTIVATING,

    /** 就绪（App 在前台使用中） */
    READY,

    /** 休眠（App 已关闭或退后台；通道仍已授权） */
    SLEEPING,
}

/**
 * 将底层运行/授权/执行标志与激活相位收敛为卡片四态。
 */
object OneKukuCardPolicy {
    fun resolve(
        serviceReady: Boolean,
        isExecuting: Boolean,
        channelSleeping: Boolean,
        @Suppress("UNUSED_PARAMETER") taskComplete: Boolean = false,
    ): OneKukuCardState = when {
        !serviceReady -> OneKukuCardState.INACTIVE
        // 执行中对外仍展示「就绪」（使用中），不单独占一态。
        isExecuting -> OneKukuCardState.READY
        channelSleeping -> OneKukuCardState.SLEEPING
        else -> OneKukuCardState.READY
    }

    fun fromActivationPhase(phase: com.oneims.app.core.OneKukuActivationPhase): OneKukuCardState? =
        when (phase) {
            com.oneims.app.core.OneKukuActivationPhase.WAITING_PAIR,
            com.oneims.app.core.OneKukuActivationPhase.PAIRING,
            com.oneims.app.core.OneKukuActivationPhase.CONNECTING,
            com.oneims.app.core.OneKukuActivationPhase.STARTING,
            -> OneKukuCardState.ACTIVATING
            // 激活失败回到未激活（详情靠 detailOverride）。
            com.oneims.app.core.OneKukuActivationPhase.FAILED ->
                OneKukuCardState.INACTIVE
            com.oneims.app.core.OneKukuActivationPhase.ACTIVE,
            com.oneims.app.core.OneKukuActivationPhase.IDLE,
            -> null
        }

    /** 进度阶段点亮数（1–4）：未激活 → 激活中 → 就绪 → 休眠。 */
    fun litStageCount(state: OneKukuCardState): Int = when (state) {
        OneKukuCardState.INACTIVE -> 1
        OneKukuCardState.ACTIVATING -> 2
        OneKukuCardState.READY -> 3
        OneKukuCardState.SLEEPING -> 4
    }

    /** 四段进度条标签（固定顺序）。 */
    fun stageLabelRes(): List<Int> = listOf(
        R.string.onekuku_stage_inactive,
        R.string.onekuku_stage_activate,
        R.string.onekuku_stage_ready,
        R.string.onekuku_stage_sleeping,
    )

    /** 通道休眠中。 */
    fun isChannelSleeping(runnerState: OneKukuRunnerState): Boolean =
        runnerState == OneKukuRunnerState.SLEEPING

    fun isBusy(state: OneKukuCardState): Boolean =
        state == OneKukuCardState.ACTIVATING

    fun isAlert(state: OneKukuCardState): Boolean =
        state == OneKukuCardState.INACTIVE

    fun isSettled(state: OneKukuCardState): Boolean = when (state) {
        OneKukuCardState.READY,
        OneKukuCardState.SLEEPING,
        -> true
        else -> false
    }
}
