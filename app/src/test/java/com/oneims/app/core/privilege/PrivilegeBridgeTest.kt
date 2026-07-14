package com.oneims.app.core.privilege

import android.os.Binder
import android.os.IBinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegeBridgeTest {

    @Test
    fun mvpSystemServices_areFrozenMinimalSet() {
        assertEquals(
            setOf("activity", "carrier_config", "isub", "phone"),
            PrivilegeBridge.MVP_SYSTEM_SERVICES,
        )
    }

    @Test
    fun wrapSystemService_rejectsNonMvpServiceName() {
        val bridge = object : PrivilegeBridge {
            override fun isRunning(): Boolean = true
            override fun isGranted(): Boolean = true
            override fun requestPermission(requestCode: Int) = Unit
            override fun getUid(): Int = 2000
            override fun wrapSystemService(name: String): IBinder {
                require(name in PrivilegeBridge.MVP_SYSTEM_SERVICES) {
                    "PrivilegeBridge MVP does not expose system service: $name"
                }
                return Binder()
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            bridge.wrapSystemService("package")
        }
        assertTrue(bridge.wrapSystemService("phone") is IBinder)
    }

    @Test
    fun isReady_requiresRunningAndGranted() {
        val notReady = FakeBridge(running = true, granted = false)
        val ready = FakeBridge(running = true, granted = true)
        assertFalse(notReady.isReady())
        assertTrue(ready.isReady())
    }

    private class FakeBridge(
        private val running: Boolean,
        private val granted: Boolean,
    ) : PrivilegeBridge {
        override fun isRunning(): Boolean = running
        override fun isGranted(): Boolean = granted
        override fun requestPermission(requestCode: Int) = Unit
        override fun getUid(): Int = -1
        override fun wrapSystemService(name: String): IBinder = Binder()
    }
}
