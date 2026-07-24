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
    fun parseBlocklistSchema() {
        val rules = BlocklistFormat.parse(BlocklistFormat.sampleJson())
        assertTrue(rules.isNotEmpty())
        assertTrue(rules.any { it.mode == CallMatchMode.PREFIX && it.pattern == "400" })
    }
}
