package com.oneims.app.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import android.telephony.CarrierConfigManager
import android.util.Log
import com.oneims.app.R
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.Locale

/**
 * APN type 是逗号分隔的稳定 token；单独收敛解析规则，避免用 contains("ims")
 * 误判诸如 "xcapims" 的值，也让 Android 17 兼容逻辑可以脱离设备做单测。
 */
internal object ImsApnTypePolicy {
    private const val IMS_TYPE = "ims"

    fun normalize(raw: String?): List<String> =
        raw.orEmpty()
            .split(',')
            .map { type -> type.trim().lowercase(Locale.ROOT) }
            .filter { type -> type.isNotEmpty() }
            .distinct()

    fun isDedicatedIms(raw: String?): Boolean = normalize(raw) == listOf(IMS_TYPE)

    fun withoutIms(raw: String?): String? {
        val types = normalize(raw)
        if (types.size <= 1 || IMS_TYPE !in types) return null
        return types.filterNot { type -> type == IMS_TYPE }.joinToString(",")
    }
}

/**
 * 由 shell 通过 ActivityManager 启动的短生命周期代理。
 *
 * Android 2025-10 补丁禁止 shell UID 直接调用 CarrierConfig override；只有活动
 * Instrumentation 才能合法接收 shell 权限委托。因此所有需要“App UID + shell 权限”
 * 的操作都集中在这里执行，普通应用进程不再伪装成 Instrumentation。
 */
@SuppressLint("PrivateApi")
class BrokerInstrumentation : Instrumentation() {
    private companion object {
        const val TAG = "BrokerInstrumentation"
        val CARRIERS_URI: Uri = Uri.parse("content://telephony/carriers")
    }

    private var startupArguments: Bundle? = null

    private data class ApnRow(
        val id: Long,
        val type: String,
        val subId: Int,
    )

