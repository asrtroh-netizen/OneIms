package com.onetools.app.caller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamSyncManifestTest {
    @Test
    fun parse_teloShapedHasUpdate() {
        val raw = """
            {
              "has_update": true,
              "latest_version": "20260725",
              "download_url": "https://cdn.example/onespam.zip",
              "size_bytes": 42,
              "checksum": "abc",
              "row_count": 9
            }
        """.trimIndent()
        val m = SpamSyncManifest.parse(raw, "20260101")
        assertTrue(m.hasUpdate)
        assertEquals("20260725", m.latestVersion)
        assertEquals("https://cdn.example/onespam.zip", m.downloadUrl)
        assertEquals(42L, m.sizeBytes)
        assertEquals("abc", m.checksum)
        assertEquals(9L, m.rowCount)
    }

    @Test
    fun parse_infersHasUpdateFromVersion() {
        val raw = """
            {
              "latest_version": "v2",
              "download_url": "https://x/a.zip",
              "checksum": "deadbeef"
            }
        """.trimIndent()
        assertTrue(SpamSyncManifest.parse(raw, "v1").hasUpdate)
        assertFalse(SpamSyncManifest.parse(raw, "v2").hasUpdate)
    }
}
