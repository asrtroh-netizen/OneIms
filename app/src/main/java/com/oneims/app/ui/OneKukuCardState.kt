package com.oneims.app.ui

import com.oneims.app.R

/**
 * 首页顶部 OneKuku 总控卡状态（规格 5 态）。
 *
 * 1 未激活 → 2 激活中（等配对/配对/连接/启动）→ 3 已就绪 → 4 执行中；5 失败。
 * 底层激活相位仍可细分，卡片层收敛，避免九段进度挤成一团。
 */
enum class OneKukuCardState {
    /** 1 · 未激活 */
    INACTIVE,

    /** 2 · 激活中（通知填码 / 配对 / 连接 / 启动） */
    ACTIVATING,

    /** 3 · 已就绪（通道可用，含休眠） */
    READY,

    /** 4 · 执行中（一键恢复等） */
    EXECUTING,

    /** 5 · 失败 */
    FAILED,
}

/**
 * 将底层运行/授权/执行标志与激活相位收敛为卡片 5 态。
 */
object OneKukuCardPolicy {
    fun resolve(
        serviceReady: Boolean,
        isExecuting: Boolean,
        @Suppress("UNUSED_PARAMETER") taskComplete: Boolean,
    ): OneKukuCardState = when {
        !serviceReady -> OneKukuCardState.INACTIVE
        isExecuting -> OneKukuCardState.EXECUTING
        // taskComplete 与空闲休眠在卡片层同属「已就绪」；细则靠 detailOverride。
        else -> OneKukuCardState.READY
    }

    fun fromActivationPhase(phase: com.oneims.app.core.OneKukuActivationPhase): OneKukuCardState? =
        when (phase) {
            com.oneims.app.core.OneKukuActivationPhase.WAITING_PAIR,
            com.oneims.app.core.OneKukuActivationPhase.PAIRING,
            com.oneims.app.core.OneKukuActivationPhase.CONNECTING,
            com.oneims.app.core.OneKukuActivationPhase.STARTING,
            -> OneKukuCardState.ACTIVATING
            com.oneims.app.core.OneKukuActivationPhase.FAILED ->
                OneKukuCardState.FAILED
            com.oneims.app.core.OneKukuActivationPhase.ACTIVE,
            com.oneims.app.core.OneKukuActivationPhase.IDLE,
            -> null
        }

    /** 进度阶段点亮数（1–5）。 */
    fun litStageCount(state: OneKukuCardState): Int = when (state) {
        OneKukuCardState.INACTIVE -> 1
        OneKukuCardState.ACTIVATING -> 2
        OneKukuCardState.READY -> 3
        OneKukuCardState.EXECUTING -> 4
        OneKukuCardState.FAILED -> 5
    }

    /** 五态进度条标签。 */
    fun stageLabelRes(): List<Int> = listOf(
        R.string.onekuku_stage_inactive,
        R.string.onekuku_stage_activate,
        R.string.onekuku_stage_ready,
        R.string.onekuku_stage_execute,
        R.string.onekuku_stage_failed,
    )

    fun isBusy(state: OneKukuCardState): Boolean = when (state) {
        OneKukuCardState.ACTIVATING,
        OneKukuCardState.EXECUTING,
        -> true
        else -> false
    }

    fun isAlert(state: OneKukuCardState): Boolean = when (state) {
        OneKukuCardState.INACTIVE,
        OneKukuCardState.FAILED,
        -> true
        else -> false
    }
}
