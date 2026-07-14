package com.oneims.app.core

import android.content.Context
import android.os.PersistableBundle
import android.telephony.SubscriptionManager
import android.util.Log
import com.oneims.app.onekuku.OneKukuSnapshotStore

/**
 * OneKuku 统一 CarrierConfig 写入门面。
 *
 * 铁律：只写调用方传入的 [subId]（必须是首页/全局选中卡）；禁止默认卡1 / slot0 / 默认数据卡；
 * 能持久则 persistent=true；写前记目标；同 subId 回读验真；单项失败不假成功。
 */
object CarrierConfigOverrideWriter {
    private const val TAG = "OneIMS-OneKuku"

    data class Result(
        val success: Boolean,
        val message: String,
        val detail: Map<String, Boolean> = emptyMap(),
        val targetLabel: String = "",
    )

    /**
     * 对 [subId] 应用 persistent CarrierConfig 覆盖。
     * 先批量写入；回读后对失败 key 再逐项补写，单 key 失败不影响其它已成功项。
     */
    fun applyPersistentOverride(
        context: Context,
        subId: Int,
        values: PersistableBundle,
        reason: String,
    ): Result {
        requireValidSubId(subId)
        require(values.keySet().isNotEmpty()) { "override values must not be empty" }
        val target = formatTargetLabel(context, subId)
        Log.i(TAG, "write target=$target reason=$reason keys=${values.keySet()}")

        val detail = linkedMapOf<String, Boolean>()
        val failures = mutableListOf<String>()

        val batchOk = runCatching {
            SystemApiBroker.overrideConfig(
                context = context,
                subId = subId,
                bundle = values,
                persistent = true,
            )
            true
        }.getOrElse { error ->
            Log.w(TAG, "batch write failed: ${error.message}")
            failures += "batch: ${error.message ?: "error"}"
            false
        }

        for (key in values.keySet()) {
            val single = PersistableBundle()
            copyKey(values, single, key)
            var ok = batchOk && verifyOverride(context, subId, single)
            if (!ok) {
                ok = runCatching {
                    SystemApiBroker.overrideConfig(
                        context = context,
                        subId = subId,
                        bundle = single,
                        persistent = true,
                    )
                    verifyOverride(context, subId, single)
                }.getOrElse { error ->
                    Log.w(TAG, "key=$key failed: ${error.message}")
                    failures += "$key: ${error.message ?: "error"}"
                    false
                }
            }
            detail[key] = ok
            if (!ok && failures.none { it.startsWith("$key:") }) {
                failures += "$key: readback mismatch or unsupported"
            }
        }

        val successCount = detail.values.count { it }
        val allOk = successCount == detail.size
        if (successCount > 0) {
            saveSnapshotAfterSuccess(context, subId)
        }
        if (allOk) {
            return Result(
                success = true,
                message = "ok · $target · $reason",
                detail = detail,
                targetLabel = target,
            )
        }
        val message = buildString {
            append("partial/fail · $target · $reason · $successCount/${detail.size}")
            if (failures.isNotEmpty()) {
                append(" · ")
                append(failures.joinToString("; "))
            }
        }
        return Result(
            success = false,
            message = message,
            detail = detail,
            targetLabel = target,
        )
    }

    /**
     * 清除 persistent 覆盖。
     * - [keys] 为空：清空该 subId 全部 override（bundle=null）
     * - [keys] 非空：按当前配置类型写入“关闭/空”复位值；无法推断类型的 key 记失败
     */
    fun clearPersistentOverride(
        context: Context,
        subId: Int,
        keys: Collection<String>,
        reason: String,
    ): Result {
        requireValidSubId(subId)
        val target = formatTargetLabel(context, subId)
        Log.i(TAG, "clear target=$target reason=$reason keys=$keys")
        if (keys.isEmpty()) {
            return runCatching {
                SystemApiBroker.overrideConfig(context, subId, null, persistent = true)
                Result(true, "cleared all · $target · $reason", targetLabel = target)
            }.getOrElse {
                Result(false, "clear failed · $target · ${it.message}", targetLabel = target)
            }
        }
        val current = SystemApiBroker.getCarrierConfig(context, subId)
        val reset = PersistableBundle()
        val detail = linkedMapOf<String, Boolean>()
        for (key in keys) {
            val placed = placeResetValue(current, reset, key)
            detail[key] = placed
        }
        if (reset.keySet().isEmpty()) {
            return Result(
                success = false,
                message = "clear failed · $target · no resettable keys",
                detail = detail,
                targetLabel = target,
            )
        }
        val applied = applyPersistentOverride(context, subId, reset, "clear:$reason")
        return applied.copy(
            detail = detail + applied.detail,
            targetLabel = target,
        )
    }

