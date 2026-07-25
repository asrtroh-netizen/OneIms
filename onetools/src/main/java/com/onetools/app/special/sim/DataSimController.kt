package com.onetools.app.special.sim

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SubscriptionManager
import android.util.Log
import com.onetools.app.special.broker.SpecialBroker
import com.onetools.app.special.broker.SpecialErrors
import com.onetools.app.special.privilege.SpecialPrivilege
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SpecialSimInfo(
    val subId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val shortName: String,
    val isDefaultData: Boolean,
)

sealed class SpecialSimSwitchResult {
    data class Success(val warning: String? = null) : SpecialSimSwitchResult()
    data class Failed(val reason: String) : SpecialSimSwitchResult()
}

object DataSimController {
    private const val TAG = "OneTools-DataSwitch"
    private const val READBACK_DELAY_MS = 2_500L
    private const val RESTORE_READBACK_DELAY_MS = 750L
    private val switchMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @SuppressLint("MissingPermission")
    fun getActiveSims(context: Context): List<SpecialSimInfo> {
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val defaultDataSubId = getDefaultDataSubId()
        return sm.activeSubscriptionInfoList.orEmpty().map { info ->
            val name = info.carrierName?.toString().orEmpty().ifBlank { "SIM" }
            SpecialSimInfo(
                subId = info.subscriptionId,
                slotIndex = info.simSlotIndex,
                carrierName = name,
                shortName = name.take(12),
                isDefaultData = info.subscriptionId == defaultDataSubId,
            )
        }.sortedBy { it.slotIndex }
    }

    fun getDefaultDataSubId(): Int = SpecialBroker.getDefaultDataSubId()

    suspend fun switchDefaultDataSubId(
        context: Context,
        targetSubId: Int,
    ): SpecialSimSwitchResult = scope.async {
        switchMutex.withLock {
            runCatching {
                switchLocked(context.applicationContext, targetSubId)
            }.getOrElse { error ->
                SpecialSimSwitchResult.Failed(SpecialErrors.describe(error))
            }
        }
    }.await()

    private suspend fun switchLocked(
        context: Context,
        targetSubId: Int,
    ): SpecialSimSwitchResult {
        val sims = getActiveSims(context)
        if (sims.none { it.subId == targetSubId }) {
            return SpecialSimSwitchResult.Failed("目标卡无效或未激活")
        }
        if (!SpecialPrivilege.isReady()) {
            return SpecialSimSwitchResult.Failed("切卡需要 Shizuku 授权")
        }
        val beforeDefault = getDefaultDataSubId()
        val beforeMobileData = SpecialBroker.readUserMobileDataEnabled(beforeDefault)
        if (targetSubId == beforeDefault) {
            return SpecialSimSwitchResult.Success()
        }
        Log.i(TAG, "switch $beforeDefault -> $targetSubId mobileData=$beforeMobileData")
        runCatching {
            SpecialBroker.setDefaultDataSubId(targetSubId)
        }.getOrElse { error ->
            return SpecialSimSwitchResult.Failed(SpecialErrors.describe(error))
        }
        delay(READBACK_DELAY_MS)
        val afterDefault = getDefaultDataSubId()
        if (afterDefault != targetSubId) {
            return SpecialSimSwitchResult.Failed("切卡后回读未确认（当前=$afterDefault）")
        }
        var warning: String? = null
        if (beforeMobileData == true) {
            val afterMobile = SpecialBroker.readUserMobileDataEnabled(targetSubId)
            if (afterMobile == false) {
                runCatching { SpecialBroker.enableUserMobileData(targetSubId) }
                delay(RESTORE_READBACK_DELAY_MS)
                val restored = SpecialBroker.readUserMobileDataEnabled(targetSubId)
                if (restored != true) {
                    warning = "已切卡，但移动数据未能确认恢复开启"
                }
            }
        }
        return SpecialSimSwitchResult.Success(warning)
    }
}
