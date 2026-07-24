package com.onetools.app.updates

import android.content.Context
import com.onetools.app.BuildConfig
import org.json.JSONObject

/**
 * OneTools proprietary update index — clean-room schema owned by One.
 * Supports ECDSA signature verification and optional Bearer membership token.
 */
object OneIndexClient {
    const val SCHEMA = "onetools.update.v1"

    fun validate(
        app: TrackedApp,
        context: Context? = null,
        bearerToken: String? = null,
    ): Result<Unit> = runCatching {
        val url = requireNotNull(app.host?.takeIf { it.isNotBlank() }) { "One Index 需要索引 URL" }
        val raw = HttpDownloads.get(url, bearerToken = bearerToken)
        val json = JSONObject(raw)
        require(json.optString("schema") == SCHEMA) {
            "不支持的 schema: ${json.optString("schema")}（需要 $SCHEMA）"
        }
        require(json.optJSONArray("apps") != null) { "索引缺少 apps[]" }
        if (context != null) {
            val requireSig = BuildConfig.ONE_INDEX_REQUIRE_SIGNATURE ||
                json.optString("auth").equals("bearer", ignoreCase = true) ||
                json.optBoolean("requireSignature", false)
            OneIndexVerifier.verify(context, json, requireSig).getOrThrow()
        }
        if (json.optString("auth").equals("bearer", ignoreCase = true)) {
            require(!bearerToken.isNullOrBlank()) { "该索引要求会员 Token" }
        }
    }

    fun latestAsset(
        app: TrackedApp,
        abis: List<String>,
        context: Context? = null,
        bearerToken: String? = null,
    ): Result<ReleaseAsset> = runCatching {
        val url = requireNotNull(app.host?.takeIf { it.isNotBlank() }) { "One Index 需要索引 URL" }
        val json = JSONObject(HttpDownloads.get(url, bearerToken = bearerToken))
        require(json.optString("schema") == SCHEMA) { "schema 必须是 $SCHEMA" }
        if (context != null) {
            val requireSig = BuildConfig.ONE_INDEX_REQUIRE_SIGNATURE ||
                json.optBoolean("requireSignature", false)
            OneIndexVerifier.verify(context, json, requireSig).getOrThrow()
        }
        if (json.optString("auth").equals("bearer", ignoreCase = true)) {
            require(!bearerToken.isNullOrBlank()) { "该索引要求会员 Token" }
        }
        val apps = json.getJSONArray("apps")
        val targetId = app.githubRepo.ifBlank { app.id.removePrefix("one-") }
        val targetPkg = app.packageName
        var matched: JSONObject? = null
        for (i in 0 until apps.length()) {
            val item = apps.getJSONObject(i)
            val id = item.optString("id")
            val pkg = item.optString("packageName")
            if (id == targetId || id.equals(app.githubRepo, ignoreCase = true) ||
                (!targetPkg.isNullOrBlank() && pkg == targetPkg) ||
                "one-$id".equals(app.id, ignoreCase = true)
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
                        note = "One Index · $SCHEMA · CDN",
                        source = AppSource.ONE_INDEX,
                        host = indexUrl,
                    ),
                )
            }
        }
    }
}
