package com.oneims.app.core

import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierConfigOverrideWriterTest {

    @Test(expected = IllegalArgumentException::class)
    fun requireValidSubIdRejectsNegative() {
        CarrierConfigOverrideWriter.requireValidSubId(-1)
    }

    @Test
    fun requireValidSubIdAcceptsPositive() {
        CarrierConfigOverrideWriter.requireValidSubId(2)
        assertTrue(true)
    }
}
