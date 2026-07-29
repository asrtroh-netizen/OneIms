package com.oneims.app.core

/**
 * Provisioning 写入结果分级：区分「OEM 随缘软失败」与「核心写入硬失败」。
 *
 * 一加 / 高通常见：CarrierConfig 已生效，但 key=26（漫游）/ key=27（WFC 模式）返回 result=1。
 * 这类不得把整单打成操作失败。
 */
object ProvisioningWritePolicy {

    /** 非关键：拒写也不应整单失败的 detail key。 */
    val SOFT_PROVISIONING_KEYS: Set<String> = setOf(
        "provision_vowifi_roaming",
        "provision_wfc_mode",
    )

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

    fun classifyApplyOutcome(detail: Map<String, Boolean>): ApplyOutcome {
        val failed = detail.filterValues { ok -> !ok }.keys.toList()
        if (failed.isEmpty()) {
            return ApplyOutcome(OutcomeKind.FULL_OK, treatAsSuccess = true, emptyList(), emptyList())
        }
        val soft = failed.filter { it in SOFT_PROVISIONING_KEYS }
        val hard = failed.filter { it !in SOFT_PROVISIONING_KEYS }
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
