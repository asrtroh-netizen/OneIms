package com.oneims.app.core.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChannelEngineTest {

    @Test
    fun current_defaultsToCareMin() {
        // onekuku：默认已切 CARE_MIN（宿主内嵌 MINI server）。
        assertEquals(ChannelEngine.CARE_MIN, ChannelEngine.current())
    }

    @Test
    fun processNiceNames_areDistinctFromPlusCare() {
        assertEquals("onebridge_server", ChannelEngine.processNiceName(ChannelEngine.ONEBRIDGE))
        assertEquals("onekuku_server", ChannelEngine.processNiceName(ChannelEngine.CARE_MIN))
        assertNotEquals(
            ChannelEngine.processNiceName(ChannelEngine.ONEBRIDGE),
            ChannelEngine.processNiceName(ChannelEngine.CARE_MIN),
        )
        // 邻仓 Care MINI 用 shizuku_plus_server；宿主融合必须避开以免同机误杀。
        assertNotEquals("shizuku_plus_server", ChannelEngine.PROCESS_CARE_MIN)
    }
}
