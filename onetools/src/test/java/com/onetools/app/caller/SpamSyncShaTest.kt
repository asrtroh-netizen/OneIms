package com.onetools.app.caller

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class SpamSyncShaTest {
    @Test
    fun sha256_knownVector() {
        val f = Files.createTempFile("onespam-sha", ".bin").toFile()
        try {
            f.writeText("OneTools-spam-pack")
            assertEquals(
                "dc94613a793849cd5e196fa3efae414e3c7c0380a2784ca80bc3d5890e01340f",
                SpamSyncRepository.sha256Hex(f),
            )
        } finally {
            f.delete()
        }
    }
}
