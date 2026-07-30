package com.oneims.app.core

import com.oneims.app.core.privilege.ChannelEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneKukuCoreComponentTest {

    @Test
    fun adbStartCommand_defaultsToHostPackage() {
        val cmd = OneKukuCoreComponent.adbStartCommand(context = null)
        assertTrue(cmd.contains(OneKukuCoreComponent.HOST_PACKAGE))
        assertTrue(cmd.contains("app_process"))
        assertTrue(cmd.contains(OneKukuCoreComponent.ENTRY_CLASS_ONEBRIDGE))
        assertTrue(cmd.startsWith("adb shell "))
    }

    @Test
    fun bridgeBootShellCommand_isOneBridgeServer() {
        assertEquals(ChannelEngine.ONEBRIDGE, ChannelEngine.current())
        val cmd = OneKukuCoreComponent.bridgeBootShellCommand()
        assertTrue(cmd.contains("onebridge_server"))
        assertTrue(cmd.contains(OneKukuCoreComponent.ENTRY_CLASS_ONEBRIDGE))
        assertTrue(!cmd.contains("onekuku_server"))
        assertTrue(!cmd.contains("rikka.shizuku.server.ShizukuService"))
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
        assertTrue(!cmd.contains("shizuku.library.path"))
        assertTrue(!cmd.contains("librish.so"))
        assertTrue(!cmd.contains("pkill"))
        assertTrue(cmd.contains("setsid") || cmd.contains("nohup"))
        assertTrue(!cmd.contains(" exec "))
        assertTrue(!cmd.contains("Android/data/"))
    }

    @Test
    fun bridgeBootShellCommand_forceRestartStillUsesPkill() {
        val cmd = OneKukuCoreComponent.bridgeBootShellCommand(forceRestart = true)
        assertTrue(cmd.contains("pkill"))
        assertTrue(cmd.contains("app_process"))
        assertTrue(cmd.contains("onebridge_server"))
        assertTrue(!cmd.contains("shizuku.library.path"))
    }

    @Test
    fun candidatePackages_hostFirstForInternalLoop() {
        assertEquals(
            OneKukuCoreComponent.HOST_PACKAGE,
            OneKukuCoreComponent.CANDIDATE_PACKAGES.first(),
        )
        assertTrue(OneKukuCoreComponent.CANDIDATE_PACKAGES.contains("com.oneims.bridge"))
        assertTrue(!OneKukuCoreComponent.CANDIDATE_PACKAGES.contains("com.onekuku.care"))
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
