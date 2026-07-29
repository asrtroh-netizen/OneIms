package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatAndSocPolicyTest {

    @Test
    fun resolveSupportLevel_nonTensorIsDegradedNotUnsupported() {
        assertEquals(
            SupportLevel.DEGRADED,
            CompatChecker.resolveSupportLevel(
                sdkOk = true,
                shizukuGranted = true,
                tensor = false,
                delegate = true,
            ),
        )
    }

    @Test
    fun resolveSupportLevel_tensorWithDelegateIsFull() {
        assertEquals(
            SupportLevel.FULL,
            CompatChecker.resolveSupportLevel(
                sdkOk = true,
                shizukuGranted = true,
                tensor = true,
                delegate = true,
            ),
        )
    }

    @Test
    fun resolveSupportLevel_lowSdkStillUnsupported() {
        assertEquals(
            SupportLevel.UNSUPPORTED,
            CompatChecker.resolveSupportLevel(
                sdkOk = false,
                shizukuGranted = true,
                tensor = true,
                delegate = true,
            ),
        )
    }

    @Test
    fun resolveSupportLevel_needChannelWhenNotGranted() {
        assertEquals(
            SupportLevel.NEED_SHIZUKU,
            CompatChecker.resolveSupportLevel(
                sdkOk = true,
                shizukuGranted = false,
                tensor = false,
                delegate = false,
            ),
        )
    }

    @Test
    fun isQualcomm_matchesSnapdragonFingerprints() {
        assertTrue(
            DeviceInfo.isQualcomm(
                DeviceInfo.SocFingerprint(
                    manufacturer = "Qualcomm",
                    model = "SM8650",
                    hardware = "qcom",
                    board = "taro",
                ),
            ),
        )
        assertTrue(
            DeviceInfo.isQualcomm(
                DeviceInfo.SocFingerprint(
                    manufacturer = "",
                    model = "",
                    hardware = "qcom",
                    board = "sdm845",
                ),
            ),
        )
        assertFalse(
            DeviceInfo.isQualcomm(
                DeviceInfo.SocFingerprint(
                    manufacturer = "Google",
                    model = "Tensor G4",
                    hardware = "zuma",
                    board = "zuma",
                ),
            ),
        )
    }
}
