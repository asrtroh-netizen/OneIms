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
import com.oneims.app.core.privilege.ChannelEngine
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * OneKuku「通道」就绪探测与拉起命令。
 *
 * Phase4：OneBridge starter 以 library 打进主 App（[HOST_PACKAGE]），
 * **不再要求安装**独立 `com.oneims.bridge` APK。有 Wi‑Fi 时用无线调试配对拉起。
 *
 * P3 目标：用宿主内嵌 Care/Shizuku server 最小面（[ChannelEngine.CARE_MIN] /
 * `onekuku_server`）替换本文件仍指向的 `onebridge_server`；邻仓
 * `com.onekuku.care` 只作编包试验田，不是用户路径。
 */
object OneKukuCoreComponent {

    /** 宿主包：内循环真源（现 OneBridge；P3 后同宿主内嵌 MINI server）。 */
    const val HOST_PACKAGE: String = "com.oneims.app"

    /** 邻仓 Care / MINI 包名：仅实验室对照，主路径不依赖已安装。 */
    const val CARE_PACKAGE: String = ShizukuSetupHelper.CARE_PACKAGE

    /**
     * 历史独立桥包名。Phase4 起不再作为安装目标；
     * [bridgeBootShellCommand] / 解析优先用宿主包。
     */
    @Deprecated("Phase4 embedded into host app", ReplaceWith("HOST_PACKAGE"))
    const val BRIDGE_PACKAGE: String = HOST_PACKAGE

    /** @deprecated 旧 MINI 试验包名；现用 [CARE_PACKAGE]。 */
    @Deprecated("Renamed to com.onekuku.care", ReplaceWith("CARE_PACKAGE"))
    const val BRANDED_CORE_PACKAGE: String = "com.oneims.onekuku.core"

    /** @deprecated Phase3 已卸；保留常量避免旧引用编译炸掉。 */
    @Deprecated("Phase3 removed upstream Shizuku package path")
    const val LEGACY_CORE_PACKAGE: String = "moe.shizuku.privileged.api"

    /** @deprecated 使用 [resolveCorePackage]；指向宿主包。 */
    const val CORE_PACKAGE: String = HOST_PACKAGE

    /**
     * 内循环真源=宿主包；旧独立桥兼容；Care 仅实验室对照（用户路径不依赖第二 App）。
     * [bridgeBootShellCommand] 按 [ChannelEngine] 选择 nice-name / 入口类。
     */
    val CANDIDATE_PACKAGES: List<String> = listOf(HOST_PACKAGE, "com.oneims.bridge", CARE_PACKAGE)

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

    /**
     * 拉起 shell 时应使用的包名（含 BridgeService 的 APK）。
     * Phase4：始终为主 App；若用户仍残留旧 `com.oneims.bridge` 也可作次选。
     */
    fun resolveCorePackage(context: Context): String? {
        val app = context.applicationContext
        val host = app.packageName.ifBlank { HOST_PACKAGE }
        val pm = app.packageManager
        if (runCatching { pm.getPackageInfo(host, 0); true }.getOrDefault(false)) {
            return host
        }
        for (pkg in CANDIDATE_PACKAGES) {
            if (pkg == host) continue
            val ok = runCatching {
                pm.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
            if (ok) return pkg
        }
        return host
    }

    /** Phase4：通道已内嵌主包，视为始终「已安装」。 */
    fun isInstalled(context: Context): Boolean = true

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
        val pkg = context?.let { resolveCorePackage(it) } ?: HOST_PACKAGE
        return "adb shell ${bridgeBootShellCommand(pkg)}"
    }

