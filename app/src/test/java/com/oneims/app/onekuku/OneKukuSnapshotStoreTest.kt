package com.oneims.app.onekuku

import com.oneims.app.model.SimInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OneKukuSnapshotStoreTest {

    @Test
    fun hashIccidIsStableAndTruncated() {
        val a = OneKukuSnapshotStore.hashIccid("89014103211118510720")
        val b = OneKukuSnapshotStore.hashIccid("89014103211118510720")
        assertEquals(a, b)
        assertEquals(16, a!!.length)
        assertNull(OneKukuSnapshotStore.hashIccid(" "))
    }

    @Test
    fun maskSensitiveValues() {
        assertEquals("***", OneKukuSnapshotStore.maskSensitiveValue("apn", "password", "secret"))
        assertEquals("***", OneKukuSnapshotStore.maskSensitiveValue("id", "phoneNumber", "13800138000"))
        val ua = OneKukuSnapshotStore.maskSensitiveValue(
            "identity",
            "imsUserAgent",
            "VeryLongUserAgentValueHere",
        )
        assertTrue(ua.contains("…") || ua == "***")
    }

    @Test
    fun maskHashHidesMiddle() {
        val masked = OneKukuSnapshotStore.maskHash("abcdefghijklmnop")
        assertEquals("ab****op", masked)
    }
}
