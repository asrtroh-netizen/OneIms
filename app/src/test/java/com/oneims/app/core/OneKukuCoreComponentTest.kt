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
    fun candidatePackages_onlyBridgeAfterPhase3() {
        assertEquals(
            listOf(OneKukuCoreComponent.BRIDGE_PACKAGE),
            OneKukuCoreComponent.CANDIDATE_PACKAGES,
        )
    }

    @Test
    fun bundledAssetCandidates_onlyBridge() {
        assertEquals(
            listOf(OneKukuCoreComponent.BUNDLED_BRIDGE_ASSET_NAME),
            OneKukuCoreComponent.BUNDLED_ASSET_CANDIDATES,
        )
        assertEquals("oneims-bridge.apk", OneKukuCoreComponent.BUNDLED_BRIDGE_ASSET_NAME)
        assertEquals(
            OneKukuCoreComponent.BUNDLED_BRIDGE_ASSET_NAME,
            OneKukuCoreComponent.BUNDLED_ASSET_NAME,
        )
    }
}
