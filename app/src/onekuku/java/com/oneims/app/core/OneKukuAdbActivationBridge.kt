package com.oneims.app.core

/**
 * 用 ADB（无线调试）拉起 OneKuku 核心特权进程的契约。
 * 实现位于 onekuku flavor（内嵌 ADB）；onelink 不提供实现。
 */
interface OneKukuAdbActivationBridge {
    /** 返回给用户复制的完整引导文案（含 pair / connect / start）。 */
    fun buildGuideScript(context: android.content.Context): String

    /** 打开系统无线调试页；成功返回 true。 */
    fun openWirelessDebugging(context: android.content.Context): Boolean
}
