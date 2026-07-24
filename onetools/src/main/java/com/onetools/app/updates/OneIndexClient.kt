package com.onetools.app.updates

import org.json.JSONObject

/**
 * OneTools proprietary update index — clean-room schema owned by One.
 *
 * Example:
 * ```json
 * {
 *   "schema": "onetools.update.v1",
 *   "generatedAt": "2026-07-25T00:00:00Z",
 *   "apps": [{
 *     "id": "demo",
 *     "title": "Demo",
 *     "packageName": "com.example.demo",
 *     "versionName": "1.0.0",
 *     "versionCode": 1,
 *     "apkUrl": "https://cdn.example/demo.apk",
 *     "apkName": "demo.apk",
 *     "changelog": "first",
 *     "sha256": ""
 *   }]
 * }
 * ```
 */
object OneIndexClient {
    const val SCHEMA = "onetools.update.v1"

    fun validate(app: TrackedApp): Result<Unit> = runCatching {
        val url = requireNotNull(app.host?.takeIf { it.isNotBlank() }) { "One Index 需要索引 URL" }
        val json = JSONObject(HttpDownloads.get(url))
        val schema = json.optString("schema")
        require(schema == SCHEMA) { "不支持的 schema: $schema（需要 $SCHEMA）" }
        require(json.optJSONArray("apps") != null) { "索引缺少 apps[]" }
    }

    fun latestAsset(app: TrackedApp, abis: List<String>): Result<ReleaseAsset> = runCatching {
        val url = requireNotNull(app.host?.takeIf { it.isNotBlank() }) { "One Index 需要索引 URL" }
        val json = JSONObject(HttpDownloads.get(url))
        require(json.optString("schema") == SCHEMA) { "schema 必须是 $SCHEMA" }
        val apps = json.getJSONArray("apps")
        val targetId = app.githubRepo.ifBlank { app.id }
        val targetPkg = app.packageName
        var matched: JSONObject? = null
        for (i in 0 until apps.length()) {
            val item = apps.getJSONObject(i)
            val id = item.optString("id")
            val pkg = item.optString("packageName")
            if (id == targetId || (!targetPkg.isNullOrBlank() && pkg == targetPkg) ||
                id.equals(app.id, ignoreCase = true)
            ) {
                matched = item
                break
            }
        }
        require(matched != null) { "索引中未找到 ${app.title} ($targetId)" }
        val apkUrl = matched!!.getString("apkUrl")
        val apkName = matched.optString("apkName").ifBlank { apkUrl.substringAfterLast('/') }
        val versionName = matched.optString("versionName", matched.optString("version", "?"))
        val changelog = matched.optString("changelog", matched.optString("body", ""))
        val asset = ReleaseAsset(
            tag = versionName,
            name = apkName,
            downloadUrl = apkUrl,
            size = matched.optLong("size", 0L),
            body = buildString {
                append(changelog)
                val sha = matched.optString("sha256")
                if (sha.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("sha256: ")
                    append(sha)
                }
            },
            publishedAt = json.optString("generatedAt", ""),
        )
        ApkAssetPicker.pick(listOf(asset), app.assetPrefer, abis)
    }

    /** Parse a local/remote index document into TrackedApp stubs (for import). */
    fun appsFromIndexJson(raw: String, indexUrl: String): List<TrackedApp> {
        val json = JSONObject(raw)
        require(json.optString("schema") == SCHEMA) { "schema 必须是 $SCHEMA" }
        val apps = json.getJSONArray("apps")
        return buildList {
            for (i in 0 until apps.length()) {
                val item = apps.getJSONObject(i)
                val id = item.optString("id").ifBlank { item.optString("packageName") }
                require(id.isNotBlank()) { "apps[].id 缺失" }
                add(
                    TrackedApp(
                        id = "one-$id".lowercase(),
                        title = item.optString("title").ifBlank { id },
                        packageName = item.optString("packageName").takeIf { it.isNotBlank() },
                        githubOwner = "one-index",
                        githubRepo = id,
                        assetPrefer = listOf(".apk"),
                        note = "One Index · $SCHEMA",
                        source = AppSource.ONE_INDEX,
                        host = indexUrl,
                    ),
                )
            }
        }
    }
}
