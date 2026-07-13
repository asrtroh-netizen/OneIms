package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SimCountryIsoManagerTest {

    @Test
    fun normalize_trimsAndLowercases() {
        assertEquals("us", SimCountryIsoManager.normalize(" US "))
        assertEquals("jp", SimCountryIsoManager.normalize("Jp"))
    }

    @Test
    fun requireValidIso_acceptsAlpha2() {
        assertEquals("hk", SimCountryIsoManager.requireValidIso("HK"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireValidIso_rejectsEmpty() {
        SimCountryIsoManager.requireValidIso("  ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireValidIso_rejectsThreeLetters() {
        SimCountryIsoManager.requireValidIso("usa")
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireValidIso_rejectsDigits() {
        SimCountryIsoManager.requireValidIso("u1")
    }
}
