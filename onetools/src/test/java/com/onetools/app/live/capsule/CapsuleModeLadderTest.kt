package com.onetools.app.live.capsule

import org.junit.Assert.assertEquals
import org.junit.Test

class CapsuleModeLadderTest {
    @Test
    fun stepUpClimbsToExpanded() {
        assertEquals(
            CapsuleDisplayMode.MINI,
            CapsuleModeLadder.stepUp(CapsuleDisplayMode.DOT),
        )
        assertEquals(
            CapsuleDisplayMode.COMPACT,
            CapsuleModeLadder.stepUp(CapsuleDisplayMode.MINI),
        )
        assertEquals(
            CapsuleDisplayMode.EXPANDED,
            CapsuleModeLadder.stepUp(CapsuleDisplayMode.COMPACT),
        )
        assertEquals(
            CapsuleDisplayMode.EXPANDED,
            CapsuleModeLadder.stepUp(CapsuleDisplayMode.EXPANDED),
        )
    }

    @Test
    fun stepDownRespectsFloor() {
        assertEquals(
            CapsuleDisplayMode.MINI,
            CapsuleModeLadder.stepDown(CapsuleDisplayMode.COMPACT, CapsuleDisplayMode.MINI),
        )
        assertEquals(
            CapsuleDisplayMode.MINI,
            CapsuleModeLadder.stepDown(CapsuleDisplayMode.MINI, CapsuleDisplayMode.MINI),
        )
        assertEquals(
            CapsuleDisplayMode.DOT,
            CapsuleModeLadder.stepDown(CapsuleDisplayMode.MINI, CapsuleDisplayMode.DOT),
        )
    }
}
