package com.oneims.app.core

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import com.oneims.app.BuildConfig
import com.oneims.app.R
import com.oneims.app.model.UpdateInfo
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内检查更新（对接 GitHub Release，对齐 carrier-ims「应用维护」）。
 *
 * 设计取舍：
 *   - 只用安卓自带 `HttpURLConnection` + `org.json` 拉取 GitHub `releases/latest`，
 *     **不引入 OkHttp/Retrofit 等重依赖**（一个只读的小请求不值得多带一个网络库）；
 *   - 下载走系统 `DownloadManager`（自带断点/通知/存储管理），完成后用其返回的
 *     content:// URI 直接拉起安装，**免手配 FileProvider**；安装需 `REQUEST_INSTALL_PACKAGES` 权限。
 *
 * 仅依赖 GitHub Releases 的公开只读接口（无 Authorization 头），因此目标仓库必须是 Public，
 * 且每次发布都要在该仓库建一个 Release 并上传 .apk 附件；仓库里放不放源码与此无关。
 */
object UpdateChecker {

    private const val REPO_OWNER = "asrtroh-netizen"
    private const val REPO_NAME = "OneIms"

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    private fun latestApiUrl() = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    /**
     * 拉取并解析最新 Release，与当前版本比较。阻塞式，请在 IO 线程调用（与项目既有模式一致）。
     * 任何异常都收敛为「失败但不崩」的 [UpdateInfo]，绝不把网络异常抛给 UI。文案随系统语言切中/英。
     */
    fun checkLatest(context: Context): UpdateInfo {
        val current = BuildConfig.VERSION_NAME
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(latestApiUrl()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                // GitHub API 强制要求 User-Agent，否则 403
                setRequestProperty("User-Agent", "OneIms-UpdateChecker")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return fail(current, context.getString(R.string.update_http_fail, code))
            }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            parse(context, json, current)
        } catch (e: Throwable) {
            fail(current, context.getString(R.string.update_net_fail, e.message ?: context.getString(R.string.update_net_exception)))
        } finally {
            conn?.disconnect()
        }
    }

    private fun parse(context: Context, json: String, current: String): UpdateInfo {
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name").ifBlank { obj.optString("name") }
        val notes = obj.optString("body").trim()
        val assets = obj.optJSONArray("assets")
        val apkUrl = pickChannelApkUrl(assets)
        val latestDisplay = tag.trim().removePrefix("v").removePrefix("V")
        val newer = isRemoteNewer(tag, current)
        val msg = when {
            tag.isBlank() -> context.getString(R.string.update_no_version)
            newer && apkUrl.isNotBlank() -> context.getString(R.string.update_found, latestDisplay, current)
            newer -> context.getString(R.string.update_found_no_apk, latestDisplay)
            else -> context.getString(R.string.update_latest, current)
        }
        return UpdateInfo(
            hasUpdate = newer,
            currentVersion = current,
            latestVersion = latestDisplay,
            downloadUrl = apkUrl,
            releaseNotes = notes,
            message = msg,
        )
    }

    /**
     * 双产品线同 Release 挂两包：必须按当前渠道挑选，禁止 OneLink 误下 OneKuku。
     * 优先匹配渠道关键词；找不到再回退任意 .apk（兼容旧单包 Release）。
     */
    private fun pickChannelApkUrl(assets: org.json.JSONArray?): String {
        if (assets == null || assets.length() == 0) return ""
        val preferred = mutableListOf<String>()
        val fallback = mutableListOf<String>()
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = a.optString("browser_download_url")
            if (url.isBlank()) continue
            if (matchesCurrentChannelApk(name)) {
                preferred += url
            } else {
                fallback += url
            }
        }
        return preferred.firstOrNull() ?: fallback.firstOrNull().orEmpty()
    }

    private fun matchesCurrentChannelApk(fileName: String): Boolean {
        val n = fileName.lowercase()
        return if (ChannelLine.usesShizuku) {
            n.contains("onelink") || (n.contains("shizuku") && !n.contains("onekuku"))
        } else {
            n.contains("onekuku") || n.contains("standalone")
        }
    }

    /** 版本号语义化比较：按点分数字段逐位比大小，忽略 v 前缀与非数字后缀。 */
    private fun isRemoteNewer(remote: String, current: String): Boolean {
        val r = normalize(remote)
        val c = normalize(current)
        val n = maxOf(r.size, c.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun normalize(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }

    private fun fail(current: String, message: String) =
        UpdateInfo(false, current, current, "", "", message)

    /**
     * 用系统 DownloadManager 下载 APK，完成后自动拉起安装。
     * 完成回调用 applicationContext 注册一次性广播接收器并在处理后注销，避免泄漏。
     */
    fun downloadAndInstall(context: Context, url: String, versionName: String) {
        val app = context.applicationContext
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        val brand = app.getString(R.string.channel_display_name)
        val fileName = "OneIms-$brand-$versionName.apk"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("OneIms $brand $versionName")
            .setDescription(app.getString(R.string.update_downloading))
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
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        app.startActivity(install)
                    }
                }
                runCatching { app.unregisterReceiver(this) }
            }
        }
        // ACTION_DOWNLOAD_COMPLETE 是系统广播，API 33+ 注册时必须显式声明导出属性
        ContextCompat.registerReceiver(
            app, receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }
}