    private data class ApnRepairOutcome(
        val splitRows: Int,
    )

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        startupArguments = arguments
        start()
    }

    override fun onStart() {
        val arguments = startupArguments
        val requestId = arguments?.getString(BrokerProtocol.ARG_REQUEST_ID).orEmpty()
        val appContext = targetContext.applicationContext
        val operation = arguments?.getString(BrokerProtocol.ARG_OPERATION)
        var operationStarted = false
        val result = runCatching {
            require(arguments != null) { "Broker arguments are missing" }
            require(requestId.isNotBlank()) { "Broker request id is missing" }
            val requiredPermissions = BrokerProtocol.requiredPermissions(operation)
            require(requiredPermissions.isNotEmpty()) {
                "Unknown broker operation: ${operation ?: "null"}"
            }
            withDelegatedShellIdentity(
                context = appContext,
                requiredPermissions = requiredPermissions,
            ) {
                operationStarted = true
                execute(appContext, arguments)
            }
        }.fold(
            onSuccess = { message ->
                BrokerResult(
                    success = true,
                    message = message,
                    operationStarted = operationStarted,
                )
            },
            onFailure = { error ->
                BrokerResult(
                    success = false,
                    message = "${operation ?: "unknown_operation"}: ${OperationErrors.describe(error)}",
                    operationStarted = operationStarted,
                )
            },
        )

        val resultCode = if (result.success) Activity.RESULT_OK else Activity.RESULT_CANCELED
        val finishError = BrokerCompletionOrder.finishBeforePublishing(
            finishInstrumentation = {
                finish(resultCode, Bundle())
            },
            publishResult = {
                if (requestId.isNotBlank()) {
                    runCatching { BrokerResultStore.write(appContext, requestId, result) }
                        .onFailure { error ->
                            Log.w(TAG, "Failed to persist broker result", error)
                        }
                    BrokerResultBus.complete(requestId, result)
                }
            },
        )
        if (finishError != null) {
            Log.w(TAG, "Failed to finish broker instrumentation", finishError)
        }
    }

    private fun execute(context: Context, arguments: Bundle): String {
        return when (arguments.getString(BrokerProtocol.ARG_OPERATION)) {
            BrokerProtocol.OP_OVERRIDE_CONFIG -> {
                val subId = arguments.getInt(BrokerProtocol.ARG_SUB_ID, -1)
                require(subId >= 0) { "Invalid subscription id: $subId" }
                overrideConfig(
                    context = context,
                    subId = subId,
                    overrides = arguments.parcelable(BrokerProtocol.ARG_OVERRIDES),
                )
                "ok"
            }

            BrokerProtocol.OP_WRITE_GLOBAL_INT -> {
                val key = arguments.getString(BrokerProtocol.ARG_SETTING_KEY).orEmpty()
                val value = arguments.getInt(BrokerProtocol.ARG_SETTING_VALUE)
                require(key.isNotBlank()) { "Global setting key is missing" }
                val resolver = context.contentResolver
                check(Settings.Global.putInt(resolver, key, value)) {
                    "Settings provider rejected $key"
                }
                check(Settings.Global.getInt(resolver, key, Int.MIN_VALUE) == value) {
                    "Global setting readback mismatch for $key"
                }
                "ok"
            }

            BrokerProtocol.OP_INSERT_IMS_APN -> {
                val values = arguments.parcelable<ContentValues>(
                    BrokerProtocol.ARG_CONTENT_VALUES,
                ) ?: error("IMS APN values are missing")
                val outcome = ensureImsApn(context, values)
                context.resources.getQuantityString(
                    R.plurals.msg_apn_ok,
                    outcome.splitRows,
                    outcome.splitRows,
                )
            }

            else -> error("Unknown broker operation")
        }
    }

    @SuppressLint("MissingPermission")
    private fun overrideConfig(
        context: Context,
        subId: Int,
        overrides: PersistableBundle?,
    ) {
        val manager = context.getSystemService(CarrierConfigManager::class.java)
            ?: error("CarrierConfigManager is unavailable")
        val managerClass = CarrierConfigManager::class.java
        val threeArgumentMethod = managerClass.methods.firstOrNull { method ->
            method.name == "overrideConfig" &&
                method.parameterTypes.size == 3 &&
                method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                method.parameterTypes[1] == PersistableBundle::class.java
        }
        if (threeArgumentMethod != null) {
            // 部分预览版把 boolean persistent 改成 int overrideType；0 均表示非持久覆盖。
            val nonPersistent = when (threeArgumentMethod.parameterTypes[2]) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                else -> error("Unsupported overrideConfig third parameter")
            }
            threeArgumentMethod.invoke(manager, subId, overrides, nonPersistent)
            return
        }

        val twoArgumentMethod = managerClass.getMethod(
            "overrideConfig",
            Int::class.javaPrimitiveType,
            PersistableBundle::class.java,
        )
        twoArgumentMethod.invoke(manager, subId, overrides)
    }

    /**
     * 按当前订阅修复 IMS APN：
     * 1) 把混在 default/supl 等通用 APN 中的 ims token 拆出；
     * 2) 复用已有专用 IMS 行，缺失时才插入；
     * 3) 任一步失败都补偿回滚本次已完成的更新/插入，避免留下半修复状态。
     *
     * AOSP 允许一行承载多种 APN type，因此这里只作为用户确认后的兼容修复，
     * 不在后台静默执行。
     */
    private fun ensureImsApn(context: Context, values: ContentValues): ApnRepairOutcome {
        val numeric = values.getAsString("numeric").orEmpty()
        val requestedSubId = values.getAsInteger("sub_id") ?: -1
        require(numeric.length in 5..6 && numeric.all(Char::isDigit)) {
            "IMS APN numeric must be 5 or 6 digits"
        }
        require(requestedSubId >= 0) { "IMS APN subscription id is missing" }

        val originalRows = readApnRows(context, numeric)
        val scopedRows = selectRowsForSubscription(originalRows, requestedSubId)
        val updatedRows = mutableListOf<ApnRow>()
        var insertedUri: Uri? = null

        try {
            splitMixedImsRows(context, scopedRows, updatedRows)
            insertedUri = insertDedicatedImsIfMissing(context, values, scopedRows)
            verifyImsApnRepair(context, numeric, requestedSubId, updatedRows)
            return ApnRepairOutcome(splitRows = updatedRows.size)
        } catch (error: Throwable) {
            rollbackImsApnRepair(context, insertedUri, updatedRows, error)
        }
    }

    private fun splitMixedImsRows(
        context: Context,
        rows: List<ApnRow>,
        updatedRows: MutableList<ApnRow>,
    ) {
        rows.forEach { row ->
            val remainingType = ImsApnTypePolicy.withoutIms(row.type) ?: return@forEach
            val updated = context.contentResolver.update(
                Uri.withAppendedPath(CARRIERS_URI, row.id.toString()),
                ContentValues().apply { put("type", remainingType) },
                null,
                null,
            )
            check(updated == 1) {
                "Telephony provider updated $updated rows for APN id=${row.id}"
            }
            updatedRows += row
        }
    }

    private fun insertDedicatedImsIfMissing(
        context: Context,
        values: ContentValues,
        scopedRows: List<ApnRow>,
    ): Uri? {
        if (scopedRows.any { row -> ImsApnTypePolicy.isDedicatedIms(row.type) }) return null
        val insertValues = ContentValues(values).apply {
            // 只有运营商全局模板可用时，专用 IMS 行保持同一全局作用域；
            // 否则同 MCC/MNC 的另一张卡可能在拆分后失去 IMS 覆盖。
            if (scopedRows.isNotEmpty() && scopedRows.all { row -> row.subId < 0 }) {
                put("sub_id", -1)
            }
        }
        return checkNotNull(context.contentResolver.insert(CARRIERS_URI, insertValues)) {
            "Telephony provider rejected dedicated IMS APN"
        }
    }

    private fun verifyImsApnRepair(
        context: Context,
        numeric: String,
        requestedSubId: Int,
        updatedRows: List<ApnRow>,
    ) {
        val allVerifiedRows = readApnRows(context, numeric)
        val verifiedRows = selectRowsForSubscription(allVerifiedRows, requestedSubId)
        check(verifiedRows.any { row -> ImsApnTypePolicy.isDedicatedIms(row.type) }) {
            "Dedicated IMS APN readback is missing"
        }
        check(updatedRows.all { updatedRow ->
            allVerifiedRows
                .firstOrNull { row -> row.id == updatedRow.id }
                ?.let { row -> ImsApnTypePolicy.withoutIms(row.type) == null } == true
        }) {
            "A mixed IMS APN type still exists after repair"
        }
    }

    private fun rollbackImsApnRepair(
        context: Context,
        insertedUri: Uri?,
        updatedRows: List<ApnRow>,
        originalError: Throwable,
    ): Nothing {
        val resolver = context.contentResolver
        val rollbackErrors = mutableListOf<Throwable>()
        insertedUri?.let { uri ->
            runCatching {
                check(resolver.delete(uri, null, null) == 1) {
                    "Failed to remove inserted IMS APN"
                }
            }.onFailure(rollbackErrors::add)
        }
        updatedRows.asReversed().forEach { row ->
            runCatching {
                val restored = resolver.update(
                    Uri.withAppendedPath(CARRIERS_URI, row.id.toString()),
                    ContentValues().apply { put("type", row.type) },
                    null,
                    null,
                )
                check(restored == 1) { "Failed to restore APN id=${row.id}; updated=$restored" }
            }.onFailure(rollbackErrors::add)
        }
        if (rollbackErrors.isEmpty()) throw originalError

        val rollbackFailure = IllegalStateException(
            "APN repair failed and rollback was incomplete; original=" +
                OperationErrors.describe(originalError) +
                "; rollback=" +
                rollbackErrors.joinToString { failure -> OperationErrors.describe(failure) },
        )
        rollbackFailure.addSuppressed(originalError)
        rollbackErrors.forEach(rollbackFailure::addSuppressed)
        throw rollbackFailure
    }

    private fun readApnRows(context: Context, numeric: String): List<ApnRow> {
        val cursor = checkNotNull(
            context.contentResolver.query(
                CARRIERS_URI,
                arrayOf("_id", "type", "sub_id"),
                "numeric=?",
                arrayOf(numeric),
                null,
            ),
        ) {
            "Telephony provider returned no APN cursor"
        }
        return cursor.use {
            val idColumn = it.getColumnIndexOrThrow("_id")
            val typeColumn = it.getColumnIndexOrThrow("type")
            val subIdColumn = it.getColumnIndexOrThrow("sub_id")
            buildList {
                while (it.moveToNext()) {
                    add(
                        ApnRow(
                            id = it.getLong(idColumn),
                            type = it.getString(typeColumn).orEmpty(),
                            subId = if (it.isNull(subIdColumn)) -1 else it.getInt(subIdColumn),
                        ),
                    )
                }
            }
        }
    }

    /**
     * 优先只处理当前订阅自己的行；运营商表只有全局行时才退到 sub_id=-1，
     * 并让拆出的专用 IMS 行保持全局作用域，避免其他同网卡丢失 IMS 覆盖。
     */
    private fun selectRowsForSubscription(rows: List<ApnRow>, requestedSubId: Int): List<ApnRow> {
        val subscriptionRows = rows.filter { row -> row.subId == requestedSubId }
        return subscriptionRows.ifEmpty { rows.filter { row -> row.subId < 0 } }
    }

    private inline fun <T> withDelegatedShellIdentity(
        context: Context,
        requiredPermissions: Set<String>,
        block: () -> T,
    ): T {
        HiddenApiBypass.addHiddenApiExemptions("")
        // Instrumentation 已由 Shizuku 以 shell UID 启动，直接让 AMS 把所需 shell 权限
        // 委托给当前 App UID。不能先 getUiAutomation()：其注册阶段本身需要
        // RETRIEVE_WINDOW_CONTENT，会在权限尚未委托时先行失败。
        SystemApiBroker.startShellPermissionDelegation(requiredPermissions)
        var operationError: Throwable? = null
        return try {
            val missing = requiredPermissions.filter { permission ->
                context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
            }
            check(missing.isEmpty()) {
                "Shell permission delegation missing: ${missing.joinToString()}"
            }
            block()
        } catch (error: Throwable) {
            operationError = error
            throw error
        } finally {
            try {
                SystemApiBroker.stopShellPermissionDelegation()
            } catch (cleanupError: Throwable) {
                // stop API 在新系统上偶发缺失/改名：不得把「写入已成功」改判失败，
                // 也不得在无主错误时用清理异常冒充业务失败（会触发误导性自动回滚）。
                when {
                    ShellDelegateCleanupPolicy.isBenignStopFailure(cleanupError) -> Log.w(
                        TAG,
                        "Shell permission stop skipped/unavailable: " +
                            OperationErrors.describe(cleanupError),
                    )
                    operationError != null -> operationError.addSuppressed(cleanupError)
                    else -> Log.w(
                        TAG,
                        "Shell permission stop failed after success: " +
                            OperationErrors.describe(cleanupError),
                        cleanupError,
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T> Bundle.parcelable(key: String): T? =
        getParcelable(key) as? T
}
