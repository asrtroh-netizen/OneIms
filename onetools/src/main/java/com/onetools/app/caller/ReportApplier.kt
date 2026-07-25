package com.onetools.app.caller

import android.content.Context

data class ReportApplyResult(
    val report: LocalReportEntity,
    val ruleId: String?,
    val onespamOk: Boolean,
)

/**
 * Phase-1: report → local CallRule BLOCK + onespam upsert (no cloud).
 */
object ReportApplier {
    private const val RULE_ID_PREFIX = "report-"

    suspend fun reportAndApply(
        context: Context,
        rawPhone: String,
        tag: ReportTag,
        note: String = "",
        source: String = "manual",
    ): ReportApplyResult {
        val prefs = CallerPrefs(context)
        val applyLocal = prefs.applyReportLocally()
        val store = LocalReportStore(context)
        val report = store.add(
            rawPhone = rawPhone,
            tag = tag,
            note = note,
            source = source,
            applyLocal = applyLocal,
        )
        if (!applyLocal || !tag.appliesLocalBlock) {
            return ReportApplyResult(report, ruleId = null, onespamOk = false)
        }
        val label = tag.labelZh
        val ruleId = RULE_ID_PREFIX + report.phone
        CallRuleStore(context).upsert(
            CallRule(
                id = ruleId,
                pattern = report.phone,
                kind = CallRuleKind.BLOCK,
                mode = CallMatchMode.EXACT,
                tag = label,
            ),
        )
        val onespamOk = SpamPackInstaller.upsertOne(
            context,
            phone = report.phone,
            tag = label,
            source = "report",
        )
        return ReportApplyResult(report, ruleId = ruleId, onespamOk = onespamOk)
    }

    suspend fun revoke(context: Context, reportId: String, phone: String) {
        LocalReportStore(context).remove(reportId)
        val ruleId = RULE_ID_PREFIX + NumberMatcher.digits(phone).removePrefix("86")
        CallRuleStore(context).remove(ruleId)
        // Also remove rules that might have been created with UUID in older builds — best effort by pattern.
        val store = CallRuleStore(context)
        store.snapshot()
            .filter {
                it.kind == CallRuleKind.BLOCK &&
                    it.mode == CallMatchMode.EXACT &&
                    NumberMatcher.digits(it.pattern).removePrefix("86") ==
                    NumberMatcher.digits(phone).removePrefix("86") &&
                    it.id.startsWith(RULE_ID_PREFIX)
            }
            .forEach { store.remove(it.id) }
        SpamPackInstaller.removeOne(context, phone)
    }

    /** Normalize helper for tests / UI validation. */
    fun normalizePhoneOrNull(raw: String): String? {
        val d = NumberMatcher.digits(raw).removePrefix("86")
        return d.takeIf { it.length >= 7 }
    }
}
