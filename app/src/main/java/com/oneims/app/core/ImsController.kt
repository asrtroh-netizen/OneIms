package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.PersistableBundle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.oneims.app.R
import com.oneims.app.model.ConfigResult
import com.oneims.app.model.ImsStatus
import com.oneims.app.model.SimInfo
import com.oneims.app.model.WfcMode

/**
 * IMS 业务控制层：把「全键开启 / WFC 模式 / IMS APN / 注册诊断」编排成上层可调用的操作。
 *
 * OneIms 铁律贯穿：
 *   - 核心能力只做加法，绝不改首选网络类型、绝不禁蜂窝；
 *   - APN 修复是唯一会调整既有行的路径，必须由用户确认并由代理读回/回滚；
 *   - CarrierConfig 经 [CarrierConfigOverrideWriter] 写当前 selectedSubId，优先 persistent=true；
 *   - [applyAll] 内置「写前 / 写后健康检查」，若基本通信（电话/数据/短信）被搞挂，
 *     立即自动 [SafetyGuard.restoreDefaults] 回滚，宁可不开 IMS 也要保命。
 */
object ImsController {
    private const val IMS_REGISTRATION_POLL_ATTEMPTS = 4
    private const val IMS_REGISTRATION_POLL_INTERVAL_MS = 2_000L

    @SuppressLint("MissingPermission")
    fun listSims(context: Context): List<SimInfo> {
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val list = try {
            sm.activeSubscriptionInfoList
        } catch (e: SecurityException) {
            null
        } ?: return emptyList()
        return list.map { info ->
            SimInfo(
                subscriptionId = info.subscriptionId,
                slotIndex = info.simSlotIndex,
                carrierId = info.carrierId,
                displayName = info.displayName?.toString() ?: "SIM",
                carrierName = info.carrierName?.toString() ?: "",
                mcc = runCatching { info.mccString ?: "" }.getOrDefault(""),
                mnc = runCatching { info.mncString ?: "" }.getOrDefault(""),
            )
        }
    }

