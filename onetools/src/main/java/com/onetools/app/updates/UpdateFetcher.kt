package com.onetools.app.updates

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object HttpDownloads {
    fun get(
        url: String,
        accept: String = "application/json",
        bearerToken: String? = null,
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "OneTools-UpdateCenter")
            setRequestProperty("Accept", accept)
            val token = bearerToken?.trim().orEmpty()
            if (token.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText().orEmpty()
            when (code) {
                401, 403 -> error("私有索引需要有效会员 Token (HTTP $code)")
                else -> {
                    require(code in 200..299) { "HTTP $code: ${text.take(140)}" }
                    text
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    fun status(url: String): Int {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "OneTools-UpdateCenter")
        }
        return try {
            conn.responseCode
        } finally {
            conn.disconnect()
        }
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
}

object UpdateFetcher {
    fun validate(
        app: TrackedApp,
        context: android.content.Context? = null,
        bearerToken: String? = null,
    ): Result<Unit> = when (app.source) {
        AppSource.GITHUB -> GitHubReleaseClient.validateRepo(app.githubOwner, app.githubRepo)
        AppSource.GITLAB -> GitLabReleaseClient.validate(app)
        AppSource.FORGEJO -> ForgejoReleaseClient.validate(app)
        AppSource.FDROID -> FDroidReleaseClient.validate(app)
        AppSource.DIRECT -> DirectApkClient.validate(app)
        AppSource.HTML -> HtmlApkClient.validate(app)
        AppSource.ONE_INDEX -> OneIndexClient.validate(app, context, bearerToken)
    }

    fun latestAsset(
        app: TrackedApp,
        abis: List<String>,
        context: android.content.Context? = null,
        bearerToken: String? = null,
    ): Result<ReleaseAsset> = when (app.source) {
        AppSource.GITHUB -> GitHubReleaseClient.latestAsset(app, abis)
        AppSource.GITLAB -> GitLabReleaseClient.latestAsset(app, abis)
        AppSource.FORGEJO -> ForgejoReleaseClient.latestAsset(app, abis)
        AppSource.FDROID -> FDroidReleaseClient.latestAsset(app, abis)
        AppSource.DIRECT -> DirectApkClient.latestAsset(app, abis)
        AppSource.HTML -> HtmlApkClient.latestAsset(app, abis)
        AppSource.ONE_INDEX -> OneIndexClient.latestAsset(app, abis, context, bearerToken)
    }

    fun downloadToFile(url: String, dest: java.io.File, onProgress: ((Long, Long) -> Unit)? = null) {
        HttpDownloads.downloadToFile(url, dest, onProgress)
    }
}

object GitLabReleaseClient {
    fun validate(app: TrackedApp): Result<Unit> = runCatching {
        val host = normalizeHost(app.host)
        val project = URLEncoder.encode("${app.githubOwner}/${app.githubRepo}", Charsets.UTF_8.name())
        val code = HttpDownloads.status("https://$host/api/v4/projects/$project")
        require(code == 200) { "GitLab 项目不可用 (HTTP $code)" }
    }

    fun latestAsset(app: TrackedApp, abis: List<String>): Result<ReleaseAsset> = runCatching {
        val host = normalizeHost(app.host)
        val project = URLEncoder.encode("${app.githubOwner}/${app.githubRepo}", Charsets.UTF_8.name())
        val body = HttpDownloads.get("https://$host/api/v4/projects/$project/releases")
        val arr = JSONArray(body)
        require(arr.length() > 0) { "No GitLab releases" }
        val release = arr.getJSONObject(0)
        val tag = release.optString("tag_name", release.optString("name", "?"))
        val notes = release.optString("description", "")
        val published = release.optString("released_at", "")
        val links = release.optJSONObject("assets")?.optJSONArray("links") ?: JSONArray()
        val candidates = buildList {
            for (i in 0 until links.length()) {
                val link = links.getJSONObject(i)
                val name = link.optString("name", link.optString("url").substringAfterLast('/'))
                val url = link.optString("direct_asset_url").ifBlank { link.optString("url") }
                if (!name.endsWith(".apk", ignoreCase = true) && !url.contains(".apk", ignoreCase = true)) continue
                add(
                    ReleaseAsset(
                        tag = tag,
                        name = name.ifBlank { url.substringAfterLast('/') },
                        downloadUrl = url,
                        size = 0L,
                        body = notes,
                        publishedAt = published,
                    ),
                )
            }
        }
        require(candidates.isNotEmpty()) { "No APK link in GitLab release $tag" }
        ApkAssetPicker.pick(candidates, app.assetPrefer, abis, app.apkRegex)
    }

    fun normalizeHost(host: String?): String {
        val raw = host?.trim().orEmpty().ifBlank { "gitlab.com" }
        return raw.removePrefix("https://").removePrefix("http://").trimEnd('/')
    }
}

object FDroidReleaseClient {
    fun validate(app: TrackedApp): Result<Unit> = runCatching {
        val pkg = app.packageName ?: app.githubRepo
        require(pkg.isNotBlank()) { "F-Droid 需要包名" }
        val base = normalizeBase(app.host)
        val code = HttpDownloads.status("$base/api/v1/packages/$pkg")
        require(code == 200) { "F-Droid 包不可用 (HTTP $code)" }
    }

    fun latestAsset(app: TrackedApp, abis: List<String>): Result<ReleaseAsset> = runCatching {
        val pkg = app.packageName ?: app.githubRepo
        require(pkg.isNotBlank()) { "F-Droid 需要包名" }
        val base = normalizeBase(app.host)
        val json = JSONObject(HttpDownloads.get("$base/api/v1/packages/$pkg"))
        val suggested = json.optLong("suggestedVersionCode", -1L)
        val packages = json.getJSONArray("packages")
        require(packages.length() > 0) { "No F-Droid packages for $pkg" }
        var chosen: JSONObject? = null
        for (i in 0 until packages.length()) {
            val p = packages.getJSONObject(i)
            if (suggested > 0 && p.optLong("versionCode") == suggested) {
                chosen = p
                break
            }
        }
        if (chosen == null) chosen = packages.getJSONObject(0)
        val apkName = chosen!!.getString("apkName")
        val versionName = chosen.optString("versionName", suggested.toString())
        val asset = ReleaseAsset(
            tag = versionName,
            name = apkName,
            downloadUrl = "$base/repo/$apkName",
            size = chosen.optLong("size", 0L),
            body = "F-Droid · versionCode=${chosen.optLong("versionCode")}",
            publishedAt = "",
        )
        // F-Droid usually one apk; still run prefer scoring for consistency.
        ApkAssetPicker.pick(listOf(asset), app.assetPrefer, abis, app.apkRegex)
    }

    fun normalizeBase(host: String?): String {
        val raw = host?.trim().orEmpty().ifBlank { "https://f-droid.org" }
        val withScheme = if (raw.startsWith("http")) raw else "https://$raw"
        return withScheme.trimEnd('/')
    }
}
