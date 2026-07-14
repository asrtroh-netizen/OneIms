package com.oneims.app.core

/**
 * 方案 B：用 ADB（无线调试）拉起 OneKuku 核心特权进程。
 *
 * 本轮交付 [ClipboardGuidedAdbBridge]（复制命令 + 跳转系统无线调试）。
 * 原生内嵌 ADB 客户端（LADB 类）实现同一接口，后续可替换而不改 UI 契约。
 */
interface OneKukuAdbActivationBridge {
    /** 返回给用户复制的完整引导文案（含 pair / connect / start）。 */
    fun buildGuideScript(context: android.content.Context): String

    /** 打开系统无线调试页；成功返回 true。 */
    fun openWirelessDebugging(context: android.content.Context): Boolean
}

object ClipboardGuidedAdbBridge : OneKukuAdbActivationBridge {
    override fun buildGuideScript(context: android.content.Context): String =
        OneKukuCoreComponent.guidedActivationScript(context)

    override fun openWirelessDebugging(context: android.content.Context): Boolean =
        ShizukuSetupHelper.openWirelessDebugging(context)
}
