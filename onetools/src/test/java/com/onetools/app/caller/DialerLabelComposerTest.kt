package com.onetools.app.caller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialerLabelComposerTest {
    @Test
    fun geoOnlyLooksNative() {
        val geo = CnMobileGeo.Hit("北京", "北京", "中国移动")
        val r = DialerLabelComposer.compose(
            geo = geo,
            ruleKind = null,
            ruleTag = null,
            fallbackAllow = "白名单",
            fallbackLabel = "已标记",
            fallbackBlock = "拦截名单",
            spamFmt = { "骚扰 · $it" },
        )
        assertEquals("北京 · 移动", r!!.displayName)
    }

    @Test
    fun labelPlusGeoJoinsCleanly() {
        val geo = CnMobileGeo.Hit("上海", "上海", "中国联通")
        val r = DialerLabelComposer.compose(
            geo = geo,
            ruleKind = CallRuleKind.LABEL,
            ruleTag = "工商银行客服",
            fallbackAllow = "白名单",
            fallbackLabel = "已标记",
            fallbackBlock = "拦截名单",
            spamFmt = { "骚扰 · $it" },
        )
        assertEquals("工商银行客服 · 上海 · 联通", r!!.displayName)
    }

    @Test
    fun nothingReturnsNull() {
        val r = DialerLabelComposer.compose(
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
