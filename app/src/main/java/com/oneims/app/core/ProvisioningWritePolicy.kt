package com.oneims.app.core

/**
 * Provisioning 写入结果分级：区分「OEM 随缘软失败」与「核心写入硬失败」。
 *
 * 产品边界（权重）：
 * - **P0 Pixel VoWIFI**：`provision_vowifi` / key=28 **硬**（第一权重，失败必须可见，不得 soft）。
 * - **P0 Pixel 其它通信 + 开机**：VoLTE key=10 **硬**；开机自启硬保证。
 * - **P1 其它机子 VoWIFI**：vivo/OPPO/一加/小米/三星/荣耀等；key=28 可 soft，VoLTE 拒写可 soft。
 *
 * 全机型软：26/27/68（漫游/模式/opt-in 旁路）。国产额外软：28 + VoLTE(10)。
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
