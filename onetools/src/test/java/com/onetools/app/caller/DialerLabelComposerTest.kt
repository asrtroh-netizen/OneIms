package com.onetools.app.caller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialerLabelComposerTest {
    @Test
    fun geoOnlyKeepsNumberAsName() {
        val geo = CnMobileGeo.Hit("北京", "北京", "中国移动")
        val r = DialerLabelComposer.compose(
            numberDisplay = "138 0013 8000",
            geo = geo,
            ruleKind = null,
            ruleTag = null,
            fallbackAllow = "白名单",
            fallbackLabel = "已标记",
            fallbackBlock = "拦截名单",
            spamFmt = { "骚扰 · $it" },
        )
        assertEquals("138 0013 8000", r!!.displayName)
        assertEquals(geo.dialerLine(), r.label)
    }

    @Test
    fun labelRuleUsesTagAsNameGeoAsLabel() {
        val geo = CnMobileGeo.Hit("上海", "上海", "中国联通")
        val r = DialerLabelComposer.compose(
            numberDisplay = "95588",
            geo = geo,
            ruleKind = CallRuleKind.LABEL,
            ruleTag = "工商银行客服",
            fallbackAllow = "白名单",
            fallbackLabel = "已标记",
            fallbackBlock = "拦截名单",
            spamFmt = { "骚扰 · $it" },
        )
        assertEquals("工商银行客服", r!!.displayName)
        assertEquals(geo.dialerLine(), r.label)
    }

    @Test
    fun nothingReturnsNull() {
        val r = DialerLabelComposer.compose(
            numberDisplay = "",
            geo = null,
            ruleKind = null,
            ruleTag = null,
            fallbackAllow = "白名单",
            fallbackLabel = "已标记",
            fallbackBlock = "拦截名单",
            spamFmt = { it },
        )
        assertNull(r)
    }
}
