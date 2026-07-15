package com.oneims.app.ui

import com.oneims.app.core.OneKukuActivationPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OneKukuCardPolicyTest {

    @Test
    fun resolve_mapsReadyPath() {
        assertEquals(
            OneKukuCardState.INACTIVE,
            OneKukuCardPolicy.resolve(false, isExecuting = false, taskComplete = false),
        )
        assertEquals(
            OneKukuCardState.SLEEPING,
            OneKukuCardPolicy.resolve(true, isExecuting = false, taskComplete = false),
        )
        assertEquals(
            OneKukuCardState.EXECUTING,
            OneKukuCardPolicy.resolve(true, isExecuting = true, taskComplete = false),
        )
        assertEquals(
            OneKukuCardState.ACTIVE,
            OneKukuCardPolicy.resolve(true, isExecuting = false, taskComplete = true),
        )
    }

    @Test
    fun resolve_executingTakesPriorityOverComplete() {
        assertEquals(
            OneKukuCardState.EXECUTING,
            OneKukuCardPolicy.resolve(true, isExecuting = true, taskComplete = true),
        )
    }

    @Test
    fun resolve_inactiveEvenIfExecutingOrCompleteFlagsSet() {
        assertEquals(
            OneKukuCardState.INACTIVE,
            OneKukuCardPolicy.resolve(false, isExecuting = true, taskComplete = true),
        )
    }

    @Test
    fun fromActivationPhase_mapsNineStatePipeline() {
        assertEquals(
            OneKukuCardState.WAITING_PAIR,
            OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.WAITING_PAIR),
        )
        assertEquals(
            OneKukuCardState.PAIRING,
            OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.PAIRING),
        )
        assertEquals(
            OneKukuCardState.CONNECTING,
            OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.CONNECTING),
        )
        assertEquals(
            OneKukuCardState.STARTING,
            OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.STARTING),
        )
        assertEquals(
            OneKukuCardState.FAILED,
            OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.FAILED),
        )
        assertNull(OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.ACTIVE))
        assertNull(OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.IDLE))
    }

    @Test
    fun litStageCount_mapsNineStatesOntoFourProgressDots() {
        assertEquals(1, OneKukuCardPolicy.litStageCount(OneKukuCardState.INACTIVE))
        assertEquals(1, OneKukuCardPolicy.litStageCount(OneKukuCardState.FAILED))
        assertEquals(2, OneKukuCardPolicy.litStageCount(OneKukuCardState.WAITING_PAIR))
        assertEquals(2, OneKukuCardPolicy.litStageCount(OneKukuCardState.PAIRING))
        assertEquals(2, OneKukuCardPolicy.litStageCount(OneKukuCardState.CONNECTING))
        assertEquals(2, OneKukuCardPolicy.litStageCount(OneKukuCardState.STARTING))
        assertEquals(3, OneKukuCardPolicy.litStageCount(OneKukuCardState.EXECUTING))
        assertEquals(4, OneKukuCardPolicy.litStageCount(OneKukuCardState.ACTIVE))
        assertEquals(4, OneKukuCardPolicy.litStageCount(OneKukuCardState.SLEEPING))
    }

    @Test
    fun enum_hasExactlyNineStates() {
        assertEquals(9, OneKukuCardState.entries.size)
        assertTrue(OneKukuCardPolicy.isBusy(OneKukuCardState.PAIRING))
        assertTrue(OneKukuCardPolicy.isAlert(OneKukuCardState.WAITING_PAIR))
    }
}
