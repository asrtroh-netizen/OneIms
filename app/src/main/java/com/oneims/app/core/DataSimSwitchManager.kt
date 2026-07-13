package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SubscriptionManager
import android.util.Log
import com.oneims.app.R
import com.oneims.app.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 应用内与控制中心磁贴共享的只读 SIM 展示模型。 */
data class SimCardInfo(
    val subId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val shortName: String,
    val isDefaultData: Boolean,
)

/** 切卡结果：[Success.warning] 表达“已切卡但数据恢复未确认”，[Failed.reason] 不透出堆栈。 */
sealed class DataSimSwitchResult {
    data class Success(val warning: String? = null) : DataSimSwitchResult()
    data class Failed(val reason: String) : DataSimSwitchResult()
}

/**
 * 默认数据卡切换的唯一执行入口。应用内 UI 与 Quick Settings Tile 都只调用这里，
 * 真正的 Shizuku/Root 系统调用全部封装在内部。
 */
interface DataSimSwitchManager {
    fun getActiveSims(context: Context): List<SimCardInfo>
    fun getDefaultDataSubId(): Int
    suspend fun switchDefaultDataSubId(context: Context, targetSubId: Int): DataSimSwitchResult
}

/**
 * 将已经提交系统副作用的短事务托管到进程级作用域。调用 Activity 被销毁时只会停止等待，
 * 不会取消正在执行的默认卡回读与 enable-only 数据恢复。
 */
internal class ProcessScopedOperationRunner(
    private val scope: CoroutineScope,
) {
    suspend fun <T> run(block: suspend () -> T): T = scope.async { block() }.await()
}

object DataSimSwitchManagerImpl : DataSimSwitchManager {

    private const val TAG = "OneIMS-DataSwitch"
    private const val READBACK_DELAY_MS = 2_500L
    private const val RESTORE_READBACK_DELAY_MS = 750L
    private val switchMutex = Mutex()
    private val operationRunner = ProcessScopedOperationRunner(
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    @SuppressLint("MissingPermission")
    override fun getActiveSims(context: Context): List<SimCardInfo> {
        val defaultDataSubId = getDefaultDataSubId()
        return ImsController.listSims(context).map { sim ->
            SimCardInfo(
                subId = sim.subscriptionId,
                slotIndex = sim.slotIndex,
                carrierName = sim.carrierName,
                shortName = formatCarrierShortName(sim.carrierName),
                isDefaultData = sim.subscriptionId == defaultDataSubId,
            )
        }
    }

    override fun getDefaultDataSubId(): Int = SystemApiBroker.getDefaultDataSubId()

    override suspend fun switchDefaultDataSubId(
        context: Context,
        targetSubId: Int,
    ): DataSimSwitchResult = operationRunner.run {
        switchMutex.withLock {
            runCatching {
                switchDefaultDataSubIdLocked(context.applicationContext, targetSubId)
            }.getOrElse { error ->
                DataSimSwitchResult.Failed(OperationErrors.describe(error))
            }
        }
    }

    private suspend fun switchDefaultDataSubIdLocked(
        context: Context,
        targetSubId: Int,
    ): DataSimSwitchResult {
        val activeSubIds = ImsController.listSims(context).map { it.subscriptionId }.toSet()
        if (targetSubId !in activeSubIds) {
            return DataSimSwitchResult.Failed(
                context.getString(R.string.data_switch_invalid_target),
            )
        }
        if (!ShizukuManager.isRunning() || !ShizukuManager.isGranted()) {
            return DataSimSwitchResult.Failed(
                context.getString(R.string.data_switch_no_permission),
            )
        }

        val beforeDefaultSubId = getDefaultDataSubId()
        val beforeMobileData = readMobileDataEnabled(beforeDefaultSubId)
        val beforeMobileDataEnabled = beforeMobileData.value
        Log.i(
            TAG,
            "before defaultDataSubId=$beforeDefaultSubId targetSubId=$targetSubId " +
                "mobileDataEnabled=$beforeMobileDataEnabled " +
                "mobileDataRead=${beforeMobileData.error ?: "ok"}",
        )
        if (targetSubId == beforeDefaultSubId) {
            Log.i(TAG, "switch command result=skipped_already_default")
            return DataSimSwitchResult.Success()
        }

        // 铁律：切卡失败绝不牵连 IMS/CarrierConfig——这里只做一次纯粹的默认数据卡系统调用，
        // 不与 ImsController.applyAll 等写入路径共享任何回滚逻辑，失败即止步于返回 Failed。
        val switchError = runCatching { SystemApiBroker.setDefaultDataSubId(targetSubId) }
            .exceptionOrNull()
        Log.i(
            TAG,
            "switch command result=${switchError?.let(OperationErrors::describe) ?: "accepted"}",
        )
        if (switchError != null) {
            return DataSimSwitchResult.Failed(OperationErrors.describe(switchError))
        }

        delay(READBACK_DELAY_MS)
        val afterDefaultSubId = getDefaultDataSubId()
        if (afterDefaultSubId != targetSubId) {
            val actualDefaultData = readMobileDataEnabled(afterDefaultSubId)
            Log.i(
                TAG,
                "after defaultDataSubId=$afterDefaultSubId mobileDataEnabled=" +
                    "${actualDefaultData.value} mobileDataRead=${actualDefaultData.error ?: "ok"} " +
                    "restoreAttempted=false " +
                    "restoreSucceeded=null restoreResult=switch_readback_mismatch",
            )
            return DataSimSwitchResult.Failed(
                context.getString(R.string.data_switch_readback_failed),
            )
        }

        val initialAfterMobileData = readMobileDataEnabled(targetSubId)
        var afterMobileDataEnabled = initialAfterMobileData.value
        var restoreAttempted = false
        var restoreSucceeded: Boolean? = null
        var restoreError: Throwable? = null

        if (shouldRestoreMobileData(beforeMobileDataEnabled, afterMobileDataEnabled)) {
            restoreAttempted = true
            restoreError = runCatching {
                SystemApiBroker.enableUserMobileData(targetSubId)
            }.exceptionOrNull()
            if (restoreError == null) {
                delay(RESTORE_READBACK_DELAY_MS)
                afterMobileDataEnabled = readMobileDataEnabled(targetSubId).value
                restoreSucceeded = afterMobileDataEnabled == true
            } else {
                restoreSucceeded = false
            }
        }

        Log.i(
            TAG,
            "after defaultDataSubId=$afterDefaultSubId mobileDataEnabled=$afterMobileDataEnabled " +
                "mobileDataRead=${initialAfterMobileData.error ?: "ok"} " +
                "restoreAttempted=$restoreAttempted restoreSucceeded=$restoreSucceeded " +
                "restoreResult=${when {
                    !restoreAttempted -> "not_needed"
                    restoreError != null -> OperationErrors.describe(restoreError)
                    restoreSucceeded == true -> "confirmed_enabled"
                    else -> "readback_not_enabled"
                }}",
        )

