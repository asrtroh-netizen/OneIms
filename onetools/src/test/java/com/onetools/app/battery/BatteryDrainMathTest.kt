package com.onetools.app.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryDrainMathTest {
    @Test
    fun mahFromCounter_andPercentFallback() {
        assertEquals(100.0, BatteryDrainMath.mahFromCounter(2000, 1900), 0.01)
        assertEquals(0.0, BatteryDrainMath.mahFromCounter(1900, 2000), 0.01)
        assertEquals(450.0, BatteryDrainMath.mahFromPercent(90, 80, 4500), 0.01)
    }

    @Test
    fun pickMahDelta_prefersCounter() {
        val v = BatteryDrainMath.pickMahDelta(
            prevMah = 2000,
            nowMah = 1900,
            prevPct = 90,
            nowPct = 80,
            designMah = 4500,
        )
        assertEquals(100.0, v, 0.01)
    }

    @Test
    fun remainingMinutes_basic() {
        val m = BatteryDrainMath.remainingMinutes(50, 4500, 450.0)
        assertTrue(m != null && m!! > 0)
        assertNull(BatteryDrainMath.remainingMinutes(50, 4500, 0.0))
    }
}
