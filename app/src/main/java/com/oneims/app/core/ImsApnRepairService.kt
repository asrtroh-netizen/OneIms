package com.oneims.app.core

import android.content.ContentValues
import android.content.Context
import com.oneims.app.R
import com.oneims.app.model.ConfigResult

/**
 * 把离线候选转换为最小 IMS 写入计划。候选只提供 APN/协议/认证参数，
 * SIM 身份和 type=ims 始终由当前订阅重建，避免把其他运营商标识带进系统表。
 */
internal object ImsApnRepairService {
    fun repair(
        context: Context,
        subId: Int,
        carrierId: Int,
        mcc: String,
        mnc: String,
        profile: ApnCatalogEntry?,
    ): ConfigResult {
        val normalizedMcc = mcc.trim()
        val normalizedMnc = mnc.trim()
        if (
            subId < 0 ||
            normalizedMcc.length != 3 ||
            normalizedMnc.length !in 2..3 ||
            !normalizedMcc.all(Char::isDigit) ||
            !normalizedMnc.all(Char::isDigit)
        ) {
            return ConfigResult(false, context.getString(R.string.msg_apn_no_mccmnc))
        }
        if (
            profile != null &&
            (
                !profile.isSafeImsTemplate ||
                    !ApnCatalogPolicy.matchesCurrentSim(
                        entry = profile,
                        mcc = normalizedMcc,
                        mnc = normalizedMnc,
                        carrierId = carrierId.takeIf { value -> value > 0 },
                    )
                )
        ) {
            return ConfigResult(false, context.getString(R.string.msg_apn_profile_mismatch))
        }

        return try {
            val template = ImsApnTemplatePolicy.resolve(profile)
            val values = ContentValues().apply {
                put("name", template.name)
                put("apn", template.apn)
                put("type", "ims")
                put("mcc", normalizedMcc)
                put("mnc", normalizedMnc)
                put("numeric", normalizedMcc + normalizedMnc)
                put("protocol", template.protocol)
                put("roaming_protocol", template.roamingProtocol)
                template.user.takeIf(String::isNotBlank)?.let { put("user", it) }
                template.password.takeIf(String::isNotBlank)?.let { put("password", it) }
                template.authType?.let { put("authtype", it) }
                put("carrier_enabled", 1)
                put("edited", 1)
                put("sub_id", subId)
            }
            val message = SystemApiBroker.ensureImsApn(context, values)
            ConfigResult(
                true,
                if (profile == null) {
                    message
                } else {
                    context.getString(
                        R.string.msg_apn_profile_applied,
                        profile.carrier.ifBlank { profile.apn },
                        profile.apn,
                        message,
                    )
                },
            )
        } catch (error: Throwable) {
            ConfigResult(
                false,
                context.getString(
                    R.string.msg_apn_failed,
                    OperationErrors.describe(error),
                ),
            )
        }
    }
}

internal data class ImsApnTemplate(
    val name: String,
    val apn: String,
    val protocol: String,
    val roamingProtocol: String,
    val user: String,
    val password: String,
    val authType: Int?,
)

internal object ImsApnTemplatePolicy {
    fun resolve(profile: ApnCatalogEntry?): ImsApnTemplate {
        val protocol = profile?.protocol?.takeIf(String::isNotBlank) ?: "IPV4V6"
        return ImsApnTemplate(
            name = profile?.carrier?.takeIf(String::isNotBlank) ?: "IMS",
            apn = profile?.apn?.takeIf(String::isNotBlank) ?: "ims",
            protocol = protocol,
            roamingProtocol =
                profile?.roamingProtocol?.takeIf(String::isNotBlank) ?: protocol,
            user = profile?.user.orEmpty(),
            password = profile?.password.orEmpty(),
            authType = profile?.authType,
        )
    }
}
