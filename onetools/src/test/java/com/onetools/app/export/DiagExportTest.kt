package com.onetools.app.export

import com.onetools.app.channel.ChannelCardState
import com.onetools.app.device.DeviceSnapshot
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagExportTest {
    @Test
    fun formatContainsChannelAndDevice() {
        val snap = DeviceSnapshot(
            capturedAtEpochMs = 1_700_000_000_000L,
            brand = "Brand",
            manufacturer = "Mfr",
            model = "ModelX",
            device = "dev",
            androidRelease = "14",
            sdkInt = 34,
            appPackage = "com.onetools.app",
            appVersionName = "1.0.0",
            appVersionCode = 10L,
            shizukuInstalled = true,
            shizukuRunning = true,
            shizukuGranted = false,
            channelState = ChannelCardState.INACTIVE,
        )
        val md = DiagExport.formatMarkdown(snap)
        assertTrue(md.contains("OneTools diagnostic"))
        assertTrue(md.contains("ModelX"))
        assertTrue(md.contains("INACTIVE"))
        assertTrue(md.contains("com.onetools.app"))
        assertTrue(md.contains("shizukuGranted: `false`"))
    }
}
