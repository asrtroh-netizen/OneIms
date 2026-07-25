package com.onetools.app.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedFormatTest {
    @Test
    fun formatsBytes() {
        assertEquals("512 B/s", SpeedFormat.formatRate(512))
        assertTrue(SpeedFormat.formatRate(2048).contains("KB"))
        assertTrue(SpeedFormat.formatRate(2 * 1024 * 1024).contains("MB"))
    }
}
