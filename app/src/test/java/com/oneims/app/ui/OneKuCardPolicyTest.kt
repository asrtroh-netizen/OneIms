package com.oneims.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OneKuCardPolicyTest {

    @Test
    fun resolve_mapsFourStates() {
        assertEquals(
            OneKuCardState.INACTIVE,
            OneKuCardPolicy.resolve(
                serviceReady = false,
                isExecuting = false,
                taskComplete = false,
            ),
        )
        assertEquals(
            OneKuCardState.SLEEPING,
            OneKuCardPolicy.resolve(
                serviceReady = true,
                isExecuting = false,
                taskComplete = false,
            ),
        )
        assertEquals(
            OneKuCardState.RUNNING,
            OneKuCardPolicy.resolve(
                serviceReady = true,
                isExecuting = true,
                taskComplete = false,
            ),
        )
        assertEquals(
            OneKuCardState.COMPLETE,
            OneKuCardPolicy.resolve(
                serviceReady = true,
                isExecuting = false,
                taskComplete = true,
            ),
        )
    }

    @Test
    fun resolve_executingTakesPriorityOverComplete() {
        assertEquals(
            OneKuCardState.RUNNING,
            OneKuCardPolicy.resolve(
                serviceReady = true,
                isExecuting = true,
                taskComplete = true,
            ),
        )
    }

    @Test
    fun resolve_inactiveEvenIfExecutingOrCompleteFlagsSet() {
        assertEquals(
            OneKuCardState.INACTIVE,
            OneKuCardPolicy.resolve(
                serviceReady = false,
                isExecuting = true,
                taskComplete = true,
            ),
        )
    }

    @Test
    fun litStageCount_matchesProgressContract() {
        assertEquals(1, OneKuCardPolicy.litStageCount(OneKuCardState.INACTIVE))
        assertEquals(2, OneKuCardPolicy.litStageCount(OneKuCardState.SLEEPING))
        assertEquals(3, OneKuCardPolicy.litStageCount(OneKuCardState.RUNNING))
        assertEquals(4, OneKuCardPolicy.litStageCount(OneKuCardState.COMPLETE))
    }
}
