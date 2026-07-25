package com.onetools.app.caller

import android.content.Context
import android.util.Log
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Full check pipeline (Telo SpamNumberRepository shape, clean-room):
 * user allow → user block → offline spam pack → optional network.
 */
object CallerCheckEngine {
    private const val TAG = "CallerCheckEngine"

    enum class ResultType {
        WHITE_LIST,
        BLACK_LIST,
        SPAM_DB,
        NETWORK_SPAM,
        NETWORK_PASS,
        NETWORK_TIMEOUT,
        PASS,
    }

    data class CheckResult(
        val shouldBlock: Boolean,
        val label: String,
        val resultType: ResultType,
        val forceBlock: Boolean = false,
        val spamTag: String = "",
        val location: PhoneLocationInfo? = null,
        val localCostMs: Long = 0L,
        val networkCostMs: Long = 0L,
        val querySource: String? = null,
    )

    suspend fun check(
        context: Context,
        rawNumber: String?,
        forceNetwork: Boolean = false,
    ): CheckResult {
        val start = System.currentTimeMillis()
        val digits = NumberMatcher.digits(rawNumber)
        if (digits.isEmpty()) {
            return CheckResult(false, "", ResultType.PASS)
        }
        val phone = digits.removePrefix("86").ifBlank { digits }
        val prefs = CallerPrefs(context)
        val rules = CallRuleStore(context).snapshot()
        val user = NumberMatcher.lookup(rules, phone)

        user.matchedRules.firstOrNull { it.kind == CallRuleKind.ALLOW }?.let { hit ->
            return CheckResult(
                shouldBlock = false,
                label = hit.tag.ifBlank { hit.pattern },
                resultType = ResultType.WHITE_LIST,
                localCostMs = System.currentTimeMillis() - start,
            )
        }
        // Legacy BLOCK rules → dialer label only (product: never reject calls).
        user.matchedRules.firstOrNull { it.kind == CallRuleKind.BLOCK }?.let { hit ->
            return CheckResult(
                shouldBlock = false,
                label = hit.tag.ifBlank { hit.pattern },
                resultType = ResultType.BLACK_LIST,
                forceBlock = false,
                spamTag = hit.tag,
                localCostMs = System.currentTimeMillis() - start,
            )
        }

        val sync = SpamSyncRepository(context)
        val spam = sync.lookupLocal(phone)
        val localCost = System.currentTimeMillis() - start
        if (spam != null) {
            val tag = spam.tag
            // Tag-based user allow/block against spam tag (Telo-like).
            val tagAllow = rules.any {
                it.mode == CallMatchMode.TAG &&
                    it.kind == CallRuleKind.ALLOW &&
                    (it.pattern == tag || it.tag == tag)
            }
            if (tagAllow) {
                return CheckResult(
                    shouldBlock = false,
                    label = tag,
                    resultType = ResultType.WHITE_LIST,
                    spamTag = tag,
                    localCostMs = localCost,
                )
            }
            val tagBlock = rules.any {
                it.mode == CallMatchMode.TAG &&
                    it.kind == CallRuleKind.BLOCK &&
                    (it.pattern == tag || it.tag == tag)
            }
            return CheckResult(
                shouldBlock = false,
                label = tag,
                resultType = if (tagBlock) ResultType.BLACK_LIST else ResultType.SPAM_DB,
                forceBlock = false,
                spamTag = tag,
                localCostMs = localCost,
                querySource = spam.source.ifBlank { "local-spam" },
            )
        }

        if (prefs.noNetworkQuery() && !forceNetwork) {
            return CheckResult(
                shouldBlock = false,
                label = "",
                resultType = ResultType.PASS,
                localCostMs = localCost,
            )
        }

        val netStart = System.currentTimeMillis()
        return try {
            val net = CallerNetworkQuery.query(phone, prefs.networkTimeoutMs())
            val networkCost = System.currentTimeMillis() - netStart
            if (net == null) {
                CheckResult(
                    shouldBlock = false,
                    label = "",
                    resultType = ResultType.PASS,
                    localCostMs = localCost,
                    networkCostMs = networkCost,
                    location = null,
                )
            } else if (net.isSpam) {
                CheckResult(
                    shouldBlock = false,
                    label = net.tag.ifBlank { "骚扰电话" },
                    resultType = ResultType.NETWORK_SPAM,
                    spamTag = net.tag,
                    location = net.location,
                    localCostMs = localCost,
                    networkCostMs = networkCost,
                    querySource = net.source.ifBlank { "network" },
                )
            } else {
                CheckResult(
                    shouldBlock = false,
                    label = net.tag,
                    resultType = ResultType.NETWORK_PASS,
                    spamTag = net.tag,
                    location = net.location,
                    localCostMs = localCost,
                    networkCostMs = networkCost,
                    querySource = net.source.ifBlank { "network" },
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "network timeout", e)
            CheckResult(
                shouldBlock = false,
                label = "Timeout/Error",
                resultType = ResultType.NETWORK_TIMEOUT,
                localCostMs = localCost,
                networkCostMs = System.currentTimeMillis() - netStart,
            )
        } catch (e: Exception) {
            Log.w(TAG, "network query failed", e)
            CheckResult(
                shouldBlock = false,
                label = "Timeout/Error",
                resultType = ResultType.NETWORK_TIMEOUT,
                localCostMs = localCost,
                networkCostMs = System.currentTimeMillis() - netStart,
            )
        }
    }

    /** Dialer / try-lookup display helper. */
    fun dialerKind(result: CheckResult, userLabelRule: CallRule?): CallRuleKind? {
        if (userLabelRule != null) return userLabelRule.kind
        return when (result.resultType) {
            ResultType.WHITE_LIST -> CallRuleKind.ALLOW
            ResultType.BLACK_LIST, ResultType.SPAM_DB, ResultType.NETWORK_SPAM -> CallRuleKind.LABEL
            else -> null
        }
    }
}
