package com.onetools.app.battery

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Clean-room Shizuku UserService: run `dumpsys batterystats` as shell UID.
 * Replaces deprecated [rikka.shizuku.Shizuku.newProcess].
 */
class ShellBatteryService : IBatteryShell.Stub {
    @Volatile
    private var lastError: String? = null

    constructor()
    constructor(context: Context) {
        // Shizuku may pass a context; unused for dumpsys.
    }

    fun destroy() {
        // no persistent resources
    }

    override fun ping(): String =
        "onetools-battery-shell uid=${android.os.Process.myUid()}"

    override fun lastError(): String = lastError.orEmpty()

    override fun dumpBatteryStats(maxChars: Int): String {
        lastError = null
        val limit = maxChars.coerceIn(4_096, 512_000)
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "batterystats"))
            try {
                val text = BufferedReader(InputStreamReader(process.inputStream)).use { br ->
                    buildString {
                        var n = 0
                        while (true) {
                            val line = br.readLine() ?: break
                            appendLine(line)
                            n += line.length + 1
                            if (n >= limit) break
                        }
                    }
                }
                val err = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
                val code = process.waitFor()
                if (text.isBlank()) {
                    lastError = err.ifBlank { "dumpsys empty (exit=$code)" }
                    return@runCatching ""
                }
                text
            } finally {
                runCatching { process.destroy() }
            }
        }.getOrElse {
            lastError = it.message ?: "dumpsys failed"
            Log.w(TAG, "dumpBatteryStats", it)
            ""
        }
    }

    companion object {
        private const val TAG = "ShellBatteryService"
    }
}
