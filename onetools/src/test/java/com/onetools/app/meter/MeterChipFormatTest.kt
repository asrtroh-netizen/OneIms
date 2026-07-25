package com.onetools.app.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterChipFormatTest {
    @Test
    fun liveBytesMatchesPixelMeterShape() {
        assertEquals("187B/s", MeterChipFormat.formatLiveBytes(187))
        assertEquals("2K/s", MeterChipFormat.formatLiveBytes(2048))
        assertTrue(MeterChipFormat.formatLiveBytes(1_500_000).endsWith("M/s"))
    }

    @Test
    fun bitsUnitUsesBitSuffix() {
        val prefs = MeterPrefsSnapshot(
            displayMode = MeterDisplayMode.TOTAL,
            rateUnit = MeterRateUnit.BITS_PER_SEC,
        )
        val chip = MeterChipFormat.format(prefs, 128 * 1024, 128 * 1024)
        assertTrue(chip.contains("b") || chip.contains("Mb") || chip.contains("Kb"))
    }

    @Test
    fun upModeUsesUpload() {
        val prefs = MeterPrefsSnapshot(displayMode = MeterDisplayMode.UP)
        assertEquals(
            MeterChipFormat.formatLiveBytes(4096),
            MeterChipFormat.format(prefs, 1, 4096),
        )
    }
}
