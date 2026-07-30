package com.oneims.app.core

/**
 * Provisioning 写入结果分级：区分「OEM 随缘软失败」与「核心写入硬失败」。
 *
 * 产品边界：
 * - **Pixel**：通信主链路；VoLTE key=10 **硬**；开机自启硬保证。
 * - **国产 VoWIFI OEM**（vivo / OPPO / 一加 / 小米等）：不走通信主战场，以 VoWIFI 为主；
 *   VoLTE 拒写可软，避免挡 VoWIFI 成功。
 *
 * 全机型软：26/27/68。国产额外软：28 + VoLTE(10) 的 detail 分级。
 */
object ProvisioningWritePolicy {

    /** 非关键：拒写也不应整单失败的 detail key（全 OEM，含 Pixel）。 */
    val SOFT_PROVISIONING_KEYS: Set<String> = setOf(
        "provision_vowifi_roaming",
        "provision_wfc_mode",
        "provision_voims_opt_in",
    )

    /**
     * 国产 VoWIFI OEM 额外软失败 detail。
     * 含 provision_volte：国产不依赖通信主链路，VoLTE 拒写不得挡 VoWIFI。
     */
    val DOMESTIC_VOWIFI_SOFT_PROVISIONING_KEYS: Set<String> = setOf(
        "provision_vowifi",
        "provision_volte",
    )

    /** AOSP provisioning int key：全机型软失败、禁止向上抛崩进程。 */
    val SOFT_PROVISIONING_INT_KEYS: Set<Int> = setOf(
        ProvisioningKeys.KEY_VOICE_OVER_WIFI_ROAMING, // 26
        ProvisioningKeys.KEY_VOICE_OVER_WIFI_MODE, // 27
        ProvisioningKeys.KEY_VOIMS_OPT_IN_STATUS, // 68
    )

    /** 国产 VoWIFI OEM 额外软 int（含 28；VoLTE 10 仅国产软抛/软分级）。 */
    val DOMESTIC_VOWIFI_SOFT_PROVISIONING_INT_KEYS: Set<Int> = setOf(
        ProvisioningKeys.KEY_VOICE_OVER_WIFI_ENABLED, // 28
        ProvisioningKeys.KEY_VOLTE_PROVISIONING_STATUS, // 10
    )

    fun softDetailKeys(
        domesticVowifiOem: Boolean = OemDeviceCompat.isDomesticVowifiOem(),
    ): Set<String> =
        if (domesticVowifiOem) {
            SOFT_PROVISIONING_KEYS + DOMESTIC_VOWIFI_SOFT_PROVISIONING_KEYS
        } else {
            SOFT_PROVISIONING_KEYS
        }

    fun isSoftProvisioningIntKey(
        key: Int,
        domesticVowifiOem: Boolean = OemDeviceCompat.isDomesticVowifiOem(),
    ): Boolean {
        if (key in SOFT_PROVISIONING_INT_KEYS) return true
        return domesticVowifiOem && key in DOMESTIC_VOWIFI_SOFT_PROVISIONING_INT_KEYS
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
        domesticVowifiOem: Boolean = OemDeviceCompat.isDomesticVowifiOem(),
    ): ApplyOutcome {
        val softKeys = softDetailKeys(domesticVowifiOem)
        val failed = detail.filterValues { ok -> !ok }.keys.toList()
        if (failed.isEmpty()) {
            return ApplyOutcome(OutcomeKind.FULL_OK, treatAsSuccess = true, emptyList(), emptyList())
        }
        val soft = failed.filter { it in softKeys }
        val hard = failed.filter { it !in softKeys }
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

    fun isOemProvisioningReject(errorText: String): Boolean {
        val t = errorText.lowercase()
        return t.contains("ims provisioning rejected") ||
            t.contains("provisioning rejected key=")
    }
}
