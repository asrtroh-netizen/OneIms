package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneKukuCoreComponentTest {

    @Test
    fun adbStartCommand_pointsAtCorePackageStartScript() {
        val cmd = OneKukuCoreComponent.adbStartCommand()
        assertTrue(cmd.contains(OneKukuCoreComponent.CORE_PACKAGE))
        assertTrue(cmd.contains("start.sh"))
        assertTrue(cmd.startsWith("adb shell sh "))
    }

    @Test
    fun bundledAssetName_isStableContract() {
        assertEquals("onekuku-core.apk", OneKukuCoreComponent.BUNDLED_ASSET_NAME)
    }
}
