package com.onetools.app.updates

/**
 * Catalog of apps the built-in update center can check / install.
 * Obtainium itself is offered as an official APK install (GPL-3 separate program).
 */
data class TrackedApp(
    val id: String,
    val title: String,
    val packageName: String?,
    val githubOwner: String,
    val githubRepo: String,
    val assetPrefer: List<String>,
    val licenseNote: String,
)

object TrackedApps {
    val all: List<TrackedApp> = listOf(
        TrackedApp(
            id = "obtainium",
            title = "Obtainium",
            packageName = "dev.imranr.obtainium",
            githubOwner = "ImranR98",
            githubRepo = "Obtainium",
            assetPrefer = listOf(
                "app-arm64-v8a-release.apk",
                "app-release.apk",
            ),
            licenseNote = "GPL-3.0 · https://github.com/ImranR98/Obtainium",
        ),
        TrackedApp(
            id = "oneims",
            title = "OneIms (Release)",
            packageName = "com.oneims.app",
            githubOwner = "asrtroh-netizen",
            githubRepo = "OneIms",
            assetPrefer = listOf(
                "OneIms-OneKuku-standalone",
                "OneIms-Lite-Shizuku",
                ".apk",
            ),
            licenseNote = "OneIMS distribution · README assets",
        ),
    )
}
