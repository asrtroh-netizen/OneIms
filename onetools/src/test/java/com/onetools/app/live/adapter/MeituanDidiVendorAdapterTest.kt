package com.onetools.app.live.adapter

import com.onetools.app.live.capsule.allowsLongPill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeituanDidiVendorAdapterTest {
    @Test
    fun meituanDeliveringWithEtaAllowsLongPill() {
        val session = accept(
            MeituanVendorAdapter,
            pkg = "com.sankuai.meituan.takeoutnew",
            title = "骑手正在配送",
            text = "预计 12 分钟送达",
        )
        assertEquals("配送中", session.pillPrimary)
        assertEquals("12分钟", session.pillSecondary)
        assertTrue(session.allowsLongPill())
        assertEquals(2, session.activeStageIndex)
    }

    @Test
    fun meituanDeliveredWinsOverRiderKeyword() {
        val session = accept(
            MeituanVendorAdapter,
            pkg = "com.sankuai.meituan.takeoutnew",
            title = "骑手已送达",
            text = "订单已完成",
        )
        assertEquals("已送达", session.pillPrimary)
        assertNull(session.pillSecondary)
        assertFalse(session.allowsLongPill())
        assertEquals(3, session.activeStageIndex)
    }

    @Test
    fun meituanNoEtaStaysShortOnly() {
        val session = accept(
            MeituanVendorAdapter,
            pkg = "com.sankuai.meituan",
            title = "商家已接单",
            text = "正在备餐",
            ongoing = true,
        )
        assertEquals("已出餐", session.pillPrimary)
        assertNull(session.pillSecondary)
        assertFalse(session.allowsLongPill())
    }

    @Test
    fun meituanMarketingNoiseIgnored() {
        val outcome = MeituanVendorAdapter.parse(
            NotificationSnippet(
                packageName = "com.sankuai.meituan",
                key = "mkt",
                title = "领红包立减",
                text = "优惠券到账",
                isOngoing = false,
            ),
        )
        assertEquals(AdapterOutcome.Ignored, outcome)
    }

    @Test
    fun didiWaitingAndOnTripStages() {
        val waiting = accept(
            DidiVendorAdapter,
            pkg = "com.sdu.didi.psnger",
            title = "正在呼叫司机",
            text = "请稍候匹配中",
            ongoing = true,
        )
        assertEquals("等待接驾", waiting.pillPrimary)
        assertEquals(0, waiting.activeStageIndex)

        val arrived = accept(
            DidiVendorAdapter,
            pkg = "com.sdu.didi.psnger",
            title = "司机已到达上车点",
            text = "粤B·8A23 王师傅 预计 2 分钟",
            ongoing = true,
        )
        assertEquals("司机赶来", arrived.pillPrimary)
        assertEquals("2分钟", arrived.pillSecondary)
        assertTrue(arrived.detailRows.any { it.first == "车牌" && it.second.contains("粤B") })
        assertEquals(1, arrived.activeStageIndex)

        val trip = accept(
            DidiVendorAdapter,
            pkg = "com.sdu.didi.psnger",
            title = "行程中",
            text = "前往目的地 预计 8 分钟",
            ongoing = true,
        )
        assertEquals("行程中", trip.pillPrimary)
        assertEquals(2, trip.activeStageIndex)
    }

    @Test
    fun didiCompletedNoLongPill() {
        val session = accept(
            DidiVendorAdapter,
            pkg = "com.sdu.didi.psnger",
            title = "行程已完成",
            text = "感谢乘坐",
        )
        assertEquals("已到达", session.pillPrimary)
        assertNull(session.pillSecondary)
        assertFalse(session.allowsLongPill())
    }

    private fun accept(
        adapter: VendorAdapter,
        pkg: String,
        title: String,
        text: String,
        ongoing: Boolean = true,
    ) = (adapter.parse(
        NotificationSnippet(
            packageName = pkg,
            key = "t-${title.hashCode()}",
            title = title,
            text = text,
            isOngoing = ongoing,
        ),
    ) as AdapterOutcome.Accepted).session
}
