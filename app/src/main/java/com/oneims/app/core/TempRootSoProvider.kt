package com.oneims.app.core

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 临时 Root so 获取：**全部从公开 [OneSo-assets] 云端拉取**（APK 不再内置 preload-*.so）。
 *
 * 顺序：强制远端下载 → 失败则用本机 `temproot-cache` → 再无则失败（不再回退 APK assets）。
 * 网络风格对齐 [UpdateChecker]（HttpURLConnection，无 OkHttp）。
 * URL 白名单仅允许 `raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/`。
 */
object TempRootSoProvider {
    private const val TAG = "OneIMS-TempRootSo"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val USER_AGENT = "OneIms-TempRootSo"

    const val REMOTE_CATALOG_URL =
        "https://raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/main/catalog.json"
    const val REMOTE_SHA256SUMS_URL =
        "https://raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/main/SHA256SUMS"
    private const val DEFAULT_BASE_URL =
        "https://raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/main/"
    private const val ALLOWED_HOST = "raw.githubusercontent.com"
    private const val ALLOWED_PATH_PREFIX = "/asrtroh-netizen/OneSo-assets/"

    private const val CACHE_DIR = "temproot-cache"
    private const val CACHED_CATALOG = "remote-catalog.json"
    private const val CACHED_SHA = "remote-SHA256SUMS"

    sealed class SoSource {
        data class Asset(val assetPath: String) : SoSource()
        data class CachedFile(val file: File, val fetchedRemote: Boolean) : SoSource()
    }

    data class Ready(
        val device: String,
        val buildId: String,
        val source: SoSource,
    )

    /**
     * IO 线程调用：点一键时确保本机有可 stage 的 so。
     *
     * **仅云端**：每次先刷新 OneSo-assets catalog/SHA 并下载最新 so；
     * 网络失败时仅退回本机 `temproot-cache`（不使用 APK 内置 so）。
     */
    fun ensure(context: Context): Ready? {
        val device = TempRootSoCatalog.currentDevice()
        val buildId = TempRootSoCatalog.currentBuildId()
        if (device.isBlank() || buildId.isBlank()) return null

        val cacheDir = File(context.filesDir, CACHE_DIR).also { it.mkdirs() }
        fetchRemoteSo(cacheDir, device, buildId)?.let { file ->
            Log.i(TAG, "use remote ${file.name} bytes=${file.length()}")
            return Ready(device, buildId, SoSource.CachedFile(file, fetchedRemote = true))
        }

        // 离线兜底：仅已下载缓存
        resolveFromCache(cacheDir, device, buildId)?.let { file ->
            Log.i(TAG, "remote miss → use cache ${file.name}")
            return Ready(device, buildId, SoSource.CachedFile(file, fetchedRemote = false))
        }

        Log.w(TAG, "no so for device=$device build=$buildId (cloud/cache only; no APK assets)")
        return null
    }

    /** 每次强制拉 catalog + so；成功返回缓存文件。 */
    private fun fetchRemoteSo(cacheDir: File, device: String, buildId: String): File? {
        val remotePath = resolveRemoteRelativePath(
            context = null,
            cacheDir = cacheDir,
            device = device,
            buildId = buildId,
            forceNetwork = true,
        ) ?: return null
        val url = absoluteSoUrl(remotePath)
        if (!isAllowedSoUrl(url)) {
            Log.w(TAG, "reject url $url")
            return null
        }
        val fileName = File(remotePath).name
        if (!fileName.matches(Regex("""^preload-[A-Za-z0-9_.-]+\.so$"""))) {
            Log.w(TAG, "reject file name $fileName")
            return null
        }
        val out = File(cacheDir, "${device}_${buildId}_$fileName")
        if (!downloadToFile(url, out)) {
            Log.w(TAG, "download failed $url")
            return null
        }
        val expectedSha = lookupSha256(cacheDir, remotePath, fileName)
        if (expectedSha != null && sha256Hex(out) != expectedSha) {
            Log.w(TAG, "downloaded sha mismatch ${out.name}")
            out.delete()
            return null
        }
        // 无 SUMS 时仍接受（公开仓应始终带 SHA256SUMS；缺失则记警告）
        if (expectedSha == null) {
            Log.w(TAG, "SHA256SUMS miss for $fileName — accepted without hash")
        }
        return out
    }