        val warning = when (
            mobileDataWarning(
                before = beforeMobileDataEnabled,
                after = initialAfterMobileData.value,
                restoreAttempted = restoreAttempted,
                restoreSucceeded = restoreSucceeded,
            )
        ) {
            MobileDataWarning.RESTORE_FAILED ->
                context.getString(R.string.data_switch_mobile_data_restore_failed)
            MobileDataWarning.STATE_UNKNOWN ->
                context.getString(R.string.data_switch_mobile_data_state_unknown)
            null -> null
        }
        return DataSimSwitchResult.Success(warning)
    }

    internal fun shouldRestoreMobileData(wasEnabled: Boolean?, isEnabledAfter: Boolean?): Boolean =
        wasEnabled == true && isEnabledAfter == false

    internal enum class MobileDataWarning {
        RESTORE_FAILED,
        STATE_UNKNOWN,
    }

    internal fun mobileDataWarning(
        before: Boolean?,
        after: Boolean?,
        restoreAttempted: Boolean,
        restoreSucceeded: Boolean?,
    ): MobileDataWarning? = when {
        restoreAttempted && restoreSucceeded != true -> MobileDataWarning.RESTORE_FAILED
        before == null || after == null -> MobileDataWarning.STATE_UNKNOWN
        else -> null
    }

    private data class MobileDataRead(
        val value: Boolean?,
        val error: String?,
    )

    private fun readMobileDataEnabled(subId: Int): MobileDataRead {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            return MobileDataRead(null, "invalid_sub_id")
        }
        return runCatching {
            val value = SystemApiBroker.readUserMobileDataEnabled(subId)
            MobileDataRead(value, if (value == null) "unsupported_read_api" else null)
        }.getOrElse { error ->
            MobileDataRead(null, OperationErrors.describe(error))
        }
    }

}

/** 运营商短名格式化，供应用内卡片与控制中心磁贴使用。 */
fun formatCarrierShortName(name: String?): String {
    val raw = name?.trim().orEmpty()
    if (raw.isEmpty()) return "—"

    return when {
        raw.contains("China Mobile", ignoreCase = true) -> "CMCC"
        raw.contains("中国移动") -> "CMCC"
        raw.contains("China Unicom", ignoreCase = true) -> "CU"
        raw.contains("中国联通") -> "CU"
        raw.contains("China Telecom", ignoreCase = true) -> "CT"
        raw.contains("中国电信") -> "CT"
        raw.contains("CMHK", ignoreCase = true) -> "CMHK"
        raw.length > 8 -> raw.take(8)
        else -> raw
    }
}
