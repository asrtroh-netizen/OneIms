package com.onetools.app.caller

import org.json.JSONArray
import org.json.JSONObject

enum class CallRuleKind { BLOCK, ALLOW }

enum class CallMatchMode { EXACT, PREFIX, TAG }

data class CallRule(
    val id: String,
    val pattern: String,
    val kind: CallRuleKind,
    val mode: CallMatchMode,
    val tag: String = "",
)

data class LookupResult(
    val decision: NumberMatcher.Decision,
    val matchedRules: List<CallRule>,
    val tags: List<String>,
)

object NumberMatcher {
    fun digits(raw: String?): String =
        raw.orEmpty().filter { it.isDigit() }

    /** Structural match for EXACT/PREFIX. TAG rules are handled in [decide]/[lookup]. */
    fun matches(rule: CallRule, incomingDigits: String): Boolean {
        if (rule.mode == CallMatchMode.TAG) return false
        if (incomingDigits.isEmpty() || rule.pattern.isEmpty()) return false
        val p = digits(rule.pattern)
        if (p.isEmpty()) return false
        return when (rule.mode) {
            CallMatchMode.EXACT -> incomingDigits == p || incomingDigits.endsWith(p)
            CallMatchMode.PREFIX -> incomingDigits.startsWith(p)
            CallMatchMode.TAG -> false
        }
    }

    /**
     * TAG rule: pattern is a tag name; hits if any EXACT/PREFIX member with that tag matches the number.
     * ALLOW wins over BLOCK.
     */
    fun lookup(rules: List<CallRule>, number: String?): LookupResult {
        val d = digits(number)
        if (d.isEmpty()) {
            return LookupResult(Decision.ALLOW_UNKNOWN, emptyList(), emptyList())
        }
        val structural = rules.filter { it.mode != CallMatchMode.TAG }
        val tagRules = rules.filter { it.mode == CallMatchMode.TAG }
        val directHits = structural.filter { matches(it, d) }
        val tagHits = tagRules.filter { tagRule ->
            val tagName = tagRule.pattern.ifBlank { tagRule.tag }
            if (tagName.isBlank()) return@filter false
            structural.any { member ->
                member.tag == tagName && matches(member, d)
            }
        }
        val hits = directHits + tagHits
        val tags = (directHits.map { it.tag } + tagHits.map { it.pattern })
            .filter { it.isNotBlank() }
            .distinct()
        val decision = when {
            hits.any { it.kind == CallRuleKind.ALLOW } -> Decision.ALLOW_LIST
            hits.any { it.kind == CallRuleKind.BLOCK } -> Decision.BLOCK
            else -> Decision.ALLOW_UNKNOWN
        }
        return LookupResult(decision, hits, tags)
    }

    fun decide(rules: List<CallRule>, number: String?): Decision = lookup(rules, number).decision

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
                val modeStr = o.optString("mode").lowercase()
                val isTag = modeStr == "tag" || o.optBoolean("tagRule", false)
                val n = o.optString("n").ifBlank { o.optString("number") }
                    .ifBlank { o.optString("tag") }
                if (n.isBlank()) continue
                val prefix = o.optBoolean("prefix", false) || modeStr == "prefix"
                val kind = when (o.optString("kind", "block").lowercase()) {
                    "allow", "white" -> CallRuleKind.ALLOW
                    else -> CallRuleKind.BLOCK
                }
                val mode = when {
                    isTag -> CallMatchMode.TAG
                    prefix -> CallMatchMode.PREFIX
                    else -> CallMatchMode.EXACT
                }
                add(
                    CallRule(
                        id = "imp-$n-$i",
                        pattern = n,
                        kind = kind,
                        mode = mode,
                        tag = o.optString("tag", if (isTag) n else ""),
                    ),
                )
            }
        }
    }

    fun sampleJson(): String = JSONObject()
        .put("schema", SCHEMA)
        .put(
            "numbers",
            JSONArray()
                .put(JSONObject().put("n", "400").put("prefix", true).put("tag", "可能骚扰"))
                .put(JSONObject().put("n", "106").put("prefix", true).put("kind", "block"))
                .put(JSONObject().put("mode", "tag").put("n", "可能骚扰").put("kind", "block")),
        )
        .toString(2)

    fun export(rules: List<CallRule>): String {
        val arr = JSONArray()
        rules.forEach { r ->
            val o = JSONObject()
                .put("n", r.pattern)
                .put("kind", r.kind.name.lowercase())
                .put("tag", r.tag)
            when (r.mode) {
                CallMatchMode.PREFIX -> o.put("prefix", true)
                CallMatchMode.TAG -> o.put("mode", "tag")
                CallMatchMode.EXACT -> Unit
            }
            arr.put(o)
        }
        return JSONObject()
            .put("schema", SCHEMA)
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("numbers", arr)
            .toString(2)
    }
}
