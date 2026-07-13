package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApnCountryIndexTest {

    @Test
    fun resolveSearchTokens_matchesHongKongVariants() {
        val tokens = ApnCountryIndex.resolveSearchTokens("香港")
        assertTrue(tokens.any { it.contains("hk", ignoreCase = true) || it == "454" })
    }

    @Test
    fun resolveSearchTokens_matchesMcc460ForChina() {
        val tokens = ApnCountryIndex.resolveSearchTokens("460")
        assertTrue(tokens.any { it.equals("cn", ignoreCase = true) || it.contains("中国") })
    }

    @Test
    fun displayName_returnsChineseForKnownIso() {
        assertEquals("香港", ApnCountryIndex.displayName("HK"))
        assertEquals("中国", ApnCountryIndex.displayName("CN"))
    }

    @Test
    fun resolveSearchTokens_coversRequiredCountryNamesIsoAndMcc() {
        mapOf(
            "美国" to setOf("us", "310"),
            "United States" to setOf("us", "310"),
            "HK" to setOf("hk", "454"),
            "China" to setOf("cn", "460"),
            "New Zealand" to setOf("nz", "530"),
            "UK" to setOf("uk", "234"),
            "GB" to setOf("gb", "234"),
            "巴西" to setOf("br", "brazil"),
            "Brazil" to setOf("br", "巴西"),
            "印度" to setOf("in", "india"),
            "India" to setOf("in", "印度"),
        ).forEach { (query, expectedTokens) ->
            val tokens = ApnCountryIndex.resolveSearchTokens(query)
                .map { token -> token.lowercase() }
                .toSet()
            assertTrue(
                "$query should resolve to one of $expectedTokens but was $tokens",
                expectedTokens.any(tokens::contains),
            )
        }
    }

    @Test
    fun englishName_returnsEnglishForKnownIso() {
        assertEquals("United States", ApnCountryIndex.englishName("US"))
        assertEquals("United Kingdom", ApnCountryIndex.englishName("GB"))
    }

    @Test
    fun searchSelection_coversEveryRequiredDatabaseFieldWithBoundArguments() {
        val selection = buildApnSearchSelection("香港")
        val clause = requireNotNull(selection.clause)

        listOf(
            "lower(carrier)",
            "lower(apn)",
            "lower(country)",
            "(mcc || mnc)",
            "mcc LIKE",
            "mnc LIKE",
            "lower(apn_types)",
        ).forEach { field ->
            assertTrue("Missing APN search field: $field", clause.contains(field))
        }
        assertTrue(selection.args.isNotEmpty())
        assertEquals(0, selection.args.size % 7)
        assertEquals(selection.args.size, clause.count { character -> character == '?' })
    }

    @Test
    fun searchSelection_escapesLikeWildcards() {
        val selection = buildApnSearchSelection("foo%_bar")
        assertTrue(selection.args.all { it.contains("\\%") && it.contains("\\_") })
    }
}
