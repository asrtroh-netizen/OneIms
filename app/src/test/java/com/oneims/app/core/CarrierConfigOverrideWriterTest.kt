package com.oneims.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class CarrierConfigOverrideWriterTest {

    @Test
    fun detectsPersistentSystemAppDenial() {
        val security = SecurityException(
            "overrideConfig with persistent=true only can be invoked by system app",
        )
        assertTrue(CarrierConfigOverrideWriter.isPersistentPrivilegeDenied(security))
        assertTrue(
            CarrierConfigOverrideWriter.isPersistentPrivilegeDenied(
                InvocationTargetException(security),
            ),
        )
        assertFalse(
            CarrierConfigOverrideWriter.isPersistentPrivilegeDenied(
                IllegalStateException("readback mismatch"),
            ),
        )
    }

    @Test
    fun rethrowsBrokerPreflightSoCallerCanSkipRollback() {
        val preflight = BrokerExecutionException(
            "ActivityManager rejected BrokerInstrumentation",
            operationStarted = false,
        )
        try {
            CarrierConfigOverrideWriter.rethrowIfWriteNeverStarted(preflight)
            fail("expected BrokerExecutionException")
        } catch (error: BrokerExecutionException) {
            assertSame(preflight, error)
            assertFalse(error.operationStarted)
        }
    }

    @Test
    fun doesNotRethrowAfterWriteMayHaveStarted() {
        val started = BrokerExecutionException("readback timed out", operationStarted = true)
        CarrierConfigOverrideWriter.rethrowIfWriteNeverStarted(started)
        CarrierConfigOverrideWriter.rethrowIfWriteNeverStarted(
            IllegalStateException("partial/fail"),
        )
    }
}
