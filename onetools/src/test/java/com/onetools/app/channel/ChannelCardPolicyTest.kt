package com.onetools.app.channel

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelCardPolicyTest {
    @Test
    fun resolve_mapsFourStates() {
        assertEquals(
            ChannelCardState.INACTIVE,
            ChannelCardPolicy.resolve(serviceReady = false, isExecuting = false, channelSleeping = false),
        )
        assertEquals(
            ChannelCardState.READY,
            ChannelCardPolicy.resolve(serviceReady = true, isExecuting = true, channelSleeping = false),
        )
        assertEquals(
            ChannelCardState.SLEEPING,
            ChannelCardPolicy.resolve(serviceReady = true, isExecuting = false, channelSleeping = true),
        )
        assertEquals(
            ChannelCardState.READY,
            ChannelCardPolicy.resolve(serviceReady = true, isExecuting = false, channelSleeping = false),
        )
    }

    @Test
    fun enum_hasExactlyFourStates() {
        assertEquals(4, ChannelCardState.entries.size)
    }
}
