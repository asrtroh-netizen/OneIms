package com.oneims.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiModelsTest {

    @Test
    fun themeMode_roundTripsStoredValues() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStored(mode.storedValue))
        }
    }

    @Test
    fun themeMode_unknownValueFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(Int.MAX_VALUE))
    }

    @Test
    fun destinations_keepStableSectionOrder() {
        assertEquals(
            listOf(
                AppDestination.HOME,
                AppDestination.CAPABILITIES,
                AppDestination.EXPERIMENTAL,
                AppDestination.DIAGNOSTICS,
                AppDestination.SPONSOR,
                AppDestination.SETTINGS,
            ),
            AppDestination.entries,
        )
        assertTrue(AppDestination.entries.map { it.labelRes }.distinct().size == 6)
    }
}
