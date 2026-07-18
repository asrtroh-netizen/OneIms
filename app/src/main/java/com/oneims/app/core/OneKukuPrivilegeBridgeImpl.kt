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
            // 高级选项按卡持久化：本卡有重放源才写入本卡快照，禁止把卡 A 的选项灌进卡 B。
            ConfigStore.lastAdvancedOptions(context, sim.subscriptionId)?.let { opt ->
                add(SnapshotEntry("advanced", "wfc_roaming", opt.wfcRoamingEnabled.toString(), method, true))
                add(SnapshotEntry("advanced", "show_wfc_mode", opt.showWfcMode.toString(), method, true))
                add(
                    SnapshotEntry(
                        "advanced",
                        "show_wfc_roaming_mode",
                        opt.showWfcRoamingMode.toString(),
                        method,
                        true,
                    ),
                )
                add(SnapshotEntry("advanced", "wifi_only", opt.supportWifiOnly.toString(), method, true))
                add(SnapshotEntry("advanced", "allow_apn_add", opt.allowAddingApns.toString(), method, true))
                add(SnapshotEntry("advanced", "vowifi_icon", opt.showVowifiIcon.toString(), method, true))
                add(
                    SnapshotEntry(
                        "advanced",
                        "data_rat_icon",
                        opt.alwaysShowDataRatIcon.toString(),
                        method,
                        true,
                    ),
                )
                add(SnapshotEntry("advanced", "4g_for_lte", opt.show4gForLteIcon.toString(), method, true))
                add(SnapshotEntry("advanced", "hide_lte_plus", opt.hideLtePlusIcon.toString(), method, true))
                add(SnapshotEntry("advanced", "show_ims_status", opt.showImsStatus.toString(), method, true))
                add(SnapshotEntry("advanced", "ss_over_cdma", opt.ssOverCdma.toString(), method, true))
                add(SnapshotEntry("advanced", "enhanced_4g", opt.enhanced4g.toString(), method, true))
            }
            // 5G 显示：仅归属卡记录
            if (fiveG.enabled && ConfigStore.lastFiveGDisplaySubId(context) == sim.subscriptionId) {
                add(SnapshotEntry("five_g_display", "enabled", "true", method, true))
                add(SnapshotEntry("five_g_display", "mode", fiveG.mode, method, true))
            }
            // 运营商附加能力（ViLTE / UT / Cross-SIM）
            caps?.let { c ->
                if (c.vilte || c.ut || c.crossSim) {
                    add(SnapshotEntry("extras", "vilte", c.vilte.toString(), method, true))
                    add(SnapshotEntry("extras", "ut", c.ut.toString(), method, true))
                    add(SnapshotEntry("extras", "cross_sim", c.crossSim.toString(), method, true))
                }
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
