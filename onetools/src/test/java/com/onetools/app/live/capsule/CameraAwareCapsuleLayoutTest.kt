package com.onetools.app.live.capsule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraAwareCapsuleLayoutTest {
    private val anchor = CameraAnchor(centerX = 540, centerY = 80, width = 80, height = 80)

    @Test
    fun belowSitsUnderCameraBand() {
        val bounds = CameraAwareCapsuleLayout.compute(
            anchor = anchor,
            mode = CapsuleDisplayMode.COMPACT,
            pillHeightPx = 102,
            offsetYPx = 0,
            exclusion = CameraExclusionMode.BELOW,
            cameraClearancePx = 8,
        )
        assertTrue(bounds.topPx >= anchor.centerY + anchor.height / 2)
        assertEquals(0, bounds.gapWidthPx)
    }

    @Test
    fun cameraCenterPinsPillBandOnCutout() {
        val pillH = 102
        val bounds = CameraAwareCapsuleLayout.compute(
            anchor = anchor,
            mode = CapsuleDisplayMode.COMPACT,
            pillHeightPx = pillH,
            offsetYPx = 0,
            exclusion = CameraExclusionMode.CAMERA_CENTER,
            cameraClearancePx = 8,
        )
        assertEquals(anchor.centerY - pillH / 2, bounds.topPx)
        assertTrue(bounds.gapWidthPx >= 16)
        assertEquals(540, bounds.centerXPx)
    }

    @Test
    fun expandedKeepsSameBandTopAsPill() {
        val pillH = 102
        val pill = CameraAwareCapsuleLayout.compute(
            anchor = anchor,
            mode = CapsuleDisplayMode.COMPACT,
            pillHeightPx = pillH,
            offsetYPx = 0,
            exclusion = CameraExclusionMode.CAMERA_CENTER,
        )
        val expanded = CameraAwareCapsuleLayout.compute(
            anchor = anchor,
            mode = CapsuleDisplayMode.EXPANDED,
            pillHeightPx = pillH,
            offsetYPx = 0,
            exclusion = CameraExclusionMode.CAMERA_CENTER,
        )
        assertEquals(pill.topPx, expanded.topPx)
    }

    @Test
    fun cutoutCalibrationShiftsAnchorCenter() {
        val calibrated = CameraAnchorResolver.applyCalibration(
            raw = anchor,
            calibXDp = 10,
            calibYDp = -4,
            density = 2f,
        )
        assertEquals(560, calibrated.centerX)
        assertEquals(72, calibrated.centerY)
        assertEquals(anchor.width, calibrated.width)
    }
}
