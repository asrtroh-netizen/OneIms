package com.onetools.app.battery

/**
 * Best-effort parse of `dumpsys batterystats` wakelock section (clean-room).
 * Output format varies by Android version — keep tolerant.
 */
object BatteryStatsDumpParser {
    data class WakeLockRow(
        val name: String,
        val detail: String,
    )

    fun parseWakeLocks(dump: String, limit: Int = 20): List<WakeLockRow> {
        val lines = dump.lineSequence().map { it.trimEnd() }.toList()
        val out = mutableListOf<WakeLockRow>()
        var inSection = false
        for (raw in lines) {
            val line = raw.trim()
            val lower = line.lowercase()
            if (lower.contains("wake lock") || lower.contains("wakelock")) {
                inSection = true
                continue
            }
            if (inSection) {
                if (line.isEmpty()) {
                    if (out.isNotEmpty()) break
                    continue
                }
                // leave section on new major header
                if (!line.startsWith(" ") && !line.startsWith("+") &&
                    !line.contains(":") && out.size > 3 &&
                    (lower.startsWith("statistics") || lower.startsWith("daily") ||
                        lower.startsWith("per-package") || lower.startsWith("uid "))
                ) {
                    break
                }
                if (line.length < 3) continue
                val name = line.take(80)
                out.add(WakeLockRow(name = name, detail = line.take(160)))
                if (out.size >= limit) break
            }
        }
        // Fallback: scan any line with "wake" + time-looking tokens
        if (out.isEmpty()) {
            lines.asSequence()
                .map { it.trim() }
                .filter { it.contains("wake", ignoreCase = true) && it.length in 8..160 }
                .take(limit)
                .forEach { out.add(WakeLockRow(it.take(60), it)) }
        }
        return out
    }

    fun parseDeepSleepHint(dump: String): String? {
        val hit = dump.lineSequence().firstOrNull { line ->
            val l = line.lowercase()
            l.contains("deep sleep") || l.contains("screen off") && l.contains("realtime")
        }?.trim()
        return hit?.take(160)
    }
}
