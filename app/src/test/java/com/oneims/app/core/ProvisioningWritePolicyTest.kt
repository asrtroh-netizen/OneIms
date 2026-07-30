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
            domesticVowifiOem = false,
        )
        assertEquals(ProvisioningWritePolicy.OutcomeKind.FULL_OK, outcome.kind)
        assertTrue(outcome.treatAsSuccess)
    }

    @Test
    fun classify_onePlusSoftKeysStillSuccess() {
        val outcome = ProvisioningWritePolicy.classifyApplyOutcome(
            mapOf(
                "carrier_config_override" to true,
                "provision_vowifi" to true,
                "provision_vowifi_roaming" to false,
                "provision_wfc_mode" to false,
            ),
            domesticVowifiOem = true,
        )
        assertEquals(ProvisioningWritePolicy.OutcomeKind.OEM_SOFT_PARTIAL, outcome.kind)
        assertTrue(outcome.treatAsSuccess)
        assertTrue(outcome.hardFailedKeys.isEmpty())
    }

    @Test
    fun classify_pixelKeepsSuccessWhenVoimsOptInSoftFails() {
        val outcome = ProvisioningWritePolicy.classifyApplyOutcome(
            mapOf(
                "carrier_config_override" to true,
                "provision_volte" to true,
                "provision_voims_opt_in" to false,
                "provision_vowifi" to true,
            ),
            domesticVowifiOem = false,
        )
        assertEquals(ProvisioningWritePolicy.OutcomeKind.OEM_SOFT_PARTIAL, outcome.kind)
        assertTrue(outcome.treatAsSuccess)
        assertTrue(outcome.hardFailedKeys.isEmpty())
    }

    @Test
    fun classify_pixelStillHardWhenVolteFails() {
        val outcome = ProvisioningWritePolicy.classifyApplyOutcome(
            mapOf(
                "carrier_config_override" to true,
                "provision_volte" to false,
                "provision_vowifi" to true,
            ),
            domesticVowifiOem = false,
        )
        assertEquals(ProvisioningWritePolicy.OutcomeKind.HARD_PARTIAL, outcome.kind)
        assertFalse(outcome.treatAsSuccess)
        assertTrue(outcome.hardFailedKeys.contains("provision_volte"))
    }

    @Test
    fun classify_pixelVowifiEnableIsFirstWeightHard() {
        // P0：Pixel VoWIFI 开关失败必须整单硬失败，绝不能 soft 吞掉。
        val outcome = ProvisioningWritePolicy.classifyApplyOutcome(
            mapOf(
                "carrier_config_override" to true,
                "provision_volte" to true,
                "provision_vowifi" to false,
                "provision_vowifi_roaming" to true,
                "provision_wfc_mode" to true,
            ),
            domesticVowifiOem = false,
        )
        assertEquals(ProvisioningWritePolicy.OutcomeKind.HARD_PARTIAL, outcome.kind)
        assertFalse(outcome.treatAsSuccess)
        assertTrue(outcome.hardFailedKeys.contains("provision_vowifi"))
        assertFalse(
            ProvisioningWritePolicy.isSoftProvisioningIntKey(28, domesticVowifiOem = false),
        )
    }

    @Test
    fun classify_domesticVowifiAllowsVolteSoftFail() {
        // 国产不走通信主战场：VoLTE 拒写 + VoWIFI 键软失败仍可整单软成功。
        val outcome = ProvisioningWritePolicy.classifyApplyOutcome(
            mapOf(
                "carrier_config_override" to true,
                "provision_volte" to false,
                "provision_vowifi" to false,
                "provision_voims_opt_in" to false,
                "provision_vowifi_roaming" to false,
                "provision_wfc_mode" to false,
            ),
            domesticVowifiOem = true,
        )
        assertEquals(ProvisioningWritePolicy.OutcomeKind.OEM_SOFT_PARTIAL, outcome.kind)
        assertTrue(outcome.treatAsSuccess)
        assertTrue(outcome.hardFailedKeys.isEmpty())
    }

    @Test
    fun classify_hardFailWhenCarrierMissing() {
        val outcome = ProvisioningWritePolicy.classifyApplyOutcome(
            mapOf(
                "carrier_config_override" to false,
                "provision_vowifi" to true,
            ),
            domesticVowifiOem = true,
        )
        assertEquals(ProvisioningWritePolicy.OutcomeKind.HARD_PARTIAL, outcome.kind)
        assertFalse(outcome.treatAsSuccess)
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
    fun softIntKeys_pixelNeverSoftensVolte10() {
        assertTrue(ProvisioningWritePolicy.isSoftProvisioningIntKey(26, domesticVowifiOem = false))
        assertTrue(ProvisioningWritePolicy.isSoftProvisioningIntKey(27, domesticVowifiOem = false))
        assertTrue(ProvisioningWritePolicy.isSoftProvisioningIntKey(68, domesticVowifiOem = false))
        assertFalse(ProvisioningWritePolicy.isSoftProvisioningIntKey(28, domesticVowifiOem = false))
        assertFalse(ProvisioningWritePolicy.isSoftProvisioningIntKey(10, domesticVowifiOem = false))
    }

    @Test
    fun softIntKeys_domesticSoftensVowifiAndVolte() {
        assertTrue(ProvisioningWritePolicy.isSoftProvisioningIntKey(28, domesticVowifiOem = true))
        assertTrue(ProvisioningWritePolicy.isSoftProvisioningIntKey(10, domesticVowifiOem = true))
        assertTrue(ProvisioningWritePolicy.isSoftProvisioningIntKey(68, domesticVowifiOem = true))
    }
}
