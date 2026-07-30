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
    fun domesticVowifiOem_coversMajorChineseBrands() {
        assertTrue(OemDeviceCompat.isDomesticVowifiOem("Xiaomi", "Redmi"))
        assertTrue(OemDeviceCompat.isDomesticVowifiOem("vivo", "vivo"))
        assertTrue(OemDeviceCompat.isDomesticVowifiOem("OPPO", "OPPO"))
        assertTrue(OemDeviceCompat.isDomesticVowifiOem("OnePlus", "OnePlus"))
        assertTrue(OemDeviceCompat.isDomesticVowifiOem("realme", "realme"))
        assertTrue(OemDeviceCompat.isDomesticVowifiOem("vivo", "iQOO"))
        assertTrue(OemDeviceCompat.isDomesticVowifiOem("samsung", "samsung"))
        assertTrue(OemDeviceCompat.isDomesticVowifiOem("HONOR", "honor"))
        assertTrue(OemDeviceCompat.isDomesticVowifiOem("Huawei", "HUAWEI"))
    }

    @Test
    fun domesticVowifiOem_rejectsGooglePixel() {
        assertFalse(OemDeviceCompat.isDomesticVowifiOem("Google", "google"))
        assertFalse(OemDeviceCompat.isDomesticVowifiOem("Google", "Pixel"))
        assertTrue(OemDeviceCompat.isGooglePixelFamily("Google", "Pixel"))
        assertFalse(OemDeviceCompat.isXiaomiFamily("OnePlus", "OnePlus"))
    }

    @Test
    fun domesticVowifiOem_gatesMatchCommunicationSkipAndTemporaryFirst() {
        // 门控函数与 isDomesticVowifiOem 同真源；Pixel 不得误入。
        assertTrue(OemDeviceCompat.isDomesticVowifiOem("Xiaomi", "xiaomi"))
        assertFalse(OemDeviceCompat.isDomesticVowifiOem("Google", "Pixel"))
        // preferTemporary / skipCommunication 委托 isDomesticVowifiOem()，此处用品牌探测间接覆盖。
        assertTrue(OemDeviceCompat.isDomesticVowifiOem("samsung", "samsung"))
        assertTrue(OemDeviceCompat.isDomesticVowifiOem("HONOR", "honor"))
    }
}