    /**
     * 一键开启 VoLTE / VoWiFi / VoNR（双通道 + 安全护栏）。
     * @param wfcMode VoWiFi 呼叫模式，默认蜂窝优先以保基本通信。
     */
    fun applyAll(
        context: Context,
        subId: Int,
        enableVolte: Boolean,
        enableVowifi: Boolean,
        enableVonr: Boolean,
        wfcMode: WfcMode = WfcMode.CELLULAR_PREFERRED,
    ): ConfigResult {
        // 写前健康快照
        val before = SafetyGuard.healthCheck(context, subId)
        val detail = LinkedHashMap<String, Boolean>()
        return try {
            // 每个写操作内部各自处理「委托绕过 / Shizuku 直调」双策略与降级
            val bundle = PersistableBundle()
            if (enableVolte) {
                CarrierConfigKeys.volteBooleanTrueKeys.forEach { bundle.putBoolean(it, true) }
                bundle.putBoolean(CarrierConfigKeys.HIDE_ENHANCED_4G_LTE, false)
            }
            if (enableVowifi) {
                CarrierConfigKeys.vowifiBooleanTrueKeys.forEach { bundle.putBoolean(it, true) }
            }
            if (enableVonr) {
                CarrierConfigKeys.vonrBooleanTrueKeys.forEach { bundle.putBoolean(it, true) }
                // AOSP Settings VoNR 入口：visibility + 设备 5G + NR availabilities 非空，三者缺一不显示。
                // 开 VoNR 时一并写入 NSA+SA，避免「只开 VoNR、未开 5G NR」时系统只剩 VoLTE。
                bundle.putIntArray(
                    CarrierConfigKeys.NR_AVAILABILITIES_INT_ARRAY,
                    CarrierConfigKeys.NR_AVAILABILITIES_NSA_AND_SA,
                )
            }
            // OneKuku：persistent override + 同 subId 回读；单项失败记入 detail
            val write = CarrierConfigOverrideWriter.applyPersistentOverride(
                context = context,
                subId = subId,
                values = bundle,
                reason = "applyAll",
            )
            detail["carrier_config_override"] = write.success
            if (!write.success && write.detail.values.none { it }) {
                throw IllegalStateException(write.message)
            }
            write.detail.forEach { (key, ok) -> detail["cc:$key"] = ok }

            if (enableVolte) {
                detail["provision_volte"] = runCatching {
                    SystemApiBroker.setProvisioningInt(
                        subId, ProvisioningKeys.KEY_VOLTE_PROVISIONING_STATUS,
                        ProvisioningKeys.PROVISIONING_VALUE_ENABLED,
                    )
                }.isSuccess
                // 对齐南宫 3.1：VoIMS opt-in，强制设置页露出 VoLTE（不替代 carrier_config 覆盖）。
                detail["provision_voims_opt_in"] = runCatching {
                    SystemApiBroker.setProvisioningInt(
                        subId, ProvisioningKeys.KEY_VOIMS_OPT_IN_STATUS,
                        ProvisioningKeys.PROVISIONING_VALUE_ENABLED,
                    )
                }.isSuccess
            }
            if (enableVowifi) {
                detail["provision_vowifi"] = runCatching {
                    SystemApiBroker.setProvisioningInt(
                        subId, ProvisioningKeys.KEY_VOICE_OVER_WIFI_ENABLED,
                        ProvisioningKeys.PROVISIONING_VALUE_ENABLED,
                    )
                }.isSuccess
                detail["provision_vowifi_roaming"] = runCatching {
                    SystemApiBroker.setProvisioningInt(
                        subId, ProvisioningKeys.KEY_VOICE_OVER_WIFI_ROAMING,
                        ProvisioningKeys.PROVISIONING_VALUE_ENABLED,
                    )
                }.isSuccess
                // VoWiFi 模式默认蜂窝优先，保证没 WiFi 时电话短信仍走蜂窝
                detail["provision_wfc_mode"] = runCatching {
                    SystemApiBroker.setProvisioningInt(
                        subId, ProvisioningKeys.KEY_VOICE_OVER_WIFI_MODE, wfcMode.value,
                    )
                }.isSuccess
            }

            // 写后健康检查：若基本通信被搞挂，立即回滚保命
            val after = SafetyGuard.healthCheck(context, subId)
            if (before.allHealthy && !after.allHealthy) {
                SafetyGuard.restoreDefaults(context, subId)
                return ConfigResult(
                    false,
                    context.getString(R.string.msg_health_rollback),
                    detail,
                )
            }
            // 记录本次成功配置，供守护/开机自动重应用
            ConfigStore.saveApplied(
                context,
                ConfigStore.Applied(subId, enableVolte, enableVowifi, enableVonr, wfcMode),
            )
            val failedProvisioning = detail.filterValues { applied -> !applied }.keys
            ConfigResult(
                failedProvisioning.isEmpty(),
                if (failedProvisioning.isEmpty()) {
                    context.getString(R.string.msg_apply_ok)
                } else {
                    context.getString(
                        R.string.msg_apply_partial,
                        failedProvisioning.joinToString(),
                    )
                },
                detail,
            )
        } catch (e: Throwable) {
            val error = OperationErrors.describe(e)
            if (e is BrokerExecutionException && !e.operationStarted) {
                return ConfigResult(
                    false,
                    context.getString(R.string.msg_write_not_started, error),
                    detail,
                )
            }
            val rollback = SafetyGuard.restoreDefaults(context, subId)
            val message = if (rollback.success) {
                context.getString(R.string.msg_write_rollback, error)
            } else {
                context.getString(R.string.msg_write_rollback_failed, error, rollback.message)
            }
            ConfigResult(false, message, detail)
        }
    }

    /**
     * 重应用「上次成功配置」——供 IMS 掉线守护 / Shizuku 就绪 / 开机后调用。
     * 无历史配置时直接跳过；成功与否不打扰用户（静默自愈）。
     */
    fun reapplyLast(context: Context, targetSubId: Int? = null): ConfigResult {
        val a = ConfigStore.lastApplied(context)
            ?: return ConfigResult(false, context.getString(R.string.msg_no_history))
        val subId = targetSubId ?: a.subId
        if (targetSubId != null && targetSubId != a.subId) {
            // 禁止把卡 A 的成功配置静默写到卡 B；优先用卡 B 自己的功能页快照。
            val ui = ConfigStore.capabilityUiState(context, targetSubId)
                ?: return ConfigResult(
                    false,
                    context.getString(R.string.msg_reapply_wrong_sim, a.subId, targetSubId),
                )
            return applyAll(
                context,
                targetSubId,
                ui.volte,
                ui.vowifi,
                ui.vonr,
                ui.wfcMode,
            )
        }
        return applyAll(
            context,
            subId,
            a.volte,
            a.vowifi,
            a.vonr,
            a.wfcMode,
        )
    }

