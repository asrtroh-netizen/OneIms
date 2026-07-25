package com.onetools.app.updates

import org.json.JSONArray
import org.json.JSONObject

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
        val code = HttpDownloads.status("https://api.github.com/repos/$owner/$repo")
        require(code == 200) { "GitHub 仓库不可用 (HTTP $code)" }
    }

    fun latestAsset(app: TrackedApp, abis: List<String>): Result<ReleaseAsset> = runCatching {
        val candidates = if (app.includePrereleases) {
            collectFromList(app)
        } else {
            collectFromLatest(app).ifEmpty { collectFromList(app) }
        }
        require(candidates.isNotEmpty()) { "No APK asset" }
        ApkAssetPicker.pick(candidates, app.assetPrefer, abis, app.apkRegex)
    }

    private fun collectFromLatest(app: TrackedApp): List<ReleaseAsset> {
        val api =
            "https://api.github.com/repos/${app.githubOwner}/${app.githubRepo}/releases/latest"
        return runCatching {
            val json = JSONObject(
                HttpDownloads.get(api, accept = "application/vnd.github+json"),
            )
            assetsFromRelease(json)
        }.getOrDefault(emptyList())
    }

    private fun collectFromList(app: TrackedApp): List<ReleaseAsset> {
        val api =
            "https://api.github.com/repos/${app.githubOwner}/${app.githubRepo}/releases?per_page=20"
        val arr = JSONArray(
            HttpDownloads.get(api, accept = "application/vnd.github+json"),
        )
        val out = ArrayList<ReleaseAsset>()
        for (i in 0 until arr.length()) {
            val rel = arr.getJSONObject(i)
            if (rel.optBoolean("draft", false)) continue
            if (!app.includePrereleases && rel.optBoolean("prerelease", false)) continue
            out += assetsFromRelease(rel)
            if (out.isNotEmpty()) break
        }
        return out
    }

    private fun assetsFromRelease(json: JSONObject): List<ReleaseAsset> {
        val tag = json.optString("tag_name", "?")
        val notes = json.optString("body", "").orEmpty()
        val published = json.optString("published_at", "").orEmpty()
        val assets = json.optJSONArray("assets") ?: return emptyList()
        return buildList {
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
    }
}

object CatalogExport {
    fun toJson(apps: List<TrackedApp>): String {
        val arr = JSONArray()
        apps.forEach { app ->
            arr.put(trackedToJson(app))
        }
        return arr.toString(2)
    }

    fun fromJson(raw: String): List<TrackedApp> {
        val arr = JSONArray(raw.trim())
        return buildList {
            for (i in 0 until arr.length()) {
                decodeTracked(arr.getJSONObject(i))?.let { add(it) }
            }
        }
    }

    fun trackedToJson(app: TrackedApp): JSONObject =
        JSONObject()
            .put("id", app.id)
            .put("title", app.title)
            .put("packageName", app.packageName)
            .put("owner", app.githubOwner)
            .put("repo", app.githubRepo)
            .put("prefer", JSONArray(app.assetPrefer))
            .put("note", app.note)
            .put("source", app.source.name)
            .put("host", app.host)
            .put("apkRegex", app.apkRegex)
            .put("includePrereleases", app.includePrereleases)
            .put("trackUpdates", app.trackUpdates)

    fun decodeTracked(o: JSONObject): TrackedApp? = runCatching {
        val prefer = o.optJSONArray("prefer")
        val list = buildList {
            if (prefer != null) {
                for (j in 0 until prefer.length()) add(prefer.getString(j))
            }
        }.ifEmpty { listOf(".apk") }
        TrackedApp(
            id = o.optString("id").ifBlank {
                "src-${o.optString("owner")}-${o.optString("repo")}".lowercase()
            },
            title = o.optString("title").ifBlank { o.optString("repo") },
            packageName = o.optString("packageName").takeIf { it.isNotBlank() && it != "null" },
            githubOwner = o.optString("owner"),
            githubRepo = o.optString("repo").ifBlank { o.optString("packageName") },
            assetPrefer = list,
            note = o.optString("note", "imported"),
            source = runCatching {
                AppSource.valueOf(o.optString("source", AppSource.GITHUB.name))
            }.getOrDefault(AppSource.GITHUB),
            host = o.optString("host").takeIf { it.isNotBlank() && it != "null" },
            apkRegex = o.optString("apkRegex").takeIf { it.isNotBlank() && it != "null" },
            includePrereleases = o.optBoolean("includePrereleases", false),
            trackUpdates = o.optBoolean("trackUpdates", true),
        )
    }.getOrNull()
}
