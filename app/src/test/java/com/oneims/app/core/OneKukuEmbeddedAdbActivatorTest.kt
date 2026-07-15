package com.oneims.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneKukuEmbeddedAdbActivatorTest {

    @Test
    fun shellBootOutput_requiresStartedMarker() {
        assertTrue(OneKukuEmbeddedAdbActivator.isShellBootOutputOk("OneBridge_started\n"))
        assertFalse(OneKukuEmbeddedAdbActivator.isShellBootOutputOk(""))
        assertFalse(OneKukuEmbeddedAdbActivator.isShellBootOutputOk("OneBridge_missing"))
        assertFalse(
            OneKukuEmbeddedAdbActivator.isShellBootOutputOk("OneBridge_missing\nOneBridge_started"),
        )
    }
}