    /** 单独设置 WFC（VoWiFi）呼叫模式，写入后回读校验。 */
    fun setWfcMode(context: Context, subId: Int, mode: WfcMode): ConfigResult {
        return try {
            SystemApiBroker.setProvisioningInt(
                subId, ProvisioningKeys.KEY_VOICE_OVER_WIFI_MODE, mode.value,
            )
            val readback = SystemApiBroker.getVoWiFiModeSetting(subId)
            val ok = readback == mode.value
            ConfigResult(
                ok,
                if (ok) context.getString(R.string.msg_wfc_set, context.getString(mode.labelRes))
                else context.getString(R.string.msg_wfc_readback, readback),
            )
        } catch (e: Throwable) {
            ConfigResult(false, context.getString(R.string.msg_set_failed, OperationErrors.describe(e)))
        }
    }

    /**
     * 运营商能力增强（对齐 carrier-ims）：ViLTE（视频通话）/ UT（补充业务）/ Cross-SIM（双卡互通）。
     * 只做加法：写 CarrierConfig 布尔为 true，走双策略降级；不触碰网络类型。
     */
    fun applyCarrierExtras(
        context: Context, subId: Int,
        vilte: Boolean, ut: Boolean, crossSim: Boolean,
    ): ConfigResult {
        return try {
            val b = PersistableBundle().apply {
                if (vilte) putBoolean(CarrierConfigKeys.VT_AVAILABLE, true)
                if (ut) {
                    putBoolean(CarrierConfigKeys.CARRIER_SUPPORTS_SS_OVER_UT, true)
                    putBoolean(CarrierConfigKeys.CARRIER_UT_PROVISIONING_REQUIRED, false)
                }
                if (crossSim) {
                    putBoolean(CarrierConfigKeys.CROSS_SIM_IMS_AVAILABLE, true)
                    putBoolean(
                        CarrierConfigKeys.ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA,
                        true,
                    )
                }
            }
            if (b.keySet().isEmpty()) {
                return ConfigResult(false, context.getString(R.string.msg_none))
            }
            val write = CarrierConfigOverrideWriter.applyPersistentOverride(
                context = context,
                subId = subId,
                values = b,
                reason = "applyCarrierExtras",
            )
            if (!write.success) {
                return ConfigResult(false, write.message)
            }
            val caps = listOfNotNull(
                if (vilte) "ViLTE" else null, if (ut) "UT" else null, if (crossSim) "Cross-SIM" else null,
            ).joinToString("/").ifEmpty { context.getString(R.string.msg_none) }
            ConfigResult(true, "${write.targetLabel}\n${context.getString(R.string.msg_extras_on, caps)}")
        } catch (e: Throwable) {
            ConfigResult(false, context.getString(R.string.msg_write_failed, OperationErrors.describe(e)))
        }
    }

    /**
     * 5G 能力（对齐 carrier-ims）：只负责开放 NR NSA+SA（[1,2]）。
     * 信号等级阈值由 [SystemDisplayOverrideManager] 按 carrier-ims 单键 SSRSRP 写入；
     * 调用方应在 NR 开启时才下发阈值（与 ImsModifier.enable5GNR && enable5GThreshold 一致）。
     * 这里仍然不改「首选网络类型」，不会主动改变基本通信选网。
     */
    fun apply5g(context: Context, subId: Int, enableSaNsa: Boolean): ConfigResult {
        return try {
            val b = PersistableBundle().apply {
                if (enableSaNsa) {
                    putIntArray(
                        CarrierConfigKeys.NR_AVAILABILITIES_INT_ARRAY,
                        CarrierConfigKeys.NR_AVAILABILITIES_NSA_AND_SA,
                    )
                }
            }
            if (!enableSaNsa || b.keySet().isEmpty()) {
                return ConfigResult(true, context.getString(R.string.msg_5g, ""))
            }
            val write = CarrierConfigOverrideWriter.applyPersistentOverride(
                context = context,
                subId = subId,
                values = b,
                reason = "apply5g",
            )
            if (!write.success) {
                return ConfigResult(false, write.message)
            }
            val nsa = context.getString(R.string.msg_5g_nsa_sa)
            ConfigResult(true, "${write.targetLabel}\n${context.getString(R.string.msg_5g, nsa)}")
        } catch (e: Throwable) {
            ConfigResult(false, context.getString(R.string.msg_write_failed, OperationErrors.describe(e)))
        }
    }

