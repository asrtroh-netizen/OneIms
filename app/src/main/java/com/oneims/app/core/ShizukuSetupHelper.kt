package com.oneims.app.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Shizuku「无 WiFi 也能开启」助手。
 *
 * 关键事实（破除误区）：Shizuku 的无线调试连的是**手机本机回环 127.0.0.1**，
 * 并不需要连 WiFi 上外网。出门没 WiFi 时，只要「无线调试」开关能打开即可；
 * 部分机型需要一个本地网络接口——开**个人热点**即可满足，无需真的联网。
 *
 * App 无法直接替用户打开系统开关（需系统权限），本助手提供三件事：
 *   1) 一键跳转到「无线调试 / 开发者选项」设置页；
 *   2) 澄清「无需 WiFi」的正确认知 + 开热点兜底；
 *   3) 免电脑自助路径：Termux + 本机 adb 对 localhost 自配对的现成命令（一键复制）。
 */
object ShizukuSetupHelper {

    /** 尝试直达「无线调试」设置页；失败则退回「开发者选项」。返回是否成功跳转。 */
    fun openWirelessDebugging(context: Context): Boolean {
        // Android 11+ 部分机型支持直达无线调试页
        val tryActions = listOf(
            "android.settings.ADB_WIRELESS_SETTINGS",
            Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        )
        for (action in tryActions) {
            val ok = runCatching {
                val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    /** 打开开发者选项页。 */
    fun openDeveloperOptions(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    /** Shizuku 官方包名。 */
    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    /** 是否已安装 Shizuku。 */
    fun isShizukuInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG) != null
    }.getOrDefault(false)

    /**
     * @deprecated 改走 [OneKukuCoreComponent.prepare]，禁止再跳应用市场装独立通道 App。
     * 保留兼容：已装则打开组件；未装返回 1（由调用方改走内置/下载），不再跳市场。
     */
    @Deprecated("Use OneKukuCoreComponent.prepare")
    fun openShizukuApp(context: Context): Int {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)
        if (launch != null) {
            return runCatching {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); 0
            }.getOrDefault(2)
        }
        return 1
    }

    /** 打开个人热点设置（给无线调试提供本地网络接口的兜底）。 */
    fun openHotspotSettings(context: Context): Boolean {
        val tryActions = listOf(
            "android.settings.TETHER_SETTINGS",
            Settings.ACTION_WIRELESS_SETTINGS,
        )
        for (action in tryActions) {
            val ok = runCatching {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
