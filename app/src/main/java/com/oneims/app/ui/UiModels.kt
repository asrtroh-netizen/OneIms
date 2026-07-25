package com.oneims.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.oneims.app.R
import com.oneims.app.core.ConfigStore
import com.oneims.app.core.DiagnosticCheckItem
import com.oneims.app.core.FixOutcome
import com.oneims.app.core.PixelImsOptions
import com.oneims.app.core.SimCardInfo
import com.oneims.app.core.SimpleFiveGDisplayConfig
import com.oneims.app.core.UserFacingDiagnosticItem
import com.oneims.app.model.SimInfo
import com.oneims.app.model.UpdateInfo
import com.oneims.app.model.WfcMode

/**
 * 顶层目的地保持精简，避免把工具型应用做成层层嵌套的控制台。
 * 原「高级」目的地已拆解并重新分配：开关类选项并入 [CAPABILITIES]，
 * 重新应用/完整配置导出并入 [DIAGNOSTICS]，身份覆盖/离线 APN 库/专家编辑器
 * 合并进 [EXPERIMENTAL]；赞赏从设置内入口提升为独立 [SPONSOR] 底栏分页。
 */
enum class AppDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(R.string.tab_home, Icons.Filled.Home),
    CAPABILITIES(R.string.tab_power, Icons.Filled.Build),
    EXPERIMENTAL(R.string.tab_experimental, Icons.Filled.Star),
    DIAGNOSTICS(R.string.tab_tools, Icons.Filled.Search),
    SPONSOR(R.string.tab_sponsor, Icons.Filled.Favorite),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