    /**
     * SIM 国家码 ISO 覆盖。见 [SimCountryIsoManager]。
     * @deprecated 请改用 [SimCountryIsoManager.apply] / [SimCountryIsoManager.applyTikTokPreset]
     */
    fun applyTiktokFix(context: Context, subId: Int, iso: String = "us"): ConfigResult =
        if (iso.isBlank()) {
            SimCountryIsoManager.clear(context, subId)
        } else {
            SimCountryIsoManager.apply(context, subId, iso)
        }

    /**
     * 按订阅修复 IMS APN：拆分混合 type 并确保存在专用 type=ims 行。
     * 依赖委托窗口内的 WRITE_APN_SETTINGS；只在用户确认后执行，不参与后台守护。
     */
    fun createImsApn(
        context: Context,
        subId: Int,
        carrierId: Int,
        mcc: String,
        mnc: String,
        profile: ApnCatalogEntry? = null,
    ): ConfigResult = ImsApnRepairService.repair(
        context = context,
        subId = subId,
        carrierId = carrierId,
        mcc = mcc,
        mnc = mnc,
        profile = profile,
    )

    /**
     * 重建 IMS 注册链并轮询结果。请求被系统接受但暂未注册不等于失败：
     * 无信号、运营商未放通或刚修复 APN 时都可能需要更久，因此结果文案明确区分。
     */
    fun restartImsRegistration(
        context: Context,
        subId: Int,
        slotIndex: Int,
    ): ConfigResult {
        if (subId < 0 || slotIndex < 0) {
            return ConfigResult(false, context.getString(R.string.please_select_sim))
        }
        return try {
            SystemApiBroker.resetIms(slotIndex)
            var registrationReadbackAvailable = false
            repeat(IMS_REGISTRATION_POLL_ATTEMPTS) {
                Thread.sleep(IMS_REGISTRATION_POLL_INTERVAL_MS)
                val registration = SystemApiBroker.queryImsRegistration(subId)
                registrationReadbackAvailable =
                    registrationReadbackAvailable || registration.querySucceeded
                if (registration.registered) {
                    return ConfigResult(
                        true,
                        context.getString(
                            R.string.msg_ims_restart_registered,
                            context.getString(registration.techLabelRes()),
                        ),
                    )
                }
            }
            ConfigResult(
                true,
                context.getString(
                    if (registrationReadbackAvailable) {
                        R.string.msg_ims_restart_pending
                    } else {
                        R.string.msg_ims_restart_unverified
                    },
                ),
            )
        } catch (e: Throwable) {
            ConfigResult(
                false,
                context.getString(
                    R.string.msg_ims_restart_failed,
                    OperationErrors.describe(e),
                ),
            )
        }
    }

    /**
     * 网络修复（对齐 carrier-ims）：忽略强制门户检测，消除「已连接但受限/感叹号」。
     * 经 Instrumentation 权限代理写 Settings.Global（需 WRITE_SECURE_SETTINGS）。恢复传 restore=true。
     */
    fun fixCaptivePortal(context: Context, restore: Boolean = false): ConfigResult {
        return try {
            // 0=忽略强制门户检测（去感叹号）；1=正常提示（恢复默认）
            SystemApiBroker.writeGlobalInt(
                context,
                "captive_portal_mode",
                if (restore) 1 else 0,
            )
            ConfigResult(true, context.getString(if (restore) R.string.msg_captive_restore else R.string.msg_captive_fix))
        } catch (e: Throwable) {
            ConfigResult(false, context.getString(R.string.msg_net_fix_failed, OperationErrors.describe(e)))
        }
    }

