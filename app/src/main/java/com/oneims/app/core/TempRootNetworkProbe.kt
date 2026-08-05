package com.oneims.app.core

import java.util.concurrent.TimeUnit

/**
 * 对齐教程 `check-network.ps1` 的只读网络体检（不收集 ICCID/IMSI/小区）。
 */
object TempRootNetworkProbe {

    data class Line(val label: String, val value: String)

    private val props = listOf(
        "ro.product.model" to "型号",
        "ro.product.device" to "代号",
        "ro.build.id" to "构建",
        "gsm.operator.alpha" to "运营商",
        "gsm.operator.numeric" to "运营商代码",
        "gsm.network.type" to "网络制式",
        "persist.radio.is_vonr_enabled_0" to "VoNR 属性",
    )

    fun snapshot(): List<Line> {
        val lines = props.map { (key, label) ->
            Line(label = label, value = readProp(key).ifBlank { "—" })
        }.toMutableList()
        lines += Line(label = "SELinux", value = readEnforce().ifBlank { "—" })
        val allowed = readAllowedNetworkTypes()
        if (allowed.isNotBlank()) {
            lines += Line(label = "允许网络类型", value = allowed)
        }
        return lines
    }

    fun formatForUi(lines: List<Line> = snapshot()): String =
        lines.joinToString("\n") { "${it.label}：${it.value}" }

    private fun readProp(key: String): String =
        runCapture(listOf("getprop", key))?.trim().orEmpty()

    private fun readEnforce(): String =
        runCapture(listOf("getenforce"))?.trim().orEmpty()

    private fun readAllowedNetworkTypes(): String =
        runCapture(
            listOf("cmd", "phone", "get-allowed-network-types-for-users", "-s", "0"),
        )?.trim()?.replace('\n', ' ').orEmpty()

    private fun runCapture(argv: List<String>): String? {
        return runCatching {
            val process = ProcessBuilder(argv)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(8, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching null
            }
            if (process.exitValue() != 0) return@runCatching output.ifBlank { null }
            output
        }.getOrNull()
    }
}
