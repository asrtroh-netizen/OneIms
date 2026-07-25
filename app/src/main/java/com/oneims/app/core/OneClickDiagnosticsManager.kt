package com.oneims.app.core

import android.content.Context
import com.oneims.app.R
import com.oneims.app.model.ConfigResult
import com.oneims.app.core.OneKukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 单条体检结果的判定：PASS 正常、FAIL 需要处理、UNKNOWN 读不到（不等同于 FAIL，避免误报）。 */
enum class CheckStatus {
    PASS,
    FAIL,
    UNKNOWN,
}

/**
 * 一键体检/一键修复的单个检查项。[fixable] 为 true 时「一键修复」会尝试调用对应的既有能力
 * （见 [OneClickDiagnosticsManager.autoFix]）；为 false 时只展示 [guidance] 引导用户手动处理，
 * 不假装自动修复能覆盖所有情况。
 */
data class DiagnosticCheckItem(
    val id: String,
    val title: String,
    val detail: String,
    val status: CheckStatus,
    val fixable: Boolean,
    val guidance: String,
)

/**
 * 一键修复单个检查项的结果：[fixed] 为 false 时 [message] 必须是可直接展示的失败原因，
 * 不透出堆栈；本身就是 PASS 或标记为不可自动修复的检查项不会走到这里。
 */
data class FixOutcome(
    val itemId: String,
    val fixed: Boolean,
    val message: String,
)

/** 面向普通用户的体检结果项，不含 Shizuku 技术细节。 */
data class UserFacingDiagnosticItem(
    val id: String,
    val title: String,
    val detail: String,
    val status: CheckStatus,
    val needsPrivilegeHint: Boolean = false,
)

object OneClickDiagnosticsManager {

    private const val ID_SHIZUKU_RUNNING = "shizuku_running"
    private const val ID_SHIZUKU_GRANTED = "shizuku_granted"
    private const val ID_SIM_SELECTED = "sim_selected"
    private const val ID_HEALTH = "health"
    private const val ID_IMS_REGISTERED = "ims_registered"
    private const val ID_GUARD = "guard"
    private const val ID_ROOT_PERSIST = "root_persist"
    private const val ID_PLATFORM_PERSISTENT = "platform_persistent"

