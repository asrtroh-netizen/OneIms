package com.onetools.app.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterRateFormatterTest {
    @Test
    fun formatsModesAndUnits() {
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
        val bits = MeterRateFormatter.format(
            base.copy(displayMode = MeterDisplayMode.DOWN, rateUnit = MeterRateUnit.BITS_PER_SEC),
            1024,
            0,
        )
        assertTrue(bits.contains("Kbit/s") || bits.contains("bit/s"))
        assertEquals(
            "One " + SpeedFormat.formatRate(1_048_576 + 1024),
            MeterRateFormatter.format(
                base.copy(displayMode = MeterDisplayMode.TOTAL),
                1_048_576,
                1024,
            ),
        )
    }
}
