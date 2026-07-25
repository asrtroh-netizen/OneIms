package com.onebattery.app.battery

/**
 * Clean-room discharge attribution helpers (AccuBattery-like: measure controller delta,
 * attribute to foreground app). Not a port of AccuBattery code.
 */
object BatteryDrainMath {
    /** mAh consumed when charge counter drops. */
    fun mahFromCounter(prevMah: Int, nowMah: Int): Double {
        if (prevMah < 0 || nowMah < 0) return 0.0
        val d = prevMah - nowMah
        return if (d > 0) d.toDouble() else 0.0
    }

    /** Fallback when counter unavailable: percent drop × design capacity. */
    fun mahFromPercent(prevPct: Int, nowPct: Int, designMah: Int): Double {
        if (designMah <= 0) return 0.0
        val d = prevPct - nowPct
        if (d <= 0) return 0.0
        return designMah.toDouble() * d / 100.0
    }

    fun pickMahDelta(
        prevMah: Int,
        nowMah: Int,
        prevPct: Int,
        nowPct: Int,
        designMah: Int,
    ): Double {
        val fromCounter = mahFromCounter(prevMah, nowMah)
        if (fromCounter > 0) return fromCounter
        return mahFromPercent(prevPct, nowPct, designMah)
    }

    /** Estimated minutes remaining at current drain rate (mAh/hour). */
    fun remainingMinutes(
        percent: Int,
        designMah: Int,
        mahPerHour: Double,
    ): Int? {
        if (percent <= 0 || designMah <= 0 || mahPerHour <= 0.05) return null
        val leftMah = designMah.toDouble() * percent / 100.0
        return ((leftMah / mahPerHour) * 60.0).toInt().coerceAtLeast(1)
    }
}
