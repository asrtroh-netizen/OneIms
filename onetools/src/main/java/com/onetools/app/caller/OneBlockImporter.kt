package com.onetools.app.caller

import android.content.Context

data class OneBlockImportResult(
    val rulesMerged: Int,
    val spamExactInstalled: Int,
    val version: String,
)

/**
 * Dual-write OneBlock JSON:
 * - all rules → [CallRuleStore] (prefix / tag / label / allow / block)
 * - EXACT BLOCK numbers → [SpamOfflineDatabase] onespam pack (Telo mast path)
 */
object OneBlockImporter {
    suspend fun importJson(context: Context, json: String): OneBlockImportResult {
        val rules = BlocklistFormat.parse(json)
        CallRuleStore(context).mergeImport(rules)
        val version = runCatching {
            org.json.JSONObject(json).optString("updatedAt")
                .ifBlank { org.json.JSONObject(json).opt("version")?.toString().orEmpty() }
        }.getOrDefault("").ifBlank { "oneblock-${System.currentTimeMillis() / 1000}" }

        val exact = rules
            .filter { it.mode == CallMatchMode.EXACT && it.kind == CallRuleKind.BLOCK }
            .map {
                Triple(
                    NumberMatcher.digits(it.pattern).removePrefix("86"),
                    it.tag.ifBlank { "骚扰电话" },
                    "oneblock",
                )
            }
            .filter { it.first.length >= 7 }
            .distinctBy { it.first }

        val spamCount = if (exact.isNotEmpty()) {
            SpamPackInstaller.installRows(context, exact, version)
        } else {
            // Keep existing onespam if JSON only has prefixes; do not wipe.
            0
        }
        return OneBlockImportResult(
            rulesMerged = rules.size,
            spamExactInstalled = spamCount,
            version = version,
        )
    }
}
