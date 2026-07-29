package com.oneims.app.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import java.io.File

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
    val SHIZUKU_MANAGER_PACKAGES: List<String> = listOf(
        "moe.shizuku.privileged.api",
        "moe.shizuku.manager",
    )

    /** 已安装的 Shizuku Manager 包名；未装返回 null。 */
    fun resolveInstalledShizukuPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in SHIZUKU_MANAGER_PACKAGES) {
            val ok = runCatching {
                pm.getApplicationInfo(pkg, 0)
                true
            }.getOrDefault(false)
            if (ok) return pkg
        }
        return null
    }

    /**
     * 对齐 Shizuku `Starter.internalCommand`：`libshizuku.so --apk=<sourceDir>`。
     * 供 Lite Root 开机用 su 代拉，避免「有 Root 仍要手点激活」。
     */
    fun buildShizukuRootStartCommand(context: Context): String? {
        val pkg = resolveInstalledShizukuPackage(context) ?: return null
        val ai = runCatching {
            context.packageManager.getApplicationInfo(pkg, 0)
        }.getOrNull() ?: return null
        val so = File(ai.nativeLibraryDir, "libshizuku.so")
        if (!so.isFile) {
            Log.w(TAG, "libshizuku.so missing under ${ai.nativeLibraryDir}")
            return null
        }
        return "${so.absolutePath} --apk=${ai.sourceDir}"
    }

    /**
     * 对齐 2.0.8 / 2.0.9：打开官方 Shizuku；未安装则跳应用市场。
     * OneLink 轻壳专用路径（配对/启动在 Shizuku 内完成）。
     *
     * @return 0=已打开 Shizuku，1=未装（已尝试跳商店），2=失败
     */
    fun openShizukuApp(context: Context): Int {
        val pm = context.packageManager
        for (pkg in SHIZUKU_MANAGER_PACKAGES) {
            val launch = pm.getLaunchIntentForPackage(pkg) ?: continue
            val ok = runCatching {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (ok) return 0
        }
        // OneLink 推荐 asrtroh 修缮版 V15.0.0（与库内逻辑同系）；商店可能装到官版。
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setData(
                        android.net.Uri.parse(
                            "https://github.com/asrtroh-netizen/shizuku/releases/tag/V15.0.0",
                        ),
                    )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            1
        }.getOrDefault(2)
    }

    /** @return 是否成功拉起已安装的 Shizuku 界面（不含跳商店）。 */
    fun openShizukuManager(context: Context): Boolean = openShizukuApp(context) == 0

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

    /** 当前 `adb_wifi_enabled` 是否已为 1（未授权读设置时按 false）。 */
    fun isAdbWifiEnabled(context: Context): Boolean =
        runCatching {
            Settings.Global.getInt(context.contentResolver, GLOBAL_ADB_WIFI, 0) == 1
        }.getOrDefault(false)

    /**
     * 确保无线调试开关为开。
     * - [AdbWifiEnsureResult.ALREADY_ON]：本来就是开的，调用方无需再硬等
     * - [AdbWifiEnsureResult.ENABLED_NOW]：刚从关→开，需短等系统起 TLS 服务
     * - [AdbWifiEnsureResult.FAILED]：无权限或写入失败
     */
    enum class AdbWifiEnsureResult { ALREADY_ON, ENABLED_NOW, FAILED }

    fun ensureAdbWifiEnabled(context: Context): AdbWifiEnsureResult {
        if (!hasWriteSecureSettings(context)) {
            Log.i(TAG, "ensureAdbWifi: no WRITE_SECURE_SETTINGS")
            return AdbWifiEnsureResult.FAILED
        }
        return runCatching {
            val wasOn = isAdbWifiEnabled(context)
            if (wasOn) {
                Log.i(TAG, "ensureAdbWifi: already on")
                return@runCatching AdbWifiEnsureResult.ALREADY_ON
            }
            Settings.Global.putInt(context.contentResolver, GLOBAL_ADB_WIFI, 1)
            val now = Settings.Global.getInt(context.contentResolver, GLOBAL_ADB_WIFI, 0)
            Log.i(TAG, "ensureAdbWifi: wrote adb_wifi_enabled=$now")
            if (now == 1) AdbWifiEnsureResult.ENABLED_NOW else AdbWifiEnsureResult.FAILED
        }.getOrElse {
            Log.w(TAG, "ensureAdbWifi failed", it)
            AdbWifiEnsureResult.FAILED
        }
    }

    /**
     * 开机无码重连关键一步：在已授予 [WRITE_SECURE_SETTINGS] 时写回
     * `adb_wifi_enabled=1`（系统重启常会关掉无线调试；配对关系仍在）。
     * 对齐 Shizuku / Tasker 常见做法。
     */
    fun tryEnableAdbWifi(context: Context): Boolean =
        when (ensureAdbWifiEnabled(context)) {
            AdbWifiEnsureResult.ALREADY_ON,
            AdbWifiEnsureResult.ENABLED_NOW,
            -> true
            AdbWifiEnsureResult.FAILED -> false
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
