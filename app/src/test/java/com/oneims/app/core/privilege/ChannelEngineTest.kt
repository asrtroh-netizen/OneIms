package com.oneims.app.core.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChannelEngineTest {

    @Test
    fun current_isOneBridgeOnly() {
        assertEquals(ChannelEngine.ONEBRIDGE, ChannelEngine.current())
    }

    @Test
    fun processNiceName_isOneBridgeServer() {
        assertEquals("onebridge_server", ChannelEngine.processNiceName())
        assertEquals("onebridge_server", ChannelEngine.PROCESS_ONEBRIDGE)
        assertNotEquals("onekuku_server", ChannelEngine.processNiceName())
        assertNotEquals("shizuku_plus_server", ChannelEngine.processNiceName())
    }
}
