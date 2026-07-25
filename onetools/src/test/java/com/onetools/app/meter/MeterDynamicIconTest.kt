package com.onetools.app.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterDynamicIconTest {
    @Test
    fun shortLabels() {
        assertEquals("0", MeterDynamicIcon.shortLabel(-1))
        assertTrue(MeterDynamicIcon.shortLabel(2048).endsWith("K"))
        assertTrue(MeterDynamicIcon.shortLabel(2_000_000).contains("M"))
    }
}
