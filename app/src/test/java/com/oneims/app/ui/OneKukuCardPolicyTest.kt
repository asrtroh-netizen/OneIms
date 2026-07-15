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
            OneKukuCardState.READY,
            OneKukuCardPolicy.resolve(true, isExecuting = false, taskComplete = false),
        )
        assertEquals(
            OneKukuCardState.EXECUTING,
            OneKukuCardPolicy.resolve(true, isExecuting = true, taskComplete = false),
        )
        assertEquals(
            OneKukuCardState.READY,
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
            OneKukuCardState.FAILED,
            OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.FAILED),
        )
        assertNull(OneKukuCardPolicy.fromActivationPhase(OneKukuActivationPhase.ACTIVE))
    }

    @Test
    fun litStageCount_isFiveStepRail() {
        assertEquals(1, OneKukuCardPolicy.litStageCount(OneKukuCardState.INACTIVE))
        assertEquals(2, OneKukuCardPolicy.litStageCount(OneKukuCardState.ACTIVATING))
        assertEquals(3, OneKukuCardPolicy.litStageCount(OneKukuCardState.READY))
        assertEquals(4, OneKukuCardPolicy.litStageCount(OneKukuCardState.EXECUTING))
        assertEquals(5, OneKukuCardPolicy.litStageCount(OneKukuCardState.FAILED))
        assertEquals(5, OneKukuCardPolicy.stageLabelRes().size)
    }

    @Test
    fun enum_hasExactlyFiveStates() {
        assertEquals(5, OneKukuCardState.entries.size)
        assertTrue(OneKukuCardPolicy.isBusy(OneKukuCardState.ACTIVATING))
        assertTrue(OneKukuCardPolicy.isAlert(OneKukuCardState.INACTIVE))
        assertTrue(OneKukuCardPolicy.isAlert(OneKukuCardState.FAILED))
    }
}