/** 主题选择会持久化为稳定整数，避免枚举改名后破坏既有偏好。 */
enum class ThemeMode(val storedValue: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);

    companion object {
        fun fromStored(value: Int): ThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

data class HomeUiState(
    val shizukuRunning: Boolean,
    val shizukuGranted: Boolean,
    val oneKukuState: OneKukuCardState,
    /** 通道是否处于休眠（就绪路径下区分「就绪 / 休眠」展示）。 */
    val oneKukuChannelSleeping: Boolean = false,
    val deviceInfo: String,
    val sims: List<SimInfo>,
    val selectedSubId: Int,
    val selectedSim: SimInfo?,
    val actionsEnabled: Boolean,
    /** 按推荐一键开启：需通道已授权 + 已选卡。 */
    val recommendActionsEnabled: Boolean = false,
    val activeOperationLabel: String? = null,
    val reapplyStatus: ConfigStore.ReapplyStatus? = null,
    val bootAutoCheck: Boolean = true,
    val autoRestore: Boolean = true,
    val autoSleep: Boolean = false,
    /** Root 开机拉起特权桥（与无线自启并列；无无线也能拉）。 */
    val rootBootStart: Boolean = false,
    /** SDK 沙盒持久写旁路；默认关。 */
    val sandboxPersistBypass: Boolean = false,
    val oneKukuDetailOverride: String? = null,
)

data class HomeActions(
    val onSelectSim: (Int) -> Unit,
    val onActivateOneKuku: () -> Unit,
    /**
     * 设置「重新激活」：强制 pkill 后重建 onebridge_server（与划掉保活默认路径相对）。
     */
    val onForceReactivateOneKuku: () -> Unit = {},
    /**
     * 从未配对时：图四说明弹窗一出现就调用——立刻挂配对通知 + 切入「激活中」相位。
     * 已配对路径不会调用本回调（首页直接 [onActivateOneKuku]）。
     */
    val onBeginWirelessPairGuide: () -> Unit = {},
    /** 出门激活卡「启动通道」：始终走配对/安装流程，不与总控卡激活短路共用。 */
    val onStartCore: () -> Unit = {},
    val onApplyRecommended: () -> Unit = {},
    val onSaveCallConfig: () -> Unit = {},
    val onRestoreCallConfig: () -> Unit,
    /** 清空 CarrierConfig 覆盖，恢复运营商系统默认（应急回滚，非快照重写）。 */
    val onRestoreSystemDefaults: () -> Unit = {},
    val onCheckOneKukuStatus: () -> Unit,
    val onStatusCheck: () -> Unit,
    val onBootAutoCheckChange: (Boolean) -> Unit,
    val onAutoRestoreChange: (Boolean) -> Unit,
    val onAutoSleepChange: (Boolean) -> Unit,
    val onRootBootStartChange: (Boolean) -> Unit = {},
    val onSandboxPersistBypassChange: (Boolean) -> Unit = {},
    val onOpenWirelessDebugging: () -> Unit = {},
    val onOpenHotspot: () -> Unit = {},
    val onCopyAdbGuide: () -> Unit = {},
)

/**
 * 「能力」页现已并入原「高级」的运营商显示/漫游开关（[advancedOptions]）与原「排障」修复工具中的
 * 重启 IMS/网络修复/TikTok 覆盖三项；身份覆盖、离线 APN 库仍在实验功能页。
 * 运营商推荐一键开启在本页最上侧；其下为通话能力与 5G。
 */
data class CapabilitiesUiState(
    val sims: List<SimInfo>,
    val selectedSubId: Int,
    val selectedSim: SimInfo?,
    val recommendActionsEnabled: Boolean = false,
    val volte: Boolean,
    val vowifi: Boolean,
    val vonr: Boolean,
    val vilte: Boolean,
    val ut: Boolean,
    val crossSim: Boolean,
    val nr5g: Boolean,
    val signalStrengthAdjustmentEnabled: Boolean,
    val wfcMode: WfcMode,
    val voWifiNameFormatIndex: Int?,
    val voWifiCustomCarrierName: String,
    val voWifiNamePreview: String,
    val advancedOptions: PixelImsOptions,
    val prerequisitesMet: Boolean,
    val actionsEnabled: Boolean,
    val activeOperationLabel: String? = null,
)

data class CapabilitiesActions(
    val onSelectSim: (Int) -> Unit,
    val onApplyRecommended: () -> Unit,
    val onVolteChange: (Boolean) -> Unit,
    val onVowifiChange: (Boolean) -> Unit,
    val onVonrChange: (Boolean) -> Unit,
    val onVilteChange: (Boolean) -> Unit,
    val onUtChange: (Boolean) -> Unit,
    val onCrossSimChange: (Boolean) -> Unit,
    val onNr5gChange: (Boolean) -> Unit,
    val onSignalStrengthAdjustmentChange: (Boolean) -> Unit,
    val onWfcModeChange: (WfcMode) -> Unit,
    val onVoWifiNameFormatChange: (Int?) -> Unit,
    val onVoWifiCustomCarrierNameChange: (String) -> Unit,
    val onApplyCore: () -> Unit,
    val onApplyExtras: () -> Unit,
    val onApplyWfcMode: () -> Unit,
    val onApplyVoWifiName: () -> Unit,
    val onAdvancedOptionsChange: (PixelImsOptions) -> Unit,
    val onApplyAdvancedOptions: () -> Unit,
    val onRestartIms: () -> Unit,
    val onFixNetwork: () -> Unit,
)

/**
 * 「排障」页现已并入原「高级」的重新应用（[reapplyStatus]）与完整 CarrierConfig 导出、
 * 原「首页」的 Shizuku 起不来自救入口；页面顶部同样带 [sims]/[selectedSubId] 驱动的选卡开关，
 * 免得用户为了换卡而跳出这一页——所有卡相关操作（体检/ePDG/重启IMS/导出配置）都对齐同一张选中卡。
 */
data class DiagnosticsUiState(
    val sims: List<SimInfo>,
    val selectedSubId: Int,
    val log: String,
    val reapplyStatus: ConfigStore.ReapplyStatus?,
    val prerequisitesMet: Boolean,
    val actionsEnabled: Boolean,
)

data class DiagnosticsActions(
    val onSelectSim: (Int) -> Unit,
    /** 只读查询，返回详情正文；由排障页弹窗展示，不写入日志。 */
    val onHealthCheck: suspend () -> String,
    val onCheckEpdg: suspend () -> String,
    val onQueryIms: suspend () -> String,
    val onDumpConfig: suspend () -> String,
    val onCopyLog: () -> Unit,
    val onClearLog: () -> Unit,
    val onReapply: () -> Unit,
    val onExportFullConfig: () -> Unit,
    val onRunUserFacingCheck: suspend (Int) -> List<UserFacingDiagnosticItem>,
    val onRunDiagnosticsCheck: suspend (Int) -> List<DiagnosticCheckItem>,
    val onAutoFixDiagnosticsItem: suspend (Int, DiagnosticCheckItem) -> FixOutcome,
)

/**
 * 「实验功能」页：承接原「能力」的身份显示覆盖、原「设置」的掉线守护（[guardEnabled]）、
 * 原「排障」的离线 APN 库入口——三块内容逻辑均未改动，仅重新分配到这一个新页面；
 * 「修复工具」剩余的重启 IMS/网络修复/TikTok 覆盖已进一步并入「能力」页。
 * 「5G 显示增强」（[fiveGDisplayConfig]）保留原应用内展示，并在用户明确点击应用后
 * 尝试写 selectedSubId 的显示层 CarrierConfig；
 * 不影响 VoLTE/VoWiFi/VoNR。后续新增的实验性功能也会持续加入这里。
 * 页面顶部同样带 [sims]/[selectedSubId] 驱动的选卡开关，
 * 身份覆盖/专家编辑器都对齐同一张选中卡，不用跳到别的页面切卡。
 */
data class ExperimentalUiState(
    val sims: List<SimInfo>,
    val selectedSubId: Int,
    val carrierName: String,
    val currentCarrierName: String,
    val imsUserAgent: String,
    val simCountryIso: String,
    val activeSimCountryIso: String,
    val catalogEnabled: Boolean,
    val guardEnabled: Boolean,
    /** Root 持久化增强开关；默认关。 */
    val rootPersistEnhance: Boolean = false,
    /** Root 开机拉起 OneBridge；默认关。 */
    val rootBootStart: Boolean = false,
    /** 强制临时 CarrierConfig 写入；默认关。 */
    val forceTemporaryOverride: Boolean = false,
    /** 只读状态摘要（通道 + 上次 persistent）。 */
    val rootPersistStatusDetail: String = "",
    val fiveGDisplayConfig: SimpleFiveGDisplayConfig,
    val signalBarDisplayMode: ConfigStore.SignalBarDisplayMode,
    val nr5g: Boolean,
    val activeDataSims: List<SimCardInfo>,
    val prerequisitesMet: Boolean,
    val actionsEnabled: Boolean,
    val activeOperationLabel: String? = null,
)

data class ExperimentalActions(
    val onSelectSim: (Int) -> Unit,
    val onCarrierNameChange: (String) -> Unit,
    val onImsUserAgentChange: (String) -> Unit,
    val onApplyIdentity: () -> Unit,
    val onResetCarrierName: () -> Unit,
    val onSimCountryIsoChange: (String) -> Unit,
    val onApplySimCountryIso: () -> Unit,
    val onClearSimCountryIso: () -> Unit,
    val onApplyTiktokFix: () -> Unit,
    val onGuardEnabledChange: (Boolean) -> Unit,
    val onRootPersistEnhanceChange: (Boolean) -> Unit = {},
    val onRootBootStartChange: (Boolean) -> Unit = {},
    val onForceTemporaryOverrideChange: (Boolean) -> Unit = {},
    val onOpenApnCatalog: () -> Unit,
    val onApplyExpertValue: (String, String) -> Unit,
    val onFiveGDisplayConfigChange: (SimpleFiveGDisplayConfig) -> Unit,
    val onApplyFiveGDisplay: () -> Unit,
    val onSignalBarDisplayModeChange: (ConfigStore.SignalBarDisplayMode) -> Unit,
    val onApplySignalBarStyle: () -> Unit,
    val onRefreshDataSims: () -> Unit,
    val onSwitchDataSim: (Int) -> Unit,
    val onOpenTileSettings: () -> Unit,
)

/** 「设置」页现已不含掉线守护——该开关已迁至实验功能页。 */
data class SettingsUiState(
    val themeMode: ThemeMode,
    val dynamicColor: Boolean,
    val checkingUpdate: Boolean,
    val updateInfo: UpdateInfo?,
)

data class SettingsActions(
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onDynamicColorChange: (Boolean) -> Unit,
    val onCheckUpdate: () -> Unit,
    val onDownloadUpdate: (UpdateInfo) -> Unit,
    val onOpenMembership: () -> Unit = {},
)
