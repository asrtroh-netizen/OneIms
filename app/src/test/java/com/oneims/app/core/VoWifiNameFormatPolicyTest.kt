package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoWifiNameFormatPolicyTest {

    @Test
    fun preview_mapsAllSupportedFormats() {
        val carrier = "China Unicom"
        val expected = listOf(
            "China Unicom",
            "China Unicom Wi-Fi Calling",
            "WLAN Call",
            "China Unicom WLAN Call",
            "China Unicom Wi-Fi",
            "WiFi Calling | China Unicom",
            "China Unicom VoWifi",
            "Wi-Fi Calling",
            "Wi-Fi",
            "WiFi Calling",
            "VoWifi",
            "China Unicom WiFi Calling",
            "WiFi Call",
        )

        expected.forEachIndexed { index, value ->
            assertEquals(
                value,
                VoWifiNameFormatPolicy.preview(index, carrier, ""),
            )
        }
        assertEquals(carrier, VoWifiNameFormatPolicy.preview(null, carrier, ""))
    }

    @Test
    fun preview_prefersValidatedCustomCarrierName() {
        assertEquals(
            "OneIMS VoWifi",
            VoWifiNameFormatPolicy.preview(6, "China Unicom", " OneIMS "),
        )
    }

    @Test
    fun followSystemPreview_ignoresStaleCustomCarrierName() {
        assertEquals(
            "China Unicom",
            VoWifiNameFormatPolicy.preview(null, "China Unicom", "OneIMS"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun indexPolicy_rejectsOutOfRangeIndex() {
        VoWifiNameFormatPolicy.requireValidIndex(13)
    }

    @Test(expected = IllegalArgumentException::class)
    fun carrierPolicy_rejectsControlCharacters() {
        VoWifiNameFormatPolicy.normalizeCarrierName("CMCC\nInjected")
    }

    @Test
    fun ownershipPolicy_comparesEveryManagedValue() {
        val ours = VoWifiFormatValues(6, 6, 6, true)
        assertTrue(VoWifiNameFormatPolicy.formatMatches(ours, ours.copy()))
        assertFalse(
            VoWifiNameFormatPolicy.formatMatches(
                ours,
                ours.copy(dataIndex = 5),
            ),
        )
    }
}