    /** 诊断：尽力读取 IMS/VoLTE/VoWiFi 可用态 + 注册态（hidden 方法反射，读不到则标未知）。 */
    @SuppressLint("MissingPermission")
    fun queryImsStatus(context: Context, subId: Int): ImsStatus {
        val tm = context.getSystemService(TelephonyManager::class.java)
            ?.createForSubscriptionId(subId)
        var volte = false
        var vowifi = false
        val sb = StringBuilder()

        val unknown = context.getString(R.string.unknown)
        runCatching {
            volte = TelephonyManager::class.java.getMethod("isVolteAvailable").invoke(tm) as Boolean
            sb.append(context.getString(R.string.ims_volte_avail, volte.toString())).append('\n')
        }.onFailure { sb.append(context.getString(R.string.ims_volte_avail, unknown)).append('\n') }

        runCatching {
            vowifi = TelephonyManager::class.java.getMethod("isWifiCallingAvailable").invoke(tm) as Boolean
            sb.append(context.getString(R.string.ims_vowifi_avail, vowifi.toString())).append('\n')
        }.onFailure { sb.append(context.getString(R.string.ims_vowifi_avail, unknown)).append('\n') }

        // 注册态（对齐 carrier-ims）：可用 ≠ 已注册，这里补出「到底注册上没」这一排障刚需信息
        val reg = SystemApiBroker.queryImsRegistration(subId)
        val regText = if (reg.registered) {
            context.getString(R.string.ims_reg_yes, context.getString(reg.techLabelRes()))
        } else {
            context.getString(R.string.ims_reg_no)
        }
        sb.append(context.getString(R.string.ims_reg, regText)).append('\n')

        runCatching {
            val mode = SystemApiBroker.getVoWiFiModeSetting(subId)
            sb.append(context.getString(R.string.ims_wfc_mode, context.getString(WfcMode.of(mode).labelRes)))
        }.onFailure { sb.append(context.getString(R.string.ims_wfc_mode, unknown)) }

        return ImsStatus(volte, vowifi, sb.toString().trim())
    }

    /**
     * 配置全量查看（对齐 carrier-ims「配置全量查看」）：dump 当前订阅生效的 CarrierConfig。
     * 纯只读排障，仅重点透出与 IMS/VoLTE/VoWiFi/5G 相关的键，避免上千项刷屏；读不到则提示。
     */
    fun dumpCarrierConfig(context: Context, subId: Int): String {
        val bundle = SystemApiBroker.getCarrierConfig(context, subId)
            ?: return context.getString(R.string.config_dump_failed)
        // 只挑与本工具能力强相关的键，按人类可读顺序输出，命中才展示
        val focusKeys = listOf(
            CarrierConfigKeys.VOLTE_AVAILABLE, CarrierConfigKeys.ENHANCED_4G_LTE_ON_BY_DEFAULT,
            CarrierConfigKeys.WFC_IMS_AVAILABLE, CarrierConfigKeys.WFC_SUPPORTS_WIFI_ONLY,
            CarrierConfigKeys.VONR_ENABLED, CarrierConfigKeys.VONR_SETTING_VISIBILITY,
            CarrierConfigKeys.VT_AVAILABLE, CarrierConfigKeys.CROSS_SIM_IMS_AVAILABLE,
            CarrierConfigKeys.CARRIER_NAME_OVERRIDE, CarrierConfigKeys.CARRIER_NAME_STRING,
            CarrierConfigKeys.IMS_USER_AGENT_STRING, CarrierConfigKeys.SHOW_IMS_REGISTRATION_STATUS,
        )
        val empty = context.getString(R.string.config_dump_empty)
        val sb = StringBuilder(context.getString(R.string.config_dump_title, subId)).append('\n')
        var hit = 0
        focusKeys.forEach { key ->
            if (bundle.containsKey(key)) {
                hit++
                // 按 AOSP 键名后缀约定用类型化 getter 取值，规避已废弃的泛型 get()
                val value = when {
                    key.endsWith("_bool") -> bundle.getBoolean(key)
                    key.endsWith("_string") || key == CarrierConfigKeys.IMS_USER_AGENT_STRING ->
                        bundle.getString(key)?.ifEmpty { empty } ?: empty
                    else -> bundle.getInt(key)
                }
                sb.append("· ").append(key).append(" = ").append(value).append('\n')
            }
        }
        sb.append(context.getString(R.string.config_dump_footer, hit, bundle.keySet().size))
        return sb.toString().trim()
    }

