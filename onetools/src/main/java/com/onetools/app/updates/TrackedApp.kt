package com.onetools.app.updates

/**
 * One 生态自主更新目录（不依赖 Obtainium）。
 */
data class TrackedApp(
    val id: String,
    val title: String,
    val packageName: String?,
    val githubOwner: String,
    val githubRepo: String,
    val assetPrefer: List<String>,
    val note: String,
)

object TrackedApps {
    val all: List<TrackedApp> = listOf(
        TrackedApp(
            id = "onetools",
            title = "OneTools",
            packageName = "com.onetools.app",
            githubOwner = "asrtroh-netizen",
            githubRepo = "OneIms",
            assetPrefer = listOf("OneTools", "onetools", ".apk"),
            note = "本应用 · 若 Release 尚无独立资产则检查失败属预期",
        ),
        TrackedApp(
            id = "oneims-onekuku",
            title = "OneIms · OneKuku",
            packageName = "com.oneims.app",
            githubOwner = "asrtroh-netizen",
            githubRepo = "OneIms",
            assetPrefer = listOf(
                "OneIms-OneKuku-standalone",
                "OneIms-OneKuku",
                ".apk",
            ),
            note = "独立激活线",
        ),
        TrackedApp(
            id = "oneims-lite",
            title = "OneIms · Lite (Shizuku)",
            packageName = "com.oneims.onelink",
            githubOwner = "asrtroh-netizen",
            githubRepo = "OneIms",
            assetPrefer = listOf(
                "OneIms-Lite-Shizuku",
                "OneIms-OneLink",
                ".apk",
            ),
            note = "Shizuku 轻壳线",
        ),
        TrackedApp(
            id = "shizuku-asrtroh",
            title = "Shizuku（asrtroh 修缮）",
            packageName = "moe.shizuku.privileged.api",
            githubOwner = "asrtroh-netizen",
            githubRepo = "shizuku",
            assetPrefer = listOf(".apk"),
            note = "通道依赖 · 官方包名可能相同",
        ),
    )
}

object InstalledVersions {
    fun versionName(context: android.content.Context, packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull()
    }
}
