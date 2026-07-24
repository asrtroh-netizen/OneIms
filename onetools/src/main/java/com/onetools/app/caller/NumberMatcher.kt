package com.onetools.app.caller

import org.json.JSONArray
import org.json.JSONObject

enum class CallRuleKind { BLOCK, ALLOW }

enum class CallMatchMode { EXACT, PREFIX }

data class CallRule(
    val id: String,
    val pattern: String,
    val kind: CallRuleKind,
    val mode: CallMatchMode,
    val tag: String = "",
)

object NumberMatcher {
    fun digits(raw: String?): String =
        raw.orEmpty().filter { it.isDigit() }

    fun matches(rule: CallRule, incomingDigits: String): Boolean {
        if (incomingDigits.isEmpty() || rule.pattern.isEmpty()) return false
        val p = digits(rule.pattern)
        if (p.isEmpty()) return false
        return when (rule.mode) {
            CallMatchMode.EXACT -> incomingDigits == p || incomingDigits.endsWith(p)
            // Prefix only: startsWith (avoid "contains" false positives like matching "00" anywhere).
            CallMatchMode.PREFIX -> incomingDigits.startsWith(p)
        }
    }

    /** ALLOW wins over BLOCK when both match. */
    fun decide(rules: List<CallRule>, number: String?): Decision {
        val d = digits(number)
        if (d.isEmpty()) return Decision.ALLOW_UNKNOWN
        val hits = rules.filter { matches(it, d) }
        if (hits.any { it.kind == CallRuleKind.ALLOW }) return Decision.ALLOW_LIST
        if (hits.any { it.kind == CallRuleKind.BLOCK }) return Decision.BLOCK
        return Decision.ALLOW_UNKNOWN
    }

    enum class Decision { ALLOW_UNKNOWN, ALLOW_LIST, BLOCK }
}

object BlocklistFormat {
    const val SCHEMA = "onetools.blocklist.v1"

    fun parse(raw: String): List<CallRule> {
        val json = JSONObject(raw.trim())
        require(json.optString("schema") == SCHEMA) { "需要 schema=$SCHEMA" }
        val arr = json.getJSONArray("numbers")
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val n = o.optString("n").ifBlank { o.optString("number") }
                if (n.isBlank()) continue
                val prefix = o.optBoolean("prefix", false) ||
                    o.optString("mode").equals("prefix", true)
                val kind = when (o.optString("kind", "block").lowercase()) {
                    "allow", "white" -> CallRuleKind.ALLOW
                    else -> CallRuleKind.BLOCK
                }
                add(
                    CallRule(
                        id = "imp-$n-$i",
                        pattern = n,
                        kind = kind,
                        mode = if (prefix) CallMatchMode.PREFIX else CallMatchMode.EXACT,
                        tag = o.optString("tag", ""),
                    ),
                )
            }
        }
    }

    fun sampleJson(): String = JSONObject()
        .put("schema", SCHEMA)
        .put("numbers", JSONArray()
            .put(JSONObject().put("n", "400").put("prefix", true).put("tag", "可能骚扰"))
            .put(JSONObject().put("n", "106").put("prefix", true).put("kind", "block")))
        .toString(2)
}
