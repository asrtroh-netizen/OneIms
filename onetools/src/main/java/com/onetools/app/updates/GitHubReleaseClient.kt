package com.onetools.app.updates

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseAsset(
    val tag: String,
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val body: String = "",
    val publishedAt: String = "",
)

object GitHubReleaseClient {
    fun validateRepo(owner: String, repo: String): Result<Unit> = runCatching {
        val api = "https://api.github.com/repos/$owner/$repo"
        val code = httpStatus(api)
        require(code == 200) { "GitHub 仓库不可用 (HTTP $code)" }
    }

    fun latestAsset(app: TrackedApp, abis: List<String>): Result<ReleaseAsset> = runCatching {
        val api = "https://api.github.com/repos/${app.githubOwner}/${app.githubRepo}/releases/latest"
        val body = httpGet(api)
        val json = JSONObject(body)
        val tag = json.optString("tag_name", "?")
        val notes = json.optString("body", "").orEmpty()
        val published = json.optString("published_at", "").orEmpty()
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
                        body = notes,
                        publishedAt = published,
                    ),
                )
            }
        }
        require(candidates.isNotEmpty()) { "No APK asset in $tag" }
        ApkAssetPicker.pick(candidates, app.assetPrefer, abis)
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
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText().orEmpty()
            require(code in 200..299) { "GitHub HTTP $code: ${text.take(120)}" }
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun httpStatus(url: String): Int {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "OneTools-UpdateCenter")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }
}

object CatalogExport {
    fun toJson(apps: List<TrackedApp>): String {
        val arr = JSONArray()
        apps.forEach { app ->
            arr.put(
                JSONObject()
                    .put("id", app.id)
                    .put("title", app.title)
                    .put("packageName", app.packageName)
                    .put("owner", app.githubOwner)
                    .put("repo", app.githubRepo)
                    .put("prefer", JSONArray(app.assetPrefer))
                    .put("note", app.note),
            )
        }
        return arr.toString(2)
    }

    fun fromJson(raw: String): List<TrackedApp> {
        val arr = JSONArray(raw.trim())
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val prefer = o.optJSONArray("prefer")
                val list = buildList {
                    if (prefer != null) {
                        for (j in 0 until prefer.length()) add(prefer.getString(j))
                    }
                }.ifEmpty { listOf(".apk") }
                add(
                    TrackedApp(
                        id = o.optString("id").ifBlank {
                            "gh-${o.getString("owner")}-${o.getString("repo")}".lowercase()
                        },
                        title = o.optString("title").ifBlank { o.getString("repo") },
                        packageName = o.optString("packageName").takeIf { it.isNotBlank() && it != "null" },
                        githubOwner = o.getString("owner"),
                        githubRepo = o.getString("repo"),
                        assetPrefer = list,
                        note = o.optString("note", "imported"),
                    ),
                )
            }
        }
    }
}
