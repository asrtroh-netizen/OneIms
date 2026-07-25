package com.onetools.app.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterDynamicIconTest {
    @Test
    fun shortLabels() {
        assertEquals("0B", MeterDynamicIcon.shortLabel(-1))
        assertTrue(MeterDynamicIcon.shortLabel(2048).endsWith("K"))
        assertTrue(MeterDynamicIcon.shortLabel(2_000_000).contains("M"))
    }

    @Test
    fun formatValueUnitMatchesPixelMeterShape() {
        assertEquals("0" to "B/s", MeterDynamicIcon.formatValueUnit(0))
        assertEquals("2" to "KB/s", MeterDynamicIcon.formatValueUnit(2048))
        val (v, u) = MeterDynamicIcon.formatValueUnit(1_500_000)
        assertTrue(v.startsWith("1"))
        assertEquals("MB/s", u)
    }
}
