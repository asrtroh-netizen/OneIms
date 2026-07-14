package com.oneims.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
