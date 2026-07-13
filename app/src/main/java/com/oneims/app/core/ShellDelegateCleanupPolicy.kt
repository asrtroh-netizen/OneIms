package com.oneims.app.core

/**
 * Instrumentation 结束时清理 shell 权限委托的失败分级。
 * stop API 在 Android 17 / 部分 OEM 上可能缺失：此类失败不得冒充业务写入失败。
 */
internal object ShellDelegateCleanupPolicy {
    fun isBenignStopFailure(
        error: Throwable,
        describe: (Throwable) -> String = OperationErrors::describe,
    ): Boolean {
        if (error is NoSuchMethodException || error.cause is NoSuchMethodException) return true
        val text = describe(error)
        return text.contains("stopDelegateShellPermissionIdentity", ignoreCase = true) ||
            text.contains("NoSuchMethodException", ignoreCase = true) &&
            text.contains("DelegateShellPermission", ignoreCase = true)
    }
}
