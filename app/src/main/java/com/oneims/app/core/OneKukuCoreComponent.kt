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
 * OneKuku「通道」安装与就绪探测。
 *
 * Phase3：只认 [BRIDGE_PACKAGE]（OneBridge）。不再安装/探测换皮 Core 或上游 Shizuku。
 * 用户路径：**不跳应用商店**；内置 `oneims-bridge.apk` + 无线调试 / 内嵌 ADB 拉起。
 */
object OneKukuCoreComponent {

    /** OneBridge 自研通道（唯一产品路径）。 */
    const val BRIDGE_PACKAGE: String = "com.oneims.bridge"

    /** @deprecated Phase3 已卸；保留常量避免旧引用编译炸掉。 */
    @Deprecated("Phase3 removed branded core path")
    const val BRANDED_CORE_PACKAGE: String = "com.oneims.onekuku.core"

    /** @deprecated Phase3 已卸；保留常量避免旧引用编译炸掉。 */
    @Deprecated("Phase3 removed upstream Shizuku package path")
    const val LEGACY_CORE_PACKAGE: String = "moe.shizuku.privileged.api"

    /** @deprecated 使用 [resolveCorePackage]；指向桥包。 */
    const val CORE_PACKAGE: String = BRIDGE_PACKAGE

    val CANDIDATE_PACKAGES: List<String> = listOf(BRIDGE_PACKAGE)

    /** 内置 OneBridge APK。 */
    const val BUNDLED_BRIDGE_ASSET_NAME: String = "oneims-bridge.apk"

    /** @deprecated Phase3 不再内置换皮 Core。 */
    @Deprecated("Phase3 removed")
    const val BUNDLED_CORE_ASSET_NAME: String = "onekuku-core.apk"

    /** @deprecated 使用 [BUNDLED_BRIDGE_ASSET_NAME]。 */
    const val BUNDLED_ASSET_NAME: String = BUNDLED_BRIDGE_ASSET_NAME

    val BUNDLED_ASSET_CANDIDATES: List<String> = listOf(BUNDLED_BRIDGE_ASSET_NAME)

    private const val CORE_REPO_OWNER = "asrtroh-netizen"
    private const val CORE_REPO_NAME = "OneIms"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    /** Release 资产名约定：自有桥包。 */
    private const val BRIDGE_ASSET_PREFIX = "OneBridge"
    private const val BRIDGE_ASSET_ALT = "oneims-bridge"

    enum class Status {
        MISSING,
        INSTALLED_STOPPED,
        RUNNING_NEED_AUTH,
        READY,
    }

    enum class PrepareResult {
        OPENED_ADB_GUIDE,
        INSTALLING_BUNDLED,
        NEEDS_DOWNLOAD,
        DOWNLOADING_CORE,
        FAILED,
    }

    fun resolveStatus(context: Context): Status = when {
        OneKukuManager.isReady() -> Status.READY
        OneKukuManager.isRunning() -> Status.RUNNING_NEED_AUTH
        isInstalled(context) -> Status.INSTALLED_STOPPED
        else -> Status.MISSING
    }

    /** 已安装的通道包（仅 OneBridge）。 */
    fun resolveCorePackage(context: Context): String? {
        val pm = context.applicationContext.packageManager
        for (pkg in CANDIDATE_PACKAGES) {
            val ok = runCatching {
                pm.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
            if (ok) return pkg
        }
        return null
    }

    fun isInstalled(context: Context): Boolean = resolveCorePackage(context) != null

    @Deprecated("Phase3: branded core path removed")
    fun isBrandedCoreInstalled(context: Context): Boolean = false

    fun hasBundledApk(context: Context): Boolean =
        resolveBundledAssetName(context) != null

    fun resolveBundledAssetName(context: Context): String? {
        val assets = context.applicationContext.assets
        for (name in BUNDLED_ASSET_CANDIDATES) {
            val ok = runCatching {
                assets.open(name).use { true }
            }.getOrDefault(false)
            if (ok) return name
        }
        return null
    }

    fun adbStartCommand(context: Context? = null): String {
        val pkg = context?.let { resolveCorePackage(it) }
            ?: BRIDGE_PACKAGE
        return "adb shell ${bridgeBootShellCommand(pkg)}"
    }

    /**
     * 不依赖 `Android/data/.../start.sh` 是否已写出——装完通道即可拉起。
     * 与 bridge 模块 `assets/start.sh` 语义等价。
     */
    fun bridgeBootShellCommand(packageName: String = BRIDGE_PACKAGE): String =
        "pkill -f onebridge_server 2>/dev/null || true; " +
            "APK=\$(pm path $packageName 2>/dev/null | head -n 1 | cut -d: -f2 | tr -d '\\r'); " +
            "if [ -z \"\$APK\" ]; then echo OneBridge_missing >&2; exit 1; fi; " +
            "export CLASSPATH=\"\$APK\"; " +
            "exec /system/bin/app_process /system/bin --nice-name=onebridge_server " +
            "com.oneims.bridge.server.BridgeService"

    fun guidedActivationScript(context: Context): String =
        context.getString(R.string.onekuku_adb_guide_script, adbStartCommand(context))

    /**
     * 一键准备：缺组件优先装内置桥包；无内置则 [NEEDS_DOWNLOAD]。
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
        val assetName = resolveBundledAssetName(app) ?: return false
        return runCatching {
            val dir = File(app.cacheDir, "apk").apply { mkdirs() }
            val out = File(dir, assetName)
            app.assets.open(assetName).use { input ->
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

    fun downloadOfficialCore(context: Context, apkUrl: String): Boolean {
        val app = context.applicationContext
        if (apkUrl.isBlank()) return false
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return false
        val fileName = "oneims-bridge.apk"
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

    /** 阻塞：解析自有 OneBridge APK 直链。失败返回 null（绝不回落上游 Shizuku 仓库）。 */
    fun resolveLatestCoreApkUrl(): String? {
        var conn: HttpURLConnection? = null
        return try {
            val api =
                "https://api.github.com/repos/$CORE_REPO_OWNER/$CORE_REPO_NAME/releases/latest"
            conn = (URL(api).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "OneIms-OneBridge")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val assets = JSONObject(json).optJSONArray("assets") ?: return null
            var fallbackApk: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                val url = a.optString("browser_download_url").ifBlank { null } ?: continue
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                if (name.startsWith(BRIDGE_ASSET_PREFIX, ignoreCase = true)) {
                    return url
                }
                if (fallbackApk == null && name.contains(BRIDGE_ASSET_ALT, ignoreCase = true)) {
                    fallbackApk = url
                }
            }
            fallbackApk
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun isCorePackageEnabled(context: Context): Boolean {
        val pkg = resolveCorePackage(context) ?: return false
        return runCatching {
            val state = context.packageManager.getApplicationEnabledSetting(pkg)
            state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER &&
                state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        }.getOrDefault(false)
    }
}
