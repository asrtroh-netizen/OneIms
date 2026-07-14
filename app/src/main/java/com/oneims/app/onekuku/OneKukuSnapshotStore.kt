package com.oneims.app.onekuku

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 成功应用后的通话配置快照。不存完整 ICCID / 手机号 / eSIM 激活码。
 */
data class OneKukuSnapshot(
    val subId: Int,
    val slotIndex: Int,
    val carrierId: Int,
    val mccmnc: String,
    val carrierName: String,
    val iccidHash: String?,
    val entries: List<SnapshotEntry>,
    val appliedAt: Long,
    val lastVerifiedAt: Long,
    val lastRestoreStatus: String,
)

data class SnapshotEntry(
    val configType: String,
    val key: String,
    val value: String,
)

object OneKukuSnapshotStore {
    private const val TAG = "OneIMS-Snapshot"
    private const val PREF = "onekuku_snapshots"
    private const val KEY_PREFIX = "snap_"

    @SuppressLint("ApplySharedPref")
    fun save(context: Context, snapshot: OneKukuSnapshot) {
        val json = JSONObject()
            .put("subId", snapshot.subId)
            .put("slotIndex", snapshot.slotIndex)
            .put("carrierId", snapshot.carrierId)
            .put("mccmnc", snapshot.mccmnc)
            .put("carrierName", snapshot.carrierName.take(64))
            .put("iccidHash", snapshot.iccidHash)
            .put("appliedAt", snapshot.appliedAt)
            .put("lastVerifiedAt", snapshot.lastVerifiedAt)
            .put("lastRestoreStatus", snapshot.lastRestoreStatus)
        val arr = JSONArray()
        snapshot.entries.forEach { entry ->
            arr.put(
                JSONObject()
                    .put("configType", entry.configType)
                    .put("key", entry.key)
                    .put("value", entry.value.take(256)),
            )
        }
        json.put("entries", arr)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFIX + snapshot.subId, json.toString())
            .commit()
        Log.i(TAG, "saved snapshot subId=${snapshot.subId} entries=${snapshot.entries.size}")
    }

    fun load(context: Context, subId: Int): OneKukuSnapshot? {
        if (subId < 0) return null
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_PREFIX + subId, null)
            ?: return null
        return runCatching { parse(raw) }.onFailure {
            Log.w(TAG, "parse failed: ${it.message}")
        }.getOrNull()
    }

    fun updateRestoreStatus(context: Context, subId: Int, status: String, verifiedAt: Long) {
        val current = load(context, subId) ?: return
        save(
            context,
            current.copy(
                lastRestoreStatus = status,
                lastVerifiedAt = verifiedAt,
            ),
        )
    }

    fun hashIccid(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun parse(raw: String): OneKukuSnapshot {
        val json = JSONObject(raw)
        val arr = json.optJSONArray("entries") ?: JSONArray()
        val entries = buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    SnapshotEntry(
                        configType = obj.optString("configType"),
                        key = obj.optString("key"),
                        value = obj.optString("value"),
                    ),
                )
            }
        }
        return OneKukuSnapshot(
            subId = json.getInt("subId"),
            slotIndex = json.optInt("slotIndex"),
            carrierId = json.optInt("carrierId"),
            mccmnc = json.optString("mccmnc"),
            carrierName = json.optString("carrierName"),
            iccidHash = json.optString("iccidHash").ifBlank { null },
            entries = entries,
            appliedAt = json.optLong("appliedAt"),
            lastVerifiedAt = json.optLong("lastVerifiedAt"),
            lastRestoreStatus = json.optString("lastRestoreStatus"),
        )
    }
}
