package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SandboxPersistSupportTest {

    @Test
    fun attemptGates_requireEnabledAndProbeAllowed() {
        assertFalse(
            SandboxPersistSupport.evaluateAttemptGates(
                enabled = false,
                forceTemporary = false,
                isRootUid = false,
                probeOutcome = PersistentCapabilityProbe.Outcome.LIKELY_ALLOWED,
            ),
        )
        assertFalse(
            SandboxPersistSupport.evaluateAttemptGates(
                enabled = true,
                forceTemporary = true,
                isRootUid = false,
                probeOutcome = PersistentCapabilityProbe.Outcome.LIKELY_ALLOWED,
            ),
        )
        assertFalse(
            SandboxPersistSupport.evaluateAttemptGates(
                enabled = true,
                forceTemporary = false,
                isRootUid = true,
                probeOutcome = PersistentCapabilityProbe.Outcome.LIKELY_ALLOWED,
            ),
        )
        assertFalse(
            SandboxPersistSupport.evaluateAttemptGates(
                enabled = true,
                forceTemporary = false,
                isRootUid = false,
                probeOutcome = PersistentCapabilityProbe.Outcome.LIKELY_BLOCKED,
            ),
        )
        assertTrue(
            SandboxPersistSupport.evaluateAttemptGates(
                enabled = true,
                forceTemporary = false,
                isRootUid = false,
                probeOutcome = PersistentCapabilityProbe.Outcome.LIKELY_ALLOWED,
            ),
        )
    }

    @Test
    fun bridge_completeSignalsOutcome() {
        val latch = CountDownLatch(1)
        val outcome = AtomicReference<Boolean?>(null)
        SandboxPersistBridge.register(latch, outcome)
        try {
            SandboxPersistBridge.complete(true)
            assertTrue(latch.await(1, TimeUnit.SECONDS))
            assertEquals(true, outcome.get())
        } finally {
            SandboxPersistBridge.clear()
        }
    }
}
