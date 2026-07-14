package com.oneims.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OneKukuCardPolicyTest {

    @Test
    fun resolve_mapsFourStates() {
        assertEquals(
            OneKukuCardState.INACTIVE,
            OneKukuCardPolicy.resolve(
                serviceReady = false,
                isExecuting = false,
                taskComplete = false,
            ),
        )
        assertEquals(
            OneKukuCardState.SLEEPING,
            OneKukuCardPolicy.resolve(
                serviceReady = true,
                isExecuting = false,
                taskComplete = false,
            ),
        )
        assertEquals(
            OneKukuCardState.RUNNING,
            OneKukuCardPolicy.resolve(
                serviceReady = true,
                isExecuting = true,
                taskComplete = false,
            ),
        )
        assertEquals(
            OneKukuCardState.COMPLETE,
            OneKukuCardPolicy.resolve(
                serviceReady = true,
                isExecuting = false,
                taskComplete = true,
            ),
        )
    }

    @Test
    fun resolve_executingTakesPriorityOverComplete() {
        assertEquals(
            OneKukuCardState.RUNNING,
            OneKukuCardPolicy.resolve(
                serviceReady = true,
                isExecuting = true,
                taskComplete = true,
            ),
        )
    }

    @Test
    fun resolve_inactiveEvenIfExecutingOrCompleteFlagsSet() {
        assertEquals(
            OneKukuCardState.INACTIVE,
            OneKukuCardPolicy.resolve(
                serviceReady = false,
                isExecuting = true,
                taskComplete = true,
            ),
        )
    }

    @Test
    fun litStageCount_matchesProgressContract() {
        assertEquals(1, OneKukuCardPolicy.litStageCount(OneKukuCardState.INACTIVE))
        assertEquals(2, OneKukuCardPolicy.litStageCount(OneKukuCardState.SLEEPING))
        assertEquals(3, OneKukuCardPolicy.litStageCount(OneKukuCardState.RUNNING))
        assertEquals(4, OneKukuCardPolicy.litStageCount(OneKukuCardState.COMPLETE))
    }
}
