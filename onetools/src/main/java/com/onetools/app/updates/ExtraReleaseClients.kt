package com.onetools.app.updates

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/** Forgejo / Gitea / Codeberg Releases API (clean-room). */
object ForgejoReleaseClient {
    fun validate(app: TrackedApp): Result<Unit> = runCatching {
        val host = app.host?.trim()?.removePrefix("https://")?.removePrefix("http://")
            ?.trimEnd('/')
            ?: "codeberg.org"
        val code = HttpDownloads.status(
            "https://$host/api/v1/repos/${app.githubOwner}/${app.githubRepo}",
        )
        require(code == 200) { "Forgejo 仓库不可用 (HTTP $code)" }
    }

    fun latestAsset(app: TrackedApp, abis: List<String>): Result<ReleaseAsset> = runCatching {
        val host = app.host?.trim()?.removePrefix("https://")?.removePrefix("http://")
            ?.trimEnd('/')
            ?: "codeberg.org"
        val api =
            "https://$host/api/v1/repos/${app.githubOwner}/${app.githubRepo}/releases?limit=20"
        val arr = JSONArray(HttpDownloads.get(api))
        require(arr.length() > 0) { "No Forgejo releases" }
        val candidates = buildList {
            for (i in 0 until arr.length()) {
                val rel = arr.getJSONObject(i)
                if (rel.optBoolean("draft", false)) continue
                if (!app.includePrereleases && rel.optBoolean("prerelease", false)) continue
                val tag = rel.optString("tag_name", "?")
                val notes = rel.optString("body", "")
                val published = rel.optString("published_at", "")
                val assets = rel.optJSONArray("assets") ?: JSONArray()
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    val name = a.optString("name")
                    if (!name.endsWith(".apk", ignoreCase = true)) continue
                    val url = a.optString("browser_download_url")
                        .ifBlank { a.optString("download_url") }
                    if (url.isBlank()) continue
                    add(
                        ReleaseAsset(
                            tag = tag,
                            name = name,
                            downloadUrl = url,
                            size = a.optLong("size", 0L),
                            body = notes,
                            publishedAt = published,
                        ),
                    )
                }
                if (isNotEmpty()) break
            }
        }
        require(candidates.isNotEmpty()) { "No APK in Forgejo releases" }
        ApkAssetPicker.pick(candidates, app.assetPrefer, abis, app.apkRegex)
    }
}

/** Single APK URL source. */
object DirectApkClient {
    fun validate(app: TrackedApp): Result<Unit> = runCatching {
        val url = resolveUrl(app)
        val code = HttpDownloads.status(url)
        require(code in 200..399) { "Direct APK 不可用 (HTTP $code)" }
    }

    fun latestAsset(app: TrackedApp, abis: List<String>): Result<ReleaseAsset> = runCatching {
        val url = resolveUrl(app)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            instanceFollowRedirects = true
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "OneTools-UpdateCenter")
        }
        try {
            val code = conn.responseCode
            require(code in 200..399) { "Direct APK HTTP $code" }
            val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "app.apk" }
            val etag = conn.getHeaderField("ETag")?.trim('"').orEmpty()
            val lastMod = conn.getHeaderField("Last-Modified").orEmpty()
            val len = conn.contentLengthLong.coerceAtLeast(0L)
            val tag = when {
                etag.isNotBlank() -> "etag-${etag.take(16)}"
                lastMod.isNotBlank() -> "lm-${lastMod.hashCode().toUInt().toString(16)}"
                else -> "len-$len"
            }
            val asset = ReleaseAsset(
                tag = tag,
                name = name,
                downloadUrl = url,
                size = len,
                body = "Direct APK",
                publishedAt = lastMod,
            )
            ApkAssetPicker.pick(listOf(asset), app.assetPrefer, abis, app.apkRegex)
        } finally {
            conn.disconnect()
        }
    }

    private fun resolveUrl(app: TrackedApp): String {
        val url = app.host?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: app.githubRepo.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: error("Direct 源需要 https://…apk URL")
        require(url.contains(".apk", ignoreCase = true)) { "Direct URL 应指向 .apk" }
        return url
    }
}

/** HTML page fallback: collect .apk hrefs (clean-room scraper). */
object HtmlApkClient {
    private val hrefPattern = Pattern.compile(
        """href\s*=\s*["']([^"']+\.apk(?:\?[^"']*)?)["']""",
        Pattern.CASE_INSENSITIVE,
    )

    fun validate(app: TrackedApp): Result<Unit> = runCatching {
        val page = pageUrl(app)
        val code = HttpDownloads.status(page)
        require(code in 200..399) { "HTML 页不可用 (HTTP $code)" }
    }

    fun latestAsset(app: TrackedApp, abis: List<String>): Result<ReleaseAsset> = runCatching {
        val page = pageUrl(app)
        val html = HttpDownloads.get(page, accept = "text/html,application/xhtml+xml,*/*")
        val links = linkedApks(html, page)
        require(links.isNotEmpty()) { "页面未找到 .apk 链接" }
        val candidates = links.mapIndexed { index, url ->
            val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "app-$index.apk" }
            ReleaseAsset(
                tag = "html-${name.hashCode().toUInt().toString(16)}",
                name = name,
                downloadUrl = url,
                size = 0L,
                body = "HTML · $page",
            )
        }
        ApkAssetPicker.pick(candidates, app.assetPrefer, abis, app.apkRegex)
    }

    fun linkedApks(html: String, pageUrl: String): List<String> {
        val matcher = hrefPattern.matcher(html)
        val out = linkedSetOf<String>()
        while (matcher.find()) {
            val raw = matcher.group(1) ?: continue
            out += absolutize(pageUrl, raw)
        }
        return out.toList()
    }

    private fun pageUrl(app: TrackedApp): String {
        val url = app.host?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: app.githubRepo.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: error("HTML 源需要页面 URL")
        return url
    }

    private fun absolutize(page: String, href: String): String {
        if (href.startsWith("http", ignoreCase = true)) return href
        if (href.startsWith("//")) {
            val scheme = page.substringBefore("://").ifBlank { "https" }
            return "$scheme:$href"
        }
        val base = page.substringBeforeLast('/') + "/"
        return if (href.startsWith("/")) {
            val origin = Regex("""https?://[^/]+""").find(page)?.value ?: return href
            origin + href
        } else {
            base + href
        }
    }
}
