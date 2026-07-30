package com.oneims.app.core

/**
 * Provisioning 写入结果分级：区分「OEM 随缘软失败」与「核心写入硬失败」。
 *
 * 一加 / 高通常见：CarrierConfig 已生效，但 key=26（漫游）/ key=27（WFC 模式）返回 result=1。
 * 小米 / HyperOS：key=28（VoWiFi 开关）与 key=68（VoIMS opt-in）也常拒写或反射失败；
 * 这类不得把整单打成操作失败，更不得向上抛崩进程。
 */
object ProvisioningWritePolicy {

    /** 非关键：拒写也不应整单失败的 detail key（全 OEM）。 */
    val SOFT_PROVISIONING_KEYS: Set<String> = setOf(
        "provision_vowifi_roaming",
        "provision_wfc_mode",
    )

    /** 小米系额外软失败 detail key（对齐 PixelIMS「只写 68 + 吞异常」的容错面）。 */
    val XIAOMI_SOFT_PROVISIONING_KEYS: Set<String> = setOf(
        "provision_vowifi",
        "provision_voims_opt_in",
    )

    /** AOSP provisioning int key：一加/高通常拒写，禁止向上抛崩进程。 */
    val SOFT_PROVISIONING_INT_KEYS: Set<Int> = setOf(
        ProvisioningKeys.KEY_VOICE_OVER_WIFI_ROAMING, // 26
        ProvisioningKeys.KEY_VOICE_OVER_WIFI_MODE, // 27
    )

    /** 小米系额外软 int key。 */
    val XIAOMI_SOFT_PROVISIONING_INT_KEYS: Set<Int> = setOf(
        ProvisioningKeys.KEY_VOICE_OVER_WIFI_ENABLED, // 28
        ProvisioningKeys.KEY_VOIMS_OPT_IN_STATUS, // 68
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
