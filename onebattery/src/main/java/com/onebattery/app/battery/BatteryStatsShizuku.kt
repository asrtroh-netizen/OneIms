package com.onebattery.app.battery

import android.content.Context
import com.onebattery.app.channel.ShizukuChannel
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Pull `dumpsys batterystats` via Shizuku.
 * Prefers UserService ([ShellBatteryService]); falls back to reflective [Shizuku.newProcess].
 */
object BatteryStatsShizuku {
    data class DumpResult(
        val ok: Boolean,
        val text: String,
        val error: String? = null,
        val via: String? = null,
    )

    fun isReady(): Boolean = ShizukuChannel.isServiceReady()

    fun dumpBatteryStats(context: Context, maxChars: Int = 200_000): DumpResult {
        if (!isReady()) {
            return DumpResult(false, "", "Shizuku 未就绪")
        }
        val app = context.applicationContext
        val user = dumpViaUserService(app, maxChars)
        if (user.ok) return user

        val legacy = dumpViaNewProcess(maxChars)
        if (legacy.ok) return legacy.copy(via = "newProcess")

        val err = buildString {
            append(user.error ?: "UserService 失败")
            if (legacy.error != null) {
                append("；回退 newProcess：")
                append(legacy.error)
            }
        }
        return DumpResult(false, "", err)
    }

    private fun dumpViaUserService(context: Context, maxChars: Int): DumpResult {
        val client = ShellBatteryClient(context)
        return try {
            client.dumpBatteryStats(maxChars).fold(
                onSuccess = { DumpResult(ok = true, text = it, via = "UserService") },
                onFailure = { DumpResult(false, "", it.message ?: "UserService 失败") },
            )
        } finally {
            client.unbind()
        }
    }

    private fun dumpViaNewProcess(maxChars: Int): DumpResult {
        return runCatching {
            val process = newProcess(arrayOf("dumpsys", "batterystats"))
                ?: return DumpResult(false, "", "Shizuku.newProcess 不可用")
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
                DumpResult(ok = text.isNotBlank(), text = text, via = "newProcess")
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
