package com.onetools.app.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficVolumeFormatTest {
    @Test
    fun formatsVolumes() {
        assertEquals("512 B", TrafficVolumeFormat.formatBytes(512))
        assertTrue(TrafficVolumeFormat.formatBytes(2048).contains("KB"))
        assertTrue(TrafficVolumeFormat.formatBytes(2 * 1024 * 1024).contains("MB"))
        assertTrue(TrafficVolumeFormat.formatBytes(3L * 1024 * 1024 * 1024).contains("GB"))
        assertEquals("0 B", TrafficVolumeFormat.formatBytes(-1))
    }
}

class TrafficWindowTest {
    @Test
    fun todayStartsAtLocalMidnight() {
        val noon = TrafficWindow.startOfLocalDay(1_700_000_000_000L) + 12L * 60L * 60L * 1000L
        val (start, end) = TrafficWindow.range(TrafficPeriod.TODAY, noon)
        assertEquals(TrafficWindow.startOfLocalDay(noon), start)
        assertEquals(noon, end)
    }

    @Test
    fun weekAndMonthWindows() {
        val now = 1_700_100_000_000L
        val week = TrafficWindow.range(TrafficPeriod.DAYS_7, now)
        assertEquals(now - 7L * 24 * 3600 * 1000, week.first)
        assertEquals(now, week.second)
        val month = TrafficWindow.range(TrafficPeriod.DAYS_30, now)
        assertEquals(now - 30L * 24 * 3600 * 1000, month.first)
    }
}
