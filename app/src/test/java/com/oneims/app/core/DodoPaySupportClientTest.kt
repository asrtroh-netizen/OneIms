package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DodoPaySupportClientTest {

    @Test
    fun extractProof_fromQuery() {
        assertEquals(
            "proof_abc123",
            DodoPaySupportClient.extractDodopayPaymentProof(
                "oneims://support/callback?payment_proof=proof_abc123",
            ),
        )
    }

    @Test
    fun extractProof_fromFragment() {
        assertEquals(
            "proof_frag9",
            DodoPaySupportClient.extractDodopayPaymentProof(
                "https://example.com/return#payment_proof=proof_frag9",
            ),
        )
    }

    @Test
    fun extractProof_rejectsNonProofPrefix() {
        assertNull(
            DodoPaySupportClient.extractDodopayPaymentProof(
                "oneims://support/callback?payment_proof=token_abc",
            ),
        )
    }

    @Test
    fun parseAmount_bounds() {
        assertEquals(6.0, DodoPaySupportClient.parseAmount("6")!!, 0.0)
        assertNull(DodoPaySupportClient.parseAmount("0"))
        assertNull(DodoPaySupportClient.parseAmount("-1"))
        assertNull(DodoPaySupportClient.parseAmount("1000"))
        assertNull(DodoPaySupportClient.parseAmount("abc"))
    }

    @Test
    fun normalizeNicknameAndMessage() {
        assertEquals("匿名朋友", DodoPaySupportClient.normalizeNickname("  "))
        assertEquals("小明", DodoPaySupportClient.normalizeNickname(" 小明 "))
        assertEquals("hello world", DodoPaySupportClient.normalizeMessage("hello\nworld"))
        assertTrue(DodoPaySupportClient.normalizeMessage("x".repeat(200)).length <= 160)
    }

    @Test
    fun officialDodoCheckout_detected() {
        assertTrue(
            DodoPaySupportClient.isOfficialDodoCheckout(
                "https://test.checkout.dodopayments.com/buy/pdt_x?quantity=1",
            ),
        )
        assertFalse(
            DodoPaySupportClient.isOfficialDodoCheckout(
                "https://pay.example.com/oneims",
            ),
        )
    }
}
