package com.oneims.app.core

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 临时 Root so 获取：本地 assets 优先，缺则从公开 [OneSo-assets] 拉 catalog + so。
 *
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

    /** IO 线程调用：点一键时确保本机有可 stage 的 so。 */
    fun ensure(context: Context): Ready? {
        val device = TempRootSoCatalog.currentDevice()
        val buildId = TempRootSoCatalog.currentBuildId()
        if (device.isBlank() || buildId.isBlank()) return null

        resolveLocalAsset(context, device, buildId)?.let { assetPath ->
            Log.i(TAG, "use asset $assetPath")
            return Ready(device, buildId, SoSource.Asset(assetPath))
        }

        val cacheDir = File(context.filesDir, CACHE_DIR).also { it.mkdirs() }
        val remotePath = resolveRemoteRelativePath(context, cacheDir, device, buildId)
            ?: run {
                Log.w(TAG, "no catalog entry device=$device build=$buildId")
                return null
            }
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
        val out = File(cacheDir, fileName)
        val expectedSha = lookupSha256(cacheDir, remotePath, fileName)

        if (out.isFile && out.length() > 0L) {
            if (expectedSha == null || sha256Hex(out) == expectedSha) {
                Log.i(TAG, "use cache ${out.name}")
                return Ready(device, buildId, SoSource.CachedFile(out, fetchedRemote = false))
            }
            Log.w(TAG, "cache sha mismatch, re-download ${out.name}")
            out.delete()
        }

        if (!downloadToFile(url, out)) {
            Log.w(TAG, "download failed $url")
            return null
        }
        if (expectedSha != null && sha256Hex(out) != expectedSha) {
            Log.w(TAG, "downloaded sha mismatch ${out.name}")
            out.delete()
            return null
        }
        Log.i(TAG, "downloaded ${out.name} bytes=${out.length()}")
        return Ready(device, buildId, SoSource.CachedFile(out, fetchedRemote = true))
    }

    fun isLikelySupported(context: Context): Boolean {
        val device = TempRootSoCatalog.currentDevice()
        val buildId = TempRootSoCatalog.currentBuildId()
        if (device.isBlank() || buildId.isBlank()) return false
        if (resolveLocalAsset(context, device, buildId) != null) return true
        val cacheDir = File(context.filesDir, CACHE_DIR)
        val cached = File(cacheDir, CACHED_CATALOG)
        if (cached.isFile) {
            lookupInCatalogJson(cached.readText(), device, buildId)?.let { return true }
        }
        // 无缓存时仍允许点按钮去拉远端（点时再判定 UnsupportedDevice）
        return true
    }

    private fun resolveLocalAsset(context: Context, device: String, buildId: String): String? {
        val match = TempRootSoCatalog.resolve(context) ?: return null
        if (match.device != device || match.buildId != buildId) return null
        val exists = runCatching {
            context.assets.open(match.assetPath).use { it.available() >= 0 }
        }.getOrDefault(false)
        return match.assetPath.takeIf { exists }
    }

    private fun resolveRemoteRelativePath(
        context: Context,
        cacheDir: File,
        device: String,
        buildId: String,
    ): String? {
        val catalogFile = File(cacheDir, CACHED_CATALOG)
        // 每次一点都尽量刷新远端 catalog（失败则用缓存）
        val fresh = downloadText(REMOTE_CATALOG_URL)
        if (fresh != null) {
            catalogFile.writeText(fresh)
            downloadText(REMOTE_SHA256SUMS_URL)?.let { File(cacheDir, CACHED_SHA).writeText(it) }
            lookupInCatalogJson(fresh, device, buildId)?.let { return it }
        } else if (catalogFile.isFile) {
            lookupInCatalogJson(catalogFile.readText(), device, buildId)?.let { return it }
        }
        // 最后看 APK 内 catalog 是否给出可拼远端的相对路径
        return TempRootSoCatalog.resolveRemoteHint(context, device, buildId)
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
                instanceFollowRedirects = true
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
                instanceFollowRedirects = true
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
