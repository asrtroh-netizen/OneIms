package com.onetools.app.channel

/**
 * 首页顶部通道总控卡状态（四态）——语义对齐 OneIMS `OneKukuCardState`。
 *
 * 未激活 → 激活中 → 就绪 ↔ 休眠
 */
enum class ChannelCardState {
    INACTIVE,
    ACTIVATING,
    READY,
    SLEEPING,
}

object ChannelCardPolicy {
    fun resolve(
        serviceReady: Boolean,
        isExecuting: Boolean,
        channelSleeping: Boolean,
    ): ChannelCardState = when {
        !serviceReady -> ChannelCardState.INACTIVE
        isExecuting -> ChannelCardState.READY
        channelSleeping -> ChannelCardState.SLEEPING
        else -> ChannelCardState.READY
    }

    fun litStageCount(state: ChannelCardState): Int = when (state) {
        ChannelCardState.INACTIVE -> 1
        ChannelCardState.ACTIVATING -> 2
        ChannelCardState.READY -> 3
        ChannelCardState.SLEEPING -> 4
    }

    fun isBusy(state: ChannelCardState): Boolean = state == ChannelCardState.ACTIVATING

    fun isAlert(state: ChannelCardState): Boolean = state == ChannelCardState.INACTIVE

    fun isSettled(state: ChannelCardState): Boolean = when (state) {
        ChannelCardState.READY,
        ChannelCardState.SLEEPING,
        -> true
        else -> false
    }
}
