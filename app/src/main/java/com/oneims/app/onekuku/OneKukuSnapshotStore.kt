package com.oneims.app.onekuku

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SubscriptionManager
import android.util.Log
import com.oneims.app.model.SimInfo
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 成功应用后的通话配置快照。
 * 隐私：不存完整 ICCID / 手机号 / eSIM 激活码 / APN 密码明文。
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

/**
 * 单条配置项。对外字段名对齐：configGroup / configKey / configValue / writeMethod / persistent。
 */
data class SnapshotEntry(
    val configGroup: String,
    val configKey: String,
    val configValue: String,
    val writeMethod: String = WRITE_METHOD_PERSISTENT_CC,
    val persistent: Boolean = true,
) {
    /** 兼容旧字段名 configType。 */
    val configType: String get() = configGroup
    val key: String get() = configKey
    val value: String get() = configValue

    companion object {
        const val WRITE_METHOD_PERSISTENT_CC = "persistent_carrier_config"
        const val WRITE_METHOD_PROVISIONING = "ims_provisioning"
        const val WRITE_METHOD_LEGACY = "legacy"

        operator fun invoke(
            configType: String,
            key: String,
            value: String,
            writeMethod: String = WRITE_METHOD_PERSISTENT_CC,
            persistent: Boolean = true,
        ): SnapshotEntry = SnapshotEntry(
            configGroup = configType,
            configKey = key,
            configValue = value,
            writeMethod = writeMethod,
            persistent = persistent,
        )
    }
}

sealed class SnapshotMatchResult {
    data class Matched(val snapshot: OneKukuSnapshot, val writeSubId: Int) : SnapshotMatchResult()
    data object NoSnapshot : SnapshotMatchResult()
    data object NoMatchingSim : SnapshotMatchResult()
}

object OneKukuSnapshotStore {
    private const val TAG = "OneIMS-Snapshot"
    private const val PREF = "onekuku_snapshots"
    private const val KEY_ALL_V2 = "snapshots_v2"
    private const val KEY_PREFIX = "snap_"
    const val MSG_NO_MATCHING_SIM = "未找到与快照匹配的 SIM"

    @SuppressLint("ApplySharedPref")
    fun save(context: Context, snapshot: OneKukuSnapshot) {
        val sanitized = sanitize(snapshot)
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val existing = loadAll(context).toMutableList()
        existing.removeAll { sameFingerprint(it, sanitized) }
        existing.add(sanitized)
        prefs.edit()
            .putString(KEY_ALL_V2, encodeAll(existing))
            // 兼容旧按 subId 索引，便于迁移与调试
            .putString(KEY_PREFIX + sanitized.subId, encodeOne(sanitized))
            .commit()
        Log.i(
            TAG,
            "saved snapshot slot=${sanitized.slotIndex} mccmnc=${sanitized.mccmnc} " +
                "subId=${sanitized.subId} entries=${sanitized.entries.size} " +
                "iccidHash=${maskHash(sanitized.iccidHash)}",
        )
    }

