package com.onetools.app.caller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerNetworkQueryTest {
    @Test
    fun parse_spamWithLocation() {
        val raw = """
            {
              "phone": "13800138000",
              "is_spam": true,
              "tag": "房产中介",
              "confidence": 80,
              "source": "cloud",
              "data": {
                "province": "北京",
                "city": "北京",
                "cardType": "移动"
              }
            }
        """.trimIndent()
        val r = CallerNetworkQuery.parse(raw)
        assertTrue(r.isSpam)
        assertEquals("房产中介", r.tag)
        assertEquals("北京 · 移动", r.location?.dialerLine())
    }
}
