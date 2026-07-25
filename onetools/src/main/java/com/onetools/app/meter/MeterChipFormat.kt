package com.onetools.app.meter

import kotlin.math.roundToInt

/**
 * Compact text for Android 16 status-bar notification chips (OEM-like).
 * Suggested platform max ≈ 7 characters — keep labels short.
 */
object MeterChipFormat {
    fun format(prefs: MeterPrefsSnapshot, downBytesPerSec: Long, upBytesPerSec: Long): String {
        // BOTH → total (Pixel Meter live-update / bitmap total path).
        val bytes = when (prefs.displayMode) {
            MeterDisplayMode.UP -> upBytesPerSec
            MeterDisplayMode.DOWN -> downBytesPerSec
            MeterDisplayMode.TOTAL, MeterDisplayMode.BOTH ->
                downBytesPerSec + upBytesPerSec
        }
        val label = when (prefs.rateUnit) {
            MeterRateUnit.BITS_PER_SEC -> shortBits(bytes.coerceAtLeast(0L) * 8L)
            MeterRateUnit.BYTES_PER_SEC -> MeterDynamicIcon.shortLabel(bytes)
        }
        return label.take(7)
    }

    fun shortBits(bitsPerSec: Long): String {
        if (bitsPerSec < 0L) return "0"
        val kb = bitsPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 10 -> "${mb.roundToInt()}Mb"
            mb >= 1 -> String.format("%.1fMb", mb)
            kb >= 1 -> "${kb.roundToInt()}Kb"
            else -> "${bitsPerSec}b"
        }.take(7)
    }
}
