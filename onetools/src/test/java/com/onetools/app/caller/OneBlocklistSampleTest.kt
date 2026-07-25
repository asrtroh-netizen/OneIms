package com.onetools.app.caller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OneBlocklistSampleTest {
    @Test
    fun sample_hasExactBlocksForOnespam() {
        val f = File("src/main/assets/sample-one-blocklist.json")
        assertTrue("sample asset missing", f.isFile)
        val rules = BlocklistFormat.parse(f.readText())
        val exactBlocks = rules.filter {
            it.mode == CallMatchMode.EXACT && it.kind == CallRuleKind.BLOCK
        }
        assertTrue(exactBlocks.size >= 4)
        assertTrue(rules.any { it.mode == CallMatchMode.PREFIX })
        assertEquals("骚扰电话", exactBlocks.first().tag.ifBlank { "骚扰电话" }.let { exactBlocks[0].tag })
    }
}
