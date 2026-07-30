package com.oneims.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemDeviceCompatTest {

    @Test
    fun xiaomiFamily_detectsCommonBrands() {
        assertTrue(OemDeviceCompat.isXiaomiFamily("Xiaomi", "Redmi"))
        assertTrue(OemDeviceCompat.isXiaomiFamily("Xiaomi", "POCO"))
        assertTrue(OemDeviceCompat.isXiaomiFamily("xiaomi", "xiaomi"))
        assertTrue(OemDeviceCompat.isXiaomiFamily("Redmi", "redmi"))
    }

    @Test
    fun xiaomiFamily_rejectsGooglePixel() {
        assertFalse(OemDeviceCompat.isXiaomiFamily("Google", "google"))
        assertFalse(OemDeviceCompat.isXiaomiFamily("OnePlus", "OnePlus"))
        assertFalse(OemDeviceCompat.isXiaomiFamily("samsung", "samsung"))
    }
}
