package com.onetools.app.updates

/**
 * Preset sources for first launch (Obtainium-like starter list).
 */
object TrackedApps {
    val presets: List<TrackedApp> = listOf(
        TrackedApp(
            id = "oneims-onekuku",
            title = "OneIms · OneKuku",
            packageName = "com.oneims.app",
            githubOwner = "asrtroh-netizen",
            githubRepo = "OneIms",
            assetPrefer = listOf("OneIms-OneKuku-standalone", "OneIms-OneKuku", ".apk"),
            note = "预设 · 独立激活线",
            source = AppSource.GITHUB,
        ),
        TrackedApp(
            id = "oneims-lite",
            title = "OneIms · Lite",
            packageName = "com.oneims.onelink",
            githubOwner = "asrtroh-netizen",
            githubRepo = "OneIms",
            assetPrefer = listOf("OneIms-Lite-Shizuku", "OneIms-OneLink", ".apk"),
            note = "预设 · Shizuku 线",
            source = AppSource.GITHUB,
        ),
        TrackedApp(
            id = "shizuku-asrtroh",
            title = "Shizuku（asrtroh）",
            packageName = "moe.shizuku.privileged.api",
            githubOwner = "asrtroh-netizen",
            githubRepo = "shizuku",
            assetPrefer = listOf(".apk"),
            note = "预设 · 通道依赖",
            source = AppSource.GITHUB,
        ),
        TrackedApp(
            id = "onetools",
            title = "OneTools",
            packageName = "com.onetools.app",
            githubOwner = "asrtroh-netizen",
            githubRepo = "OneIms",
            assetPrefer = listOf("OneTools", "onetools", ".apk"),
            note = "预设 · 本应用（需 Release 挂资产）",
            source = AppSource.GITHUB,
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
    if (pkg.isNullOrBlank() || pkg == packageName) this else copy(packageName = pkg)
