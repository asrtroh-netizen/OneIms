package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningWritePolicyTest {

    @Test
    fun classify_fullOk() {
        val outcome = ProvisioningWritePolicy.classifyApplyOutcome(
            mapOf(
                "carrier_config_override" to true,
                "provision_vowifi" to true,
                "provision_vowifi_roaming" to true,
                "provision_wfc_mode" to true,
            ),
            xiaomiFamily = false,
        )
        assertEquals(ProvisioningWritePolicy.OutcomeKind.FULL_OK, outcome.kind)
        assertTrue(outcome.treatAsSuccess)
    }

    @Test
    fun classify_onePlusSoftKeysStillSuccess() {
        // 对齐一加日志：CarrierConfig OK，key=26/27 拒写。
        val outcome = ProvisioningWritePolicy.classifyApplyOutcome(
            mapOf(
                "carrier_config_override" to true,
                "provision_vowifi" to true,
                "provision_vowifi_roaming" to false,
                "provision_wfc_mode" to false,
            ),
            xiaomiFamily = false,
        )
        assertEquals(ProvisioningWritePolicy.OutcomeKind.OEM_SOFT_PARTIAL, outcome.kind)
        assertTrue(outcome.treatAsSuccess)
        assertEquals(
            listOf("provision_vowifi_roaming", "provision_wfc_mode"),
            outcome.softFailedKeys,
        )
        assertTrue(outcome.hardFailedKeys.isEmpty())
    }

    @Test
    fun classify_xiaomiSoftKeysStillSuccess() {
        val outcome = ProvisioningWritePolicy.classifyApplyOutcome(
            mapOf(
                "carrier_config_override" to true,
                "provision_volte" to true,
                "provision_vowifi" to false,
                "provision_voims_opt_in" to false,
                "provision_vowifi_roaming" to false,
                "provision_wfc_mode" to false,
            ),
            xiaomiFamily = true,
        )
        assertEquals(ProvisioningWritePolicy.OutcomeKind.OEM_SOFT_PARTIAL, outcome.kind)
        assertTrue(outcome.treatAsSuccess)
        assertTrue(outcome.hardFailedKeys.isEmpty())
    }

    @Test
    fun classify_xiaomiStillHardWhenVolteFails() {
        val outcome = ProvisioningWritePolicy.classifyApplyOutcome(
            mapOf(
                "carrier_config_override" to true,
                "provision_volte" to false,
                "provision_vowifi" to false,
            ),
            xiaomiFamily = true,
        )
        assertEquals(ProvisioningWritePolicy.OutcomeKind.HARD_PARTIAL, outcome.kind)
        assertFalse(outcome.treatAsSuccess)
        assertTrue(outcome.hardFailedKeys.contains("provision_volte"))
    }

    @Test
    fun classify_hardFailWhenCarrierOrCoreMissing() {
        val outcome = ProvisioningWritePolicy.classifyApplyOutcome(
            mapOf(
                "carrier_config_override" to true,
                "provision_vowifi" to false,
                "provision_wfc_mode" to false,
            ),
            xiaomiFamily = false,
        )
        assertEquals(ProvisioningWritePolicy.OutcomeKind.HARD_PARTIAL, outcome.kind)
        assertFalse(outcome.treatAsSuccess)
        assertTrue(outcome.hardFailedKeys.contains("provision_vowifi"))
    }

    @Test
    fun isOemProvisioningReject_matchesKey27() {
        assertTrue(
            ProvisioningWritePolicy.isOemProvisioningReject(
                "IllegalStateException: IMS provisioning rejected key=27, result=1",
            ),
        )
        assertFalse(ProvisioningWritePolicy.isOemProvisioningReject("permission denied"))
    }

    @Test
    fun softIntKeys_include26And27() {
        assertTrue(ProvisioningWritePolicy.isSoftProvisioningIntKey(26, xiaomiFamily = false))
        assertTrue(ProvisioningWritePolicy.isSoftProvisioningIntKey(27, xiaomiFamily = false))
        assertFalse(ProvisioningWritePolicy.isSoftProvisioningIntKey(28, xiaomiFamily = false))
        assertFalse(ProvisioningWritePolicy.isSoftProvisioningIntKey(10, xiaomiFamily = false))
    }

    @Test
    fun softIntKeys_xiaomiAdds28And68() {
        assertTrue(ProvisioningWritePolicy.isSoftProvisioningIntKey(28, xiaomiFamily = true))
        assertTrue(ProvisioningWritePolicy.isSoftProvisioningIntKey(68, xiaomiFamily = true))
        assertFalse(ProvisioningWritePolicy.isSoftProvisioningIntKey(10, xiaomiFamily = true))
    }
}