    /**
     * 从指定包 APK 用 app_process 拉起通道 server。
     *
     * - [ChannelEngine.ONEBRIDGE]（默认）：`--nice-name=onebridge_server` +
     *   `com.oneims.bridge.server.BridgeService`（已在 `:bridge` / 主 APK）
     * - [ChannelEngine.CARE_MIN]：`--nice-name=onekuku_server` +
     *   `rikka.shizuku.server.ShizukuService`
     *
     * **P3a 依赖**：CARE_MIN 入口类需后续迁入 server 最小面后才真正可加载；
     * 本方法在引擎切到 CARE_MIN 时仍会写出完整命令字符串，便于 starter / 单测接线，
     * 类未进 APK 前运行时会 ClassNotFound（属预期，非本方法静默回落）。
     *
     * Phase4：默认宿主包；**不用 exec**，后台拉起后 echo 标记。
     *
     * **对齐 Shizuku**：默认不 `pkill` 已在跑的 server——划掉 App 后 shell 进程应继续活着，
     * 重进只等 binder 再投递。显式 [forceRestart]=true 才杀掉重建（设置里「重新激活」用）。
     *
     * 状态标记必须是「整行输出」专用串，且不能用会嵌在脚本正文里被 PTY 回显误判的旧名。
     */
    fun bridgeBootShellCommand(
        packageName: String = HOST_PACKAGE,
        forceRestart: Boolean = false,
    ): String {
        val nice = ChannelEngine.processNiceName()
        val entryClass = when (ChannelEngine.current()) {
            ChannelEngine.ONEBRIDGE -> ENTRY_CLASS_ONEBRIDGE
            ChannelEngine.CARE_MIN -> ENTRY_CLASS_CARE_MIN
        }
        // setsid+stdin 断开：libadb 的 shell: 流关闭时否则会 SIGHUP 带走刚拉起的 server，
        // 表现为 binder 收到后约 2s 就 dead（与 V15「划掉还能活」差一截）。
        val start =
            "APK=\$(pm path $packageName 2>/dev/null | head -n 1 | cut -d: -f2 | tr -d '\\r'); " +
                "if [ -z \"\$APK\" ]; then printf '%s\\n' $SHELL_BOOT_MISS; exit 1; fi; " +
                "export CLASSPATH=\"\$APK\"; " +
                "(setsid /system/bin/app_process /system/bin --nice-name=$nice " +
                "$entryClass >/dev/null 2>&1 </dev/null &) || " +
                "(nohup /system/bin/app_process /system/bin --nice-name=$nice " +
                "$entryClass >/dev/null 2>&1 </dev/null &); " +
                "printf '%s\\n' $SHELL_BOOT_OK"
        return if (forceRestart) {
            "pkill -f $nice 2>/dev/null || true; $start"
        } else {
            // 已在跑：直接 OK，由 server 周期重投 binder 给新 App 进程。
            "if pidof $nice >/dev/null 2>&1; then printf '%s\\n' $SHELL_BOOT_OK; " +
                "else $start; fi"
        }
    }

    /** ONEBRIDGE 现网入口（`:bridge` 已打进主 APK）。 */
    const val ENTRY_CLASS_ONEBRIDGE: String = "com.oneims.bridge.server.BridgeService"

    /**
     * CARE_MIN 目标入口（邻仓 server 最小面）。
     * P3a：命令字符串先写出；类进 APK 依赖 server 模块迁入（见
     * `docs/architecture/2026-07-30-care-min-server-import-whitelist.md`）。
     */
    const val ENTRY_CLASS_CARE_MIN: String = "rikka.shizuku.server.ShizukuService"

    /** shell 成功标记（整行）；勿改成会出现在命令正文其它位置的子串。 */
    const val SHELL_BOOT_OK: String = "__OB_BOOT_OK__"

    /** shell 失败：找不到 APK。 */
    const val SHELL_BOOT_MISS: String = "__OB_BOOT_MISS__"

    fun guidedActivationScript(context: Context): String =
        context.getString(R.string.onekuku_adb_guide_script, adbStartCommand(context))

    /**
     * 一键准备（Phase4）：通道已内嵌，只打开无线调试设置，供通知栏填六位码。
     * **不再**触发安装独立桥包 / 下载 APK。
     */
    fun prepare(context: Context): PrepareResult {
        val app = context.applicationContext
        val opened = EmbeddedKadbActivationBridge.openWirelessDebugging(app)
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
