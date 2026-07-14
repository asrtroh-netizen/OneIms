package com.oneims.app.core

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.oneims.app.R
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * OneKuku「核心组件」安装与就绪探测（方案 A）。
 *
 * 产品面只说 OneKuku；当前兼容实现仍使用与 rikka 客户端 binder 协议兼容的官方服务包。
 * 用户路径：**不再跳应用商店装独立第三方 App**，改为内置 APK / 官方核心包下载 + ADB 拉起（方案 B）。
 */
object OneKukuCoreComponent {

    /** 与 rikka API 通信所需的服务端包名（内部常量，不对用户展示）。 */
    const val CORE_PACKAGE: String = "moe.shizuku.privileged.api"

    const val BUNDLED_ASSET_NAME: String = "onekuku-core.apk"

    private const val CORE_REPO_OWNER = "RikkaApps"
    private const val CORE_REPO_NAME = "Shizuku"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    enum class Status {
        /** 未安装核心组件 */
        MISSING,

        /** 已安装但特权进程未跑 */
        INSTALLED_STOPPED,

        /** 已运行但未授权给 OneIMS */
        RUNNING_NEED_AUTH,

        /** 运行且已授权 */
        READY,
    }

    enum class PrepareResult {
        /** 已跳转无线调试，并复制了 ADB 启动命令 */
        OPENED_ADB_GUIDE,

        /** 已拉起内置 APK 安装器 */
        INSTALLING_BUNDLED,

        /** 需要 IO 线程解析下载地址后调用 [downloadOfficialCore] */
        NEEDS_DOWNLOAD,

        /** 已开始下载核心组件 */
        DOWNLOADING_CORE,

        /** 失败 */
        FAILED,
    }

    fun resolveStatus(context: Context): Status = when {
        OneKukuManager.isReady() -> Status.READY
        OneKukuManager.isRunning() -> Status.RUNNING_NEED_AUTH
        isInstalled(context) -> Status.INSTALLED_STOPPED
        else -> Status.MISSING
    }

    fun isInstalled(context: Context): Boolean =
        runCatching {
            context.applicationContext.packageManager.getPackageInfo(CORE_PACKAGE, 0)
            true
        }.getOrDefault(false)

    fun hasBundledApk(context: Context): Boolean =
        runCatching {
            context.assets.open(BUNDLED_ASSET_NAME).use { true }
        }.getOrDefault(false)

    fun adbStartCommand(): String =
        "adb shell sh /storage/emulated/0/Android/data/$CORE_PACKAGE/start.sh"

    /**
     * 免电脑引导脚本（方案 B · 剪贴板实现；原生内嵌 ADB 客户端另迭代）。
     */
    fun guidedActivationScript(context: Context): String =
        context.getString(R.string.onekuku_adb_guide_script, adbStartCommand())

    /**
     * 一键准备（同步部分）：缺组件优先装内置包，否则返回 [PrepareResult.NEEDS_DOWNLOAD]；
     * 已装则开无线调试并复制 ADB 启动命令。**绝不**跳转应用市场。
     */
    fun prepare(context: Context): PrepareResult {
        val app = context.applicationContext
        if (!isInstalled(app)) {
            if (hasBundledApk(app) && installBundledApk(app)) {
                return PrepareResult.INSTALLING_BUNDLED
            }
            return PrepareResult.NEEDS_DOWNLOAD
        }
        val bridge: OneKukuAdbActivationBridge = EmbeddedKadbActivationBridge
        val opened = bridge.openWirelessDebugging(app)
        ShizukuSetupHelper.copyToClipboard(
            app,
            app.getString(R.string.app_name),
            bridge.buildGuideScript(app),
        )
        return if (opened) PrepareResult.OPENED_ADB_GUIDE else PrepareResult.FAILED
    }

    fun installBundledApk(context: Context): Boolean {
        val app = context.applicationContext
        return runCatching {
            val dir = File(app.cacheDir, "apk").apply { mkdirs() }
            val out = File(dir, BUNDLED_ASSET_NAME)
            app.assets.open(BUNDLED_ASSET_NAME).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            val uri = FileProvider.getUriForFile(
                app,
                "${app.packageName}.fileprovider",
                out,
            )
            val install = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            app.startActivity(install)
            true
        }.getOrDefault(false)
    }

    /**
     * 从官方核心组件 Release 拉取 APK 并安装（UI 文案为 OneKuku，不诱导去商店）。
     * [apkUrl] 须先在 IO 线程用 [resolveLatestCoreApkUrl] 解析。
     */
    fun downloadOfficialCore(context: Context, apkUrl: String): Boolean {
        val app = context.applicationContext
        if (apkUrl.isBlank()) return false
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return false
        val fileName = "OneKuku-core.apk"
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle(app.getString(R.string.onekuku_core_download_title))
            .setDescription(app.getString(R.string.onekuku_core_download_desc))
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, fileName)
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (finishedId != downloadId) return
                runCatching {
                    val apkUri = dm.getUriForDownloadedFile(downloadId)
                    if (apkUri != null) {
                        val install = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(apkUri, "application/vnd.android.package-archive")
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        app.startActivity(install)
                    }
                }
                runCatching { app.unregisterReceiver(this) }
            }
        }
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        return true
    }

    /** 阻塞：解析官方核心组件最新 APK 直链。失败返回 null。 */
    fun resolveLatestCoreApkUrl(): String? {
        var conn: HttpURLConnection? = null
        return try {
            val api =
                "https://api.github.com/repos/$CORE_REPO_OWNER/$CORE_REPO_NAME/releases/latest"
            conn = (URL(api).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "OneIms-OneKukuCore")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val assets = JSONObject(json).optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    return a.optString("browser_download_url").ifBlank { null }
                }
            }
            null
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun isCorePackageEnabled(context: Context): Boolean =
        runCatching {
            val state = context.packageManager.getApplicationEnabledSetting(CORE_PACKAGE)
            state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER &&
                state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        }.getOrDefault(false)
}
