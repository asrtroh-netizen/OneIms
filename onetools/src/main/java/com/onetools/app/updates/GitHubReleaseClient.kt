package com.onetools.app.updates

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseAsset(
    val tag: String,
    val name: String,
    val downloadUrl: String,
    val size: Long,
)

object GitHubReleaseClient {
    fun latestAsset(app: TrackedApp): Result<ReleaseAsset> = runCatching {
        val api = "https://api.github.com/repos/${app.githubOwner}/${app.githubRepo}/releases/latest"
        val body = httpGet(api)
        val json = JSONObject(body)
        val tag = json.optString("tag_name", "?")
        val assets = json.getJSONArray("assets")
        val candidates = buildList {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.getString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                if (name.contains("fdroid", ignoreCase = true)) continue
                if (name.endsWith(".idsig") || name.contains(".sha256")) continue
                add(
                    ReleaseAsset(
                        tag = tag,
                        name = name,
                        downloadUrl = a.getString("browser_download_url"),
                        size = a.optLong("size", 0L),
                    ),
                )
            }
        }
        require(candidates.isNotEmpty()) { "No APK asset in $tag" }
        val preferred = app.assetPrefer.firstNotNullOfOrNull { prefer ->
            candidates.firstOrNull { it.name.contains(prefer, ignoreCase = true) }
        }
        preferred ?: candidates.first()
    }

    fun downloadToFile(url: String, dest: java.io.File, onProgress: ((Long, Long) -> Unit)? = null) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "OneTools-UpdateCenter")
            setRequestProperty("Accept", "application/octet-stream")
        }
        conn.inputStream.use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                var done = 0L
                val total = conn.contentLengthLong
                while (input.read(buffer).also { read = it } >= 0) {
                    output.write(buffer, 0, read)
                    done += read
                    onProgress?.invoke(done, total)
                }
            }
        }
        conn.disconnect()
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "OneTools-UpdateCenter")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }
}
