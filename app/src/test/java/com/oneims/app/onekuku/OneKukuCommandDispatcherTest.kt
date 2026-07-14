package com.oneims.app.onekuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneKukuCommandDispatcherTest {

    @Test
    fun whitelistCoversAllCommandsWithoutShell() {
        val values = OneKukuCommand.entries.toSet()
        assertTrue(values.contains(OneKukuCommand.RESTORE_ALL_CALL_CONFIGS))
        assertTrue(values.contains(OneKukuCommand.CHECK_ONEKUKU_STATUS))
        assertTrue(values.contains(OneKukuCommand.SLEEP_ONEKUKU))
        assertEquals(10, values.size)
        values.forEach { command ->
            assertTrue(
                "command name must not look like shell: $command",
                !command.name.contains("SHELL", ignoreCase = true),
            )
        }
    }

    @Test
    fun snapshotHashIsStableAndTruncated() {
        val a = OneKukuSnapshotStore.hashIccid("89014103211118510720")
        val b = OneKukuSnapshotStore.hashIccid("89014103211118510720")
        val c = OneKukuSnapshotStore.hashIccid("89014103211118510721")
        assertEquals(a, b)
        assertEquals(16, a!!.length)
        assertTrue(a != c)
        assertEquals(null, OneKukuSnapshotStore.hashIccid("  "))
    }
}
