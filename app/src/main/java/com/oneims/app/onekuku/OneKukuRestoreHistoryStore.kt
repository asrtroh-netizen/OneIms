package com.oneims.app.onekuku

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class RestoreHistoryResult {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
}

enum class RestoreItemStatus {
    SUCCESS,
    FAILED,
    SKIPPED,
}

/**
 * 最近一次 OneKuku 恢复记录（摘要级，无终端/命令/敏感明文）。
 */
data class OneKukuRestoreHistoryRecord(
    val restoreId: String,
    val startedAt: Long,
    val finishedAt: Long,
    val targetSubId: Int,
    val targetSlotIndex: Int,
    val carrierName: String,
    val mccmnc: String,
    val iccidHashMasked: String?,
    val result: RestoreHistoryResult,
    val oneKukuStatusBefore: String,
    val oneKukuStatusAfter: String,
    val itemResults: Map<String, RestoreItemStatus>,
    val failureReason: String?,
    val logSummary: String,
)

object OneKukuRestoreHistoryStore {
    private const val TAG = "OneIMS-Restore"
    private const val PREF = "onekuku_restore_history"
    private const val KEY_LATEST = "latest"

    val ITEM_KEYS = listOf(
        "identity",
        "ims",
        "wfc",
        "nr5g",
        "signal",
        "vowifi_name",
    )

    @SuppressLint("ApplySharedPref")
    fun save(context: Context, record: OneKukuRestoreHistoryRecord) {
        val sanitized = record.copy(
            carrierName = record.carrierName.take(64),
            failureReason = record.failureReason
                ?.let { OneKukuSnapshotStore.maskSensitiveValue("restore", "reason", it) }
                ?.take(200),
            logSummary = OneKukuSnapshotStore.maskSensitiveValue(
                "restore",
                "log",
                record.logSummary,
            ).take(240),
            iccidHashMasked = record.iccidHashMasked?.let {
                OneKukuSnapshotStore.maskHash(it.removePrefix("hash:").removePrefix("****"))
            },
        )
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LATEST, encode(sanitized))
            .commit()
        Log.i(
            TAG,
            "history saved id=${sanitized.restoreId} result=${sanitized.result} " +
                "slot=${sanitized.targetSlotIndex}",
        )
    }

    fun loadLatest(context: Context): OneKukuRestoreHistoryRecord? {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_LATEST, null)
            ?: return null
        return runCatching { parse(raw) }.onFailure {
            Log.w(TAG, "history parse failed: ${it.message}")
        }.getOrNull()
    }

    fun newRestoreId(): String = UUID.randomUUID().toString().take(8)

    fun statusLabel(state: OneKukuRunnerState): String = when (state) {
        OneKukuRunnerState.INACTIVE -> "inactive"
        OneKukuRunnerState.STARTING,
        OneKukuRunnerState.ACTIVE,
        OneKukuRunnerState.EXECUTING,
        -> "executing"
        OneKukuRunnerState.SLEEPING -> "sleeping"
        OneKukuRunnerState.FAILED -> "failed"
    }

    fun mapItemResults(
        detail: Map<String, Boolean>,
        snapshot: OneKukuSnapshot?,
    ): Map<String, RestoreItemStatus> {
        fun hasGroup(group: String): Boolean =
            snapshot?.entries?.any { it.configGroup == group } == true

        return ITEM_KEYS.associateWith { key ->
            when {
                detail.containsKey(key) ->
                    if (detail[key] == true) RestoreItemStatus.SUCCESS else RestoreItemStatus.FAILED
                key == "identity" && !hasGroup("identity") -> RestoreItemStatus.SKIPPED
                key == "vowifi_name" && !hasGroup("vowifi_name") -> RestoreItemStatus.SKIPPED
                key == "signal" && !hasGroup("signal") -> RestoreItemStatus.SKIPPED
                key == "nr5g" && !hasGroup("nr5g") -> RestoreItemStatus.SKIPPED
                else -> RestoreItemStatus.SKIPPED
            }
        }
    }

    private fun encode(record: OneKukuRestoreHistoryRecord): String {
        val items = JSONArray()
        record.itemResults.forEach { (k, v) ->
            items.put(JSONObject().put("key", k).put("status", v.name))
        }
        return JSONObject()
            .put("restoreId", record.restoreId)
            .put("startedAt", record.startedAt)
            .put("finishedAt", record.finishedAt)
            .put("targetSubId", record.targetSubId)
            .put("targetSlotIndex", record.targetSlotIndex)
            .put("carrierName", record.carrierName)
            .put("mccmnc", record.mccmnc)
            .put("iccidHashMasked", record.iccidHashMasked)
            .put("result", record.result.name)
            .put("oneKukuStatusBefore", record.oneKukuStatusBefore)
            .put("oneKukuStatusAfter", record.oneKukuStatusAfter)
            .put("itemResults", items)
            .put("failureReason", record.failureReason)
            .put("logSummary", record.logSummary)
            .toString()
    }

    private fun parse(raw: String): OneKukuRestoreHistoryRecord {
        val json = JSONObject(raw)
        val arr = json.optJSONArray("itemResults") ?: JSONArray()
        val items = linkedMapOf<String, RestoreItemStatus>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val key = obj.optString("key")
            val status = runCatching {
                RestoreItemStatus.valueOf(obj.optString("status"))
            }.getOrDefault(RestoreItemStatus.SKIPPED)
            if (key.isNotBlank()) items[key] = status
        }
        return OneKukuRestoreHistoryRecord(
            restoreId = json.optString("restoreId"),
            startedAt = json.optLong("startedAt"),
            finishedAt = json.optLong("finishedAt"),
            targetSubId = json.optInt("targetSubId"),
            targetSlotIndex = json.optInt("targetSlotIndex"),
            carrierName = json.optString("carrierName"),
            mccmnc = json.optString("mccmnc"),
            iccidHashMasked = json.optString("iccidHashMasked").ifBlank { null },
            result = runCatching {
                RestoreHistoryResult.valueOf(json.optString("result"))
            }.getOrDefault(RestoreHistoryResult.FAILED),
            oneKukuStatusBefore = json.optString("oneKukuStatusBefore"),
            oneKukuStatusAfter = json.optString("oneKukuStatusAfter"),
            itemResults = items,
            failureReason = json.optString("failureReason").ifBlank { null },
            logSummary = json.optString("logSummary"),
        )
    }
}