    fun readConfigForSubId(
        context: Context,
        subId: Int,
        keys: Collection<String>,
    ): PersistableBundle? {
        requireValidSubId(subId)
        val full = SystemApiBroker.getCarrierConfig(context, subId) ?: return null
        if (keys.isEmpty()) return PersistableBundle(full)
        val out = PersistableBundle()
        keys.forEach { key ->
            if (full.containsKey(key)) {
                copyKey(full, out, key)
            }
        }
        return out
    }

    fun verifyOverride(
        context: Context,
        subId: Int,
        expected: PersistableBundle,
    ): Boolean {
        requireValidSubId(subId)
        if (expected.keySet().isEmpty()) return true
        val actual = SystemApiBroker.getCarrierConfig(context, subId) ?: return false
        return expected.keySet().all { key ->
            if (!actual.containsKey(key)) return@all false
            valuesEqual(expected.get(key), actual.get(key))
        }
    }

    fun formatTargetLabel(context: Context, subId: Int): String {
        val sim = ImsController.listSims(context).firstOrNull { it.subscriptionId == subId }
        val slot = (sim?.slotIndex ?: -1) + 1
        val carrier = sim?.carrierName?.takeIf { it.isNotBlank() }
            ?: sim?.displayName?.takeIf { it.isNotBlank() }
            ?: "未知运营商"
        return "当前目标：卡$slot · $carrier · subId=$subId"
    }

    fun requireValidSubId(subId: Int) {
        require(subId >= 0) {
            "Invalid selectedSubId=$subId (must use home/global SIM pill; no default slot0/data SIM)"
        }
        require(subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            "Invalid selectedSubId=$subId"
        }
    }

    private fun saveSnapshotAfterSuccess(context: Context, subId: Int) {
        val sim = ImsController.listSims(context).firstOrNull { it.subscriptionId == subId }
            ?: return
        runCatching {
            OneKukuSnapshotStore.save(
                context,
                OneKukuSnapshotFactory.fromCurrent(context, sim),
            )
        }.onFailure {
            Log.w(TAG, "snapshot save failed: ${it.message}")
        }
    }

    private fun placeResetValue(
        current: PersistableBundle?,
        dest: PersistableBundle,
        key: String,
    ): Boolean {
        val sample = current?.get(key)
        return when (sample) {
            is Boolean -> {
                dest.putBoolean(key, false)
                true
            }
            is Int -> {
                dest.putInt(key, 0)
                true
            }
            is Long -> {
                dest.putLong(key, 0L)
                true
            }
            is Double -> {
                dest.putDouble(key, 0.0)
                true
            }
            is String -> {
                dest.putString(key, "")
                true
            }
            is BooleanArray -> {
                dest.putBooleanArray(key, booleanArrayOf())
                true
            }
            is IntArray -> {
                dest.putIntArray(key, intArrayOf())
                true
            }
            is LongArray -> {
                dest.putLongArray(key, longArrayOf())
                true
            }
            is DoubleArray -> {
                dest.putDoubleArray(key, doubleArrayOf())
                true
            }
            is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                dest.putStringArray(key, emptyArray())
                true
            }
            null -> {
                // 未知类型：尝试布尔关闭（常见 carrier 开关）
                dest.putBoolean(key, false)
                true
            }
            else -> false
        }
    }

    @Suppress("DEPRECATION")
    private fun copyKey(from: PersistableBundle, to: PersistableBundle, key: String) {
        when (val value = from.get(key)) {
            is Boolean -> to.putBoolean(key, value)
            is Int -> to.putInt(key, value)
            is Long -> to.putLong(key, value)
            is Double -> to.putDouble(key, value)
            is String -> to.putString(key, value)
            is BooleanArray -> to.putBooleanArray(key, value)
            is IntArray -> to.putIntArray(key, value)
            is LongArray -> to.putLongArray(key, value)
            is DoubleArray -> to.putDoubleArray(key, value)
            is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                to.putStringArray(key, value as Array<String>)
            }
            else -> error("Unsupported PersistableBundle type for key=$key")
        }
    }

    private fun valuesEqual(expected: Any?, actual: Any?): Boolean = when (expected) {
        is BooleanArray -> actual is BooleanArray && expected.contentEquals(actual)
        is DoubleArray -> actual is DoubleArray && expected.contentEquals(actual)
        is IntArray -> actual is IntArray && expected.contentEquals(actual)
        is LongArray -> actual is LongArray && expected.contentEquals(actual)
        is Array<*> -> actual is Array<*> && expected.contentDeepEquals(actual)
        else -> expected == actual
    }
}
