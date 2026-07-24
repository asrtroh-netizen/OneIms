package com.onetools.app.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterChipFormatTest {
    @Test
    fun chipStaysWithinSevenChars() {
        val prefs = MeterPrefsSnapshot(displayMode = MeterDisplayMode.DOWN)
        val chip = MeterChipFormat.format(prefs, 2 * 1024 * 1024, 0)
        assertTrue(chip.length <= 7)
        assertTrue(chip.contains("M"))
    }

    @Test
    fun bitsUnitUsesBitSuffix() {
        val prefs = MeterPrefsSnapshot(
            displayMode = MeterDisplayMode.TOTAL,
            rateUnit = MeterRateUnit.BITS_PER_SEC,
        )
        val chip = MeterChipFormat.format(prefs, 128 * 1024, 128 * 1024)
        assertTrue(chip.length <= 7)
        assertTrue(chip.endsWith("b") || chip.contains("Mb") || chip.contains("Kb"))
    }

    @Test
    fun upModeUsesUpload() {
        val prefs = MeterPrefsSnapshot(displayMode = MeterDisplayMode.UP)
        assertEquals(
            MeterDynamicIcon.shortLabel(4096),
            MeterChipFormat.format(prefs, 1, 4096),
        )
    }
}
