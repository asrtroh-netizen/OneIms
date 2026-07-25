package com.onetools.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlApkClientTest {
    @Test
    fun linkedApks_resolvesRelative() {
        val html = """
            <a href="/files/app-arm64.apk">dl</a>
            <a href="https://cdn.example/x.apk">abs</a>
        """.trimIndent()
        val links = HtmlApkClient.linkedApks(html, "https://example.com/app/")
        assertTrue(links.any { it.endsWith("app-arm64.apk") })
        assertTrue(links.any { it.contains("cdn.example") })
    }

    @Test
    fun apkRegex_filters() {
        val cands = listOf(
            ReleaseAsset("1", "app-arm64-v8a.apk", "u1", 1),
            ReleaseAsset("1", "app-x86.apk", "u2", 1),
        )
        val filtered = ApkAssetPicker.filterByRegex(cands, "arm64")
        assertEquals(1, filtered.size)
        assertEquals("app-arm64-v8a.apk", filtered[0].name)
    }
}
