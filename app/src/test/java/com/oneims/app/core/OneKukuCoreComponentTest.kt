package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneKukuCoreComponentTest {

    @Test
    fun adbStartCommand_defaultsToBridgePackage() {
        val cmd = OneKukuCoreComponent.adbStartCommand(context = null)
        assertTrue(cmd.contains(OneKukuCoreComponent.BRIDGE_PACKAGE))
        assertTrue(cmd.contains("start.sh"))
        assertTrue(cmd.startsWith("adb shell sh "))
    }

    @Test
    fun candidatePackages_preferBridgeThenBrandedThenLegacy() {
        assertEquals(
            listOf(
                OneKukuCoreComponent.BRIDGE_PACKAGE,
                OneKukuCoreComponent.BRANDED_CORE_PACKAGE,
                OneKukuCoreComponent.LEGACY_CORE_PACKAGE,
            ),
            OneKukuCoreComponent.CANDIDATE_PACKAGES,
        )
    }

    @Test
    fun bundledAssetCandidates_preferBridgeThenCore() {
        assertEquals(
            listOf(
                OneKukuCoreComponent.BUNDLED_BRIDGE_ASSET_NAME,
                OneKukuCoreComponent.BUNDLED_CORE_ASSET_NAME,
            ),
            OneKukuCoreComponent.BUNDLED_ASSET_CANDIDATES,
        )
        assertEquals("oneims-bridge.apk", OneKukuCoreComponent.BUNDLED_BRIDGE_ASSET_NAME)
        assertEquals("onekuku-core.apk", OneKukuCoreComponent.BUNDLED_CORE_ASSET_NAME)
        assertEquals(
            OneKukuCoreComponent.BUNDLED_BRIDGE_ASSET_NAME,
            OneKukuCoreComponent.BUNDLED_ASSET_NAME,
        )
    }
}
