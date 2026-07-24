package com.onetools.app.caller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryLabelLogicTest {
    @Test
    fun allowWinsForDisplaySource() {
        val allow = CallRule("a", "400800", CallRuleKind.ALLOW, CallMatchMode.PREFIX, "客服白名单")
        val block = CallRule("b", "400", CallRuleKind.BLOCK, CallMatchMode.PREFIX, "骚扰")
        val decision = NumberMatcher.decide(listOf(block, allow), "4008001234")
        assertEquals(NumberMatcher.Decision.ALLOW_LIST, decision)
    }

    @Test
    fun signedSampleStillParses() {
        val sample = """
            {
              "schema": "onetools.blocklist.v1",
              "version": 1,
              "numbers": [
                {"n":"400","prefix":true,"tag":"可能骚扰","kind":"block"}
              ],
              "keyId": "one-cdn-2026r2",
              "sigAlg": "SHA256withECDSA",
              "signature": "deadbeef"
            }
        """.trimIndent()
        val rules = BlocklistFormat.parse(sample)
        assertTrue(rules.any { it.pattern == "400" })
    }
}
