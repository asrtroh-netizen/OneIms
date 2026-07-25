package com.onebattery.app.battery

/**
 * Clean-room capacity / health math (public method described by AccuBattery docs:
 * mAh added ÷ percent gained × 100). Original implementation — not a port of their code.
 */
object BatteryCapacityEstimator {
    const val MIN_PERCENT_DELTA = 20

    /** Estimate full-battery mAh from one charge sample. */
    fun estimateFullMah(
        startPercent: Int,
        endPercent: Int,
        startChargeMah: Int,
        endChargeMah: Int,
    ): Int? {
        val dPct = endPercent - startPercent
        val dMah = endChargeMah - startChargeMah
        if (dPct < MIN_PERCENT_DELTA) return null
        if (startChargeMah < 0 || endChargeMah < 0 || dMah <= 0) return null
        return ((dMah.toDouble() * 100.0) / dPct).toInt().coerceAtLeast(1)
    }

    fun averageMah(samples: List<Int>): Int? {
        if (samples.isEmpty()) return null
        return samples.average().toInt().coerceAtLeast(1)
    }

    /** Health% = estimated ÷ design × 100. Caps display at 150 for noisy early samples. */
    fun healthPercent(estimatedMah: Int, designMah: Int): Int? {
        if (estimatedMah <= 0 || designMah <= 0) return null
        return ((estimatedMah.toDouble() * 100.0) / designMah).toInt().coerceIn(1, 150)
    }
}
