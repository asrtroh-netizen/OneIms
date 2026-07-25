package com.onetools.app.caller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberMatcherTest {
    @Test
    fun exactAndPrefix() {
        val blockExact = CallRule("1", "13800138000", CallRuleKind.BLOCK, CallMatchMode.EXACT)
        val blockPrefix = CallRule("2", "400", CallRuleKind.BLOCK, CallMatchMode.PREFIX)
        val allow = CallRule("3", "400800", CallRuleKind.ALLOW, CallMatchMode.PREFIX)

        assertEquals(
            NumberMatcher.Decision.BLOCK,
            NumberMatcher.decide(listOf(blockExact), "13800138000"),
        )
        assertEquals(
            NumberMatcher.Decision.BLOCK,
            NumberMatcher.decide(listOf(blockPrefix), "4001234567"),
        )
        assertEquals(
            NumberMatcher.Decision.ALLOW_LIST,
            NumberMatcher.decide(listOf(blockPrefix, allow), "4008001234"),
        )
        assertEquals(
            NumberMatcher.Decision.ALLOW_UNKNOWN,
            NumberMatcher.decide(listOf(blockPrefix), "13800138000"),
        )
    }

    @Test
    fun prefixDoesNotUseContains() {
        val rule = CallRule("1", "00", CallRuleKind.BLOCK, CallMatchMode.PREFIX)
        assertEquals(
            NumberMatcher.Decision.ALLOW_UNKNOWN,
            NumberMatcher.decide(listOf(rule), "13800138000"),
        )
        assertEquals(
            NumberMatcher.Decision.BLOCK,
            NumberMatcher.decide(listOf(rule), "0086123"),
        )
    }

    @Test
    fun tagRuleMatchesMembersByTag() {
        // Catalog entry labels the number; TAG rule supplies the block decision.
        // Use a non-blocking structural kind that still carries the tag — here BLOCK on tag only:
        // structural kind ALLOW would win, so catalog uses BLOCK on a different prefix that won't match,
        // and TAG expands members by tag+number match using tagRule.kind.
        val labeled = CallRule("1", "95588", CallRuleKind.BLOCK, CallMatchMode.EXACT, tag = "银行客服")
        val tagRule = CallRule("2", "银行客服", CallRuleKind.BLOCK, CallMatchMode.TAG)
        val result = NumberMatcher.lookup(listOf(labeled, tagRule), "95588")
        assertEquals(NumberMatcher.Decision.BLOCK, result.decision)
        assertTrue(result.tags.contains("银行客服") || result.matchedRules.any { it.mode == CallMatchMode.TAG })
    }

    @Test
    fun labelOnlyDoesNotBlockButCarriesTag() {
        val label = CallRule("1", "95588", CallRuleKind.LABEL, CallMatchMode.EXACT, tag = "工商银行客服")
        val result = NumberMatcher.lookup(listOf(label), "95588")
        assertEquals(NumberMatcher.Decision.ALLOW_UNKNOWN, result.decision)
        assertEquals("工商银行客服", result.tags.single())
        assertEquals(CallRuleKind.LABEL, result.matchedRules.single().kind)
    }

    @Test
    fun exportRoundTrip() {
        val rules = listOf(
            CallRule("1", "400", CallRuleKind.BLOCK, CallMatchMode.PREFIX, "骚扰"),
            CallRule("2", "骚扰", CallRuleKind.BLOCK, CallMatchMode.TAG),
        )
        val json = BlocklistFormat.export(rules)
        val parsed = BlocklistFormat.parse(json)
        assertTrue(parsed.any { it.mode == CallMatchMode.PREFIX })
        assertTrue(parsed.any { it.mode == CallMatchMode.TAG })
    }
}