    /**
     * 身份显示覆盖（对齐 carrier-ims/TurboIMS）：自定义「运营商名称显示」与「IMS SIP User Agent」。
     * 只写 CarrierConfig 显示层键、走双策略降级，不碰基带真实归属、不影响电话/数据/短信保命通信。
     * 任一参数传空串即「不改该项」；carrierName 传空且 clearName=true 时关闭名称覆盖恢复默认。
     */
    fun applyIdentityOverride(
        context: Context, subId: Int,
        carrierName: String, imsUserAgent: String,
    ): ConfigResult {
        val sim = listSims(context).firstOrNull { it.subscriptionId == subId }
        val normalizedName = IdentityInputPolicy.normalize(carrierName)
        val normalizedUserAgent = IdentityInputPolicy.normalize(imsUserAgent)
        IdentityInputPolicy.carrierNameError(normalizedName)?.let { error ->
            return ConfigResult(false, context.getString(error.identityErrorResource()))
        }
        IdentityInputPolicy.imsUserAgentError(normalizedUserAgent)?.let { error ->
            return ConfigResult(false, context.getString(error.identityErrorResource()))
        }
        return try {
            val b = PersistableBundle()
            val applied = mutableListOf<String>()
            if (normalizedName.isNotBlank()) {
                b.putBoolean(CarrierConfigKeys.CARRIER_NAME_OVERRIDE, true)
                b.putString(CarrierConfigKeys.CARRIER_NAME_STRING, normalizedName)
                applied += context.getString(R.string.identity_carrier_name, normalizedName)
            }
            if (normalizedUserAgent.isNotBlank()) {
                b.putString(CarrierConfigKeys.IMS_USER_AGENT_STRING, normalizedUserAgent)
                applied += context.getString(R.string.identity_ims_ua)
            }
            if (applied.isEmpty()) return ConfigResult(false, context.getString(R.string.identity_none))
            val write = CarrierConfigOverrideWriter.applyPersistentOverride(
                context = context,
                subId = subId,
                values = b,
                reason = "applyIdentityOverride",
            )
            if (!write.success) {
                return ConfigResult(false, write.message)
            }
            val readback = CarrierConfigOverrideWriter.readConfigForSubId(
                context,
                subId,
                b.keySet(),
            )
            if (readback == null) {
                return ConfigResult(
                    false,
                    context.getString(
                        R.string.identity_readback_failed,
                        (sim?.slotIndex ?: -1) + 1,
                        subId,
                    ),
                )
            }
            if (normalizedName.isNotBlank()) {
                val actualName = readback.getString(CarrierConfigKeys.CARRIER_NAME_STRING).orEmpty()
                val overrideOn = readback.getBoolean(CarrierConfigKeys.CARRIER_NAME_OVERRIDE)
                if (!overrideOn || actualName != normalizedName) {
                    return ConfigResult(
                        false,
                        context.getString(
                            R.string.identity_readback_mismatch,
                            (sim?.slotIndex ?: -1) + 1,
                            subId,
                        ),
                    )
                }
            }
            if (normalizedUserAgent.isNotBlank()) {
                val actualUa = readback.getString(CarrierConfigKeys.IMS_USER_AGENT_STRING).orEmpty()
                if (actualUa != normalizedUserAgent) {
                    return ConfigResult(
                        false,
                        context.getString(
                            R.string.identity_readback_mismatch,
                            (sim?.slotIndex ?: -1) + 1,
                            subId,
                        ),
                    )
                }
            }
            val targetLabel = write.targetLabel.ifBlank {
                sim?.let {
                    context.getString(
                        R.string.identity_target_applied,
                        it.slotIndex + 1,
                        formatCarrierShortName(it.carrierName),
                        applied.joinToString("、"),
                    )
                }
            } ?: context.getString(R.string.identity_applied, applied.joinToString("、"))
            ConfigResult(
                true,
                if (targetLabel.contains(applied.first())) {
                    targetLabel
                } else {
                    "$targetLabel · ${applied.joinToString("、")}"
                },
            )
        } catch (e: Throwable) {
            ConfigResult(false, context.getString(R.string.identity_failed, OperationErrors.describe(e)))
        }
    }

    /** 仅关闭运营商名称覆盖，不清除同一 bundle 中其它 IMS 配置。 */
    fun clearCarrierNameOverride(context: Context, subId: Int): ConfigResult {
        return try {
            val bundle = PersistableBundle().apply {
                putBoolean(CarrierConfigKeys.CARRIER_NAME_OVERRIDE, false)
                putString(CarrierConfigKeys.CARRIER_NAME_STRING, "")
            }
            val write = CarrierConfigOverrideWriter.applyPersistentOverride(
                context = context,
                subId = subId,
                values = bundle,
                reason = "clearCarrierNameOverride",
            )
            if (!write.success) {
                return ConfigResult(false, write.message)
            }
            ConfigResult(true, "${write.targetLabel}\n${context.getString(R.string.identity_name_restored)}")
        } catch (e: Throwable) {
            ConfigResult(
                false,
                context.getString(R.string.identity_failed, OperationErrors.describe(e)),
            )
        }
    }

    private fun IdentityInputPolicy.Error.identityErrorResource(): Int = when (this) {
        IdentityInputPolicy.Error.CONTROL_CHARACTER -> R.string.identity_invalid_control
        IdentityInputPolicy.Error.TOO_LONG -> R.string.identity_invalid_length
    }
}
