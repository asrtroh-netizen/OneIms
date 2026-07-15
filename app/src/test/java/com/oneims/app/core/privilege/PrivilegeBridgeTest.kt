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
        val bridge = object : PrivilegeBridge by NoopListenersBridge() {
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

    @Test
    fun fallback_fansOutBinderReceivedToBothSources() {
        val primary = RecordingBridge(running = false)
        val fallback = RecordingBridge(running = true)
        val bridge = FallbackPrivilegeBridge(primary, fallback)
        var hits = 0
        bridge.addBinderReceivedListener({ hits += 1 }, sticky = true)
        assertEquals(1, primary.receivedCount)
        assertEquals(1, fallback.receivedCount)
        assertEquals(1, hits)
    }

    private open class NoopListenersBridge : PrivilegeBridge {
        override fun isRunning(): Boolean = false
        override fun isGranted(): Boolean = false
        override fun requestPermission(requestCode: Int) = Unit
        override fun getUid(): Int = -1
        override fun wrapSystemService(name: String): IBinder = Binder()
        override fun addBinderReceivedListener(listener: () -> Unit, sticky: Boolean) = Unit
        override fun removeBinderReceivedListener(listener: () -> Unit) = Unit
        override fun addBinderDeadListener(listener: () -> Unit) = Unit
        override fun removeBinderDeadListener(listener: () -> Unit) = Unit
        override fun addRequestPermissionResultListener(
            listener: PrivilegeBridge.PermissionResultListener,
        ) = Unit
        override fun removeRequestPermissionResultListener(
            listener: PrivilegeBridge.PermissionResultListener,
        ) = Unit
    }

    private class FakeBridge(
        private val running: Boolean,
        private val granted: Boolean,
    ) : NoopListenersBridge() {
        override fun isRunning(): Boolean = running
        override fun isGranted(): Boolean = granted
    }

    private class RecordingBridge(
        private val running: Boolean,
    ) : NoopListenersBridge() {
        var receivedCount: Int = 0
            private set

        override fun isRunning(): Boolean = running

        override fun addBinderReceivedListener(listener: () -> Unit, sticky: Boolean) {
            receivedCount += 1
            if (sticky && running) listener()
        }
    }
}
