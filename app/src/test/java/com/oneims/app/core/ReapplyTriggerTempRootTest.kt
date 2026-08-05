package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ReapplyTriggerTempRootTest {
    @Test
    fun fromStored_recognizesTempRoot() {
        assertEquals(ReapplyTrigger.TEMP_ROOT, ReapplyTrigger.fromStored("temp_root"))
    }
}