    /**
     * 逐项体检，全部只读，不改任何配置。subId 为 -1（未选卡）时后续依赖选卡的检查项
     * 直接判定为该项的"无法检查"，不勉强用无效 subId 硬查导致误报。
     * 内部含 Binder/反射调用，与项目里 [SafetyGuard.healthCheck] 同级别，调用方需自行放到
     * 后台线程（IO dispatcher）执行，不要直接在 Compose 主线程调用。
     */
    fun runCheck(context: Context, subId: Int): List<DiagnosticCheckItem> {
        val shizukuRunning = OneKukuManager.isRunning()
        val shizukuGranted = shizukuRunning && OneKukuManager.isGranted()

        val items = mutableListOf<DiagnosticCheckItem>()

        items += DiagnosticCheckItem(
            id = ID_SHIZUKU_RUNNING,
            title = context.getString(R.string.diagnostic_check_shizuku_running),
            detail = context.getString(
                if (shizukuRunning) {
                    R.string.diagnostic_check_shizuku_running_yes
                } else {
                    R.string.diagnostic_check_shizuku_running_no
                },
            ),
            status = if (shizukuRunning) CheckStatus.PASS else CheckStatus.FAIL,
            fixable = false,
            guidance = context.getString(R.string.diagnostic_check_shizuku_start_guidance),
        )

        items += DiagnosticCheckItem(
            id = ID_SHIZUKU_GRANTED,
            title = context.getString(R.string.diagnostic_check_shizuku_permission),
            detail = when {
                !shizukuRunning ->
                    context.getString(R.string.diagnostic_check_shizuku_permission_unavailable)
                shizukuGranted ->
                    context.getString(R.string.diagnostic_check_authorized)
                else -> context.getString(R.string.diagnostic_check_not_authorized)
            },
            status = when {
                !shizukuRunning -> CheckStatus.UNKNOWN
                shizukuGranted -> CheckStatus.PASS
                else -> CheckStatus.FAIL
            },
            fixable = shizukuRunning && !shizukuGranted,
            guidance = context.getString(R.string.diagnostic_check_permission_guidance),
        )

        items += DiagnosticCheckItem(
            id = ID_SIM_SELECTED,
            title = context.getString(R.string.diagnostic_check_sim_selected),
            detail = if (subId >= 0) {
                context.getString(R.string.diagnostic_check_selected_sub_id, subId)
            } else {
                context.getString(R.string.diagnostic_user_no_sim_selected)
            },
            status = if (subId >= 0) CheckStatus.PASS else CheckStatus.FAIL,
            fixable = false,
            guidance = context.getString(R.string.diagnostic_check_select_sim_guidance),
        )

        if (subId >= 0) {
            val health = runCatching { SafetyGuard.healthCheck(context, subId) }.getOrNull()
            val healthy = health?.let { it.simReady && it.voiceCapable && it.dataCapable } ?: false
            items += DiagnosticCheckItem(
                id = ID_HEALTH,
                title = context.getString(R.string.diagnostic_check_communication_health),
                detail = health?.detail
                    ?: context.getString(R.string.diagnostic_check_read_failed),
                status = when {
                    health == null -> CheckStatus.UNKNOWN
                    healthy -> CheckStatus.PASS
                    else -> CheckStatus.FAIL
                },
                fixable = health != null && !healthy && shizukuGranted,
                guidance = context.getString(R.string.diagnostic_check_health_guidance),
            )

            val imsReg = runCatching { SystemApiBroker.queryImsRegistration(subId) }.getOrNull()
            items += DiagnosticCheckItem(
                id = ID_IMS_REGISTERED,
                title = context.getString(R.string.diagnostic_check_ims_registration),
                detail = when {
                    imsReg == null || !imsReg.querySucceeded ->
                        context.getString(R.string.diagnostic_check_read_failed)
                    imsReg.registered ->
                        context.getString(R.string.diagnostic_user_registered)
                    else -> context.getString(R.string.diagnostic_user_not_registered)
                },
                status = when {
                    imsReg == null || !imsReg.querySucceeded -> CheckStatus.UNKNOWN
                    imsReg.registered -> CheckStatus.PASS
                    else -> CheckStatus.FAIL
                },
                fixable = imsReg != null && imsReg.querySucceeded && !imsReg.registered && shizukuGranted,
                guidance = context.getString(R.string.diagnostic_check_ims_guidance),
            )
        } else {
            items += DiagnosticCheckItem(
                id = ID_HEALTH,
                title = context.getString(R.string.diagnostic_check_communication_health),
                detail = context.getString(R.string.diagnostic_check_no_sim_cannot_check),
                status = CheckStatus.UNKNOWN,
                fixable = false,
                guidance = context.getString(R.string.diagnostic_check_select_sim_first),
            )
            items += DiagnosticCheckItem(
                id = ID_IMS_REGISTERED,
                title = context.getString(R.string.diagnostic_check_ims_registration),
                detail = context.getString(R.string.diagnostic_check_no_sim_cannot_check),
                status = CheckStatus.UNKNOWN,
                fixable = false,
                guidance = context.getString(R.string.diagnostic_check_select_sim_first),
            )
        }

        val guardEnabled = ConfigStore.isGuardEnabled(context)
        items += DiagnosticCheckItem(
            id = ID_GUARD,
            title = context.getString(R.string.diagnostic_check_guard),
            detail = context.getString(
                if (guardEnabled) {
                    R.string.diagnostic_check_enabled
                } else {
                    R.string.diagnostic_check_disabled
                },
            ),
            status = if (guardEnabled) CheckStatus.PASS else CheckStatus.UNKNOWN,
            fixable = false,
            guidance = context.getString(R.string.diagnostic_check_guard_guidance),
        )

        val rootStatus = RootPersistenceSupport.readStatus(context)
        items += DiagnosticCheckItem(
            id = ID_ROOT_PERSIST,
            title = context.getString(R.string.diagnostic_check_root_persist),
            detail = RootPersistenceSupport.statusDetail(context),
            status = when {
                rootStatus.rootChannel && rootStatus.lastPersistent == true -> CheckStatus.PASS
                rootStatus.rootChannel && rootStatus.lastPersistent == false -> CheckStatus.UNKNOWN
                rootStatus.rootChannel -> CheckStatus.UNKNOWN
                else -> CheckStatus.UNKNOWN
            },
            fixable = false,
            guidance = context.getString(R.string.diagnostic_check_root_persist_guidance),
        )

        val probe = PersistentCapabilityProbe.probe(context)
        val forceTemporary = ConfigStore.isForceTemporaryOverride(context)
        items += DiagnosticCheckItem(
            id = ID_PLATFORM_PERSISTENT,
            title = context.getString(R.string.diagnostic_check_platform_persistent),
            detail = platformPersistentDetail(context, probe, forceTemporary),
            status = when (probe.outcome) {
                PersistentCapabilityProbe.Outcome.LIKELY_ALLOWED -> CheckStatus.PASS
                PersistentCapabilityProbe.Outcome.LIKELY_BLOCKED -> CheckStatus.UNKNOWN
                PersistentCapabilityProbe.Outcome.UNKNOWN -> CheckStatus.UNKNOWN
            },
            fixable = false,
            guidance = context.getString(R.string.diagnostic_check_platform_persistent_guidance),
        )

        return items
    }

