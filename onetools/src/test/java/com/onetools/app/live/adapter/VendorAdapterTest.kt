package com.onetools.app.live.adapter

import org.junit.Assert.assertTrue
import org.junit.Test

class VendorAdapterTest {
    @Test
    fun meituanDeliveringAccepted() {
        val out = MeituanVendorAdapter.parse(
            NotificationSnippet(
                packageName = "com.sankuai.meituan.takeoutnew",
                key = "k1",
                title = "美团外卖",
                text = "骑手配送中，预计12分钟送达",
                isOngoing = true,
            ),
        )
        assertTrue(out is AdapterOutcome.Accepted)
        val session = (out as AdapterOutcome.Accepted).session
        assertTrue(
            "primary=${session.pillPrimary}",
            session.pillPrimary.contains("配送"),
        )
        assertTrue(session.stages.size == 4)
        assertTrue(session.activeStageIndex == 2)
    }

    @Test
    fun meituanMarketingIgnored() {
        val out = MeituanVendorAdapter.parse(
            NotificationSnippet(
                packageName = "com.sankuai.meituan",
                key = "k2",
                title = "美团红包来了",
                text = "领券立减10元",
                isOngoing = false,
            ),
        )
        assertTrue(out is AdapterOutcome.Ignored)
    }

    @Test
    fun didiExtractsPlate() {
        val out = DidiVendorAdapter.parse(
            NotificationSnippet(
                packageName = "com.sdu.didi.psnger",
                key = "k3",
                title = "行程中",
                text = "司机张师傅 粤B·D1234 预计3分钟",
                isOngoing = true,
            ),
        )
        assertTrue(out is AdapterOutcome.Accepted)
        val rows = (out as AdapterOutcome.Accepted).session.detailRows
        assertTrue(rows.any { it.first == "车牌" && it.second.contains("D1234") })
    }

    @Test
    fun cainiaoDispatchStage() {
        val out = CainiaoVendorAdapter.parse(
            NotificationSnippet(
                packageName = "com.cainiao.wireless",
                key = "k4",
                title = "菜鸟裹裹",
                text = "包裹正在派送中",
                isOngoing = true,
            ),
        )
        assertTrue(out is AdapterOutcome.Accepted)
        assertTrue((out as AdapterOutcome.Accepted).session.pillPrimary.contains("派送"))
    }

    @Test
    fun elemeAndAmapAndMusicAndRailAccepted() {
        assertTrue(
            ElemeVendorAdapter.parse(
                NotificationSnippet("me.ele", "e1", "饿了么", "骑手配送中预计15分钟", true),
            ) is AdapterOutcome.Accepted,
        )
        assertTrue(
            VendorAdapterRegistry.parse(
                NotificationSnippet("com.autonavi.minimap", "a1", "导航", "剩余3公里 预计8分钟", true),
            ) is AdapterOutcome.Accepted,
        )
        assertTrue(
            VendorAdapterRegistry.parse(
                NotificationSnippet("com.tencent.qqmusic", "q1", "晴天", "周杰伦", true),
            ) is AdapterOutcome.Accepted,
        )
        assertTrue(
            Rail12306VendorAdapter.parse(
                NotificationSnippet("com.MobileTicket", "r1", "G123", "检票口12A 正点", true),
            ) is AdapterOutcome.Accepted,
        )
        assertTrue(
            UmetripVendorAdapter.parse(
                NotificationSnippet("com.umetrip.android.msky.app", "u1", "CA1234", "登机口B12 延误", true),
            ) is AdapterOutcome.Accepted,
        )
    }
}
