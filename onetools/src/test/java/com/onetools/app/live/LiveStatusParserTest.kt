package com.onetools.app.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStatusParserTest {
    @Test
    fun meituan_chip_is_at_most_7_chars() {
        val chip = LiveStatusParser.toChipText(
            LiveStatusSource.MEITUAN,
            "美团外卖",
            "骑手正在配送，预计 12:30 送达",
        )
        assertTrue(chip.length <= 7)
        assertTrue(chip.startsWith("美"))
    }

    @Test
    fun didi_empty_falls_back() {
        val chip = LiveStatusParser.toChipText(LiveStatusSource.DIDI, null, null)
        assertEquals("滴进行中", chip)
    }

    @Test
    fun cainiao_strips_brand_prefix() {
        val chip = LiveStatusParser.toChipText(
            LiveStatusSource.CAINIAO,
            "菜鸟裹裹",
            "包裹派送中",
        )
        assertTrue(chip.startsWith("菜"))
        assertTrue(chip.length <= 7)
    }
}
