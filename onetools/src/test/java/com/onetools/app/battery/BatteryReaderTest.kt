package com.onetools.app.battery

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryReaderTest {
    @Test
    fun healthLabel_mapsKnownCodes() {
        assertEquals("良好", BatteryReader.healthLabel(BatteryManager.BATTERY_HEALTH_GOOD))
        assertEquals("未知", BatteryReader.healthLabel(-1))
    }
}
