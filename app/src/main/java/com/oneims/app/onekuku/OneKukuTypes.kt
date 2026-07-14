package com.oneims.app.onekuku

/**
 * OneKuku 核心运行态（对用户/状态卡可见语义）。
 * 模块内禁止依赖第三方特权 SDK 名称。
 */
enum class OneKukuRunnerState {
    INACTIVE,
    STARTING,
    ACTIVE,
    EXECUTING,
    SLEEPING,
    FAILED,
}

/** 白名单命令：禁止通用 shell / 任意用户输入。 */
enum class OneKukuCommand {
    ACTIVATE_ONEKUKU,
    CHECK_ONEKUKU_STATUS,
    RESTORE_ALL_CALL_CONFIGS,
    RESTORE_IMS,
    RESTORE_WFC,
    RESTORE_5G,
    RESTORE_VOWIFI_NAME,
    RESTORE_IDENTITY_OVERRIDE,
    VERIFY_CURRENT_CONFIG,
    SLEEP_ONEKUKU,
}

data class OneKukuCommandResult(
    val success: Boolean,
    val state: OneKukuRunnerState,
    val message: String,
    val detail: Map<String, Boolean> = emptyMap(),
)

/**
 * 特权探测桥：由 core 注入实现。
 * onekuku 包本身不引用第三方特权 API。
 */
interface OneKukuPrivilegeBridge {
    fun isActivated(): Boolean
    fun requestWake(): Boolean
}
