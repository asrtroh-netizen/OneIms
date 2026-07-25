package com.onetools.app.updates

import com.onetools.app.BuildConfig

/**
 * Preset sources — One 生态默认挂自有 CDN One Index（可签名 / 可会员 Token）。
 */
object TrackedApps {
    private val cdn: String get() = BuildConfig.ONE_CDN_INDEX_URL

    val presets: List<TrackedApp> = listOf(
        TrackedApp(
            id = "one-oneims-onekuku",
            title = "OneIms · OneKuku",
            packageName = "com.oneims.app",
            githubOwner = "one-index",
            githubRepo = "oneims-onekuku",
            assetPrefer = listOf(".apk"),
            note = "One CDN · 独立激活线",
            source = AppSource.ONE_INDEX,
            host = cdn,
        ),
        TrackedApp(
            id = "one-oneims-lite",
            title = "OneIms · Lite",
            packageName = "com.oneims.onelink",
            githubOwner = "one-index",
            githubRepo = "oneims-lite",
            assetPrefer = listOf(".apk"),
            note = "One CDN · Shizuku 线",
            source = AppSource.ONE_INDEX,
            host = cdn,
        ),
        TrackedApp(
            id = "one-shizuku-asrtroh",
            title = "Shizuku（asrtroh）",
            packageName = "moe.shizuku.privileged.api",
            githubOwner = "one-index",
            githubRepo = "shizuku-asrtroh",
            assetPrefer = listOf(".apk"),
            note = "One CDN · 通道依赖",
            source = AppSource.ONE_INDEX,
            host = cdn,
        ),
        TrackedApp(
            id = "one-onetools",
            title = "OneTools",
            packageName = "com.onetools.app",
            githubOwner = "one-index",
            githubRepo = "onetools",
            assetPrefer = listOf(".apk"),
            note = "One CDN · 本应用",
            source = AppSource.ONE_INDEX,
            host = cdn,
        ),
    )
}

data class TrackedApp(
    val id: String,
    val title: String,
    val packageName: String?,
    val githubOwner: String,
    val githubRepo: String,
    val assetPrefer: List<String>,
    val note: String,
    val source: AppSource = AppSource.GITHUB,
    val host: String? = null,
    /** Optional regex matched against APK file name. */
    val apkRegex: String? = null,
    val includePrereleases: Boolean = false,
    val trackUpdates: Boolean = true,
)

object InstalledVersions {
    fun versionName(context: android.content.Context, packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull()
    }
}

fun TrackedApp.withPackageName(pkg: String?): TrackedApp =
    if (pkg.isNullOrBlank()) this else copy(packageName = pkg)
