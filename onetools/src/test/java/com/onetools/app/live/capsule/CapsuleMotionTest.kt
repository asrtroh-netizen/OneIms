package com.onetools.app.live.capsule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleMotionTest {
    @Test
    fun expandCurveEndpoints() {
        assertEquals(0f, CapsuleMotion.expandCurveY(0f), 1e-3f)
        assertEquals(1f, CapsuleMotion.expandCurveY(1f), 1e-3f)
    }

    @Test
    fun collapseCurveEndpoints() {
        assertEquals(0f, CapsuleMotion.collapseCurveY(0f), 1e-3f)
        assertEquals(1f, CapsuleMotion.collapseCurveY(1f), 1e-3f)
    }

    @Test
    fun expandCurveIsMonotonicRising() {
        var prev = CapsuleMotion.expandCurveY(0f)
        for (i in 1..20) {
            val y = CapsuleMotion.expandCurveY(i / 20f)
            assertTrue("t=${i / 20f} y=$y prev=$prev", y + 1e-4f >= prev)
            prev = y
        }
    }

    @Test
    fun durationsMatchLearnedRhythm() {
        assertEquals(300, CapsuleMotion.EXPAND_MS)
        assertEquals(260, CapsuleMotion.COLLAPSE_MS)
        assertEquals(1.08f, CapsuleMotion.PILL_OVERSHOOT, 0f)
    }
}
