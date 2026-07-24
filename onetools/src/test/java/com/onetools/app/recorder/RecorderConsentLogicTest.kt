package com.onetools.app.recorder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorderConsentLogicTest {
    @Test
    fun callPhaseDistinct() {
        assertTrue(CallPhase.OFFHOOK != CallPhase.IDLE)
        assertFalse(CallPhase.RINGING == CallPhase.OFFHOOK)
    }
}
