package com.onetools.app.battery

import com.onetools.app.channel.ShizukuChannel
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Pull `dumpsys batterystats` via Shizuku when available (best-effort).
 * Uses reflective [Shizuku.newProcess] — deprecated upstream; UserService is future work.
 */
object BatteryStatsShizuku {
    data class DumpResult(
        val ok: Boolean,
        val text: String,
        val error: String? = null,
    )

    fun isReady(): Boolean = ShizukuChannel.isServiceReady()

    fun dumpBatteryStats(maxChars: Int = 200_000): DumpResult {
        if (!isReady()) {
            return DumpResult(false, "", "Shizuku 未就绪")
        }
        return runCatching {
            val process = newProcess(arrayOf("dumpsys", "batterystats"))
                ?: return DumpResult(false, "", "Shizuku.newProcess 不可用，需后续 UserService")
            try {
                val text = BufferedReader(InputStreamReader(process.inputStream)).use { br ->
                    buildString {
                        var n = 0
                        while (true) {
                            val line = br.readLine() ?: break
                            appendLine(line)
                            n += line.length + 1
                            if (n >= maxChars) break
                        }
                    }
                }
                process.waitFor()
                DumpResult(ok = text.isNotBlank(), text = text)
            } finally {
                runCatching { process.destroy() }
            }
        }.getOrElse {
            DumpResult(false, "", it.message ?: "dumpsys failed")
        }
    }

    private fun newProcess(cmd: Array<String>): Process? {
        return runCatching {
            val m = Shizuku::class.java.methods.firstOrNull { method ->
                method.name == "newProcess" && method.parameterTypes.size >= 1
            } ?: return null
            val args = when (m.parameterTypes.size) {
                1 -> arrayOf(cmd)
                3 -> arrayOf(cmd, null, null)
                else -> arrayOf(cmd)
            }
            m.invoke(null, *args) as? Process
        }.getOrNull()
    }
}