    private fun platformPersistentDetail(
        context: Context,
        probe: PersistentCapabilityProbe.Result,
        forceTemporary: Boolean,
    ): String {
        val outcomeText = when (probe.outcome) {
            PersistentCapabilityProbe.Outcome.LIKELY_ALLOWED ->
                context.getString(R.string.diagnostic_platform_persistent_allowed)
            PersistentCapabilityProbe.Outcome.LIKELY_BLOCKED ->
                context.getString(R.string.diagnostic_platform_persistent_blocked)
            PersistentCapabilityProbe.Outcome.UNKNOWN ->
                context.getString(R.string.diagnostic_platform_persistent_unknown)
        }
        val signals = probe.signals
        val signalText = if (signals == null) {
            probe.errorHint?.let {
                context.getString(R.string.diagnostic_platform_persistent_error, it)
            }.orEmpty()
        } else {
            context.getString(
                R.string.diagnostic_platform_persistent_signals,
                if (signals.hasIsSystemApp) "Y" else "N",
                if (signals.hasSecureOverrideConfig) "Y" else "N",
                if (signals.hasIsSdkSandboxUidInternal) "Y" else "N",
            )
        }
        val forceText = context.getString(
            if (forceTemporary) {
                R.string.diagnostic_force_temporary_on
            } else {
                R.string.diagnostic_force_temporary_off
            },
        )
        return listOf(outcomeText, signalText, forceText)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }

    /**
     * 面向用户的体检摘要：隐藏 Shizuku API/delegate 等技术项，只展示能理解的状态与建议。
     */
    fun runUserFacingCheck(context: Context, subId: Int): List<UserFacingDiagnosticItem> {
        val shizukuRunning = OneKukuManager.isRunning()
        val shizukuGranted = shizukuRunning && OneKukuManager.isGranted()
        val items = mutableListOf<UserFacingDiagnosticItem>()

        val simReady = if (subId >= 0) {
            runCatching { SafetyGuard.healthCheck(context, subId) }.getOrNull()
        } else {
            null
        }
        items += UserFacingDiagnosticItem(
            id = "sim_status",
            title = context.getString(R.string.diagnostic_user_sim_status),
            detail = when {
                subId < 0 -> context.getString(R.string.diagnostic_user_no_sim_selected)
                simReady == null -> context.getString(R.string.diagnostic_user_unavailable)
                simReady.simReady -> context.getString(R.string.diagnostic_user_normal)
                else -> context.getString(R.string.diagnostic_user_abnormal)
            },
            status = when {
                subId < 0 -> CheckStatus.FAIL
                simReady == null -> CheckStatus.UNKNOWN
                simReady.simReady -> CheckStatus.PASS
                else -> CheckStatus.FAIL
            },
        )

        val imsReg = if (subId >= 0) {
            runCatching { SystemApiBroker.queryImsRegistration(subId) }.getOrNull()
        } else {
            null
        }
        items += UserFacingDiagnosticItem(
            id = ID_IMS_REGISTERED,
            title = context.getString(R.string.diagnostic_user_ims_status),
            detail = when {
                subId < 0 -> context.getString(R.string.diagnostic_user_no_sim_selected)
                imsReg == null || !imsReg.querySucceeded ->
                    context.getString(R.string.diagnostic_user_unknown)
                imsReg.registered -> context.getString(R.string.diagnostic_user_registered)
                else -> context.getString(R.string.diagnostic_user_not_registered)
            },
            status = when {
                subId < 0 -> CheckStatus.UNKNOWN
                imsReg == null || !imsReg.querySucceeded -> CheckStatus.UNKNOWN
                imsReg.registered -> CheckStatus.PASS
                else -> CheckStatus.FAIL
            },
        )

        val config = if (subId >= 0) {
            runCatching { SystemApiBroker.getCarrierConfig(context, subId) }.getOrNull()
        } else {
            null
        }
        val volteAvailable = config?.getBoolean(CarrierConfigKeys.VOLTE_AVAILABLE) == true
        items += UserFacingDiagnosticItem(
            id = "volte",
            title = "VoLTE",
            detail = when {
                subId < 0 -> context.getString(R.string.diagnostic_user_unknown)
                config == null -> context.getString(R.string.diagnostic_user_unknown)
                volteAvailable -> context.getString(R.string.diagnostic_user_available)
                else -> context.getString(R.string.diagnostic_user_unavailable_state)
            },
            status = if (volteAvailable) CheckStatus.PASS else CheckStatus.UNKNOWN,
        )
        val vowifiAvailable = config?.getBoolean(CarrierConfigKeys.WFC_IMS_AVAILABLE) == true
        items += UserFacingDiagnosticItem(
            id = "vowifi",
            title = "VoWiFi",
            detail = when {
                subId < 0 -> context.getString(R.string.diagnostic_user_unknown)
                config == null -> context.getString(R.string.diagnostic_user_unknown)
                vowifiAvailable -> context.getString(R.string.diagnostic_user_available)
                else -> context.getString(R.string.diagnostic_user_unavailable_state)
            },
            status = if (vowifiAvailable) CheckStatus.PASS else CheckStatus.UNKNOWN,
        )

        val networkLabel = if (subId >= 0) {
            runCatching { SafetyGuard.currentNetworkTypeLabel(context, subId) }.getOrDefault("")
        } else {
            ""
        }
        items += UserFacingDiagnosticItem(
            id = "network",
            title = context.getString(R.string.diagnostic_user_current_network),
            detail = networkLabel.ifBlank {
                context.getString(R.string.diagnostic_user_unknown)
            },
            status = if (networkLabel.isNotBlank()) CheckStatus.PASS else CheckStatus.UNKNOWN,
        )

        val defaultSubId = runCatching { SystemApiBroker.getDefaultDataSubId() }.getOrDefault(-1)
        val dataSim = ImsController.listSims(context).firstOrNull { it.subscriptionId == defaultSubId }
        items += UserFacingDiagnosticItem(
            id = "data_sim",
            title = context.getString(R.string.diagnostic_user_data_sim),
            detail = dataSim?.let {
                context.getString(R.string.diagnostic_user_sim_slot, it.slotIndex + 1)
            } ?: context.getString(R.string.diagnostic_user_unknown),
            status = if (dataSim != null) CheckStatus.PASS else CheckStatus.UNKNOWN,
        )

        val internal = runCheck(context, subId)
        val needsFix = internal.any { it.status == CheckStatus.FAIL && it.id != ID_GUARD }
        val privilegeMissing = !shizukuGranted && internal.any {
            it.status == CheckStatus.FAIL && (it.id == ID_SHIZUKU_GRANTED || it.fixable)
        }
        items += UserFacingDiagnosticItem(
            id = "fix_suggestion",
            title = context.getString(R.string.diagnostic_user_fix_suggestion),
            detail = when {
                !needsFix -> context.getString(R.string.diagnostic_user_no_fix_needed)
                privilegeMissing ->
                    context.getString(R.string.diagnostic_user_privilege_required)
                else -> context.getString(R.string.diagnostic_user_fix_needed)
            },
            status = if (needsFix) CheckStatus.FAIL else CheckStatus.PASS,
            needsPrivilegeHint = privilegeMissing,
        )
        return items
    }

