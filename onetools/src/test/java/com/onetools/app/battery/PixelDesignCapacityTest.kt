package com.onetools.app.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PixelDesignCapacityTest {
    @Test
    fun prefersLongerModelName() {
        val p = PixelDesignCapacity.match(
            model = "Pixel 9 Pro XL",
            manufacturer = "Google",
        )
        assertEquals("Pixel 9 Pro XL", p?.label)
        assertEquals(5060, p?.mah)
    }

    @Test
    fun matchesPixel8() {
        val p = PixelDesignCapacity.match(model = "Pixel 8", manufacturer = "Google")
        assertEquals("Pixel 8", p?.label)
        assertEquals(4575, p?.mah)
    }

    @Test
    fun ignoresNonPixel() {
        assertNull(
            PixelDesignCapacity.match(model = "SM-S918B", manufacturer = "samsung"),
        )
    }

    @Test
    fun modelAloneCanIdentifyPixel() {
        val p = PixelDesignCapacity.match(model = "Pixel 7a", manufacturer = "")
        assertEquals(4385, p?.mah)
    }
}
