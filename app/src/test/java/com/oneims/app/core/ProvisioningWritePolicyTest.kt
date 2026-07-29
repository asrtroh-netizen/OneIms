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
    fun classify_hardFailWhenCarrierOrCoreMissing() {
        val outcome = ProvisioningWritePolicy.classifyApplyOutcome(
            mapOf(
                "carrier_config_override" to true,
                "provision_vowifi" to false,
                "provision_wfc_mode" to false,
            ),
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
}
