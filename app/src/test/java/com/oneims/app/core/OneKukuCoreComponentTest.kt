package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneKukuCoreComponentTest {

    @Test
    fun adbStartCommand_defaultsToBrandedPackage() {
        val cmd = OneKukuCoreComponent.adbStartCommand(context = null)
        assertTrue(cmd.contains(OneKukuCoreComponent.BRANDED_CORE_PACKAGE))
        assertTrue(cmd.contains("start.sh"))
        assertTrue(cmd.startsWith("adb shell sh "))
    }

    @Test
    fun candidatePackages_preferBrandedThenLegacy() {
        assertEquals(
            listOf(
                OneKukuCoreComponent.BRANDED_CORE_PACKAGE,
                OneKukuCoreComponent.LEGACY_CORE_PACKAGE,
            ),
            OneKukuCoreComponent.CANDIDATE_PACKAGES,
        )
    }

    @Test
    fun bundledAssetName_isStableContract() {
        assertEquals("onekuku-core.apk", OneKukuCoreComponent.BUNDLED_ASSET_NAME)
    }
}