    private fun resolveFromCache(cacheDir: File, device: String, buildId: String): File? {
        val prefix = "${device}_${buildId}_"
        val hits = cacheDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(".so") && it.length() > 0L }
            .orEmpty()
        return hits.maxByOrNull { it.lastModified() }
    }

    fun isLikelySupported(context: Context): Boolean {
        val device = TempRootSoCatalog.currentDevice()
        val buildId = TempRootSoCatalog.currentBuildId()
        if (device.isBlank() || buildId.isBlank()) return false
        val cacheDir = File(context.filesDir, CACHE_DIR)
        val cached = File(cacheDir, CACHED_CATALOG)
        if (cached.isFile) {
            lookupInCatalogJson(cached.readText(), device, buildId)?.let { return true }
        }
        // 无本地缓存时仍允许点按钮去拉远端（点时再判定 UnsupportedDevice）
        return true
    }

    private fun resolveRemoteRelativePath(
        context: Context?,
        cacheDir: File,
        device: String,
        buildId: String,
        forceNetwork: Boolean,
    ): String? {
        val catalogFile = File(cacheDir, CACHED_CATALOG)
        if (forceNetwork) {
            val fresh = downloadText(REMOTE_CATALOG_URL)
            if (fresh != null) {
                catalogFile.writeText(fresh)
                downloadText(REMOTE_SHA256SUMS_URL)?.let {
                    File(cacheDir, CACHED_SHA).writeText(it)
                }
                lookupInCatalogJson(fresh, device, buildId)?.let { return it }
                return null
            }
            // 强制远端时网络失败：不读 APK hint 冒充远端成功
            return null
        }
        if (catalogFile.isFile) {
            lookupInCatalogJson(catalogFile.readText(), device, buildId)?.let { return it }
        }
        if (context != null) {
            return TempRootSoCatalog.resolveRemoteHint(context, device, buildId)
        }
        return null
    }

    internal fun lookupInCatalogJson(json: String, device: String, buildId: String): String? {
        return runCatching {
            val root = JSONObject(json)
            val devices = root.optJSONObject("devices") ?: return null
            val builds = devices.optJSONObject(device) ?: return null
            val raw = builds.optString(buildId, "").trim()
            if (raw.isEmpty()) return null
            normalizeRelativePath(raw)
        }.getOrNull()
    }

    private fun normalizeRelativePath(raw: String): String {
        val trimmed = raw.trim().removePrefix("./")
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("so/") -> trimmed
            trimmed.startsWith("temproot/") -> trimmed.removePrefix("temproot/")
            trimmed.endsWith(".so") && !trimmed.contains('/') -> {
                // 旧本地 catalog：仅文件名 → 推断 so/<BuildId>/ 需由调用方补；此处保留文件名
                trimmed
            }
            else -> trimmed
        }
    }

    private fun absoluteSoUrl(remotePath: String): String {
        if (remotePath.startsWith("https://", ignoreCase = true) ||
            remotePath.startsWith("http://", ignoreCase = true)
        ) {
            return remotePath
        }
        // 仅文件名时无法安全猜 Build 目录，拒绝（应由远端 catalog 给完整 so/... 路径）
        if (!remotePath.contains('/')) {
            return ""
        }
        return DEFAULT_BASE_URL.trimEnd('/') + "/" + remotePath.trimStart('/')
    }

    internal fun isAllowedSoUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return runCatching {
            val u = URL(url)
            if (!u.protocol.equals("https", ignoreCase = true)) return false
            if (!u.host.equals(ALLOWED_HOST, ignoreCase = true)) return false
            val path = u.path.orEmpty()
            if (!path.startsWith(ALLOWED_PATH_PREFIX)) return false
            if (!path.endsWith(".so")) return false
            if (".." in path.split('/')) return false
            true
        }.getOrDefault(false)
    }

    private fun lookupSha256(cacheDir: File, remotePath: String, fileName: String): String? {
        val shaFile = File(cacheDir, CACHED_SHA)
        if (!shaFile.isFile) return null
        val lines = shaFile.readLines()
        val needleA = remotePath.replace('\\', '/')
        val needleB = fileName
        for (line in lines) {
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) continue
            val hash = parts[0].lowercase()
            val name = parts[1].trim().removePrefix("./")
            if (name == needleA || name.endsWith("/$needleB") || name == needleB) {
                return hash
            }
        }
        return null
    }

    private fun downloadText(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                // 禁止重定向逃逸出白名单 host
                instanceFollowRedirects = false
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            Log.w(TAG, "GET text fail $url: ${t.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun downloadToFile(url: String, dest: File): Boolean {
        var conn: HttpURLConnection? = null
        val tmp = File(dest.parentFile, dest.name + ".part")
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = false
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return false
            conn.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (tmp.length() <= 0L) {
                tmp.delete()
                return false
            }
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
        } catch (t: Throwable) {
            Log.w(TAG, "GET file fail $url: ${t.message}")
            tmp.delete()
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }
}