    fun loadAll(context: Context): List<OneKukuSnapshot> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val v2 = prefs.getString(KEY_ALL_V2, null)
        if (!v2.isNullOrBlank()) {
            return runCatching { parseAll(v2) }.getOrElse {
                Log.w(TAG, "parse v2 failed: ${it.message}")
                emptyList()
            }
        }
        // 迁移旧 snap_<subId> 键
        val migrated = prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(KEY_PREFIX) || key == KEY_ALL_V2) return@mapNotNull null
            val raw = value as? String ?: return@mapNotNull null
            runCatching { parseOne(raw) }.getOrNull()
        }
        if (migrated.isNotEmpty()) {
            prefs.edit().putString(KEY_ALL_V2, encodeAll(migrated)).apply()
        }
        return migrated
    }

    /** 按旧 subId 直读（兼容）；恢复请优先用 [resolveForSelectedSim]。 */
    fun load(context: Context, subId: Int): OneKukuSnapshot? {
        if (subId < 0) return null
        return loadAll(context).firstOrNull { it.subId == subId }
            ?: run {
                val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .getString(KEY_PREFIX + subId, null)
                    ?: return null
                runCatching { parseOne(raw) }.getOrNull()
            }
    }

    /**
     * 为当前选中 SIM 解析可恢复快照。
     * 匹配优先级：iccidHash → slot+carrierId+mccmnc → slot+mccmnc+carrierName → 旧 subId。
     * 无匹配则 [SnapshotMatchResult.NoMatchingSim]，禁止写错卡。
     */
    fun resolveForSelectedSim(
        context: Context,
        selectedSubId: Int,
        sims: List<SimInfo>,
        iccidRaw: String? = null,
    ): SnapshotMatchResult {
        if (selectedSubId < 0) return SnapshotMatchResult.NoSnapshot
        val selected = sims.firstOrNull { it.subscriptionId == selectedSubId }
            ?: return SnapshotMatchResult.NoMatchingSim
        val all = loadAll(context)
        if (all.isEmpty()) return SnapshotMatchResult.NoSnapshot

        val selectedHash = hashIccid(iccidRaw ?: readIccidRaw(context, selectedSubId))
        val matched = all.mapNotNull { snap ->
            val score = matchScore(snap, selected, selectedHash)
            if (score > 0) snap to score else null
        }.maxByOrNull { it.second }?.first

        return if (matched != null) {
            SnapshotMatchResult.Matched(matched, writeSubId = selectedSubId)
        } else {
            // 有快照但与当前卡对不上 → 明确禁止误写
            SnapshotMatchResult.NoMatchingSim
        }
    }

    fun findForSim(
        context: Context,
        sim: SimInfo,
        iccidRaw: String? = null,
    ): OneKukuSnapshot? {
        val hash = hashIccid(iccidRaw ?: readIccidRaw(context, sim.subscriptionId))
        return loadAll(context)
            .mapNotNull { snap ->
                val score = matchScore(snap, sim, hash)
                if (score > 0) snap to score else null
            }
            .maxByOrNull { it.second }
            ?.first
    }

    fun updateRestoreStatus(context: Context, subId: Int, status: String, verifiedAt: Long) {
        val all = loadAll(context)
        val target = all.firstOrNull { it.subId == subId }
            ?: all.firstOrNull()
            ?: return
        save(
            context,
            target.copy(
                lastRestoreStatus = status,
                lastVerifiedAt = verifiedAt,
                subId = subId,
            ),
        )
    }

    fun hashIccid(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    @SuppressLint("MissingPermission")
    fun readIccidRaw(context: Context, subId: Int): String? {
        if (subId < 0) return null
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return null
        return runCatching {
            sm.activeSubscriptionInfoList
                ?.firstOrNull { it.subscriptionId == subId }
                ?.iccId
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** 日志打码：只留前后各 2 位。 */
    fun maskHash(hash: String?): String {
        val value = hash.orEmpty()
        if (value.length <= 4) return "****"
        return value.take(2) + "****" + value.takeLast(2)
    }

    fun maskSensitiveValue(group: String, key: String, value: String): String {
        val lowerKey = key.lowercase()
        if (
            lowerKey.contains("password") ||
            lowerKey.contains("passwd") ||
            lowerKey.contains("auth") ||
            group.equals("apn", ignoreCase = true)
        ) {
            return "***"
        }
        if (lowerKey.contains("phone") || lowerKey.contains("msisdn") || lowerKey.contains("number")) {
            return "***"
        }
        if (lowerKey.contains("useragent") || lowerKey.contains("user_agent")) {
            return if (value.length <= 12) "***" else value.take(6) + "…" + value.takeLast(2)
        }
        if (lowerKey.contains("activation") || lowerKey.contains("esim")) {
            return "***"
        }
        return value.take(256)
    }

    private fun matchScore(snap: OneKukuSnapshot, sim: SimInfo, simIccidHash: String?): Int {
        val snapHash = snap.iccidHash
        if (!snapHash.isNullOrBlank() && !simIccidHash.isNullOrBlank() && snapHash == simIccidHash) {
            return 100
        }
        val mccmnc = "${sim.mcc}${sim.mnc}"
        if (
            snap.slotIndex == sim.slotIndex &&
            snap.carrierId == sim.carrierId &&
            snap.carrierId > 0 &&
            snap.mccmnc == mccmnc &&
            mccmnc.isNotBlank()
        ) {
            return 80
        }
        if (
            snap.slotIndex == sim.slotIndex &&
            snap.mccmnc == mccmnc &&
            mccmnc.isNotBlank() &&
            snap.carrierName.equals(sim.carrierName, ignoreCase = true) &&
            snap.carrierName.isNotBlank()
        ) {
            return 60
        }
        if (snap.subId == sim.subscriptionId && sim.subscriptionId >= 0) {
            return 20
        }
        return 0
    }

    private fun sameFingerprint(a: OneKukuSnapshot, b: OneKukuSnapshot): Boolean {
        if (!a.iccidHash.isNullOrBlank() && a.iccidHash == b.iccidHash) return true
        if (
            a.slotIndex == b.slotIndex &&
            a.carrierId == b.carrierId &&
            a.carrierId > 0 &&
            a.mccmnc == b.mccmnc &&
            a.mccmnc.isNotBlank()
        ) {
            return true
        }
        return a.subId == b.subId && a.subId >= 0
    }

    private fun sanitize(snapshot: OneKukuSnapshot): OneKukuSnapshot {
        val entries = snapshot.entries
            .filterNot { it.configGroup.equals("apn", ignoreCase = true) }
            .map { entry ->
                entry.copy(
                    configValue = maskSensitiveValue(
                        entry.configGroup,
                        entry.configKey,
                        entry.configValue,
                    ),
                )
            }
        return snapshot.copy(
            carrierName = snapshot.carrierName.take(64),
            entries = entries,
        )
    }

    private fun encodeAll(list: List<OneKukuSnapshot>): String {
        val arr = JSONArray()
        list.forEach { arr.put(encodeOneObject(it)) }
        return arr.toString()
    }

    private fun encodeOne(snapshot: OneKukuSnapshot): String = encodeOneObject(snapshot).toString()

    private fun encodeOneObject(snapshot: OneKukuSnapshot): JSONObject {
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
                    .put("configGroup", entry.configGroup)
                    .put("configKey", entry.configKey)
                    .put("configValue", entry.configValue.take(256))
                    .put("writeMethod", entry.writeMethod)
                    .put("persistent", entry.persistent)
                    // 旧字段兼容写出
                    .put("configType", entry.configGroup)
                    .put("key", entry.configKey)
                    .put("value", entry.configValue.take(256)),
            )
        }
        json.put("entries", arr)
        return json
    }

    private fun parseAll(raw: String): List<OneKukuSnapshot> {
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                add(parseObject(arr.getJSONObject(i)))
            }
        }
    }

    private fun parseOne(raw: String): OneKukuSnapshot = parseObject(JSONObject(raw))

    private fun parseObject(json: JSONObject): OneKukuSnapshot {
        val arr = json.optJSONArray("entries") ?: JSONArray()
        val entries = buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val group = obj.optString("configGroup").ifBlank {
                    obj.optString("configType")
                }
                val key = obj.optString("configKey").ifBlank { obj.optString("key") }
                val value = obj.optString("configValue").ifBlank { obj.optString("value") }
                add(
                    SnapshotEntry(
                        configGroup = group,
                        configKey = key,
                        configValue = value,
                        writeMethod = obj.optString("writeMethod")
                            .ifBlank { SnapshotEntry.WRITE_METHOD_LEGACY },
                        persistent = if (obj.has("persistent")) {
                            obj.optBoolean("persistent", true)
                        } else {
                            true
                        },
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
