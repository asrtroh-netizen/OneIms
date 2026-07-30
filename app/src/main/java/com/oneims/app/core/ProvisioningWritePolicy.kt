package com.oneims.app.core

/**
 * Provisioning 写入结果分级：区分「OEM 随缘软失败」与「核心写入硬失败」。
 *
 * 产品边界（勿扩）：
 * - **主战场 Pixel / Tensor**：CarrierConfig + VoLTE 硬路径必须硬；不得因旁路 soft 把整单打挂。
 * - **非 Pixel**：额外兜底以 **VoWIFI 相关** 为主（小米 key=28），不把 VoLTE key=10 软化。
 *
 * 一加 / 高通：key=26/27 常拒写。
 * 全机型：key=68（VoIMS opt-in）拒写不应当整单失败（对齐 pixel-volte-patch 容错面）。
 * 小米 / HyperOS：额外软化 key=28（VoWiFi 开关）。
 */
object ProvisioningWritePolicy {

    /** 非关键：拒写也不应整单失败的 detail key（全 OEM，含 Pixel）。 */
    val SOFT_PROVISIONING_KEYS: Set<String> = setOf(
        "provision_vowifi_roaming",
        "provision_wfc_mode",
        "provision_voims_opt_in",
    )

    /** 小米系额外：非 Pixel VoWIFI 相关软失败。 */
    val XIAOMI_SOFT_PROVISIONING_KEYS: Set<String> = setOf(
        "provision_vowifi",
    )

    /** AOSP provisioning int key：全机型软失败、禁止向上抛崩进程。 */
    val SOFT_PROVISIONING_INT_KEYS: Set<Int> = setOf(
        ProvisioningKeys.KEY_VOICE_OVER_WIFI_ROAMING, // 26
        ProvisioningKeys.KEY_VOICE_OVER_WIFI_MODE, // 27
        ProvisioningKeys.KEY_VOIMS_OPT_IN_STATUS, // 68
    )

    /** 小米系额外软 int key（VoWIFI）。VoLTE key=10 永不进入此集合。 */
    val XIAOMI_SOFT_PROVISIONING_INT_KEYS: Set<Int> = setOf(
        ProvisioningKeys.KEY_VOICE_OVER_WIFI_ENABLED, // 28
    )

    fun softDetailKeys(xiaomiFamily: Boolean = OemDeviceCompat.isXiaomiFamily()): Set<String> =
        if (xiaomiFamily) {
            SOFT_PROVISIONING_KEYS + XIAOMI_SOFT_PROVISIONING_KEYS
        } else {
            SOFT_PROVISIONING_KEYS
        }

    fun isSoftProvisioningIntKey(
        key: Int,
        xiaomiFamily: Boolean = OemDeviceCompat.isXiaomiFamily(),
    ): Boolean {
        if (key in SOFT_PROVISIONING_INT_KEYS) return true
        return xiaomiFamily && key in XIAOMI_SOFT_PROVISIONING_INT_KEYS
    }

    enum class OutcomeKind {
        FULL_OK,
        OEM_SOFT_PARTIAL,
        HARD_PARTIAL,
    }

    data class ApplyOutcome(
        val kind: OutcomeKind,
        val treatAsSuccess: Boolean,
        val softFailedKeys: List<String>,
        val hardFailedKeys: List<String>,
    )

    fun classifyApplyOutcome(
        detail: Map<String, Boolean>,
        xiaomiFamily: Boolean = OemDeviceCompat.isXiaomiFamily(),
    ): ApplyOutcome {
        val softKeys = softDetailKeys(xiaomiFamily)
        val failed = detail.filterValues { ok -> !ok }.keys.toList()
        if (failed.isEmpty()) {
            return ApplyOutcome(OutcomeKind.FULL_OK, treatAsSuccess = true, emptyList(), emptyList())
        }
        val soft = failed.filter { it in softKeys }
        val hard = failed.filter { it !in softKeys }
        // CarrierConfig 总开关失败仍算硬失败；仅软键失败且 override 成功 → 软成功。
        val carrierOk = detail["carrier_config_override"] != false
        return if (hard.isEmpty() && carrierOk) {
            ApplyOutcome(
                kind = OutcomeKind.OEM_SOFT_PARTIAL,
                treatAsSuccess = true,
                softFailedKeys = soft,
                hardFailedKeys = emptyList(),
            )
        } else {
            ApplyOutcome(
                kind = OutcomeKind.HARD_PARTIAL,
                treatAsSuccess = false,
                softFailedKeys = soft,
                hardFailedKeys = hard,
            )
        }
    }

    /** 是否为 `IMS provisioning rejected key=…` 类 OEM 拒写。 */
    fun isOemProvisioningReject(errorText: String): Boolean {
        val t = errorText.lowercase()
        return t.contains("ims provisioning rejected") ||
            t.contains("provisioning rejected key=")
    }
}
