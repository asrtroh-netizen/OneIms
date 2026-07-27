package com.onetools.app.live.adapter

import com.onetools.app.live.LiveStatusSource
import com.onetools.app.live.capsule.allowsLongPill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 美团完整状态链 + 滴滴行程状态契约单测。
 * 包名取自 [LiveStatusSource]；解析走适配器 / [VendorAdapterRegistry.parse]。
 */
class MeituanDidiVendorAdapterTest {
    private val meituanPkg = LiveStatusSource.MEITUAN.packages.first()
    private val meituanTakeoutPkg = "com.sankuai.meituan.takeoutnew"
    private val didiPkg = LiveStatusSource.DIDI.packages.first()

    // ── 美团 ──────────────────────────────────────────────

    @Test
    fun meituanOrderedStage0() {
        val session = accept(
            MeituanVendorAdapter,
            pkg = meituanPkg,
            title = "美团外卖",
            text = "订单已下单成功",
            ongoing = true,
        )
        assertEquals("已下单", session.pillPrimary)
        assertEquals(0, session.activeStageIndex)
        assertNull(session.pillSecondary)
        assertFalse(session.allowsLongPill())
    }

    @Test
    fun meituanCookingStage1NoEta() {
        val session = accept(
            MeituanVendorAdapter,
            pkg = meituanPkg,
            title = "商家已接单",
            text = "正在备餐",
            ongoing = true,
        )
        assertEquals("已出餐", session.pillPrimary)
        assertEquals(1, session.activeStageIndex)
        assertNull(session.pillSecondary)
        assertFalse(session.allowsLongPill())
    }

    @Test
    fun meituanDeliveringWithEtaAllowsLongPill() {
        val session = accept(
            MeituanVendorAdapter,
            pkg = meituanTakeoutPkg,
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
            pkg = meituanTakeoutPkg,
            title = "骑手已送达",
            text = "订单已完成",
        )
        assertEquals("已送达", session.pillPrimary)
        assertNull(session.pillSecondary)
        assertFalse(session.allowsLongPill())
        assertEquals(3, session.activeStageIndex)
    }

    @Test
    fun meituanMarketingNoiseIgnored() {
        val outcome = VendorAdapterRegistry.parse(
            NotificationSnippet(
                packageName = meituanPkg,
                key = "mkt",
                title = "领红包立减",
                text = "优惠券到账",
                isOngoing = false,
            ),
        )
        assertEquals(AdapterOutcome.Ignored, outcome)
    }

    @Test
    fun meituanPaymentSuccessNonOngoingAccepted() {
        val session = accept(
            MeituanVendorAdapter,
            pkg = meituanPkg,
            title = "美团外卖",
            text = "订单支付成功，等待商家接单",
            ongoing = false,
        )
        assertEquals("已下单", session.pillPrimary)
        assertEquals(0, session.activeStageIndex)
    }

    @Test
    fun meituanSankuaiSubpackageMatchedViaRegistry() {
        val outcome = VendorAdapterRegistry.parse(
            NotificationSnippet(
                packageName = "com.sankuai.meituan.meituanwaimai",
                key = "subpkg",
                title = "美团外卖",
                text = "订单已提交成功",
                isOngoing = false,
            ),
        )
        assertTrue(outcome is AdapterOutcome.Accepted)
        assertEquals(
            "已下单",
            (outcome as AdapterOutcome.Accepted).session.pillPrimary,
        )
    }

    // ── 滴滴 ──────────────────────────────────────────────

    @Test
    fun didiWaitingAndOnTripStages() {
        val waiting = accept(
            DidiVendorAdapter,
            pkg = didiPkg,
            title = "正在呼叫司机",
            text = "请稍候匹配中",
            ongoing = true,
        )
        assertEquals("等待接驾", waiting.pillPrimary)
        assertEquals(0, waiting.activeStageIndex)

        val coming = accept(
            DidiVendorAdapter,
            pkg = didiPkg,
            title = "司机已接单",
            text = "正在赶来",
            ongoing = true,
        )
        assertTrue(
            "期望「司机赶来」或「已接单」，实际=${coming.pillPrimary}",
            coming.pillPrimary == "司机赶来" || coming.pillPrimary == "已接单",
        )
        assertEquals(1, coming.activeStageIndex)

        val arrived = accept(
            DidiVendorAdapter,
            pkg = didiPkg,
            title = "司机已到达上车点",
            text = "粤B·8A23 王师傅 预计 2 分钟",
            ongoing = true,
        )
        // 当前契约：司机已到达上车点归入 stage1，primary=司机赶来
        assertEquals("司机赶来", arrived.pillPrimary)
        assertEquals("2分钟", arrived.pillSecondary)
        assertTrue(arrived.detailRows.any { it.first == "车牌" && it.second.contains("粤B") })
        assertEquals(1, arrived.activeStageIndex)

        val trip = accept(
            DidiVendorAdapter,
            pkg = didiPkg,
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
            pkg = didiPkg,
            title = "行程已完成",
            text = "感谢乘坐",
        )
        assertEquals("已到达", session.pillPrimary)
        assertNull(session.pillSecondary)
        assertFalse(session.allowsLongPill())
        assertEquals(3, session.activeStageIndex)
    }

    @Test
    fun didiPlateInDetailRowsViaRegistry() {
        val outcome = VendorAdapterRegistry.parse(
            NotificationSnippet(
                packageName = didiPkg,
                key = "dd-plate",
                title = "行程中",
                text = "司机张师傅 粤B·D1234 预计3分钟",
                isOngoing = true,
            ),
        )
        assertTrue(outcome is AdapterOutcome.Accepted)
        val rows = (outcome as AdapterOutcome.Accepted).session.detailRows
        assertTrue(
            "detailRows 应含车牌 D1234，实际=$rows",
            rows.any { it.first == "车牌" && it.second.contains("D1234") },
        )
    }

    @Test
    fun didiMarketingNoiseIgnored() {
        val outcome = VendorAdapterRegistry.parse(
            NotificationSnippet(
                packageName = didiPkg,
                key = "dd-mkt",
                title = "滴滴出行",
                text = "邀请有礼领券立减",
                isOngoing = false,
            ),
        )
        assertEquals(AdapterOutcome.Ignored, outcome)
    }

    @Test
    fun didiHailingNonOngoingAccepted() {
        val session = accept(
            DidiVendorAdapter,
            pkg = didiPkg,
            title = "滴滴出行",
            text = "正在为您呼叫快车，请稍候",
            ongoing = false,
        )
        assertEquals("等待接驾", session.pillPrimary)
        assertEquals(0, session.activeStageIndex)
    }

    @Test
    fun didiSubpackageMatchedViaRegistry() {
        val outcome = VendorAdapterRegistry.parse(
            NotificationSnippet(
                packageName = "com.sdu.didi.psnger.v3",
                key = "dd-sub",
                title = "滴滴",
                text = "司机正在赶来 预计5分钟",
                isOngoing = true,
            ),
        )
        assertTrue(outcome is AdapterOutcome.Accepted)
        assertEquals(
            "司机赶来",
            (outcome as AdapterOutcome.Accepted).session.pillPrimary,
        )
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
