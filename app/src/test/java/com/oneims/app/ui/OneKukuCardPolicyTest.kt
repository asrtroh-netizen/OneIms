package com.oneims.app.ui

import com.oneims.app.core.OneKukuActivationPhase
import com.oneims.app.onekuku.OneKukuRunnerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OneKukuCardPolicyTest {

    @Test
    fun resolve_mapsFourStates() {
        assertEquals(
            OneKukuCardState.INACTIVE,
            OneKukuCardPolicy.resolve(
                serviceReady = false,
                isExecuting = false,
                channelSleeping = false,
            ),
        )
        assertEquals(
            OneKukuCardState.READY,
            OneKukuCardPolicy.resolve(
                serviceReady = true,
                isExecuting = false,
                channelSleeping = false,
            ),
        )
        assertEquals(
            OneKukuCardState.SLEEPING,
            OneKukuCardPolicy.resolve(
                serviceReady = true,
                isExecuting = false,
                channelSleeping = true,
            ),
        )
        // 执行中对外仍是就绪。
        assertEquals(
            OneKukuCardState.READY,
            OneKukuCardPolicy.resolve(
                serviceReady = true,
                isExecuting = true,
                channelSleeping = true,
            ),
        )
    }

    @Test
    fun fromActivationPhase_collapsesPipelineIntoActivating() {
        assertEquals(
            OneKukuCardState.ACTIVATING,
            OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.WAITING_PAIR),
        )
        assertEquals(
            OneKukuCardState.ACTIVATING,
            OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.PAIRING),
        )
        assertEquals(
            OneKukuCardState.ACTIVATING,
            OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.CONNECTING),
        )
        assertEquals(
            OneKukuCardState.ACTIVATING,
            OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.STARTING),
        )
        assertEquals(
            OneKukuCardState.INACTIVE,
            OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.FAILED),
        )
        assertNull(OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.ACTIVE))
    }

    @Test
    fun litStageCount_isFourStepRail() {
        assertEquals(1, OneKukuCardPolicy.litStageCount(OneKukuCardState.INACTIVE))
        assertEquals(2, OneKukuCardPolicy.litStageCount(OneKukuCardState.ACTIVATING))
        assertEquals(3, OneKukuCardPolicy.litStageCount(OneKukuCardState.READY))
        assertEquals(4, OneKukuCardPolicy.litStageCount(OneKukuCardState.SLEEPING))
        assertEquals(4, OneKukuCardPolicy.stageLabelRes().size)
    }

    @Test
    fun isChannelSleeping_onlyWhenRunnerSleeping() {
        assertTrue(OneKukuCardPolicy.isChannelSleeping(OneKukuRunnerState.SLEEPING))
        assertTrue(!OneKukuCardPolicy.isChannelSleeping(OneKukuRunnerState.ACTIVE))
        assertTrue(!OneKukuCardPolicy.isChannelSleeping(OneKukuRunnerState.EXECUTING))
    }

    @Test
    fun enum_hasExactlyFourStates() {
        assertEquals(4, OneKukuCardState.entries.size)
        assertTrue(OneKukuCardPolicy.isBusy(OneKukuCardState.ACTIVATING))
        assertTrue(OneKukuCardPolicy.isAlert(OneKukuCardState.INACTIVE))
        assertTrue(OneKukuCardPolicy.isSettled(OneKukuCardState.READY))
        assertTrue(OneKukuCardPolicy.isSettled(OneKukuCardState.SLEEPING))
    }
}
