package com.onetools.app.meter

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Status-bar Live Update chip text — aligned with Pixel Meter
 * [NetworkRepository.formatSpeedTextForLiveUpdate] (Apache-2.0).
 */
object MeterChipFormat {
    fun format(prefs: MeterPrefsSnapshot, downBytesPerSec: Long, upBytesPerSec: Long): String {
        val bytes = when (prefs.displayMode) {
            MeterDisplayMode.UP -> upBytesPerSec
            MeterDisplayMode.DOWN -> downBytesPerSec
            MeterDisplayMode.TOTAL, MeterDisplayMode.BOTH ->
                downBytesPerSec + upBytesPerSec
        }
        return when (prefs.rateUnit) {
            MeterRateUnit.BITS_PER_SEC -> formatLiveBits(bytes.coerceAtLeast(0L) * 8L)
            MeterRateUnit.BYTES_PER_SEC -> formatLiveBytes(bytes)
        }
    }

    /** Pixel Meter live chip: `187B/s`, `12K/s`, `1.2M/s`. */
    fun formatLiveBytes(bytesPerSec: Long): String {
        val bytes = bytesPerSec.coerceAtLeast(0L)
        if (bytes < 1024L) return "${bytes}B/s"
        val kb = bytes / 1024.0
        if (kb < 1000) return "${"%.0f".format(Locale.US, kb)}K/s"
        val mb = kb / 1024.0
        if (mb < 1000) {
            return if (mb < 100) "${"%.1f".format(Locale.US, mb)}M/s"
            else "${"%.0f".format(Locale.US, mb)}M/s"
        }
        val gb = mb / 1024.0
        return "${"%.1f".format(Locale.US, gb)}G/s"
    }

    fun formatLiveBits(bitsPerSec: Long): String {
        if (bitsPerSec < 0L) return "0b/s"
        val kb = bitsPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 10 -> "${mb.roundToInt()}Mb/s"
            mb >= 1 -> String.format(Locale.US, "%.1fMb/s", mb)
            kb >= 1 -> "${kb.roundToInt()}Kb/s"
            else -> "${bitsPerSec}b/s"
        }
    }

    fun shortBits(bitsPerSec: Long): String = formatLiveBits(bitsPerSec).removeSuffix("/s")
}
