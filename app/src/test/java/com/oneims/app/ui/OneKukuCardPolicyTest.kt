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
    fun fromActivationPhase_mapsNineStatePipeline() {
        assertEquals(
            OneKukuCardState.WAITING_PAIR,
            OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.WAITING_PAIR),
        )
        assertEquals(
            OneKukuCardState.FAILED,
            OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.FAILED),
        )
        assertNull(OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.ACTIVE))
    }

    @Test
    fun litStageCount_isNineStepRail() {
        assertEquals(1, OneKukuCardPolicy.litStageCount(OneKukuCardState.INACTIVE))
        assertEquals(2, OneKukuCardPolicy.litStageCount(OneKukuCardState.WAITING_PAIR))
        assertEquals(3, OneKukuCardPolicy.litStageCount(OneKukuCardState.PAIRING))
        assertEquals(4, OneKukuCardPolicy.litStageCount(OneKukuCardState.CONNECTING))
        assertEquals(5, OneKukuCardPolicy.litStageCount(OneKukuCardState.STARTING))
        assertEquals(6, OneKukuCardPolicy.litStageCount(OneKukuCardState.ACTIVE))
        assertEquals(7, OneKukuCardPolicy.litStageCount(OneKukuCardState.SLEEPING))
        assertEquals(8, OneKukuCardPolicy.litStageCount(OneKukuCardState.EXECUTING))
        assertEquals(1, OneKukuCardPolicy.litStageCount(OneKukuCardState.FAILED))
        assertEquals(9, OneKukuCardPolicy.stageLabelRes().size)
    }

    @Test
    fun enum_hasExactlyNineStates() {
        assertEquals(9, OneKukuCardState.entries.size)
        assertTrue(OneKukuCardPolicy.isBusy(OneKukuCardState.PAIRING))
        assertTrue(OneKukuCardPolicy.isAlert(OneKukuCardState.WAITING_PAIR))
    }
}
