package com.onetools.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkAssetPickerTest {
    private fun asset(name: String) = ReleaseAsset(
        tag = "v1",
        name = name,
        downloadUrl = "https://example.com/$name",
        size = 1,
    )

    @Test
    fun prefersDeviceAbiOverWrongAbi() {
        val picked = ApkAssetPicker.pick(
            candidates = listOf(
                asset("app-armeabi-v7a-release.apk"),
                asset("app-arm64-v8a-release.apk"),
                asset("app-x86_64-release.apk"),
            ),
            prefer = listOf(".apk"),
            abis = listOf("arm64-v8a", "armeabi-v7a"),
        )
        assertEquals("app-arm64-v8a-release.apk", picked.name)
    }

    @Test
    fun preferTokenBreaksTieOnUniversal() {
        val picked = ApkAssetPicker.pick(
            candidates = listOf(
                asset("OneTools-debug.apk"),
                asset("OneTools-release.apk"),
            ),
            prefer = listOf("OneTools", "release"),
            abis = listOf("arm64-v8a"),
        )
        assertEquals("OneTools-release.apk", picked.name)
    }
}

class VersionCompareTest {
    @Test
    fun detectsUpdateAvailable() {
        assertEquals(
            VersionCompare.UpdateState.UPDATE_AVAILABLE,
            VersionCompare.state("1.2.0", "v1.3.0"),
        )
    }

    @Test
    fun detectsUpToDate() {
        assertEquals(
            VersionCompare.UpdateState.UP_TO_DATE,
            VersionCompare.state("3.0.2", "v3.0.2"),
        )
    }

    @Test
    fun notInstalled() {
        assertEquals(
            VersionCompare.UpdateState.NOT_INSTALLED,
            VersionCompare.state(null, "v1.0.0"),
        )
    }

    @Test
    fun tokenizeStripsPrefix() {
        assertEquals(listOf(2, 3, 1), VersionCompare.tokenize("v2.3.1-beta"))
    }
}

class CatalogExportTest {
    @Test
    fun roundTrip() {
        val apps = listOf(
            TrackedApp(
                id = "gh-a-b",
                title = "Demo",
                packageName = "com.demo",
                githubOwner = "a",
                githubRepo = "b",
                assetPrefer = listOf(".apk"),
                note = "n",
            ),
        )
        val json = CatalogExport.toJson(apps)
        val back = CatalogExport.fromJson(json)
        assertEquals(1, back.size)
        assertEquals("com.demo", back[0].packageName)
        assertEquals("a", back[0].githubOwner)
        assertTrue(json.contains("owner"))
    }
}
