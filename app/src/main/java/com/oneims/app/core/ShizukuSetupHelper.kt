package com.oneims.app.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * OneKuku 通道「无 WiFi 也能准备」助手。
 *
 * 关键事实：无线调试连的是**手机本机回环 127.0.0.1**，
 * 并不需要连 WiFi 上外网。出门没 WiFi 时，只要「无线调试」开关能打开即可；
 * 部分机型需要一个本地网络接口——开**个人热点**即可满足，无需真的联网。
 */
object ShizukuSetupHelper {

    private const val TAG = "OneIMS-WirelessDbg"
    private const val GLOBAL_ADB_WIFI = "adb_wifi_enabled"

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

    fun hasWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 开机无码重连关键一步：在已授予 [WRITE_SECURE_SETTINGS] 时写回
     * `adb_wifi_enabled=1`（系统重启常会关掉无线调试；配对关系仍在）。
     * 对齐 Shizuku / Tasker 常见做法。
     */
    fun tryEnableAdbWifi(context: Context): Boolean {
        if (!hasWriteSecureSettings(context)) {
            Log.i(TAG, "tryEnableAdbWifi: no WRITE_SECURE_SETTINGS")
            return false
        }
        return runCatching {
            Settings.Global.putInt(context.contentResolver, GLOBAL_ADB_WIFI, 1)
            val now = Settings.Global.getInt(context.contentResolver, GLOBAL_ADB_WIFI, 0)
            Log.i(TAG, "tryEnableAdbWifi: wrote adb_wifi_enabled=$now")
            now == 1
        }.getOrElse {
            Log.w(TAG, "tryEnableAdbWifi failed", it)
            false
        }
    }

    /** 打开开发者选项页。 */
    fun openDeveloperOptions(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    /** @deprecated 使用 [OneKukuCoreComponent.HOST_PACKAGE] */
    const val SHIZUKU_PKG = OneKukuCoreComponent.HOST_PACKAGE

    /** 是否已安装 OneBridge 通道。 */
    fun isShizukuInstalled(context: Context): Boolean =
        OneKukuCoreComponent.isInstalled(context)

    /**
     * @deprecated 改走 [OneKukuCoreComponent.prepare]，禁止再跳应用市场装独立通道 App。
     * 保留兼容：已装则打开组件；未装返回 1（由调用方改走内置/下载），不再跳市场。
     */
    @Deprecated("Use OneKukuCoreComponent.prepare")
    fun openShizukuApp(context: Context): Int {
        val pkg = OneKukuCoreComponent.resolveCorePackage(context) ?: return 1
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
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
