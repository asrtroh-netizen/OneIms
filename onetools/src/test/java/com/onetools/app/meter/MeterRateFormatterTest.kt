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
            "One " + SpeedFormat.formatRate(1_048_576 + 1024),
            MeterRateFormatter.format(
                base.copy(displayMode = MeterDisplayMode.TOTAL),
                1_048_576,
                1024,
            ),
        )
    }
}
