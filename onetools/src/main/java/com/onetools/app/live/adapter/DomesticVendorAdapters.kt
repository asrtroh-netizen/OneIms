package com.onetools.app.live.adapter

import com.onetools.app.live.LiveStatusSource
import com.onetools.app.live.capsule.CapsuleContentSlots
import com.onetools.app.live.capsule.CapsuleExpandTemplate

/** 饿了么：外卖进度（对齐美团阶段心智）。 */
object ElemeVendorAdapter : VendorAdapter {
    override val source = LiveStatusSource.ELEME

    override fun parse(snippet: NotificationSnippet): AdapterOutcome {
        val joined = listOfNotNull(snippet.title, snippet.text).joinToString(" ")
        if (joined.isBlank() || marketingNoise(joined)) return AdapterOutcome.Ignored
        val eta = extractEtaMinutes(joined) ?: 20
        val stage = when {
            joined.contains("配送") || joined.contains("骑手") || joined.contains("送餐") -> 2
            joined.contains("已送达") || joined.contains("已完成") -> 3
            joined.contains("出餐") || joined.contains("制作") || joined.contains("备餐") -> 1
            joined.contains("下单") || joined.contains("已接单") -> 0
            else -> if (snippet.isOngoing) 2 else return AdapterOutcome.Ignored
        }
        val primary = listOf("已下单", "已出餐", "配送中", "已送达")[stage]
        val slots = CapsuleContentSlots(
            iconGlyph = "饿",
            primary = primary,
            secondary = "${eta}分钟",
            stages = stageList(listOf("已下单", "已出餐", "配送中", "已送达"), stage),
            activeStageIndex = stage,
            actions = listOf("查看订单"),
        )
        return AdapterOutcome.Accepted(
            sessionFromSlots(
                id = "live-eleme-${snippet.key}",
                source = source,
                slots = slots,
                title = "饿了么 · $primary",
                subtitle = (snippet.text ?: snippet.title ?: "").take(48),
                template = CapsuleExpandTemplate.PROGRESS_CARD,
                accent = 0xFF0097A7.toInt(),
            ),
            if (snippet.isOngoing) 0.8f else 0.55f,
        )
    }
}

/** 高德 / 百度：导航进行中。 */
class NavVendorAdapter(
    override val source: LiveStatusSource,
    private val brand: String,
    private val accent: Int,
) : VendorAdapter {
    override fun parse(snippet: NotificationSnippet): AdapterOutcome {
        val joined = listOfNotNull(snippet.title, snippet.text).joinToString(" ")
        if (joined.isBlank() || marketingNoise(joined)) return AdapterOutcome.Ignored
        val navigating = listOf("导航", "行驶", "剩余", "公里", "米后", "进入", "驶入", "目的地")
            .any { joined.contains(it) }
        if (!navigating && !snippet.isOngoing) return AdapterOutcome.Ignored
        val eta = extractEtaMinutes(joined)
        val dist = Regex("(\\d+(\\.\\d+)?)\\s*(公里|km|米)").find(joined)?.value
        val primary = if (navigating || snippet.isOngoing) "导航中" else "路线"
        val secondary = eta?.let { "${it}分钟" } ?: dist
        val slots = CapsuleContentSlots(
            iconGlyph = source.chipPrefix,
            primary = primary,
            secondary = secondary,
            detailRows = buildList {
                add("服务" to brand)
                if (eta != null) add("预计" to "${eta}分钟")
                if (dist != null) add("剩余" to dist)
                val dest = snippet.title?.takeIf { !it.contains("导航") }
                if (!dest.isNullOrBlank()) add("前往" to dest.take(16))
            },
            actions = listOf("打开地图"),
        )
        return AdapterOutcome.Accepted(
            sessionFromSlots(
                id = "live-${source.id}-${snippet.key}",
                source = source,
                slots = slots,
                title = "$brand · $primary",
                subtitle = (snippet.text ?: snippet.title ?: "").take(48),
                template = CapsuleExpandTemplate.DETAIL_CARD,
                accent = accent,
            ),
            0.7f,
        )
    }
}

/** QQ / 网易云：正在播放。 */
class MusicVendorAdapter(
    override val source: LiveStatusSource,
    private val brand: String,
    private val accent: Int,
) : VendorAdapter {
    override fun parse(snippet: NotificationSnippet): AdapterOutcome {
        val title = snippet.title?.trim().orEmpty()
        val text = snippet.text?.trim().orEmpty()
        val joined = "$title $text"
        if (joined.isBlank() || marketingNoise(joined)) return AdapterOutcome.Ignored
        val playing = snippet.isOngoing ||
            listOf("正在播放", "播放中", "歌词", "·").any { joined.contains(it) } ||
            (title.isNotBlank() && text.isNotBlank())
        if (!playing) return AdapterOutcome.Ignored
        val song = title.ifBlank { "播放中" }.take(18)
        val artist = text.take(18).ifBlank { brand }
        val slots = CapsuleContentSlots(
            iconGlyph = source.chipPrefix,
            primary = song,
            secondary = artist,
            detailRows = listOf(
                "歌曲" to song,
                "艺人" to artist,
                "来源" to brand,
            ),
            actions = listOf("上一首", "播放/暂停", "下一首"),
        )
        return AdapterOutcome.Accepted(
            sessionFromSlots(
                id = "live-${source.id}-${snippet.key}",
                source = source,
                slots = slots,
                title = "$brand · 正在播放",
                subtitle = "$song · $artist",
                template = CapsuleExpandTemplate.DETAIL_CARD,
                accent = accent,
            ),
            0.65f,
        )
    }
}

