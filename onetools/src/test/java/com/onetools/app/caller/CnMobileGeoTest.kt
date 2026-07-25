package com.onetools.app.caller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CnMobileGeoTest {
    @Test
    fun parsesStarterAndLooksUpPrefix() {
        val json = """
            {
              "schema": "onetools.geo.v1",
              "prefixes": {
                "1380013": { "p": "北京", "c": "北京", "op": "中国移动" }
              }
            }
        """.trimIndent()
        val map = CnMobileGeo.parseJson(json)
        val hit = CnMobileGeo.lookupMap(map, "13800138000")
        assertNotNull(hit)
        assertEquals("北京 · 移动", hit!!.dialerLine())
    }
}
