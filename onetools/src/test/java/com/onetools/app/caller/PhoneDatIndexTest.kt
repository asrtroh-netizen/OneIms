package com.onetools.app.caller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PhoneDatIndexTest {
    @Test
    fun fullGeoDatResolvesKnownPrefix() {
        val dat = File("src/main/assets/caller/geo.dat")
        assertTrue("geo.dat missing — run download into assets/caller/geo.dat", dat.isFile)
        val bytes = dat.readBytes()
        assertTrue(bytes.size > 1_000_000)
        assertEquals("2302", PhoneDatIndex.versionLabel(bytes))
        // Well-known Beijing mobile prefix present in 2302 dataset.
        val hit = PhoneDatIndex.lookup(bytes, "13800138000")
        assertNotNull(hit)
        assertTrue(hit!!.province.isNotBlank() || hit.city.isNotBlank())
        assertTrue(hit.dialerLine().isNotBlank())
        // 1860000 is Beijing in the 2302 MIT dataset (area 010).
        val bj = PhoneDatIndex.lookup(bytes, "18600001234")
        assertNotNull(bj)
        assertTrue(bj!!.dialerLine().contains("北京") || bj.province.contains("北京"))
    }
}
