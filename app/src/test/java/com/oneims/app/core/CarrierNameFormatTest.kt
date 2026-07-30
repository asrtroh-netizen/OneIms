package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CarrierNameFormatTest {

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
}
