package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TempRootFailClassifyTest {
    @Test
    fun classifiesSelinuxDaemonBlock() {
        val reason = OneKukuTempRootActivator.classifySuMissingReason(
            exploitOut = "enforce=1 slide ok",
            suOut = "su: connect daemon: Permission denied",
        )
        assertEquals("selinux_blocks_su_daemon", reason)
    }

    @Test
    fun classifiesGenericMissing() {
        val reason = OneKukuTempRootActivator.classifySuMissingReason(
            exploitOut = "cfi miss",
            suOut = "No such file",
        )
        assertEquals("exploit_ran_but_su_missing", reason)
    }
}
