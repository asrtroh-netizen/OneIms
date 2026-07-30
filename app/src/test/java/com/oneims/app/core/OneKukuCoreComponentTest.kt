package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneKukuCoreComponentTest {

    @Test
    fun adbStartCommand_defaultsToHostPackage() {
        val cmd = OneKukuCoreComponent.adbStartCommand(context = null)
        assertTrue(cmd.contains(OneKukuCoreComponent.HOST_PACKAGE))
        assertTrue(cmd.contains("app_process"))
        assertTrue(cmd.contains("BridgeService"))
        assertTrue(cmd.startsWith("adb shell "))
    }

    @Test
    fun bridgeBootShellCommand_doesNotRequireStartShFile() {
        val cmd = OneKukuCoreComponent.bridgeBootShellCommand()
        assertTrue(cmd.contains("pm path"))
        assertTrue(cmd.contains(OneKukuCoreComponent.HOST_PACKAGE))
        assertTrue(cmd.contains("app_process"))
        assertTrue(cmd.contains(OneKukuCoreComponent.SHELL_BOOT_OK))
        assertTrue(cmd.contains("printf"))
        assertTrue(cmd.contains("pidof onebridge_server"))
        assertTrue(!cmd.contains("pkill"))
        // Detach from adb shell: setsid (preferred) or nohup fallback — avoid SIGHUP kill.
        assertTrue(cmd.contains("setsid") || cmd.contains("nohup"))
        assertTrue(!cmd.contains(" exec "))
        assertTrue(!cmd.contains("Android/data/"))
    }

    @Test
    fun bridgeBootShellCommand_forceRestartStillUsesPkill() {
        val cmd = OneKukuCoreComponent.bridgeBootShellCommand(forceRestart = true)
        assertTrue(cmd.contains("pkill"))
        assertTrue(cmd.contains("app_process"))
    }

    @Test
    fun candidatePackages_careFirstDuringFusion() {
        assertEquals(
            OneKukuCoreComponent.CARE_PACKAGE,
            OneKukuCoreComponent.CANDIDATE_PACKAGES.first(),
        )
        assertTrue(OneKukuCoreComponent.CANDIDATE_PACKAGES.contains(OneKukuCoreComponent.HOST_PACKAGE))
        assertTrue(OneKukuCoreComponent.CANDIDATE_PACKAGES.contains("com.oneims.bridge"))
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
