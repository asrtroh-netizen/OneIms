package com.oneims.app.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSimSwitchManagerTest {

    @Test
    fun formatCarrierShortName_mapsKnownCarriers() {
        assertEquals("CMCC", formatCarrierShortName("China Mobile"))
        assertEquals("CMCC", formatCarrierShortName("中国移动"))
        assertEquals("CU", formatCarrierShortName("China Unicom"))
        assertEquals("CU", formatCarrierShortName("中国联通"))
        assertEquals("CT", formatCarrierShortName("China Telecom"))
        assertEquals("CT", formatCarrierShortName("中国电信"))
        assertEquals("CMHK", formatCarrierShortName("CMHK 4G"))
    }

    @Test
    fun formatCarrierShortName_handlesEmptyAndLongNames() {
        assertEquals("—", formatCarrierShortName(null))
        assertEquals("—", formatCarrierShortName("   "))
        assertEquals("VeryLong", formatCarrierShortName("VeryLongCarrierName"))
    }

    @Test
    fun mobileDataRecovery_onlyRestoresKnownOnToOffTransition() {
        assertTrue(DataSimSwitchManagerImpl.shouldRestoreMobileData(true, false))
        assertFalse(DataSimSwitchManagerImpl.shouldRestoreMobileData(true, true))
        assertFalse(DataSimSwitchManagerImpl.shouldRestoreMobileData(false, false))
        assertFalse(DataSimSwitchManagerImpl.shouldRestoreMobileData(false, true))
        assertFalse(DataSimSwitchManagerImpl.shouldRestoreMobileData(null, false))
        assertFalse(DataSimSwitchManagerImpl.shouldRestoreMobileData(true, null))
    }

    @Test
    fun defaultDataSubId_fallsBackWhenHiddenApiReturnsInvalid() {
        assertEquals(23, chooseDefaultDataSubId(-1, 23))
        assertEquals(17, chooseDefaultDataSubId(17, 23))
        assertEquals(-1, chooseDefaultDataSubId(-1, -1))
    }

    @Test
    fun mobileDataWarning_reportsUnknownAndRestoreFailure() {
        assertEquals(
            DataSimSwitchManagerImpl.MobileDataWarning.STATE_UNKNOWN,
            DataSimSwitchManagerImpl.mobileDataWarning(
                before = null,
                after = false,
                restoreAttempted = false,
                restoreSucceeded = null,
            ),
        )
        assertEquals(
            DataSimSwitchManagerImpl.MobileDataWarning.RESTORE_FAILED,
            DataSimSwitchManagerImpl.mobileDataWarning(
                before = true,
                after = false,
                restoreAttempted = true,
                restoreSucceeded = false,
            ),
        )
        assertEquals(
            null,
            DataSimSwitchManagerImpl.mobileDataWarning(
                before = true,
                after = true,
                restoreAttempted = false,
                restoreSucceeded = null,
            ),
        )
    }

    @Test
    fun processScopedRunner_continuesAfterAwaitingCallerIsCancelled() = runBlocking {
        val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val runner = ProcessScopedOperationRunner(processScope)
            val started = CompletableDeferred<Unit>()
            val completed = CompletableDeferred<Unit>()
            val caller = launch {
                runner.run {
                    started.complete(Unit)
                    delay(50)
                    completed.complete(Unit)
                }
            }

            started.await()
            caller.cancelAndJoin()
            withTimeout(1_000) { completed.await() }
        } finally {
            processScope.cancel()
        }
    }
}
