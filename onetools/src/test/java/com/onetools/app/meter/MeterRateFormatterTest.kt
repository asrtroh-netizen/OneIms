package com.onetools.app.meter

import org.junit.Assert.assertEquals
import org.junit.Test

class MeterRateFormatterTest {
    @Test
    fun formatsModes() {
        val base = MeterPrefsSnapshot(prefix = "One")
        assertEquals(
            "One ↓ 1.0 MB/s · ↑ 512 KB/s",
            MeterRateFormatter.format(
                base.copy(displayMode = MeterDisplayMode.BOTH),
                1_048_576,
                512 * 1024,
            ),
        )
        assertEquals(
            "One ↑ 512 KB/s · ↓ 1.0 MB/s",
            MeterRateFormatter.format(
                base.copy(
                    displayMode = MeterDisplayMode.BOTH,
                    speedOrder = MeterSpeedOrder.UP_THEN_DOWN,
                ),
                1_048_576,
                512 * 1024,
            ),
        )
