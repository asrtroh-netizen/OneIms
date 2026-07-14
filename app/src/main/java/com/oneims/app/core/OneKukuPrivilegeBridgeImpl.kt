package com.oneims.app.core

import android.content.Context
import com.oneims.app.model.SimInfo
import com.oneims.app.onekuku.OneKukuPrivilegeBridge
import com.oneims.app.onekuku.OneKukuSnapshot
import com.oneims.app.onekuku.OneKukuSnapshotStore
import com.oneims.app.onekuku.SnapshotEntry

/**
 * 将现有特权就绪态接到 OneKuku 执行器，避免 onekuku 包直接依赖第三方 SDK。
 */
object OneKukuPrivilegeBridgeImpl : OneKukuPrivilegeBridge {
    override fun isActivated(): Boolean = OneKukuManager.isReady()

    override fun requestWake(): Boolean {
        if (OneKukuManager.isReady()) return true
        if (OneKukuManager.isRunning()) {
            OneKukuManager.requestActivation()
            return OneKukuManager.isReady()
        }
        return false
    }
}

object OneKukuSnapshotFactory {
    fun fromCurrent(
        context: Context,
        sim: SimInfo,
        iccidRaw: String? = null,
    ): OneKukuSnapshot {
        val caps = ConfigStore.capabilityUiState(context, sim.subscriptionId)
        val applied = ConfigStore.lastApplied(context)
            ?.takeIf { it.subId == sim.subscriptionId }
        val fiveG = ConfigStore.fiveGDisplayConfig(context)
        val signal = ConfigStore.signalStrengthAdjustmentEnabled(context, sim.subscriptionId)
        val vowifi = VoWifiNameFormatManager.readSelection(context, sim.subscriptionId)
        val identity = ConfigStore.identityDraft(context, sim.subscriptionId)
        val now = System.currentTimeMillis()
        val entries = buildList {
            val volte = caps?.volte ?: applied?.volte ?: true
            val vowifiOn = caps?.vowifi ?: applied?.vowifi ?: true
            val vonr = caps?.vonr ?: applied?.vonr ?: false
            val wfc = (caps?.wfcMode ?: applied?.wfcMode)?.value ?: 1
            add(SnapshotEntry("ims", "volte", volte.toString()))
            add(SnapshotEntry("ims", "vowifi", vowifiOn.toString()))
            add(SnapshotEntry("ims", "vonr", vonr.toString()))
            add(SnapshotEntry("ims", "wfcMode", wfc.toString()))
            add(SnapshotEntry("nr5g", "enabled", (caps?.nr5g == true || fiveG.enabled).toString()))
            add(SnapshotEntry("signal", "adjustment", signal.toString()))
            vowifi.formatIndex?.let {
                add(SnapshotEntry("vowifi_name", "formatIndex", it.toString()))
            }
            if (vowifi.customCarrierName.isNotBlank()) {
                add(SnapshotEntry("vowifi_name", "customCarrier", vowifi.customCarrierName))
            }
            identity?.let {
                if (it.carrierName.isNotBlank()) {
                    add(SnapshotEntry("identity", "carrierName", it.carrierName))
                }
                if (it.imsUserAgent.isNotBlank()) {
                    add(SnapshotEntry("identity", "imsUserAgent", it.imsUserAgent))
                }
            }
        }
        return OneKukuSnapshot(
            subId = sim.subscriptionId,
            slotIndex = sim.slotIndex,
            carrierId = sim.carrierId,
            mccmnc = "${sim.mcc}${sim.mnc}",
            carrierName = sim.carrierName,
            iccidHash = OneKukuSnapshotStore.hashIccid(iccidRaw),
            entries = entries,
            appliedAt = now,
            lastVerifiedAt = now,
            lastRestoreStatus = "applied",
        )
    }
}
