package com.oneims.app.core

/**
 * 对齐教程 `patch-local-carrierconfig.ps1` 的最小网络键集合（纯文本补丁，可单测）。
 */
object CarrierConfigXmlMinimalPatcher {

    private val booleanKeys = listOf(
        "carrier_volte_available_bool",
        "vonr_enabled_bool",
        "vonr_setting_visibility_bool",
        "show_4g_for_lte_data_icon_bool",
    )

    private val nrArraySnippet = """
        <int-array name="carrier_nr_availabilities_int_array" num="2">
          <item value="1" />
          <item value="2" />
        </int-array>
    """.trimIndent()

    fun patch(original: String): String {
        if (original.isBlank()) return original
        val nl = if (original.contains("\r\n")) "\r\n" else "\n"
        var text = original
        val bundleClosings = Regex("</bundle>").findAll(text).count()
        if (bundleClosings != 1) {
            return original
        }
        for (key in booleanKeys) {
            text = replaceOrInsertBoolean(text, key, nl)
        }
        text = replaceOrInsertNrArray(text, nl)
        return text
    }

    private fun replaceOrInsertBoolean(text: String, name: String, nl: String): String {
        val pattern = Regex("""<boolean\s+name="${Regex.escape(name)}"\s+value="[^"]*"\s*/>""")
        val matches = pattern.findAll(text).toList()
        if (matches.size > 1) return text
        val replacement = """<boolean name="$name" value="true" />"""
        if (matches.size == 1) {
            return pattern.replaceFirst(text, replacement)
        }
        return insertBeforeBundle(text, replacement, nl)
    }

    private fun replaceOrInsertNrArray(text: String, nl: String): String {
        val pattern = Regex(
            """<int-array\s+name="carrier_nr_availabilities_int_array"\s+num="[^"]*">[\s\S]*?</int-array>""",
        )
        val matches = pattern.findAll(text).toList()
        if (matches.size > 1) return text
        if (matches.size == 1) {
            return pattern.replaceFirst(text, nrArraySnippet)
        }
        return insertBeforeBundle(text, nrArraySnippet, nl)
    }

    private fun insertBeforeBundle(text: String, snippet: String, nl: String): String {
        return text.replace("</bundle>", snippet + nl + "</bundle>")
    }
}