/** 12306：车次/检票。 */
object Rail12306VendorAdapter : VendorAdapter {
    override val source = LiveStatusSource.RAIL12306

    override fun parse(snippet: NotificationSnippet): AdapterOutcome {
        val joined = listOfNotNull(snippet.title, snippet.text).joinToString(" ")
        if (joined.isBlank() || marketingNoise(joined)) return AdapterOutcome.Ignored
        val train = Regex("([GDCZTKYL]\\d{1,5})").find(joined)?.value
        val gate = Regex("检票口\\s*([A-Z0-9\\-]+)").find(joined)?.groupValues?.getOrNull(1)
            ?: Regex("([A-Z]?\\d{1,2}[A-Z]?)\\s*检票").find(joined)?.groupValues?.getOrNull(1)
        val relevant = listOf("车次", "检票", "候车", "正点", "晚点", "停运", "出发").any { joined.contains(it) } ||
            train != null || snippet.isOngoing
        if (!relevant) return AdapterOutcome.Ignored
        val primary = train ?: "出行"
        val secondary = when {
            joined.contains("晚点") -> "晚点"
            gate != null -> "检票$gate"
            joined.contains("候车") -> "候车中"
            else -> "行程"
        }
        val slots = CapsuleContentSlots(
            iconGlyph = "火",
            primary = primary,
            secondary = secondary,
            detailRows = buildList {
                if (train != null) add("车次" to train)
                if (gate != null) add("检票口" to gate)
                add("提示" to (snippet.text ?: snippet.title ?: "").take(32))
            },
            actions = listOf("打开12306"),
        )
        return AdapterOutcome.Accepted(
            sessionFromSlots(
                id = "live-rail-${snippet.key}",
                source = source,
                slots = slots,
                title = "铁路12306 · $primary",
                subtitle = (snippet.text ?: snippet.title ?: "").take(48),
                template = CapsuleExpandTemplate.DETAIL_CARD,
                accent = 0xFF1565C0.toInt(),
            ),
            0.75f,
        )
    }
}

/** 航旅纵横：航班动态。 */
object UmetripVendorAdapter : VendorAdapter {
    override val source = LiveStatusSource.UMETRIP

    override fun parse(snippet: NotificationSnippet): AdapterOutcome {
        val joined = listOfNotNull(snippet.title, snippet.text).joinToString(" ")
        if (joined.isBlank() || marketingNoise(joined)) return AdapterOutcome.Ignored
        val flight = Regex("([A-Z]{2}\\d{2,4})").find(joined)?.value
        val gate = Regex("登机口\\s*([A-Z]?\\d{1,3})").find(joined)?.groupValues?.getOrNull(1)
            ?: Regex("Gate\\s*([A-Z]?\\d{1,3})", RegexOption.IGNORE_CASE).find(joined)?.groupValues?.getOrNull(1)
        val relevant = listOf("航班", "登机", "起飞", "到达", "延误", "取消", "值机").any { joined.contains(it) } ||
            flight != null || snippet.isOngoing
        if (!relevant) return AdapterOutcome.Ignored
        val primary = flight ?: "航班"
        val secondary = when {
            joined.contains("延误") || joined.contains("晚到") -> "延误"
            joined.contains("取消") -> "取消"
            gate != null -> "登机口$gate"
            joined.contains("登机") -> "登机中"
            joined.contains("起飞") -> "起飞"
            joined.contains("到达") -> "到达"
            else -> "动态"
        }
        val slots = CapsuleContentSlots(
            iconGlyph = "航",
            primary = primary,
            secondary = secondary,
            detailRows = buildList {
                if (flight != null) add("航班" to flight)
                if (gate != null) add("登机口" to gate)
                add("状态" to secondary)
                add("详情" to (snippet.text ?: snippet.title ?: "").take(32))
            },
            actions = listOf("打开航旅"),
        )
        return AdapterOutcome.Accepted(
            sessionFromSlots(
                id = "live-umetrip-${snippet.key}",
                source = source,
                slots = slots,
                title = "航旅纵横 · $primary",
                subtitle = (snippet.text ?: snippet.title ?: "").take(48),
                template = CapsuleExpandTemplate.DETAIL_CARD,
                accent = 0xFF5C6BC0.toInt(),
            ),
            0.75f,
        )
    }
}
