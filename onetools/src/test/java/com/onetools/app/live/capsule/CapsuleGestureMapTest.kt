package com.onetools.app.live.capsule

import org.junit.Assert.assertEquals
import org.junit.Test

class CapsuleGestureMapTest {
    @Test
    fun defaultsMatchLegacyOverlayBehavior() {
        assertEquals(CapsuleGestureAction.NONE, CapsuleGestureDefaults.actionFor(CapsuleGestureSlot.TAP))
        assertEquals(CapsuleGestureAction.EXPAND, CapsuleGestureDefaults.actionFor(CapsuleGestureSlot.SWIPE_DOWN))
        assertEquals(CapsuleGestureAction.COLLAPSE, CapsuleGestureDefaults.actionFor(CapsuleGestureSlot.SWIPE_UP))
        assertEquals(CapsuleGestureAction.TOGGLE_LONG, CapsuleGestureDefaults.actionFor(CapsuleGestureSlot.SWIPE_LEFT))
        assertEquals(CapsuleGestureAction.PREV, CapsuleGestureDefaults.actionFor(CapsuleGestureSlot.SWIPE_RIGHT))
    }

    @Test
    fun cycleWalksAllActions() {
        var current = CapsuleGestureAction.NONE
        val seen = mutableSetOf<CapsuleGestureAction>()
        repeat(CapsuleGestureAction.entries.size) {
            current = CapsuleGestureDefaults.cycle(current)
            seen += current
        }
        assertEquals(CapsuleGestureAction.entries.toSet(), seen)
    }

    @Test
    fun fromPrefFallsBackSafely() {
        assertEquals(
            CapsuleGestureAction.EXPAND,
            CapsuleGestureAction.fromPref("bogus", CapsuleGestureAction.EXPAND),
        )
        assertEquals(
            CapsuleGestureAction.NEXT,
            CapsuleGestureAction.fromPref("NEXT", CapsuleGestureAction.EXPAND),
        )
    }
}
