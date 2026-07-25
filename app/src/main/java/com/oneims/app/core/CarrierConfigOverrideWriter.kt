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
 * 优先 persistent=true；若系统拒绝（非 system app）则回退 persistent=false；
 * 写前记目标；同 subId 回读验真；单项失败不假成功。
 */
object CarrierConfigOverrideWriter {
    private const val TAG = "OneIMS-OneKuku"

    data class Result(
        val success: Boolean,
        val message: String,
        val detail: Map<String, Boolean> = emptyMap(),
        val targetLabel: String = "",
        /** true=持久覆盖；false=临时覆盖（重启/服务重启可能丢失）。 */
        val persistent: Boolean = true,
    )

    /**
     * 对 [subId] 应用 CarrierConfig 覆盖（优先持久，权限不足则临时）。
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
        var usedPersistent = true

        val batchOk = runCatching {
            usedPersistent = overrideConfigBestEffort(context, subId, values)
            true
        }.getOrElse { error ->
            // 写入尚未真正开始时不得吞掉异常：上层需跳过「自动回滚」恐吓文案。
            rethrowIfWriteNeverStarted(error)
            Log.w(TAG, "batch write failed: ${error.message}")
            failures += "batch: ${sanitizeOverrideError(error)}"
            false
        }

        for (key in values.keySet()) {
            val single = PersistableBundle()
            copyKey(values, single, key)
            var ok = batchOk && verifyOverride(context, subId, single)
            if (!ok) {
                ok = runCatching {
                    val persistent = overrideConfigBestEffort(context, subId, single)
                    usedPersistent = usedPersistent && persistent
                    verifyOverride(context, subId, single)
                }.getOrElse { error ->
                    rethrowIfWriteNeverStarted(error)
                    Log.w(TAG, "key=$key failed: ${error.message}")
                    failures += "$key: ${sanitizeOverrideError(error)}"
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
        val modeHint = if (usedPersistent) "persistent" else "temporary"
        if (allOk) {
            return RootPersistenceSupport.decorateResultMessage(
                context,
                Result(
                    success = true,
                    message = "ok · $target · $reason · $modeHint",
                    detail = detail,
                    targetLabel = target,
                    persistent = usedPersistent,
                ),
            )
        }
        val message = buildString {
            append("partial/fail · $target · $reason · $successCount/${detail.size} · $modeHint")
            if (failures.isNotEmpty()) {
                append(" · ")
                append(failures.joinToString("; "))
            }
        }
        return RootPersistenceSupport.decorateResultMessage(
            context,
            Result(
                success = false,
                message = message,
                detail = detail,
                targetLabel = target,
                persistent = usedPersistent,
            ),
        )
    }

    /**
     * 清除覆盖。
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
                val persistent = overrideConfigBestEffort(context, subId, null)
                val modeHint = if (persistent) "persistent" else "temporary"
                RootPersistenceSupport.decorateResultMessage(
                    context,
                    Result(
                        success = true,
                        message = "cleared all · $target · $reason · $modeHint",
                        targetLabel = target,
                        persistent = persistent,
                    ),
                )
            }.getOrElse {
                rethrowIfWriteNeverStarted(it)
                RootPersistenceSupport.decorateResultMessage(
                    context,
                    Result(
                        success = false,
                        message = "clear failed · $target · ${sanitizeOverrideError(it)}",
                        targetLabel = target,
                        persistent = false,
                    ),
                )
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
        return "当前目标：卡$slot · $carrier"
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

    /**
     * @return 实际是否使用了 persistent=true
     */
    private fun overrideConfigBestEffort(
        context: Context,
        subId: Int,
        bundle: PersistableBundle?,
    ): Boolean {
        if (ConfigStore.isForceTemporaryOverride(context)) {
            Log.i(TAG, "force_temporary_override=on → skip persistent=true")
            SystemApiBroker.overrideConfig(
                context = context,
                subId = subId,
                bundle = bundle,
                persistent = false,
            )
            return false
        }
        return try {
            SystemApiBroker.overrideConfig(
                context = context,
                subId = subId,
                bundle = bundle,
                persistent = true,
            )
            true
        } catch (error: Throwable) {
            if (!isPersistentPrivilegeDenied(error)) throw error
            Log.w(TAG, "persistent=true denied, fallback to temporary: ${error.message}")
            SystemApiBroker.overrideConfig(
                context = context,
                subId = subId,
                bundle = bundle,
                persistent = false,
            )
            false
        }
    }

    internal fun isPersistentPrivilegeDenied(error: Throwable): Boolean {
        val messages = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
        return messages.contains("only can be invoked by system app", ignoreCase = true) ||
            (
                messages.contains("persistent=true", ignoreCase = true) &&
                    messages.contains("system app", ignoreCase = true)
                )
    }

    private fun sanitizeOverrideError(error: Throwable): String {
        if (isPersistentPrivilegeDenied(error)) {
            return "需要系统级持久写入权限；已尝试临时覆盖仍失败"
        }
        return error.message ?: error.javaClass.simpleName
    }

    /**
     * [BrokerExecutionException.operationStarted]=false 表示 AMS/桥尚未真正开写；
     * 必须原样抛给 [ImsController]，避免被聚合成 partial/fail 后误触发回滚文案。
     */
    internal fun rethrowIfWriteNeverStarted(error: Throwable) {
        val bee = generateSequence(error) { it.cause }
            .filterIsInstance<BrokerExecutionException>()
            .firstOrNull()
        if (bee != null && !bee.operationStarted) {
            throw bee
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
