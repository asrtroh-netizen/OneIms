package com.onetools.app.battery

import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryStatsDumpParserTest {
    @Test
    fun parseWakeLocks_fromSection() {
        val dump = """
            Statistics since last charge:
              Wake lock history:
                +1m2s334ms partial myapp:wakelock
                +12s foo:job
              Daily stats:
        """.trimIndent()
        val rows = BatteryStatsDumpParser.parseWakeLocks(dump)
        assertTrue(rows.isNotEmpty())
    }

    @Test
    fun parseDeepSleepHint() {
        val dump = "foo\n  Time on battery: deep sleep 40%\nbar"
        val hint = BatteryStatsDumpParser.parseDeepSleepHint(dump)
        assertTrue(hint != null && hint!!.contains("deep sleep", ignoreCase = true))
    }
}
