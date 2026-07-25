package com.onetools.app.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryCapacityEstimatorTest {
    @Test
    fun estimateFullMah_basic() {
        // +40% and +1800 mAh → full ≈ 4500
        assertEquals(
            4500,
            BatteryCapacityEstimator.estimateFullMah(20, 60, 1000, 2800),
        )
    }

    @Test
    fun estimateFullMah_rejectsSmallDelta() {
        assertNull(BatteryCapacityEstimator.estimateFullMah(50, 60, 1000, 1500))
    }

    @Test
    fun healthPercent_ratio() {
        assertEquals(90, BatteryCapacityEstimator.healthPercent(4050, 4500))
    }

    @Test
    fun averageMah() {
        assertEquals(4500, BatteryCapacityEstimator.averageMah(listOf(4400, 4600)))
        assertNull(BatteryCapacityEstimator.averageMah(emptyList()))
    }
}
