package com.oneims.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInfoSocTest {

    @Test
    fun tensorPixel_isSupportedForVowifi() {
        val fp = DeviceInfo.SocFingerprint(
            manufacturer = "Google",
            model = "Tensor G3",
            hardware = "zuma",
            board = "zuma",
        )
        assertTrue(DeviceInfo.isTensor(fp))
        assertFalse(DeviceInfo.isMediaTek(fp))
        assertTrue(DeviceInfo.supportsVowifiForceEnable(fp))
    }

    @Test
    fun dimensity9500_isMediaTekAndNotPrimaryVowifiPath() {
        val fp = DeviceInfo.SocFingerprint(
            manufacturer = "MediaTek",
            model = "Dimensity 9500",
            hardware = "mt6991",
            board = "mt6991",
        )
        assertFalse(DeviceInfo.isTensor(fp))
        assertTrue(DeviceInfo.isMediaTek(fp))
        assertFalse(DeviceInfo.supportsVowifiForceEnable(fp))
    }

    @Test
    fun dimensity8300_chineseLabel_isMediaTek() {
        val fp = DeviceInfo.SocFingerprint(
            manufacturer = "联发科",
            model = "天玑8300",
            hardware = "mt6897",
            board = "mt6897",
        )
        assertTrue(DeviceInfo.isMediaTek(fp))
        assertFalse(DeviceInfo.supportsVowifiForceEnable(fp))
    }

    @Test
    fun snapdragon_nonTensor_notPrimaryVowifiPathButNotMediaTek() {
        val fp = DeviceInfo.SocFingerprint(
            manufacturer = "Qualcomm",
            model = "SM8650",
            hardware = "qcom",
            board = "taro",
        )
        assertFalse(DeviceInfo.isTensor(fp))
        assertFalse(DeviceInfo.isMediaTek(fp))
        assertFalse(DeviceInfo.supportsVowifiForceEnable(fp))
    }
}
