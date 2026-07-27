package com.onetools.app.live.capsule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsulePillFaceTest {
    @Test
    fun meituanShortShowsTimeOnly() {
        val session = OneCapsuleTemplates.meituanDelivering()
        assertTrue(session.allowsLongPill())
        val face = session.pillFace(CapsulePillSize.SHORT)
        assertEquals("", face.primary)
        assertEquals("12分钟", face.secondary)
    }

    @Test
    fun meituanLongShowsStatusAndTime() {
        val session = OneCapsuleTemplates.meituanDelivering()
        val face = session.pillFace(CapsulePillSize.LONG)
        assertEquals("骑手已接单", face.primary)
        assertEquals("12分钟", face.secondary)
    }

    @Test
    fun cainiaoCodeOnlyNeverAllowsLong() {
        val session = OneCapsuleTemplates.cainiaoParcel()
        assertFalse(session.allowsLongPill())
        val shortFace = session.pillFace(CapsulePillSize.SHORT)
        val longFace = session.pillFace(CapsulePillSize.LONG)
        assertEquals("A3K9", shortFace.primary)
        assertEquals(null, shortFace.secondary)
        // 强制 LONG 请求仍落回短形态内容
        assertEquals(shortFace.primary, longFace.primary)
        assertEquals(shortFace.secondary, longFace.secondary)
    }

    @Test
    fun looksLikeTimeMetricRecognizesEta() {
        assertTrue(looksLikeTimeMetric("12分钟"))
        assertTrue(looksLikeTimeMetric("3分"))
        assertFalse(looksLikeTimeMetric("包裹"))
        assertFalse(looksLikeTimeMetric("A3K9"))
    }
}
