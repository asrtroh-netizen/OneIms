package com.oneims.app.ui

import com.oneims.app.core.OneKukuActivationPhase
import com.oneims.app.onekuku.OneKukuRunnerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OneKukuCardPolicyTest {

    @Test
    fun resolve_mapsThreeStates_sleepingShowsReady() {
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
        // 后台待命不再单独占一态。
        assertEquals(
            OneKukuCardState.READY,
            OneKukuCardPolicy.resolve(
                serviceReady = true,
                isExecuting = false,
                channelSleeping = true,
            ),
        )
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
    fun litStageCount_isThreeStepRail() {
        assertEquals(1, OneKukuCardPolicy.litStageCount(OneKukuCardState.INACTIVE))
        assertEquals(2, OneKukuCardPolicy.litStageCount(OneKukuCardState.ACTIVATING))
        assertEquals(3, OneKukuCardPolicy.litStageCount(OneKukuCardState.READY))
        assertEquals(3, OneKukuCardPolicy.stageLabelRes().size)
    }

    @Test
    fun isChannelSleeping_onlyWhenRunnerSleeping() {
        assertTrue(OneKukuCardPolicy.isChannelSleeping(OneKukuRunnerState.SLEEPING))
        assertTrue(!OneKukuCardPolicy.isChannelSleeping(OneKukuRunnerState.ACTIVE))
        assertTrue(!OneKukuCardPolicy.isChannelSleeping(OneKukuRunnerState.EXECUTING))
    }

    @Test
    fun enum_hasExactlyThreeStates() {
        assertEquals(3, OneKukuCardState.entries.size)
        assertTrue(OneKukuCardPolicy.isBusy(OneKukuCardState.ACTIVATING))
        assertTrue(OneKukuCardPolicy.isAlert(OneKukuCardState.INACTIVE))
        assertTrue(OneKukuCardPolicy.isSettled(OneKukuCardState.READY))
    }
}
