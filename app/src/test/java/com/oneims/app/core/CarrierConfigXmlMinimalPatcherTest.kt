package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierConfigXmlMinimalPatcherTest {

    @Test
    fun patch_insertsMinimalKeysBeforeBundleClose() {
        val original = """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <bundle>
            <boolean name="carrier_volte_provisioned_bool" value="false" />
            </bundle>
        """.trimIndent()

        val patched = CarrierConfigXmlMinimalPatcher.patch(original)

        assertTrue(patched.contains("""name="carrier_volte_available_bool" value="true""""))
        assertTrue(patched.contains("""name="vonr_enabled_bool" value="true""""))
        assertTrue(patched.contains("carrier_nr_availabilities_int_array"))
        assertEquals(1, Regex("</bundle>").findAll(patched).count())
    }

    @Test
    fun patch_updatesExistingBooleanInPlace() {
        val original = """
            <bundle>
            <boolean name="carrier_volte_available_bool" value="false" />
            </bundle>
        """.trimIndent()

        val patched = CarrierConfigXmlMinimalPatcher.patch(original)

        assertTrue(patched.contains("""name="carrier_volte_available_bool" value="true""""))
        assertEquals(1, Regex("carrier_volte_available_bool").findAll(patched).count())
    }

    @Test
    fun patch_rejectsMultipleBundleClosings() {
        val original = "<bundle></bundle></bundle>"
        assertEquals(original, CarrierConfigXmlMinimalPatcher.patch(original))
    }

    @Test
    fun patch_addsDisplayNameKeysWhenCarrierProvided() {
        val original = """
            <bundle>
            <boolean name="carrier_volte_provisioned_bool" value="false" />
            </bundle>
        """.trimIndent()

        val patched = CarrierConfigXmlMinimalPatcher.patch(original, displayCarrierName = "中国电信")

        assertTrue(patched.contains("""name="carrier_name_override_bool" value="true""""))
        assertTrue(patched.contains("<string name=\"carrier_name_string\">中国电信</string>"))
        assertTrue(patched.contains("""name="spn_display_condition_override_int" value="2""""))
    }
}