    /**
     * 只对 [item.fixable] 为 true 的项执行；调用方应先确认这一点，避免对不可修复项做无意义调用。
     * 每一项都直接转调既有能力，不新增写入逻辑：授权走 [OneKukuManager.requestPermission]，
     * 健康异常走 [SafetyGuard.restoreDefaults]，IMS 未注册走 [ImsController.restartImsRegistration]。
     */
    suspend fun autoFix(
        context: Context,
        subId: Int,
        item: DiagnosticCheckItem,
    ): FixOutcome = withContext(Dispatchers.IO) {
        if (!item.fixable) {
            return@withContext FixOutcome(item.id, fixed = false, message = item.guidance)
        }
        when (item.id) {
            ID_SHIZUKU_GRANTED -> {
                OneKukuManager.requestActivation()
                // 授权走系统弹窗异步回调，这里只能确认"已发起请求"，不能同步判定用户是否同意。
                FixOutcome(
                    item.id,
                    fixed = false,
                    message = context.getString(R.string.diagnostic_fix_permission_requested),
                )
            }
            ID_HEALTH -> {
                val result = runCatching { SafetyGuard.restoreDefaults(context, subId) }
                    .getOrElse { ConfigResult(false, OperationErrors.describe(it)) }
                FixOutcome(item.id, fixed = result.success, message = result.message)
            }
            ID_IMS_REGISTERED -> {
                val sim = ImsController.listSims(context).firstOrNull { it.subscriptionId == subId }
                if (sim == null) {
                    FixOutcome(
                        item.id,
                        fixed = false,
                        message = context.getString(R.string.diagnostic_fix_sim_not_found),
                    )
                } else {
                    val result = runCatching {
                        ImsController.restartImsRegistration(context, subId, sim.slotIndex)
                    }.getOrElse { ConfigResult(false, OperationErrors.describe(it)) }
                    FixOutcome(item.id, fixed = result.success, message = result.message)
                }
            }
            else -> FixOutcome(item.id, fixed = false, message = item.guidance)
        }
    }
}
