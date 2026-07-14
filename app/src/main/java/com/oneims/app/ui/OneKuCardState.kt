package com.oneims.app.ui

/**
 * 首页顶部 OneKu 总控卡对外状态。
 * 底层特权通道对用户不可见，只暴露「未激活 / 休眠 / 执行 / 完成」。
 */
enum class OneKuCardState {
    /** 核心服务未激活：红色警告态。 */
    INACTIVE,

    /** 已激活并休眠：白色就绪态。 */
    SLEEPING,

    /** 正在恢复通话配置。 */
    RUNNING,

    /** 本轮恢复完成并回到休眠。 */
    COMPLETE,
}

/**
 * 将底层运行/授权/执行/完成标志收敛为卡片状态与进度点亮数（1–4）。
 */
object OneKuCardPolicy {
    fun resolve(
        serviceReady: Boolean,
        isExecuting: Boolean,
        taskComplete: Boolean,
    ): OneKuCardState = when {
        !serviceReady -> OneKuCardState.INACTIVE
        isExecuting -> OneKuCardState.RUNNING
        taskComplete -> OneKuCardState.COMPLETE
        else -> OneKuCardState.SLEEPING
    }

    /** 进度阶段：待命 / 激活 / 执行 / 完成，返回应点亮的数量。 */
    fun litStageCount(state: OneKuCardState): Int = when (state) {
        OneKuCardState.INACTIVE -> 1
        OneKuCardState.SLEEPING -> 2
        OneKuCardState.RUNNING -> 3
        OneKuCardState.COMPLETE -> 4
    }
}
