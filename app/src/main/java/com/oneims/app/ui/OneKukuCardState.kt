package com.oneims.app.ui

/**
 * 首页顶部 OneKuku 总控卡状态（规格 9 态）。
 *
 * 1 未激活 → 2 等待配对 → 3 配对中 → 4 连接中 → 5 启动中
 * → 6 已激活 → 7 休眠中 → 8 执行中；失败为独立态。
 */
enum class OneKukuCardState {
    /** 1 · 未激活 */
    INACTIVE,

    /** 2 · 等待配对（通知栏填码） */
    WAITING_PAIR,

    /** 3 · 配对中 */
    PAIRING,

    /** 4 · 连接中 */
    CONNECTING,

    /** 5 · 启动中 */
    STARTING,

    /** 6 · 已激活（通道就绪 / 本轮恢复完成） */
    ACTIVE,

    /** 7 · 休眠中 */
    SLEEPING,

    /** 8 · 执行中（一键恢复等） */
    EXECUTING,

    /** 9 · 失败 */
    FAILED,
}

/**
 * 将底层运行/授权/执行标志与激活相位收敛为卡片 9 态；
 * 进度条仍为 4 段（待命 / 激活 / 执行 / 完成）。
 */
object OneKukuCardPolicy {
    fun resolve(
        serviceReady: Boolean,
        isExecuting: Boolean,
        taskComplete: Boolean,
    ): OneKukuCardState = when {
        !serviceReady -> OneKukuCardState.INACTIVE
        isExecuting -> OneKukuCardState.EXECUTING
        taskComplete -> OneKukuCardState.ACTIVE
        else -> OneKukuCardState.SLEEPING
    }

    fun fromActivationPhase(phase: com.oneims.app.core.OneKukuActivationPhase): OneKukuCardState? =
        when (phase) {
            com.oneims.app.core.OneKukuActivationPhase.WAITING_PAIR ->
                OneKukuCardState.WAITING_PAIR
            com.oneims.app.core.OneKukuActivationPhase.PAIRING ->
                OneKukuCardState.PAIRING
            com.oneims.app.core.OneKukuActivationPhase.CONNECTING ->
                OneKukuCardState.CONNECTING
            com.oneims.app.core.OneKukuActivationPhase.STARTING ->
                OneKukuCardState.STARTING
            com.oneims.app.core.OneKukuActivationPhase.FAILED ->
                OneKukuCardState.FAILED
            // ACTIVE/IDLE：交给 resolve（休眠/已激活/执行）
            com.oneims.app.core.OneKukuActivationPhase.ACTIVE,
            com.oneims.app.core.OneKukuActivationPhase.IDLE,
            -> null
        }

    /** 进度阶段点亮数（1–4）。 */
    fun litStageCount(state: OneKukuCardState): Int = when (state) {
        OneKukuCardState.INACTIVE,
        OneKukuCardState.FAILED,
        -> 1
        OneKukuCardState.WAITING_PAIR,
        OneKukuCardState.PAIRING,
        OneKukuCardState.CONNECTING,
        OneKukuCardState.STARTING,
        -> 2
        OneKukuCardState.EXECUTING -> 3
        OneKukuCardState.ACTIVE,
        OneKukuCardState.SLEEPING,
        -> 4
    }

    fun isBusy(state: OneKukuCardState): Boolean = when (state) {
        OneKukuCardState.PAIRING,
        OneKukuCardState.CONNECTING,
        OneKukuCardState.STARTING,
        OneKukuCardState.EXECUTING,
        -> true
        else -> false
    }

    fun isAlert(state: OneKukuCardState): Boolean = when (state) {
        OneKukuCardState.INACTIVE,
        OneKukuCardState.WAITING_PAIR,
        OneKukuCardState.FAILED,
        -> true
        else -> false
    }
}
