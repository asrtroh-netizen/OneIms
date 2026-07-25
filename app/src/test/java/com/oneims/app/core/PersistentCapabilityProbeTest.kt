package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PersistentCapabilityProbeTest {

    @Test
    fun decide_oldPlatformWithoutIsSystemApp_isAllowed() {
        val outcome = PersistentCapabilityProbe.decide(
            PersistentCapabilityProbe.Signals(
                hasIsSystemApp = false,
                hasSecureOverrideConfig = false,
                hasIsSdkSandboxUidInternal = false,
            ),
        )
        assertEquals(PersistentCapabilityProbe.Outcome.LIKELY_ALLOWED, outcome)
    }

    @Test
    fun decide_newPlatformWithSandboxUidCheck_isBlocked() {
        val outcome = PersistentCapabilityProbe.decide(
            PersistentCapabilityProbe.Signals(
                hasIsSystemApp = true,
                hasSecureOverrideConfig = true,
                hasIsSdkSandboxUidInternal = true,
            ),
        )
        assertEquals(PersistentCapabilityProbe.Outcome.LIKELY_BLOCKED, outcome)
    }

    @Test
    fun decide_hasIsSystemAppButNoSandboxUidCheck_isAllowed() {
        val outcome = PersistentCapabilityProbe.decide(
            PersistentCapabilityProbe.Signals(
                hasIsSystemApp = true,
                hasSecureOverrideConfig = true,
                hasIsSdkSandboxUidInternal = false,
            ),
        )
        assertEquals(PersistentCapabilityProbe.Outcome.LIKELY_ALLOWED, outcome)
    }

    @Test
    fun decide_missingSecureOverride_isUnknown() {
        val outcome = PersistentCapabilityProbe.decide(
            PersistentCapabilityProbe.Signals(
                hasIsSystemApp = true,
                hasSecureOverrideConfig = false,
                hasIsSdkSandboxUidInternal = false,
            ),
        )
        assertEquals(PersistentCapabilityProbe.Outcome.UNKNOWN, outcome)
    }
}
