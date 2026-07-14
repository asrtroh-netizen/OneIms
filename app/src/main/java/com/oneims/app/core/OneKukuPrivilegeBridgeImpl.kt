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
        val method = SnapshotEntry.WRITE_METHOD_PERSISTENT_CC
        val rawIccid = iccidRaw ?: OneKukuSnapshotStore.readIccidRaw(context, sim.subscriptionId)

        val entries = buildList {
            val volte = caps?.volte ?: applied?.volte ?: true
            val vowifiOn = caps?.vowifi ?: applied?.vowifi ?: true
            val vonr = caps?.vonr ?: applied?.vonr ?: false
            val wfc = (caps?.wfcMode ?: applied?.wfcMode)?.value ?: 1
            add(SnapshotEntry("ims", "volte", volte.toString(), method, true))
            add(SnapshotEntry("ims", "vowifi", vowifiOn.toString(), method, true))
            add(SnapshotEntry("ims", "vonr", vonr.toString(), method, true))
            add(SnapshotEntry("wfc", "mode", wfc.toString(), method, true))
            // 兼容恢复侧仍读 ims/wfcMode
            add(SnapshotEntry("ims", "wfcMode", wfc.toString(), method, true))
            add(
                SnapshotEntry(
                    "nr5g",
                    "enabled",
                    (caps?.nr5g == true || fiveG.enabled).toString(),
                    method,
                    true,
                ),
            )
            add(SnapshotEntry("signal", "adjustment", signal.toString(), method, true))
            vowifi.formatIndex?.let {
                add(SnapshotEntry("vowifi_name", "formatIndex", it.toString(), method, true))
            }
            if (vowifi.customCarrierName.isNotBlank()) {
                add(
                    SnapshotEntry(
                        "vowifi_name",
                        "customCarrier",
                        vowifi.customCarrierName,
                        method,
                        true,
                    ),
                )
            }
            identity?.let {
                if (it.carrierName.isNotBlank()) {
                    add(SnapshotEntry("identity", "carrierName", it.carrierName, method, true))
                }
                if (it.imsUserAgent.isNotBlank()) {
                    add(
                        SnapshotEntry(
                            "identity",
                            "imsUserAgent",
                            OneKukuSnapshotStore.maskSensitiveValue(
                                "identity",
                                "imsUserAgent",
                                it.imsUserAgent,
                            ),
                            method,
                            true,
                        ),
                    )
                }
            }
            // 5G 显示相关：仅在已启用时记录摘要，不存敏感串全文过长
            if (fiveG.enabled) {
                add(SnapshotEntry("five_g_display", "enabled", "true", method, true))
                add(SnapshotEntry("five_g_display", "mode", fiveG.mode, method, true))
            }
        }

        return OneKukuSnapshot(
            subId = sim.subscriptionId,
            slotIndex = sim.slotIndex,
            carrierId = sim.carrierId,
            mccmnc = "${sim.mcc}${sim.mnc}",
            carrierName = sim.carrierName,
            iccidHash = OneKukuSnapshotStore.hashIccid(rawIccid),
            entries = entries,
            appliedAt = now,
            lastVerifiedAt = now,
            lastRestoreStatus = "applied",
        )
    }
}
