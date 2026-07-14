package com.oneims.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OneKukuHomeToolsTest {

    @Test
    fun sanitizeUserText_hidesPrivilegeChannelNames() {
        val sanitized = OneKukuHomeTools.sanitizeUserText(
            "Shizuku binder failed; try adb again via Termux",
        )
        assertFalse(sanitized.contains("Shizuku", ignoreCase = true))
        assertFalse(sanitized.contains("adb", ignoreCase = true))
        assertFalse(sanitized.contains("Termux", ignoreCase = true))
        assertEquals(
            "OneKuku binder failed; try 调试桥 again via 终端助手",
            sanitized,
        )
    }
}
