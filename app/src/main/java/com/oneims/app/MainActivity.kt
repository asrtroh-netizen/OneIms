package com.oneims.app

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.oneims.app.core.CarrierProfiles
import com.oneims.app.core.ApnCatalogEntry
import com.oneims.app.core.ConfigStore
import com.oneims.app.core.DodoPaySupportClient
import com.oneims.app.core.DeviceInfo
import com.oneims.app.core.DiagFileLogger
import com.oneims.app.core.EpdgChecker
import com.oneims.app.core.GuardService
import com.oneims.app.core.ImsController
import com.oneims.app.core.OperationErrors
import com.oneims.app.core.OperationFeedbackKind
import com.oneims.app.core.OperationFeedbackPolicy
import com.oneims.app.core.PixelImsCompat
import com.oneims.app.core.PixelImsOptions
import com.oneims.app.core.ReapplyManager
import com.oneims.app.core.ReapplyTrigger
import com.oneims.app.core.RootPersistenceSupport
import com.oneims.app.core.SandboxPersistSupport
import com.oneims.app.core.SafetyGuard
import com.oneims.app.core.SystemUpdateShield
import com.oneims.app.core.OneClickDiagnosticsManager
import com.oneims.app.core.ShizukuSetupHelper
import com.oneims.app.core.OneKukuCoreComponent
import com.oneims.app.core.OneKukuEmbeddedAdbActivator
import com.oneims.app.core.OneKukuHostServerBootstrap
import com.oneims.app.core.OneKukuMiniAdbClient
import com.oneims.app.core.OneKukuPairingNotification
import com.oneims.app.core.OneKukuActivationUi
import com.oneims.app.core.OneKukuActivationPhase
import com.oneims.app.core.ChannelLine
import com.oneims.app.core.OneKukuManager
import com.oneims.app.core.privilege.ChannelEngine
import com.oneims.app.core.WirelessPairingCodeReceiver
import com.oneims.app.core.SystemDisplayOverrideManager
import com.oneims.app.core.SimCountryIsoManager
import com.oneims.app.core.UpdateChecker
import com.oneims.app.core.VoWifiNameFormatManager
import com.oneims.app.model.EpdgResult
import com.oneims.app.model.SimInfo
import com.oneims.app.model.UpdateInfo
import com.oneims.app.model.WfcMode
import com.oneims.app.ui.AppDestination
import com.oneims.app.ui.ApnCatalogDialog
import com.oneims.app.ui.CapabilitiesActions
import com.oneims.app.ui.CapabilitiesScreen
import com.oneims.app.ui.CapabilitiesUiState
import com.oneims.app.ui.DiagnosticsActions
import com.oneims.app.ui.DiagnosticsScreen
import com.oneims.app.ui.DiagnosticsUiState
import com.oneims.app.ui.ExperimentalActions
import com.oneims.app.ui.ExperimentalScreen
import com.oneims.app.ui.ExperimentalUiState
import com.oneims.app.ui.HomeActions
import com.oneims.app.ui.HomeScreen
import com.oneims.app.ui.HomeUiState
import com.oneims.app.ui.OneImsScaffold
import com.oneims.app.ui.OneImsPrimaryButton
import com.oneims.app.core.OneKukuPrivilegeBridgeImpl
import com.oneims.app.core.OneKukuSnapshotFactory
import com.oneims.app.onekuku.OneKukuBootRestoreCoordinator
import com.oneims.app.onekuku.OneKukuBootRestoreStore
import com.oneims.app.onekuku.OneKukuBootUiHint
import com.oneims.app.onekuku.OneKukuCallRestoreExecutor
import com.oneims.app.onekuku.OneKukuCommand
import com.oneims.app.onekuku.OneKukuCommandDispatcher
import com.oneims.app.onekuku.OneKukuHiddenRunner
import com.oneims.app.onekuku.OneKukuRunnerState
import com.oneims.app.onekuku.OneKukuSnapshotStore
import com.oneims.app.onekuku.OneKukuSleepController
import com.oneims.app.core.OneKukuBootRestoreService
import com.oneims.app.core.OneKukuResidentService
import com.oneims.app.ui.OneKukuCardPolicy
import com.oneims.app.ui.OneKukuCardState
import com.oneims.app.ui.OneKukuHomeTools
import com.oneims.app.ui.SettingsActions
import com.oneims.app.ui.MembershipPaywallScreen
import com.oneims.app.ui.SettingsScreen
import com.oneims.app.ui.SettingsUiState
import com.oneims.app.ui.SponsorScreen
import com.oneims.app.ui.ThemeMode
import com.oneims.app.ui.theme.OneImsTheme
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import com.oneims.app.core.privilege.PrivilegeBridge
import com.oneims.app.core.privilege.PrivilegeBridges

private const val MAX_DIAGNOSTIC_LOG_CHARS = 256 * 1024
private const val MAX_DIAGNOSTIC_ENTRY_CHARS = 192 * 1024

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagFileLogger.breadcrumb("MainActivity.onCreate")
        // 启动页使用正式品牌红色，首帧先用浅色系统栏图标保证对比度。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            val context = LocalContext.current
            val systemDark = isSystemInDarkTheme()
            var themeModeValue by remember {
                mutableIntStateOf(ConfigStore.themeMode(context))
            }
            var dynamicColor by remember {
                mutableStateOf(ConfigStore.isDynamicColorEnabled(context))
            }
            val themeMode = ThemeMode.fromStored(themeModeValue)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                val systemBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle,
                    navigationBarStyle = systemBarStyle,
                )
            }

            // 独立版首页/主色对齐邻仓新作 Shizuku：固定 Google Blue，不用壁纸动态取色。
            OneImsTheme(
                darkTheme = darkTheme,
                // 与 Lite 同一套主题路径（含动态取色），避免独立版强制关动态色导致配色漂移。
                dynamicColor = dynamicColor,
            ) {
                AppRoot(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    onThemeModeChange = { mode ->
                        themeModeValue = mode.storedValue
                        ConfigStore.setThemeMode(context, mode.storedValue)
                    },
                    onDynamicColorChange = { enabled ->
                        dynamicColor = enabled
                        ConfigStore.setDynamicColorEnabled(context, enabled)
                    },
                )
            }
        }
    }
}

private data class ConfirmationRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)

@Composable
private fun AppRoot(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    // 国产 OEM 上 binder/反射偶发异常若漏出协程会直接闪退；Supervisor + 落盘后继续可用。
    val uiExceptionHandler = remember {
        CoroutineExceptionHandler { _, error ->
            DiagFileLogger.e("UI", "uncaught coroutine: ${error.message}", error)
            // 不在此处 showSnackbar：handler 线程不确定，避免二次崩。
        }
    }
    val baseScope = rememberCoroutineScope()
    val scope = remember(baseScope, uiExceptionHandler) {
        baseScope + SupervisorJob() + uiExceptionHandler
    }
    // V15 UserPresent 风格：前台/binder 死后 0/5/15s 错峰复连（取消旧任务防叠刷）。
    val privilegeReconnectJobHolder = remember { arrayOf<Job?>(null) }

    var destination by remember { mutableStateOf(AppDestination.HOME) }
    var membershipPaywallVisible by remember { mutableStateOf(false) }
    var pendingSupportProof by remember { mutableStateOf<String?>(null) }
    fun consumeSupportIntent(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        val proof = DodoPaySupportClient.extractDodopayPaymentProof(data) ?: return
        pendingSupportProof = proof
        destination = AppDestination.SPONSOR
    }

    LaunchedEffect(Unit) {
        val activity = context as? ComponentActivity ?: return@LaunchedEffect
        consumeSupportIntent(activity.intent)
    }

    LaunchedEffect(Unit) {
        OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)
    }

    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity
        if (activity == null) {
            return@DisposableEffect onDispose { }
        }
        val listener = Consumer<Intent> { intent ->
            activity.setIntent(intent)
            consumeSupportIntent(intent)
        }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }

    var shizukuRunning by remember { mutableStateOf(OneKukuManager.isRunning()) }
    var shizukuGranted by remember { mutableStateOf(OneKukuManager.isGranted()) }
    var oneKukuTaskComplete by remember { mutableStateOf(false) }
    var oneKukuRestoring by remember { mutableStateOf(false) }
    var oneKukuBootAutoCheck by remember {
        mutableStateOf(ConfigStore.isOneKukuBootAutoCheck(context))
    }
    var oneKukuAutoRestore by remember {
        mutableStateOf(ConfigStore.isOneKukuAutoRestore(context))
    }
    var oneKukuAutoSleep by remember {
        mutableStateOf(ConfigStore.isOneKukuAutoSleep(context))
    }
    var sims by remember { mutableStateOf(emptyList<SimInfo>()) }
    var selectedSubId by remember { mutableIntStateOf(ConfigStore.getSelectedSubId(context)) }
    var deviceInfo by remember { mutableStateOf("") }
    var log by remember { mutableStateOf(context.getString(R.string.log_ready)) }
    var busyLabel by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<ConfirmationRequest?>(null) }
    var apnCatalogVisible by remember { mutableStateOf(false) }
    var adbPairDialogVisible by remember { mutableStateOf(false) }
    var adbPairCode by remember { mutableStateOf("") }
    var adbPairBusy by remember { mutableStateOf(false) }
    var coreMissingDialogVisible by remember { mutableStateOf(false) }
    /** 系统安装器返回后，ON_RESUME 自动续跑配对，避免「已装仍弹还没装」死循环。 */
    var awaitingCoreInstall by remember { mutableStateOf(false) }

    var volte by remember { mutableStateOf(true) }
    var vowifi by remember { mutableStateOf(true) }
    var vonr by remember { mutableStateOf(false) }
    var vilte by remember { mutableStateOf(false) }
    var ut by remember { mutableStateOf(false) }
    var crossSim by remember { mutableStateOf(false) }
    var nr5g by remember { mutableStateOf(false) }
    var wfcMode by remember { mutableStateOf(WfcMode.CELLULAR_PREFERRED) }
    var carrierName by remember { mutableStateOf("") }
    var imsUserAgent by remember { mutableStateOf("") }
    var simCountryIso by remember { mutableStateOf("") }
    var activeSimCountryIso by remember { mutableStateOf("") }
    var advancedOptions by remember { mutableStateOf(PixelImsOptions()) }
    var reapplyStatus by remember {
        mutableStateOf(ConfigStore.lastReapplyStatus(context))
    }

    var guardEnabled by remember {
        mutableStateOf(ConfigStore.isGuardEnabled(context))
    }
    var rootPersistEnhance by remember {
        mutableStateOf(ConfigStore.isRootPersistEnhance(context))
    }
    var rootBootStart by remember {
        mutableStateOf(ConfigStore.isRootBootStart(context))
    }
    var forceTemporaryOverride by remember {
        mutableStateOf(ConfigStore.isForceTemporaryOverride(context))
    }
    var systemUpdateShield by remember {
        mutableStateOf(ConfigStore.isSystemUpdateShield(context))
    }
    var sandboxPersistBypass by remember {
        mutableStateOf(ConfigStore.isSandboxPersistBypass(context))
    }
    var signalStrengthAdjustmentEnabled by remember {
        mutableStateOf(ConfigStore.signalStrengthAdjustmentEnabled(context, selectedSubId))
    }
    var voWifiNameFormatIndex by remember { mutableStateOf<Int?>(null) }
    var voWifiCustomCarrierName by remember { mutableStateOf("") }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var phonePermissionResultCount by remember { mutableIntStateOf(0) }

    val selectedSim = sims.firstOrNull { it.subscriptionId == selectedSubId }
    val actionsAvailable = busyLabel == null
    var bootUiHint by remember {
        mutableStateOf(OneKukuBootRestoreStore.readHint(context))
    }
    // 通知栏填码在 Receiver 里改相位；必须用 StateFlow 收集，不能只靠 activationEpoch。
    val activationPhase by OneKukuActivationUi.phaseState.collectAsState()
    var activationEpoch by remember { mutableIntStateOf(0) }
    /** 本轮引导是否已打开过无线调试，避免弹窗确认与激活重复抢会话。 */
    var pairingUiPrimed by remember { mutableStateOf(false) }
    val bootForceInactive = bootUiHint == OneKukuBootUiHint.NEEDS_ACTIVATION
    // activationEpoch：生命周期休眠/唤醒后强制重组，才能读到 HiddenRunner 最新态。
    @Suppress("UNUSED_VARIABLE")
    val runnerEpoch = activationEpoch
    val bridgeReady = shizukuRunning && shizukuGranted && !bootForceInactive
    // 桥已就绪时禁止 CONNECTING/STARTING 相位盖住 Hero（否则日志 ACTIVE、UI「激活中」横跳）。
    val oneKukuState = if (bridgeReady) {
        OneKukuCardPolicy.resolve(
            serviceReady = true,
            isExecuting = false,
            channelSleeping = OneKukuCardPolicy.isChannelSleeping(
                OneKukuHiddenRunner.currentState(),
            ),
            taskComplete = oneKukuTaskComplete || bootUiHint == OneKukuBootUiHint.RESTORE_COMPLETE,
        )
    } else {
        OneKukuCardPolicy.fromActivationPhase(activationPhase)
            ?: OneKukuCardPolicy.resolve(
                serviceReady = false,
                isExecuting = oneKukuRestoring ||
                    bootUiHint == OneKukuBootUiHint.RESTORING ||
                    bootUiHint == OneKukuBootUiHint.WAITING_WIFI ||
                    OneKukuHiddenRunner.currentState() == OneKukuRunnerState.EXECUTING ||
                    OneKukuHiddenRunner.currentState() == OneKukuRunnerState.STARTING,
                channelSleeping = OneKukuCardPolicy.isChannelSleeping(
                    OneKukuHiddenRunner.currentState(),
                ),
                taskComplete = oneKukuTaskComplete || bootUiHint == OneKukuBootUiHint.RESTORE_COMPLETE,
            )
    }
    // 三态后卡片不再有 SLEEPING；内部 runner 待命仍可探测，供调试/兼容字段。
    val oneKukuChannelSleeping = OneKukuCardPolicy.isChannelSleeping(
        OneKukuHiddenRunner.currentState(),
    )
    val oneKukuDetailOverride = when {
        // 桥已就绪：忽略残留 CONNECTING 文案（划掉再开复连常见）。
        bridgeReady -> null
        // OneLink：不展示内嵌 ADB 配对相位文案（即便相位短暂残留）。
        ChannelLine.usesShizuku &&
            (
                activationPhase == OneKukuActivationPhase.WAITING_PAIR ||
                    activationPhase == OneKukuActivationPhase.PAIRING ||
                    activationPhase == OneKukuActivationPhase.CONNECTING
                ) ->
            context.getString(R.string.onekuku_msg_need_prepare)
        activationPhase == OneKukuActivationPhase.WAITING_PAIR ->
            context.getString(R.string.onekuku_msg_waiting_pair_detail)
        activationPhase == OneKukuActivationPhase.PAIRING ->
            context.getString(R.string.onekuku_msg_phase_pairing)
        activationPhase == OneKukuActivationPhase.CONNECTING ->
            context.getString(R.string.onekuku_msg_phase_connecting)
        activationPhase == OneKukuActivationPhase.STARTING ->
            context.getString(R.string.onekuku_msg_phase_starting)
        activationPhase == OneKukuActivationPhase.FAILED ->
            OneKukuActivationUi.lastFailureReason?.let {
                context.getString(R.string.onekuku_pair_text_fail, it)
            }
        bootUiHint == OneKukuBootUiHint.WAITING_WIFI ->
            context.getString(R.string.onekuku_detail_waiting_wifi)
        bootUiHint == OneKukuBootUiHint.NO_SNAPSHOT_SLEEPING ||
            OneKukuBootRestoreStore.shouldShowNoSnapshotNote(context) -> {
            // 快照已在则不再覆盖显示「暂无」（陈旧 no_snapshot 标记会被保存成功路径清掉）。
            val hasSnapshot = selectedSubId >= 0 &&
                OneKukuSnapshotStore.load(context, selectedSubId) != null
            if (hasSnapshot) {
                null
            } else {
                context.getString(R.string.onekuku_detail_no_snapshot)
            }
        }
        else -> null
    }

    /** 从特权桥重读 running/granted，避免 binder 瞬断后 UI 假掉。 */
    fun syncPrivilegeFlagsFromBridge() {
        shizukuRunning = runCatching { OneKukuManager.isRunning() }.getOrDefault(false)
        shizukuGranted = runCatching { OneKukuManager.isGranted() }.getOrDefault(false)
    }

    /**
     * 通道就绪后的收尾（对齐 Shizuku）：
     * 特权活在桥接进程，不靠 App 前台常驻。
     * 前台收尾一律标「就绪」；关 App / 退后台由生命周期切入「休眠」。
     * 须定义在 [syncPrivilegeFlagsFromBridge] 之后（本地函数不可前向引用）。
     */
    fun settleOneKukuChannelAfterReady() {
        // 硬门禁：仅 binder+授权双真才收尾，避免「假就绪」后误标 Active。
        if (!OneKukuManager.isReady()) return
        if (ChannelLine.usesEmbeddedBridge) {
            OneKukuResidentService.stop(context)
        }
        OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)
        OneKukuHiddenRunner.markActive()
        // 划掉后台再开：桥已就绪时必须清掉 NEEDS_ACTIVATION，否则
        // serviceReady=…&&!bootForceInactive 会把卡片钉死在「未激活」（一加/小米常见）。
        if (bootUiHint == OneKukuBootUiHint.NEEDS_ACTIVATION ||
            bootUiHint == OneKukuBootUiHint.WAITING_WIFI
        ) {
            val hint = if (OneKukuBootRestoreStore.shouldShowNoSnapshotNote(context)) {
                OneKukuBootUiHint.NO_SNAPSHOT_SLEEPING
            } else {
                OneKukuBootUiHint.READY_SLEEPING
            }
            OneKukuBootRestoreStore.writeHint(context, hint)
            bootUiHint = hint
        }
        // 清掉 CONNECTING/STARTING，否则 fromActivationPhase 会盖住 READY（ACTIVE+激活中横跳）。
        OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
        syncPrivilegeFlagsFromBridge()
        activationEpoch++
    }

    /** App 退后台 / 关闭：已授权则进入休眠（不拆桥、不要求重配对）。 */
    fun sleepChannelWhenBackgrounded() {
        if (!OneKukuManager.isReady()) return
        if (OneKukuCardPolicy.isBusy(oneKukuState)) return
        OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)
        OneKukuSleepController.sleep(context)
        activationEpoch++
    }

    // 已有快照却仍挂着「暂无」提示：异步清掉，避免下轮又被读回。
    LaunchedEffect(selectedSubId, bootUiHint) {
        if (selectedSubId < 0) return@LaunchedEffect
        val hasSnapshot = OneKukuSnapshotStore.load(context, selectedSubId) != null
        if (!hasSnapshot) return@LaunchedEffect
        if (OneKukuBootRestoreStore.shouldShowNoSnapshotNote(context)) {
            OneKukuBootRestoreStore.setNoSnapshotNote(context, false)
        }
        if (bootUiHint == OneKukuBootUiHint.NO_SNAPSHOT_SLEEPING) {
            OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.READY_SLEEPING)
            bootUiHint = OneKukuBootUiHint.READY_SLEEPING
        }
    }

    LaunchedEffect(Unit) {
        // 进首页立刻对齐：后台已就绪则直接休眠态；已配对未就绪则立刻踢开机编排（0 防抖）。
        if (OneKukuManager.isReady()) {
            shizukuRunning = true
            shizukuGranted = OneKukuManager.isGranted()
            if (bootUiHint == OneKukuBootUiHint.NEEDS_ACTIVATION ||
                bootUiHint == OneKukuBootUiHint.WAITING_WIFI
            ) {
                val hint = if (OneKukuBootRestoreStore.shouldShowNoSnapshotNote(context)) {
                    OneKukuBootUiHint.NO_SNAPSHOT_SLEEPING
                } else {
                    OneKukuBootUiHint.READY_SLEEPING
                }
                OneKukuBootRestoreStore.writeHint(context, hint)
                bootUiHint = hint
            }
            OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
            settleOneKukuChannelAfterReady()
        } else if (ChannelLine.usesShizuku) {
            // OneLink：前台只靠 Shizuku binder 轮询复连，禁止 enqueue BootRestore FGS
            // （小米/一加划掉后台再开会 ForegroundServiceDidNotStartInTime → 进程被杀 → 假未激活）。
        } else if (ConfigStore.isOneKukuBootAutoCheck(context) &&
            OneKukuEmbeddedAdbActivator.hasPairedOnce(context)
        ) {
            // 已配对未就绪：前台由下方 prepareOneKukuCore 单路径自动连。
            // 勿再 enqueue 开机编排——双跑会抢 activate 锁，打开体感「卡老半天」。
        } else if (ConfigStore.isOneKukuBootAutoCheck(context)) {
            OneKukuBootRestoreService.enqueue(context, debounceMs = 2_000L)
        }
        val activity = context as? ComponentActivity
        if (activity?.intent?.getBooleanExtra(
                OneKukuBootRestoreCoordinator.EXTRA_OPEN_RESTORE,
                false,
            ) == true
        ) {
            OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.NEEDS_ACTIVATION)
            bootUiHint = OneKukuBootUiHint.NEEDS_ACTIVATION
        }
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            runCatching {
                val running = OneKukuManager.isRunning()
                val granted = OneKukuManager.isGranted()
                shizukuRunning = running
                shizukuGranted = granted
                var latest = OneKukuBootRestoreStore.readHint(context)
                // 桥已就绪时，禁止把持久化 NEEDS_ACTIVATION 刷回 UI（开机编排/划掉后台会写脏 hint）。
                if (running && granted &&
                    (
                        latest == OneKukuBootUiHint.NEEDS_ACTIVATION ||
                            latest == OneKukuBootUiHint.WAITING_WIFI
                        )
                ) {
                    val hint = if (OneKukuBootRestoreStore.shouldShowNoSnapshotNote(context)) {
                        OneKukuBootUiHint.NO_SNAPSHOT_SLEEPING
                    } else {
                        OneKukuBootUiHint.READY_SLEEPING
                    }
                    OneKukuBootRestoreStore.writeHint(context, hint)
                    latest = hint
                }
                if (latest != bootUiHint) {
                    bootUiHint = latest
                }
            }.onFailure { error ->
                DiagFileLogger.w("UI", "boot/privilege poll failed: ${error.message}", error)
            }
        }
    }

    // 通知栏配对成功后：同步 binder 状态，清掉「需要激活」提示，避免卡在激活中。
    LaunchedEffect(activationPhase) {
        when (activationPhase) {
            OneKukuActivationPhase.ACTIVE,
            OneKukuActivationPhase.IDLE,
            -> {
                pairingUiPrimed = false
                shizukuRunning = OneKukuManager.isRunning()
                shizukuGranted = OneKukuManager.isGranted()
                if (shizukuRunning && shizukuGranted &&
                    bootUiHint == OneKukuBootUiHint.NEEDS_ACTIVATION
                ) {
                    OneKukuBootRestoreStore.writeHint(
                        context,
                        OneKukuBootUiHint.READY_SLEEPING,
                    )
                    bootUiHint = OneKukuBootUiHint.READY_SLEEPING
                }
            }
            OneKukuActivationPhase.FAILED -> {
                shizukuRunning = OneKukuManager.isRunning()
                shizukuGranted = OneKukuManager.isGranted()
            }
            else -> Unit
        }
    }

    LaunchedEffect(bootUiHint) {
        when (bootUiHint) {
            OneKukuBootUiHint.RESTORING,
            OneKukuBootUiHint.WAITING_WIFI,
            -> {
                oneKukuRestoring = true
                oneKukuTaskComplete = false
            }
            OneKukuBootUiHint.RESTORE_COMPLETE -> {
                oneKukuRestoring = false
                oneKukuTaskComplete = true
            }
            OneKukuBootUiHint.NEEDS_ACTIVATION -> {
                oneKukuRestoring = false
                oneKukuTaskComplete = false
            }
            OneKukuBootUiHint.READY_SLEEPING,
            OneKukuBootUiHint.NO_SNAPSHOT_SLEEPING,
            -> oneKukuRestoring = false
        }
    }

    LaunchedEffect(shizukuRunning, shizukuGranted) {
        if (!shizukuRunning || !shizukuGranted) {
            oneKukuTaskComplete = false
            oneKukuRestoring = false
        }
    }
    // 标志已就绪但相位仍卡 CONNECTING：强制清掉，消「ACTIVE+激活中」横跳。
    LaunchedEffect(bridgeReady, activationPhase) {
        if (!bridgeReady) return@LaunchedEffect
        if (activationPhase == OneKukuActivationPhase.CONNECTING ||
            activationPhase == OneKukuActivationPhase.STARTING
        ) {
            OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
        }
    }
    val phonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        phonePermissionResultCount += 1
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun appendLog(message: String) {
        val normalized = message.trim().take(MAX_DIAGNOSTIC_ENTRY_CHARS)
        if (normalized.isNotEmpty()) {
            log = "• $normalized\n$log".take(MAX_DIAGNOSTIC_LOG_CHARS)
            DiagFileLogger.ui(normalized)
        }
    }

    fun publish(message: String) {
        val sanitized = OneKukuHomeTools.sanitizeUserText(message)
        appendLog(sanitized)
        val feedback = when (OperationFeedbackPolicy.classify(sanitized)) {
            OperationFeedbackKind.INLINE -> sanitized
            OperationFeedbackKind.PERMISSION_DELEGATION_FAILED ->
                context.getString(R.string.operation_feedback_permission_delegate_failed)
            OperationFeedbackKind.ROLLBACK_FAILED ->
                context.getString(R.string.operation_feedback_rollback_failed)
            OperationFeedbackKind.LONG_FAILURE ->
                context.getString(R.string.operation_feedback_failure_logged)
            OperationFeedbackKind.LONG_RESULT ->
                context.getString(R.string.operation_feedback_result_logged)
        }
        scope.launch {
            snackbarHostState.showSnackbar(
                message = feedback,
                duration = if (feedback == sanitized) {
                    SnackbarDuration.Short
                } else {
                    SnackbarDuration.Long
                },
            )
        }
    }

    /** 抑制「已激活」连发（复连路径可能同秒多次 settle）。 */
    var lastActivatedPublishAt by remember { mutableLongStateOf(0L) }

    /** 同步 running/granted 并按真实状态给 snackbar（避免「请确认授权」与 Hero 就绪打架）。 */
    fun syncPrivilegeUiAndPublishActivation() {
        shizukuRunning = OneKukuManager.isRunning()
        shizukuGranted = OneKukuManager.isGranted()
        when {
            OneKukuManager.isReady() -> {
                settleOneKukuChannelAfterReady()
                val now = System.currentTimeMillis()
                if (now - lastActivatedPublishAt >= 2_500L) {
                    lastActivatedPublishAt = now
                    publish(context.getString(R.string.onekuku_msg_activated))
                }
            }
            OneKukuManager.isRunning() && !OneKukuManager.isGranted() -> {
                OneKukuManager.requestActivation()
                publish(context.getString(R.string.onekuku_msg_permission_requested))
            }
            else -> publish(context.getString(R.string.onekuku_msg_need_active))
        }
    }

    /**
     * OneLink 线激活：复刻 2.0.8/2.0.9（接入 OneKuku 内嵌栈之前）的纯 Shizuku 路径。
     * 不装 OneBridge、不收六位码、不钉「激活中」；配对/Start 全在官方 Shizuku 里完成。
     * 须定义在 [beginWirelessPairGuide] / [prepareOneKukuCore] 之前（本地函数不可前向引用）。
     */
    /** 抑制冷启/复连路径短时间反复把官方 Shizuku 拉到前台。 */
    var lastShizukuOpenAt by remember { mutableLongStateOf(0L) }

    fun prepareOneLinkShizukuChannel() {
        awaitingCoreInstall = false
        coreMissingDialogVisible = false
        adbPairDialogVisible = false
        // 2.0.9 无 CONNECTING 相位；避免打开 Shizuku 后首页一直钉在「激活中」。
        OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
        activationEpoch++
        shizukuRunning = OneKukuManager.isRunning()
        shizukuGranted = OneKukuManager.isGranted()
        when {
            OneKukuManager.isGranted() -> {
                OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)
                OneKukuHiddenRunner.wake()
                OneKukuSleepController.sleepIfEnabled(context)
                publish(context.getString(R.string.onekuku_msg_already_active))
            }
            OneKukuManager.isRunning() -> {
                OneKukuManager.requestActivation()
                publish(context.getString(R.string.onekuku_msg_permission_requested))
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastShizukuOpenAt < 15_000L) {
                    publish(context.getString(R.string.onekuku_msg_need_prepare))
                } else {
                    lastShizukuOpenAt = now
                    val result = ShizukuSetupHelper.openShizukuApp(context)
                    publish(
                        context.getString(
                            when (result) {
                                0 -> R.string.log_shizuku_opened
                                1 -> R.string.log_shizuku_not_installed
                                else -> R.string.log_open_failed
                            },
                        ),
                    )
                }
            }
        }
        shizukuRunning = OneKukuManager.isRunning()
        shizukuGranted = OneKukuManager.isGranted()
    }

    /**
     * 图四说明弹窗出现时立刻执行：挂配对通知 + WAITING_PAIR（卡片离开红色）。
     * 不在这里跑 mDNS/connect，避免「说明出来了、通知还要等半天」。
     * 打开无线调试仅一次，确认后再 [prepareOneKukuCore] 继续激活。
     */
    fun beginWirelessPairGuide() {
        // OneLink：不进内嵌配对引导，直接走 2.0.9 Shizuku 激活。
        if (ChannelLine.usesShizuku) {
            prepareOneLinkShizukuChannel()
            return
        }
        awaitingCoreInstall = false
        coreMissingDialogVisible = false
        adbPairDialogVisible = false
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            (context as? ComponentActivity)?.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                0x7101,
            )
        }
        val alreadyWaiting =
            OneKukuActivationUi.phase == OneKukuActivationPhase.WAITING_PAIR ||
                OneKukuActivationUi.phase == OneKukuActivationPhase.PAIRING
        OneKukuActivationUi.setPhase(OneKukuActivationPhase.WAITING_PAIR)
        activationEpoch++
        OneKukuPairingNotification.showWaiting(context)
        OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.NEEDS_ACTIVATION)
        bootUiHint = OneKukuBootUiHint.NEEDS_ACTIVATION
        // 弹窗阶段只挂通知 + 切「激活中」色，不跳设置页，保证说明能看完。
        // 无线调试在确认后的 prepareOneKukuCore 里打开（pairingUiPrimed 防重复）。
        if (!alreadyWaiting) {
            publish(context.getString(R.string.onekuku_msg_pairing_notification_shown))
        }
    }

    /**
     * OneKuku 激活默认流程：
     * - OneLink 线：走官方 Shizuku（安装/启动管理器 + 授权），不跑内置 OneBridge 配对。
     * - OneKuku 线：
     *   - 从未配对：先挂通知栏等六位码，再探测；无 transport 则保持等待。
     *   - 已配对过：不预先弹六位码 UI，优先静默打开无线调试并直连；仅 NeedPairingCode 再挂通知。
     */
    fun prepareOneKukuCore(forceRestart: Boolean = false) {
        if (ChannelLine.usesShizuku) {
            prepareOneLinkShizukuChannel()
            return
        }
        awaitingCoreInstall = false
        coreMissingDialogVisible = false
        adbPairDialogVisible = false
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            (context as? ComponentActivity)?.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                0x7101,
            )
        }
        val pairedBefore = OneKukuEmbeddedAdbActivator.hasPairedOnce(context)
        // 从未配对：先挂通知/WAITING_PAIR 让用户感知。
        // 已配对：先静默 wake，成功则不闪「激活中」；只有直连路径才进 CONNECTING。
        if (!pairedBefore) {
            if (OneKukuActivationUi.phase != OneKukuActivationPhase.WAITING_PAIR &&
                OneKukuActivationUi.phase != OneKukuActivationPhase.PAIRING &&
                OneKukuActivationUi.phase != OneKukuActivationPhase.CONNECTING
            ) {
                OneKukuActivationUi.setPhase(OneKukuActivationPhase.WAITING_PAIR)
                activationEpoch++
                OneKukuPairingNotification.showWaiting(context)
                publish(context.getString(R.string.onekuku_msg_pairing_notification_shown))
            }
        }
        scope.launch {
            OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)
            // CARE_MIN：外置 V15 binder 让 isReady()=true 仍不够，必须有宿主 onekuku_server。
            val careMin = !ChannelLine.usesShizuku &&
                ChannelEngine.current() == ChannelEngine.CARE_MIN
            if (careMin) {
                withContext(Dispatchers.IO) {
                    OneKukuHostServerBootstrap.ensureRunning(context)
                }
            }
            // 非强制重建：等已存活的通道 server 重投 binder（划掉 App 后再开无需 ADB）。
            if (!forceRestart) {
                val wake = OneKukuHiddenRunner.wake()
                val hostOk = !careMin || OneKukuHostServerBootstrap.isHostServerAlive()
                if (wake.success && OneKukuManager.isReady() && hostOk) {
                    pairingUiPrimed = false
                    OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
                    activationEpoch++
                    OneKukuPairingNotification.cancel(context)
                    if (bootUiHint == OneKukuBootUiHint.NEEDS_ACTIVATION ||
                        bootUiHint == OneKukuBootUiHint.WAITING_WIFI
                    ) {
                        val hint = if (OneKukuBootRestoreStore.shouldShowNoSnapshotNote(context)) {
                            OneKukuBootUiHint.NO_SNAPSHOT_SLEEPING
                        } else {
                            OneKukuBootUiHint.READY_SLEEPING
                        }
                        OneKukuBootRestoreStore.writeHint(context, hint)
                        bootUiHint = hint
                    }
                    syncPrivilegeUiAndPublishActivation()
                    return@launch
                }
                val binderDeadline = System.currentTimeMillis() + 9_000L
                while (System.currentTimeMillis() < binderDeadline &&
                    !OneKukuManager.isRunning()
                ) {
                    delay(300)
                }
                if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
                    OneKukuManager.requestActivation()
                }
                if (OneKukuManager.isReady() &&
                    (!careMin || OneKukuHostServerBootstrap.isHostServerAlive())
                ) {
                    pairingUiPrimed = false
                    OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
                    activationEpoch++
                    OneKukuPairingNotification.cancel(context)
                    if (bootUiHint == OneKukuBootUiHint.NEEDS_ACTIVATION ||
                        bootUiHint == OneKukuBootUiHint.WAITING_WIFI
                    ) {
                        val hint = if (OneKukuBootRestoreStore.shouldShowNoSnapshotNote(context)) {
                            OneKukuBootUiHint.NO_SNAPSHOT_SLEEPING
                        } else {
                            OneKukuBootUiHint.READY_SLEEPING
                        }
                        OneKukuBootRestoreStore.writeHint(context, hint)
                        bootUiHint = hint
                    }
                    syncPrivilegeUiAndPublishActivation()
                    return@launch
                }
            }
            if (pairedBefore) {
                // 静默 wake / binder 等待失败后才进入「连接中」，避免一点首页就钉死「激活中」。
                if (OneKukuActivationUi.phase != OneKukuActivationPhase.CONNECTING &&
                    OneKukuActivationUi.phase != OneKukuActivationPhase.STARTING &&
                    OneKukuActivationUi.phase != OneKukuActivationPhase.PAIRING
                ) {
                    OneKukuActivationUi.setPhase(OneKukuActivationPhase.CONNECTING)
                    activationEpoch++
                }
                // 仅「刚从关→开」才短等 TLS 起来；本来就开着则零等待直连。
                when (ShizukuSetupHelper.ensureAdbWifiEnabled(context)) {
                    ShizukuSetupHelper.AdbWifiEnsureResult.ENABLED_NOW ->
                        delay(1_200L)
                    ShizukuSetupHelper.AdbWifiEnsureResult.ALREADY_ON -> Unit
                    ShizukuSetupHelper.AdbWifiEnsureResult.FAILED -> {
                        if (!pairingUiPrimed) {
                            OneKukuCoreComponent.prepare(context)
                            pairingUiPrimed = true
                        }
                    }
                }
            } else if (!pairingUiPrimed) {
                OneKukuCoreComponent.prepare(context)
                pairingUiPrimed = true
            }
            publish(context.getString(R.string.onekuku_msg_activating))
            when (
                val outcome = OneKukuMiniAdbClient.activateExistingOrNeedPair(
                    context,
                    forceRestart = forceRestart,
                )
            ) {
                is OneKukuMiniAdbClient.Outcome.NeedPairingCode -> {
                    OneKukuActivationUi.setPhase(OneKukuActivationPhase.WAITING_PAIR)
                    activationEpoch++
                    OneKukuPairingNotification.showWaiting(context)
                    if (pairedBefore) {
                        // 已配对但直连失败：这时才需要用户填码，并打开无线调试页便于操作。
                        if (!pairingUiPrimed) {
                            OneKukuCoreComponent.prepare(context)
                            pairingUiPrimed = true
                        }
                        publish(context.getString(R.string.onekuku_msg_pairing_notification_shown))
                    }
                }
                is OneKukuMiniAdbClient.Outcome.Success -> {
                    pairingUiPrimed = false
                    OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
                    activationEpoch++
                    OneKukuPairingNotification.cancel(context)
                    // Success 只保证 binder 曾就绪；必须同步 granted，禁止 stale 假 READY。
                    syncPrivilegeUiAndPublishActivation()
                    if (OneKukuActivationUi.pendingRestoreAfterPair) {
                        OneKukuActivationUi.pendingRestoreAfterPair = false
                        if (OneKukuManager.isReady()) {
                            publish(context.getString(R.string.onekuku_msg_activated))
                        }
                    }
                }
                is OneKukuMiniAdbClient.Outcome.Failed -> {
                    if (outcome.reason == "wifi_sta_required") {
                        pairingUiPrimed = false
                        OneKukuActivationUi.setPhase(
                            OneKukuActivationPhase.FAILED,
                            failure = outcome.reason,
                        )
                        activationEpoch++
                        publish(context.getString(R.string.onekuku_msg_wifi_sta_required))
                    } else if (pairedBefore) {
                        // 已配对路径失败：保持失败态，不假装还在等六位码。
                        pairingUiPrimed = false
                        OneKukuActivationUi.setPhase(
                            OneKukuActivationPhase.FAILED,
                            failure = outcome.reason,
                        )
                        activationEpoch++
                        OneKukuPairingNotification.cancel(context)
                        publish(
                            context.getString(
                                R.string.onekuku_msg_embedded_adb_fallback,
                                outcome.reason,
                            ),
                        )
                    } else {
                        OneKukuActivationUi.setPhase(OneKukuActivationPhase.WAITING_PAIR)
                        activationEpoch++
                        OneKukuPairingNotification.showWaiting(context)
                    }
                }
            }
        }
    }

    /**
     * 学 V15 `UserPresentRestartReceiver`：已配对但桥未就绪时，0 / 5s / 15s 错峰再拉。
     * 覆盖「前台单次 prepare 失败 / binder 中途死掉」——lite 外置 Manager 不易踩的假死窗。
     * 须定义在 [prepareOneKukuCore] 之后。
     */
    fun schedulePrivilegeReconnectShots(reason: String) {
        if (ChannelLine.usesShizuku) return
        if (!OneKukuEmbeddedAdbActivator.hasPairedOnce(context)) return
        // 已有一轮在跑：勿因 binder_dead / 冷启+ON_START 双触发叠两路。
        synchronized(privilegeReconnectJobHolder) {
            val running = privilegeReconnectJobHolder[0]
            if (running?.isActive == true) {
                DiagFileLogger.i("Privilege", "reconnect shots already active; ignore reason=$reason")
                return
            }
            privilegeReconnectJobHolder[0] = scope.launch {
            DiagFileLogger.i("Privilege", "v15-style reconnect shots reason=$reason")
            /**
             * 只等 onebridge_server 重投 binder / 本地 wake，**不**开 libadb shell。
             * 否则每次复连都会拨 adbd，系统弹出「USB 调试已连接/断开」
             * （文案如此，实际多半是无线调试回环，与数据线无关）。
             */
            suspend fun awaitBinderOnly(label: String, windowMs: Long): Boolean {
                val deadline = System.currentTimeMillis() + windowMs
                DiagFileLogger.i("Privilege", "reconnect $label wait-binder-only ${windowMs}ms")
                while (System.currentTimeMillis() < deadline) {
                    syncPrivilegeFlagsFromBridge()
                    if (OneKukuManager.isReady()) {
                        settleOneKukuChannelAfterReady()
                        activationEpoch++
                        return true
                    }
                    OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)
                    OneKukuHiddenRunner.wake()
                    delay(300L)
                }
                syncPrivilegeFlagsFromBridge()
                if (OneKukuManager.isReady()) {
                    settleOneKukuChannelAfterReady()
                    activationEpoch++
                    return true
                }
                return false
            }
            // 0～15s：对齐 V15「server 仍活 → 只重投 binder」，避免刷 ADB 弹窗。
            if (awaitBinderOnly("t0", 5_000L)) return@launch
            if (awaitBinderOnly("t5", 10_000L)) return@launch
            DiagFileLogger.w(
                "Privilege",
                "reconnect binder-only timeout → fallback ADB once reason=$reason",
            )
            activationEpoch++
            prepareOneKukuCore(forceRestart = false)
            val settleDeadline = System.currentTimeMillis() + 12_000L
            while (System.currentTimeMillis() < settleDeadline) {
                if (OneKukuManager.isReady()) {
                    settleOneKukuChannelAfterReady()
                    activationEpoch++
                    return@launch
                }
                delay(400L)
            }
            syncPrivilegeFlagsFromBridge()
            if (OneKukuManager.isReady()) {
                settleOneKukuChannelAfterReady()
            } else {
                DiagFileLogger.w("Privilege", "reconnect shots exhausted reason=$reason")
            }
            activationEpoch++
            }
        }
    }

    /**
     * App 回到前台：桥仍在则秒级唤醒；桥已掉（小米杀进程/ binder 死）则主动复连，
     * 避免状态框卡在「未激活」。须定义在 [prepareOneKukuCore] 之后（本地函数不可前向引用）。
     */
    fun wakeChannelWhenForegrounded() {
        OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)
        syncPrivilegeFlagsFromBridge()
        if (OneKukuManager.isReady()) {
            OneKukuHiddenRunner.wake()
            settleOneKukuChannelAfterReady()
            activationEpoch++
            return
        }
        scope.launch {
            DiagFileLogger.i(
                "Privilege",
                "foreground reconnect channel=${ChannelLine.id} " +
                    "running=$shizukuRunning granted=$shizukuGranted",
            )
            if (ChannelLine.usesShizuku) {
                repeat(15) {
                    syncPrivilegeFlagsFromBridge()
                    if (OneKukuManager.isReady()) {
                        settleOneKukuChannelAfterReady()
                        activationEpoch++
                        return@launch
                    }
                    if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
                        OneKukuManager.requestActivation()
                    }
                    delay(200)
                }
                syncPrivilegeFlagsFromBridge()
                activationEpoch++
                return@launch
            }
            if (OneKukuEmbeddedAdbActivator.hasPairedOnce(context)) {
                // 单次 prepare 不够：学 V15 错峰多拍。勿预置 CONNECTING，避免 binder 已到仍钉「激活中」。
                activationEpoch++
                schedulePrivilegeReconnectShots("foreground")
            } else {
                syncPrivilegeFlagsFromBridge()
                activationEpoch++
            }
        }
    }

    // 冷启复连：OneKuku 已配对无码直连；OneLink 等 Shizuku binder 晚到后自动对齐状态。
    LaunchedEffect(Unit) {
        if (OneKukuManager.isReady()) {
            OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
            settleOneKukuChannelAfterReady()
            return@LaunchedEffect
        }
        if (ChannelLine.usesShizuku) {
            repeat(15) {
                syncPrivilegeFlagsFromBridge()
                if (OneKukuManager.isReady()) {
                    OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
                    settleOneKukuChannelAfterReady()
                    return@LaunchedEffect
                }
                if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
                    OneKukuManager.requestActivation()
                }
                delay(200)
            }
            syncPrivilegeFlagsFromBridge()
            return@LaunchedEffect
        }
        if (!OneKukuEmbeddedAdbActivator.hasPairedOnce(context)) return@LaunchedEffect
        val phase = OneKukuActivationUi.phase
        if (phase == OneKukuActivationPhase.CONNECTING ||
            phase == OneKukuActivationPhase.STARTING ||
            phase == OneKukuActivationPhase.PAIRING ||
            phase == OneKukuActivationPhase.WAITING_PAIR
        ) {
            return@LaunchedEffect
        }
        // 与前台复连同一条路：先等 binder 重投，避免冷启立刻 libadb 刷「USB 调试」弹窗。
        schedulePrivilegeReconnectShots("cold_start")
    }

    // 激活中相位超时兜底：避免直连挂死时永远钉在「激活中」。
    LaunchedEffect(activationPhase) {
        if (activationPhase != OneKukuActivationPhase.CONNECTING &&
            activationPhase != OneKukuActivationPhase.STARTING &&
            activationPhase != OneKukuActivationPhase.WAITING_PAIR &&
            activationPhase != OneKukuActivationPhase.PAIRING
        ) {
            return@LaunchedEffect
        }
        delay(45_000L)
        if (OneKukuManager.isReady()) {
            OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
            settleOneKukuChannelAfterReady()
            return@LaunchedEffect
        }
        val stillBusy = OneKukuActivationUi.phase == OneKukuActivationPhase.CONNECTING ||
            OneKukuActivationUi.phase == OneKukuActivationPhase.STARTING ||
            OneKukuActivationUi.phase == OneKukuActivationPhase.WAITING_PAIR ||
            OneKukuActivationUi.phase == OneKukuActivationPhase.PAIRING
        if (stillBusy) {
            OneKukuActivationUi.setPhase(
                OneKukuActivationPhase.FAILED,
                failure = "activation_timeout",
            )
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity
            ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> sleepChannelWhenBackgrounded()
                Lifecycle.Event.ON_START -> wakeChannelWhenForegrounded()
                Lifecycle.Event.ON_RESUME -> {
                    // 每次回前台再对一次桥状态，防止小米/一加上偶发 UI 与 binder 脱节。
                    syncPrivilegeFlagsFromBridge()
                    if (OneKukuManager.isReady()) {
                        settleOneKukuChannelAfterReady()
                    }
                    if (!awaitingCoreInstall) return@LifecycleEventObserver
                    if (!OneKukuCoreComponent.isInstalled(context)) return@LifecycleEventObserver
                    // 装完从系统安装器 / 通道页返回：自动关掉「还没装」并进入配对
                    awaitingCoreInstall = false
                    coreMissingDialogVisible = false
                    prepareOneKukuCore()
                }
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    /** 「启动通道」专用：不走总控卡 isRunning 短路，保证能进配对/安装弹窗。 */
    fun startCoreFromPrepCard() {
        if (OneKukuManager.isReady()) {
            publish(context.getString(R.string.onekuku_msg_already_active))
            return
        }
        prepareOneKukuCore()
        if (ChannelLine.usesShizuku) return
        OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.NEEDS_ACTIVATION)
        bootUiHint = OneKukuBootUiHint.NEEDS_ACTIVATION
    }

    fun runEmbeddedAdbWithCode(code: String) {
        scope.launch {
            adbPairBusy = true
            try {
                when (
                    val outcome = OneKukuEmbeddedAdbActivator.activate(context, pairingCode = code)
                ) {
                    is OneKukuEmbeddedAdbActivator.Outcome.NeedPairingCode ->
                        publish(context.getString(R.string.onekuku_msg_need_pairing_code))
                    is OneKukuEmbeddedAdbActivator.Outcome.Success -> {
                        adbPairDialogVisible = false
                        OneKukuPairingNotification.cancel(context)
                        OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
                        syncPrivilegeUiAndPublishActivation()
                    }
                    is OneKukuEmbeddedAdbActivator.Outcome.Failed -> {
                        OneKukuPairingNotification.showWaiting(context)
                        publish(
                            if (outcome.reason == "wifi_sta_required") {
                                context.getString(R.string.onekuku_msg_wifi_sta_required)
                            } else {
                                context.getString(
                                    R.string.onekuku_msg_embedded_adb_fallback,
                                    outcome.reason,
                                )
                            },
                        )
                    }
                }
            } finally {
                adbPairBusy = false
            }
        }
    }

    fun selectSim(subId: Int) {
        selectedSubId = subId
        ConfigStore.setSelectedSubId(context, subId)
    }

    fun refreshAll(showFeedback: Boolean = false) {
        runCatching {
            sims = ImsController.listSims(context)
            if (sims.none { it.subscriptionId == selectedSubId }) {
                val restored = ConfigStore.getSelectedSubId(context)
                val fallback = sims.firstOrNull { it.subscriptionId == restored }?.subscriptionId
                    ?: sims.firstOrNull()?.subscriptionId
                    ?: -1
                selectSim(fallback)
            }
            shizukuRunning = runCatching { OneKukuManager.isRunning() }.getOrDefault(false)
            shizukuGranted = runCatching { OneKukuManager.isGranted() }.getOrDefault(false)
            deviceInfo = runCatching { DeviceInfo.summary(context) }
                .getOrElse { error ->
                    DiagFileLogger.w("UI", "DeviceInfo.summary failed: ${error.message}", error)
                    error.message.orEmpty()
                }
            if (showFeedback) {
                publish(context.getString(R.string.refreshed))
            }
        }.onFailure { error ->
            DiagFileLogger.e("UI", "refreshAll failed: ${error.message}", error)
            publish(
                context.getString(
                    R.string.operation_failed,
                    OperationErrors.describe(error),
                ),
            )
        }
    }

    fun runOperation(
        label: String,
        onComplete: () -> Unit = {},
        operation: () -> String,
    ) {
        if (busyLabel != null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.operation_already_running),
                )
            }
            return
        }
        // 先冻结编辑器再启动协程，避免用户在同一帧内继续修改已快照的 Apply 参数。
        busyLabel = label
        scope.launch {
            val message = try {
                withContext(Dispatchers.IO) {
                    runCatching(operation).getOrElse { error ->
                        context.getString(
                            R.string.operation_failed,
                            OperationErrors.describe(error),
                        )
                    }
                }
            } finally {
                busyLabel = null
            }
            onComplete()
            publish(message)
        }
    }

    fun requestConfirmation(
        title: String,
        message: String,
        confirmLabel: String,
        onConfirm: () -> Unit,
    ) {
        confirmation = ConfirmationRequest(title, message, confirmLabel, onConfirm)
    }

    fun ensurePrivilegedAccess(): Boolean {
        shizukuRunning = OneKukuManager.isRunning()
        shizukuGranted = OneKukuManager.isGranted()
        val message = when {
            !shizukuRunning -> context.getString(R.string.shizuku_not_running_message)
            !shizukuGranted -> context.getString(R.string.shizuku_permission_required_message)
            else -> return true
        }
        publish(message)
        return false
    }

    LaunchedEffect(phonePermissionResultCount) {
        val phonePermissionGranted =
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
        when {
            phonePermissionGranted ->
                refreshAll(showFeedback = phonePermissionResultCount > 0)
            phonePermissionResultCount == 0 ->
                phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            else -> {
                refreshAll()
                publish(context.getString(R.string.phone_permission_denied_message))
            }
        }
    }

    LaunchedEffect(selectedSubId, shizukuGranted) {
        reapplyStatus = ConfigStore.lastReapplyStatus(context)
        signalStrengthAdjustmentEnabled =
            ConfigStore.signalStrengthAdjustmentEnabled(context, selectedSubId)
        if (selectedSubId >= 0 && shizukuGranted) {
            advancedOptions = withContext(Dispatchers.IO) {
                runCatching {
                    PixelImsCompat.readOptions(context, selectedSubId)
                }.getOrDefault(PixelImsOptions())
            }
            val voWifiSelection = withContext(Dispatchers.IO) {
                runCatching {
                    VoWifiNameFormatManager.readSelection(context, selectedSubId)
                }.getOrNull()
            }
            voWifiNameFormatIndex = voWifiSelection?.formatIndex
            voWifiCustomCarrierName = voWifiSelection?.customCarrierName.orEmpty()
        } else {
            voWifiNameFormatIndex = null
            voWifiCustomCarrierName = ""
        }
    }

    // 功能页开关按 selectedSubId 持久化：切卡只加载该卡快照，不重置、不串卡。
    // 身份覆盖 / 国家码输入框同样按卡加载草稿，避免卡 A 文案带到卡 B。
    LaunchedEffect(selectedSubId) {
        val identity = ConfigStore.identityDraft(context, selectedSubId)
        carrierName = identity?.carrierName.orEmpty()
        imsUserAgent = identity?.imsUserAgent.orEmpty()

        val countryDraft = ConfigStore.simCountryIsoDraft(context, selectedSubId)
        simCountryIso = countryDraft.orEmpty()
        activeSimCountryIso = withContext(Dispatchers.IO) {
            runCatching {
                SimCountryIsoManager.readCurrent(context, selectedSubId).orEmpty()
            }.getOrDefault("")
        }

        val saved = ConfigStore.capabilityUiState(context, selectedSubId)
        if (saved != null) {
            volte = saved.volte
            vowifi = saved.vowifi
            vonr = saved.vonr
            vilte = saved.vilte
            ut = saved.ut
            crossSim = saved.crossSim
            nr5g = saved.nr5g
            wfcMode = saved.wfcMode
            return@LaunchedEffect
        }
        val sim = sims.firstOrNull { it.subscriptionId == selectedSubId }
        if (sim != null) {
            val profile = CarrierProfiles.match(sim.mcc, sim.mnc)
            volte = profile.recommendVolte
            vowifi = profile.recommendVowifi
            vonr = profile.recommendVonr
            wfcMode = profile.recommendWfcMode
        } else {
            volte = true
            vowifi = true
            vonr = false
            wfcMode = WfcMode.CELLULAR_PREFERRED
        }
        vilte = false
        ut = false
        crossSim = false
        nr5g = false
    }

    fun currentCapabilityUiState(): ConfigStore.CapabilityUiState =
        ConfigStore.CapabilityUiState(
            volte = volte,
            vowifi = vowifi,
            vonr = vonr,
            vilte = vilte,
            ut = ut,
            crossSim = crossSim,
            nr5g = nr5g,
            wfcMode = wfcMode,
        )

    fun persistCapabilityUi(subId: Int = selectedSubId) {
        if (subId < 0) return
        ConfigStore.setCapabilityUiState(context, subId, currentCapabilityUiState())
    }

    fun applyRecommendedProfile() {
        val sim = selectedSim
        if (!ensurePrivilegedAccess()) {
            return
        }
        if (sim == null) {
            publish(context.getString(R.string.please_select_sim))
            return
        }
        val profile = CarrierProfiles.match(sim.mcc, sim.mnc)
        volte = profile.recommendVolte
        vowifi = profile.recommendVowifi
        vonr = profile.recommendVonr
        wfcMode = profile.recommendWfcMode
        persistCapabilityUi(sim.subscriptionId)
        runOperation(context.getString(R.string.apply_recommended)) {
            val result = ImsController.applyAll(
                context = context,
                subId = sim.subscriptionId,
                enableVolte = profile.recommendVolte,
                enableVowifi = profile.recommendVowifi,
                enableVonr = profile.recommendVonr,
                wfcMode = profile.recommendWfcMode,
            )
            if (result.success) {
                OneKukuSnapshotStore.save(
                    context,
                    OneKukuSnapshotFactory.fromCurrent(context, sim),
                )
            }
            result.message
        }
    }

    fun persistIdentityDraft(subId: Int = selectedSubId) {
        if (subId < 0) return
        ConfigStore.setIdentityDraft(
            context,
            subId,
            ConfigStore.IdentityDraft(
                carrierName = carrierName,
                imsUserAgent = imsUserAgent,
            ),
        )
    }

    fun persistSimCountryIsoDraft(subId: Int = selectedSubId) {
        if (subId < 0) return
        ConfigStore.setSimCountryIsoDraft(context, subId, simCountryIso)
    }

    fun refreshActiveSimCountryIso(subId: Int = selectedSubId) {
        if (subId < 0) {
            activeSimCountryIso = ""
            return
        }
        activeSimCountryIso = runCatching {
            SimCountryIsoManager.readCurrent(context, subId).orEmpty()
        }.getOrDefault("")
    }

    DisposableEffect(Unit) {
        val bridge = PrivilegeBridges.current
        val permissionListener = PrivilegeBridge.PermissionResultListener { requestCode, _ ->
            if (requestCode == OneKukuManager.REQUEST_CODE) {
                refreshAll()
                publish(
                    context.getString(
                        if (OneKukuManager.isGranted()) {
                            R.string.onekuku_msg_activated
                        } else {
                            R.string.onekuku_msg_activation_denied
                        },
                    ),
                )
            }
        }
        val binderReceivedListener: () -> Unit = {
            syncPrivilegeFlagsFromBridge()
            refreshAll()
            if (OneKukuManager.isReady()) {
                // 已接回：停掉错峰复连，避免后续 t5/t15 再触发 ADB 把刚活的 server 打掉。
                privilegeReconnectJobHolder[0]?.cancel()
                privilegeReconnectJobHolder[0] = null
                settleOneKukuChannelAfterReady()
            }
        }
        val binderDeadListener: () -> Unit = {
            // 只按桥真值刷新，禁止强行把 granted 打成 false（Shizuku 授权可粘滞）。
            DiagFileLogger.w("Privilege", "binder dead → resync flags + v15 reconnect shots")
            syncPrivilegeFlagsFromBridge()
            oneKukuTaskComplete = false
            oneKukuRestoring = false
            activationEpoch++
            // Watchdog 负责后台静默重拉；前台 UI 再叠 0/5/15s，避免 Hero 假死在「未激活」。
            if (!ChannelLine.usesShizuku &&
                OneKukuEmbeddedAdbActivator.hasPairedOnce(context) &&
                !OneKukuManager.isReady()
            ) {
                schedulePrivilegeReconnectShots("binder_dead")
            }
        }

        runCatching { bridge.addRequestPermissionResultListener(permissionListener) }
        runCatching { bridge.addBinderReceivedListener(binderReceivedListener, sticky = true) }
        runCatching { bridge.addBinderDeadListener(binderDeadListener) }

        onDispose {
            privilegeReconnectJobHolder[0]?.cancel()
            privilegeReconnectJobHolder[0] = null
            runCatching { bridge.removeRequestPermissionResultListener(permissionListener) }
            runCatching { bridge.removeBinderReceivedListener(binderReceivedListener) }
            runCatching { bridge.removeBinderDeadListener(binderDeadListener) }
        }
    }

    if (membershipPaywallVisible) {
        Box(modifier = Modifier.fillMaxSize()) {
            MembershipPaywallScreen(
                onBack = { membershipPaywallVisible = false },
                onPurchase = {
                    publish(context.getString(R.string.membership_purchase_pending))
                },
                onRestore = {
                    publish(context.getString(R.string.membership_restore_pending))
                },
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        return
    }

    OneImsScaffold(
        selectedDestination = destination,
        onDestinationSelected = { destination = it },
        busyLabel = busyLabel,
        snackbarHostState = snackbarHostState,
    ) {
        Crossfade(
            targetState = destination,
            animationSpec = tween(durationMillis = 220),
            label = "destination",
        ) { currentDestination ->
            when (currentDestination) {
                AppDestination.HOME -> HomeScreen(
                    state = HomeUiState(
                        shizukuRunning = shizukuRunning,
                        shizukuGranted = shizukuGranted,
                        oneKukuState = oneKukuState,
                        oneKukuChannelSleeping = oneKukuChannelSleeping,
                        deviceInfo = deviceInfo,
                        sims = sims,
                        selectedSubId = selectedSubId,
                        selectedSim = selectedSim,
                        actionsEnabled = actionsAvailable,
                        recommendActionsEnabled = selectedSubId >= 0 &&
                            shizukuGranted &&
                            actionsAvailable,
                        activeOperationLabel = busyLabel,
                        reapplyStatus = reapplyStatus,
                        bootAutoCheck = oneKukuBootAutoCheck,
                        autoRestore = oneKukuAutoRestore,
                        autoSleep = oneKukuAutoSleep,
                        rootBootStart = rootBootStart,
                        sandboxPersistBypass = sandboxPersistBypass,
                        oneKukuDetailOverride = oneKukuDetailOverride,
                    ),
                    actions = HomeActions(
                        onSelectSim = { selectSim(it) },
                        onBeginWirelessPairGuide = { beginWirelessPairGuide() },
                        onApplyRecommended = { applyRecommendedProfile() },
                        onSaveCallConfig = {
                            val sim = selectedSim
                            if (sim == null) {
                                publish(context.getString(R.string.onekuku_msg_config_save_need_sim))
                            } else {
                                // 保存快照只写本地 prefs，不要求通道特权。
                                runOperation(
                                    label = context.getString(R.string.onekuku_action_save_config),
                                    onComplete = {
                                        OneKukuBootRestoreStore.setNoSnapshotNote(context, false)
                                        OneKukuBootRestoreStore.writeHint(
                                            context,
                                            OneKukuBootUiHint.READY_SLEEPING,
                                        )
                                        bootUiHint = OneKukuBootUiHint.READY_SLEEPING
                                    },
                                ) {
                                    OneKukuSnapshotStore.save(
                                        context,
                                        OneKukuSnapshotFactory.fromCurrent(context, sim),
                                    )
                                    context.getString(R.string.onekuku_msg_config_saved)
                                }
                            }
                        },
                        onActivateOneKuku = {
                            when {
                                !OneKukuManager.isRunning() -> {
                                    prepareOneKukuCore()
                                    OneKukuBootRestoreStore.writeHint(
                                        context,
                                        OneKukuBootUiHint.NEEDS_ACTIVATION,
                                    )
                                    bootUiHint = OneKukuBootUiHint.NEEDS_ACTIVATION
                                }
                                OneKukuManager.isGranted() -> {
                                    OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)
                                    OneKukuHiddenRunner.wake()
                                    OneKukuSleepController.sleepIfEnabled(context)
                                    refreshAll()
                                    oneKukuTaskComplete = false
                                    OneKukuBootRestoreStore.writeHint(
                                        context,
                                        OneKukuBootUiHint.READY_SLEEPING,
                                    )
                                    bootUiHint = OneKukuBootUiHint.READY_SLEEPING
                                    publish(context.getString(R.string.onekuku_msg_already_active))
                                }
                                else -> {
                                    OneKukuManager.requestActivation()
                                    publish(context.getString(R.string.onekuku_msg_permission_requested))
                                }
                            }
                            shizukuRunning = OneKukuManager.isRunning()
                            shizukuGranted = OneKukuManager.isGranted()
                        },
                        onForceReactivateOneKuku = {
                            prepareOneKukuCore(forceRestart = true)
                            OneKukuBootRestoreStore.writeHint(
                                context,
                                OneKukuBootUiHint.NEEDS_ACTIVATION,
                            )
                            bootUiHint = OneKukuBootUiHint.NEEDS_ACTIVATION
                            shizukuRunning = OneKukuManager.isRunning()
                            shizukuGranted = OneKukuManager.isGranted()
                        },
                        onStartCore = { startCoreFromPrepCard() },
                        onCheckOneKukuStatus = {
                            oneKukuTaskComplete = false
                            shizukuRunning = OneKukuManager.isRunning()
                            shizukuGranted = OneKukuManager.isGranted()
                            OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)
                            val check = OneKukuCommandDispatcher.dispatch(
                                context = context,
                                command = OneKukuCommand.CHECK_ONEKUKU_STATUS,
                                subId = selectedSubId,
                            )
                            publish(
                                when {
                                    !OneKukuManager.isRunning() ->
                                        context.getString(R.string.onekuku_settings_state_invalid)
                                    !OneKukuManager.isGranted() ->
                                        context.getString(R.string.onekuku_settings_state_inactive)
                                    check.success ->
                                        context.getString(R.string.onekuku_msg_status_ok)
                                    else ->
                                        OneKukuHomeTools.sanitizeUserText(check.message)
                                },
                            )
                        },
                        onRestoreCallConfig = {
                            shizukuRunning = OneKukuManager.isRunning()
                            shizukuGranted = OneKukuManager.isGranted()
                            val targetSubId = selectedSubId
                            when {
                                busyLabel != null ->
                                    publish(context.getString(R.string.operation_already_running))
                                !OneKukuManager.isRunning() -> {
                                    // OneLink：先开 Shizuku；OneKuku：先走通知栏配对，成功后再恢复
                                    OneKukuActivationUi.pendingRestoreAfterPair = true
                                    prepareOneKukuCore()
                                    publish(
                                        context.getString(
                                            if (ChannelLine.usesShizuku) {
                                                R.string.onekuku_msg_need_prepare
                                            } else {
                                                R.string.onekuku_msg_pairing_notification_shown
                                            },
                                        ),
                                    )
                                }
                                else -> {
                                    val restoreOutcome =
                                        arrayOf(OneKukuHomeTools.RestoreOutcome.FAILURE)
                                    oneKukuTaskComplete = false
                                    oneKukuRestoring = true
                                    OneKukuBootRestoreStore.writeHint(
                                        context,
                                        OneKukuBootUiHint.RESTORING,
                                    )
                                    bootUiHint = OneKukuBootUiHint.RESTORING
                                    runOperation(
                                        label = context.getString(R.string.onekuku_busy_restore),
                                        onComplete = {
                                            oneKukuRestoring = false
                                            reapplyStatus =
                                                ConfigStore.lastReapplyStatus(context)
                                            shizukuRunning = OneKukuManager.isRunning()
                                            shizukuGranted = OneKukuManager.isGranted()
                                            when (restoreOutcome[0]) {
                                                OneKukuHomeTools.RestoreOutcome.SUCCESS,
                                                OneKukuHomeTools.RestoreOutcome.PARTIAL,
                                                -> {
                                                    oneKukuTaskComplete = true
                                                    OneKukuBootRestoreStore.writeHint(
                                                        context,
                                                        OneKukuBootUiHint.RESTORE_COMPLETE,
                                                    )
                                                    bootUiHint =
                                                        OneKukuBootUiHint.RESTORE_COMPLETE
                                                    OneKukuSleepController.sleepIfEnabled(context)
                                                }
                                                OneKukuHomeTools.RestoreOutcome.FAILURE -> {
                                                    oneKukuTaskComplete = false
                                                    if (!OneKukuManager.isReady()) {
                                                        OneKukuBootRestoreStore.writeHint(
                                                            context,
                                                            OneKukuBootUiHint.NEEDS_ACTIVATION,
                                                        )
                                                        bootUiHint =
                                                            OneKukuBootUiHint.NEEDS_ACTIVATION
                                                    }
                                                }
                                            }
                                        },
                                    ) {
                                        val report = OneKukuCallRestoreExecutor.execute(
                                            context = context,
                                            selectedSubId = targetSubId,
                                            sims = sims,
                                        )
                                        restoreOutcome[0] = report.outcome
                                        report.userMessage
                                    }
                                }
                            }
                        },
                        onRestoreSystemDefaults = {
                            val targetSubId = selectedSubId
                            when {
                                busyLabel != null ->
                                    publish(context.getString(R.string.operation_already_running))
                                targetSubId < 0 ->
                                    publish(context.getString(R.string.please_select_sim))
                                !OneKukuManager.isGranted() ->
                                    publish(
                                        context.getString(R.string.shizuku_permission_required_message),
                                    )
                                else -> requestConfirmation(
                                    title = context.getString(R.string.confirm_restore_title),
                                    message = context.getString(R.string.confirm_restore_message),
                                    confirmLabel = context.getString(R.string.action_restore),
                                ) {
                                    runOperation(context.getString(R.string.action_restore)) {
                                        SafetyGuard.restoreDefaults(context, targetSubId).message
                                    }
                                }
                            }
                        },
                        onStatusCheck = {
                            if (busyLabel != null) {
                                publish(context.getString(R.string.operation_already_running))
                            } else {
                                val targetSubId = selectedSubId
                                busyLabel = context.getString(R.string.onekuku_busy_status_check)
                                scope.launch {
                                    val message = try {
                                        refreshAll()
                                        withContext(Dispatchers.IO) {
                                            val running = OneKukuManager.isRunning()
                                            val granted = OneKukuManager.isGranted()
                                            val statusLabel = OneKukuHomeTools.settingsStatusLabel(
                                                context = context,
                                                state = OneKukuCardPolicy.resolve(
                                                    serviceReady = running && granted,
                                                    isExecuting = false,
                                                    channelSleeping = OneKukuCardPolicy.isChannelSleeping(
                                                        OneKukuHiddenRunner.currentState(),
                                                    ),
                                                    taskComplete = false,
                                                ),
                                                serviceRunning = running,
                                            )
                                            val simLine = sims.firstOrNull {
                                                it.subscriptionId == targetSubId
                                            }?.let {
                                                context.getString(
                                                    R.string.onekuku_status_sim,
                                                    it.slotIndex + 1,
                                                    it.carrierName,
                                                )
                                            } ?: context.getString(R.string.onekuku_status_no_sim)
                                            val imsLine = if (targetSubId >= 0 && granted) {
                                                OneKukuHomeTools.sanitizeUserText(
                                                    ImsController.queryImsStatus(
                                                        context,
                                                        targetSubId,
                                                    ).rawText,
                                                )
                                            } else {
                                                context.getString(R.string.onekuku_status_ims_skipped)
                                            }
                                            buildString {
                                                append(
                                                    context.getString(
                                                        R.string.onekuku_status_onekuku,
                                                        statusLabel,
                                                    ),
                                                )
                                                append('\n')
                                                append(simLine)
                                                append('\n')
                                                append(imsLine)
                                            }
                                        }
                                    } finally {
                                        busyLabel = null
                                    }
                                    publish(message)
                                }
                            }
                        },
                        onBootAutoCheckChange = { enabled ->
                            ConfigStore.setOneKukuBootAutoCheck(context, enabled)
                            oneKukuBootAutoCheck = enabled
                            publish(
                                context.getString(
                                    if (enabled) {
                                        R.string.onekuku_settings_boot_on
                                    } else {
                                        R.string.onekuku_settings_boot_off
                                    },
                                ),
                            )
                        },
                        onAutoRestoreChange = { enabled ->
                            ConfigStore.setOneKukuAutoRestore(context, enabled)
                            oneKukuAutoRestore = enabled
                            publish(
                                context.getString(
                                    if (enabled) {
                                        R.string.onekuku_settings_restore_on
                                    } else {
                                        R.string.onekuku_settings_restore_off
                                    },
                                ),
                            )
                        },
                        onAutoSleepChange = { enabled ->
                            ConfigStore.setOneKukuAutoSleep(context, enabled)
                            oneKukuAutoSleep = enabled
                            publish(
                                context.getString(
                                    if (enabled) {
                                        R.string.onekuku_settings_sleep_on
                                    } else {
                                        R.string.onekuku_settings_sleep_off
                                    },
                                ),
                            )
                        },
                        onRootBootStartChange = { enabled ->
                            rootBootStart = enabled
                            ConfigStore.setRootBootStart(context, enabled)
                            publish(
                                context.getString(
                                    if (enabled) {
                                        R.string.root_boot_on
                                    } else {
                                        R.string.root_boot_off
                                    },
                                ),
                            )
                        },
                        onSandboxPersistBypassChange = { enabled ->
                            sandboxPersistBypass = enabled
                            SandboxPersistSupport.setEnabled(context, enabled)
                            publish(
                                context.getString(
                                    if (enabled) {
                                        R.string.sandbox_persist_on
                                    } else {
                                        R.string.sandbox_persist_off
                                    },
                                ),
                            )
                        },
                        onOpenWirelessDebugging = {
                            publish(
                                context.getString(
                                    if (ShizukuSetupHelper.openWirelessDebugging(context)) {
                                        R.string.log_jumped_wireless
                                    } else {
                                        R.string.log_jump_failed
                                    },
                                ),
                            )
                        },
                        onOpenHotspot = {
                            publish(
                                context.getString(
                                    if (ShizukuSetupHelper.openHotspotSettings(context)) {
                                        R.string.log_hotspot_opened
                                    } else {
                                        R.string.log_open_failed
                                    },
                                ),
                            )
                        },
                        onCopyAdbGuide = {
                            // 规格禁止默认复制 adb 命令：改为打开无线调试
                            publish(
                                context.getString(
                                    if (ShizukuSetupHelper.openWirelessDebugging(context)) {
                                        R.string.log_jumped_wireless
                                    } else {
                                        R.string.log_jump_failed
                                    },
                                ),
                            )
                        },
                    ),
                )

                AppDestination.CAPABILITIES -> CapabilitiesScreen(
                    state = CapabilitiesUiState(
                        sims = sims,
                        selectedSubId = selectedSubId,
                        selectedSim = selectedSim,
                        recommendActionsEnabled = selectedSubId >= 0 &&
                            shizukuGranted &&
                            actionsAvailable,
                        volte = volte,
                        vowifi = vowifi,
                        vonr = vonr,
                        vilte = vilte,
                        ut = ut,
                        crossSim = crossSim,
                        nr5g = nr5g,
                        signalStrengthAdjustmentEnabled = signalStrengthAdjustmentEnabled,
                        wfcMode = wfcMode,
                        voWifiNameFormatIndex = voWifiNameFormatIndex,
                        voWifiCustomCarrierName = voWifiCustomCarrierName,
                        voWifiNamePreview = VoWifiNameFormatManager.preview(
                            formatIndex = voWifiNameFormatIndex,
                            systemCarrierName = selectedSim?.carrierName.orEmpty(),
                            customCarrierName = voWifiCustomCarrierName,
                        ),
                        advancedOptions = advancedOptions,
                        prerequisitesMet = selectedSubId >= 0 && shizukuGranted,
                        actionsEnabled = selectedSubId >= 0 &&
                            shizukuGranted &&
                            actionsAvailable,
                        activeOperationLabel = busyLabel,
                    ),
                    actions = CapabilitiesActions(
                        onSelectSim = { selectSim(it) },
                        onApplyRecommended = { applyRecommendedProfile() },
                        onVolteChange = {
                            volte = it
                            persistCapabilityUi()
                        },
                        onVowifiChange = {
                            vowifi = it
                            persistCapabilityUi()
                        },
                        onVonrChange = {
                            vonr = it
                            persistCapabilityUi()
                        },
                        onVilteChange = {
                            vilte = it
                            persistCapabilityUi()
                        },
                        onUtChange = {
                            ut = it
                            persistCapabilityUi()
                        },
                        onCrossSimChange = {
                            crossSim = it
                            persistCapabilityUi()
                        },
                        onNr5gChange = {
                            nr5g = it
                            persistCapabilityUi()
                        },
                        onSignalStrengthAdjustmentChange = { enabled ->
                            signalStrengthAdjustmentEnabled = enabled
                            if (selectedSubId >= 0) {
                                ConfigStore.setSignalStrengthAdjustmentEnabled(
                                    context,
                                    selectedSubId,
                                    enabled,
                                )
                            }
                        },
                        onWfcModeChange = {
                            wfcMode = it
                            persistCapabilityUi()
                        },
                        onVoWifiNameFormatChange = { index ->
                            voWifiNameFormatIndex = index
                            if (index == null) voWifiCustomCarrierName = ""
                        },
                        onVoWifiCustomCarrierNameChange = { voWifiCustomCarrierName = it },
                        onApplyCore = {
                            val targetSubId = selectedSubId
                            val targetVolte = volte
                            val targetVowifi = vowifi
                            val targetVonr = vonr
                            val targetWfcMode = wfcMode
                            val targetNr5g = nr5g
                            val targetSignal = signalStrengthAdjustmentEnabled
                            persistCapabilityUi(targetSubId)
                            // 实时门禁：UI 上 stale granted 时禁止进入写链路（一加假就绪 + 闪退）。
                            if (!ensurePrivilegedAccess()) return@CapabilitiesActions
                            runOperation(context.getString(R.string.action_apply_core)) {
                                val coreResult = ImsController.applyAll(
                                    context,
                                    targetSubId,
                                    targetVolte,
                                    targetVowifi,
                                    targetVonr,
                                    targetWfcMode,
                                )
                                // 不再 throw：一加等机会把「操作失败」链路放大成体感闪退/中断。
                                if (!coreResult.success) {
                                    return@runOperation coreResult.message
                                }
                                // 同区「5G NR + 信号阈值」并入一键；格子样式由独家页/OneTools 独立应用。
                                val nrResult = ImsController.apply5g(
                                    context,
                                    targetSubId,
                                    targetNr5g,
                                )
                                if (!nrResult.success) {
                                    return@runOperation listOf(
                                        coreResult.message,
                                        nrResult.message,
                                    ).joinToString("\n")
                                }
                                val signalMessage = when {
                                    targetSignal && !targetNr5g -> {
                                        runCatching {
                                            SystemDisplayOverrideManager.applySignalStrengthAdjustment(
                                                context = context,
                                                subId = targetSubId,
                                                enabled = false,
                                                preferenceEnabled = true,
                                            )
                                        }
                                        context.getString(R.string.signal_bar_needs_nr_enabled)
                                    }
                                    else -> runCatching {
                                        SystemDisplayOverrideManager.applySignalStrengthAdjustment(
                                            context = context,
                                            subId = targetSubId,
                                            enabled = targetSignal && targetNr5g,
                                            preferenceEnabled = targetSignal,
                                        )
                                    }.getOrElse { error ->
                                        context.getString(
                                            R.string.signal_bar_system_apply_failed,
                                            OperationErrors.describe(error),
                                        )
                                    }
                                }
                                val message =
                                    "${coreResult.message}\n${nrResult.message}\n$signalMessage"
                                sims.firstOrNull { it.subscriptionId == targetSubId }?.let { sim ->
                                    OneKukuSnapshotStore.save(
                                        context,
                                        OneKukuSnapshotFactory.fromCurrent(context, sim),
                                    )
                                }
                                message
                            }
                        },
                        onApplyExtras = {
                            val targetSubId = selectedSubId
                            val targetVilte = vilte
                            val targetUt = ut
                            val targetCrossSim = crossSim
                            persistCapabilityUi(targetSubId)
                            runOperation(context.getString(R.string.action_apply_extras)) {
                                ImsController.applyCarrierExtras(
                                    context,
                                    targetSubId,
                                    targetVilte,
                                    targetUt,
                                    targetCrossSim,
                                ).message
                            }
                        },
                        onApplyWfcMode = {
                            val targetSubId = selectedSubId
                            val targetWfcMode = wfcMode
                            runOperation(context.getString(R.string.action_set_wfc)) {
                                ImsController.setWfcMode(
                                    context,
                                    targetSubId,
                                    targetWfcMode,
                                ).message
                            }
                        },
                        onApplyVoWifiName = {
                            val targetSubId = selectedSubId
                            val targetFormat = voWifiNameFormatIndex
                            val targetCarrierName = voWifiCustomCarrierName
                            runOperation(
                                label = context.getString(R.string.vowifi_name_apply),
                            ) {
                                VoWifiNameFormatManager.apply(
                                    context = context,
                                    subId = targetSubId,
                                    formatIndex = targetFormat,
                                    customCarrierName = targetCarrierName,
                                )
                            }
                        },
                        onAdvancedOptionsChange = { advancedOptions = it },
                        onApplyAdvancedOptions = {
                            if (ensurePrivilegedAccess()) {
                                val targetSubId = selectedSubId
                                val optionsToApply = advancedOptions
                                runOperation(context.getString(R.string.advanced_apply)) {
                                    PixelImsCompat.applyOptions(
                                        context,
                                        targetSubId,
                                        optionsToApply,
                                    ).message
                                }
                            }
                        },
                        onRestartIms = {
                            val sim = selectedSim
                            if (sim == null) {
                                publish(context.getString(R.string.please_select_sim))
                            } else {
                                requestConfirmation(
                                    title = context.getString(R.string.confirm_restart_ims_title),
                                    message = context.getString(R.string.confirm_restart_ims_message),
                                    confirmLabel = context.getString(R.string.tool_restart_ims),
                                ) {
                                    runOperation(context.getString(R.string.tool_restart_ims)) {
                                        ImsController.restartImsRegistration(
                                            context = context,
                                            subId = sim.subscriptionId,
                                            slotIndex = sim.slotIndex,
                                        ).message
                                    }
                                }
                            }
                        },
                        onFixNetwork = {
                            requestConfirmation(
                                title = context.getString(R.string.confirm_network_fix_title),
                                message = context.getString(R.string.confirm_network_fix_message),
                                confirmLabel = context.getString(R.string.action_continue),
                            ) {
                                runOperation(context.getString(R.string.action_net_fix)) {
                                    ImsController.fixCaptivePortal(context).message
                                }
                            }
                        },
                    ),
                )

                AppDestination.EXPERIMENTAL -> ExperimentalScreen(
                    state = ExperimentalUiState(
                        sims = sims,
                        selectedSubId = selectedSubId,
                        carrierName = carrierName,
                        currentCarrierName = selectedSim?.carrierName.orEmpty(),
                        imsUserAgent = imsUserAgent,
                        simCountryIso = simCountryIso,
                        activeSimCountryIso = activeSimCountryIso,
                        catalogEnabled = actionsAvailable,
                        guardEnabled = guardEnabled,
                        rootPersistEnhance = rootPersistEnhance,
                        rootBootStart = rootBootStart,
                        forceTemporaryOverride = forceTemporaryOverride,
                        systemUpdateShield = systemUpdateShield,
                        rootPersistStatusDetail = RootPersistenceSupport.statusDetail(context),
                        prerequisitesMet = selectedSubId >= 0 && shizukuGranted,
                        actionsEnabled = selectedSubId >= 0 &&
                            shizukuGranted &&
                            actionsAvailable,
                        activeOperationLabel = busyLabel,
                    ),
                    actions = ExperimentalActions(
                        onSelectSim = { selectSim(it) },
                        onCarrierNameChange = {
                            carrierName = it
                            persistIdentityDraft()
                        },
                        onImsUserAgentChange = {
                            imsUserAgent = it
                            persistIdentityDraft()
                        },
                        onApplyIdentity = {
                            val targetSubId = selectedSubId
                            val carrierNameToApply = carrierName
                            val userAgentToApply = imsUserAgent
                            persistIdentityDraft(targetSubId)
                            requestConfirmation(
                                title = context.getString(R.string.confirm_identity_title),
                                message = context.getString(R.string.confirm_identity_message),
                                confirmLabel = context.getString(R.string.action_apply_identity),
                            ) {
                                runOperation(context.getString(R.string.action_apply_identity)) {
                                    ImsController.applyIdentityOverride(
                                        context,
                                        targetSubId,
                                        carrierNameToApply,
                                        userAgentToApply,
                                    ).message
                                }
                            }
                        },
                        onResetCarrierName = {
                            val targetSubId = selectedSubId
                            requestConfirmation(
                                title = context.getString(R.string.confirm_restore_identity_title),
                                message = context.getString(R.string.confirm_restore_identity_message),
                                confirmLabel = context.getString(R.string.action_restore_identity_name),
                            ) {
                                runOperation(
                                    context.getString(R.string.action_restore_identity_name),
                                ) {
                                    ImsController.clearCarrierNameOverride(
                                        context,
                                        targetSubId,
                                    ).message
                                }
                            }
                        },
                        onSimCountryIsoChange = {
                            simCountryIso = it
                            persistSimCountryIsoDraft()
                        },
                        onApplySimCountryIso = {
                            val targetSubId = selectedSubId
                            val isoToApply = simCountryIso
                            persistSimCountryIsoDraft(targetSubId)
                            requestConfirmation(
                                title = context.getString(R.string.confirm_sim_country_title),
                                message = context.getString(
                                    R.string.confirm_sim_country_message,
                                    isoToApply.trim().uppercase(java.util.Locale.ROOT),
                                ),
                                confirmLabel = context.getString(R.string.sim_country_apply),
                            ) {
                                runOperation(context.getString(R.string.sim_country_apply)) {
                                    val result = SimCountryIsoManager.apply(
                                        context,
                                        targetSubId,
                                        isoToApply,
                                    )
                                    if (result.success) {
                                        refreshActiveSimCountryIso(targetSubId)
                                    }
                                    result.message
                                }
                            }
                        },
                        onClearSimCountryIso = {
                            val targetSubId = selectedSubId
                            requestConfirmation(
                                title = context.getString(R.string.confirm_sim_country_clear_title),
                                message = context.getString(R.string.confirm_sim_country_clear_message),
                                confirmLabel = context.getString(R.string.sim_country_clear),
                            ) {
                                runOperation(context.getString(R.string.sim_country_clear)) {
                                    val result = SimCountryIsoManager.clear(context, targetSubId)
                                    if (result.success) {
                                        refreshActiveSimCountryIso(targetSubId)
                                    }
                                    result.message
                                }
                            }
                        },
                        onApplyTiktokFix = {
                            val targetSubId = selectedSubId
                            requestConfirmation(
                                title = context.getString(R.string.confirm_tiktok_title),
                                message = context.getString(R.string.confirm_tiktok_message),
                                confirmLabel = context.getString(R.string.action_continue),
                            ) {
                                runOperation(context.getString(R.string.action_tiktok_fix)) {
                                    val result = SimCountryIsoManager.applyTikTokPreset(
                                        context,
                                        targetSubId,
                                    )
                                    if (result.success) {
                                        simCountryIso = "us"
                                        persistSimCountryIsoDraft(targetSubId)
                                        refreshActiveSimCountryIso(targetSubId)
                                    }
                                    result.message
                                }
                            }
                        },
                        onGuardEnabledChange = { enabled ->
                            guardEnabled = enabled
                            ConfigStore.setGuardEnabled(context, enabled)
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                                    != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                }
                                GuardService.start(context)
                                publish(context.getString(R.string.guard_on))
                            } else {
                                GuardService.stop(context)
                                publish(context.getString(R.string.guard_off))
                            }
                        },
                        onRootPersistEnhanceChange = { enabled ->
                            rootPersistEnhance = enabled
                            RootPersistenceSupport.setEnhanceEnabled(context, enabled)
                            publish(
                                context.getString(
                                    if (enabled) {
                                        R.string.root_persist_on
                                    } else {
                                        R.string.root_persist_off
                                    },
                                ),
                            )
                        },
                        onRootBootStartChange = { enabled ->
                            rootBootStart = enabled
                            ConfigStore.setRootBootStart(context, enabled)
                            publish(
                                context.getString(
                                    if (enabled) {
                                        R.string.root_boot_on
                                    } else {
                                        R.string.root_boot_off
                                    },
                                ),
                            )
                        },
                        onForceTemporaryOverrideChange = { enabled ->
                            forceTemporaryOverride = enabled
                            ConfigStore.setForceTemporaryOverride(context, enabled)
                            publish(
                                context.getString(
                                    if (enabled) {
                                        R.string.force_temporary_on
                                    } else {
                                        R.string.force_temporary_off
                                    },
                                ),
                            )
                        },
                        onSystemUpdateShieldChange = { enabled ->
                            if (!OneKukuManager.isReady()) {
                                publish(context.getString(R.string.system_update_shield_need_channel))
                            } else {
                                systemUpdateShield = enabled
                                SystemUpdateShield.setPreference(context, enabled)
                                runOperation(context.getString(R.string.system_update_shield_title)) {
                                    val result = SystemUpdateShield.applyPreference(context)
                                    if (!result.ok && enabled) {
                                        systemUpdateShield = false
                                        SystemUpdateShield.setPreference(context, false)
                                    }
                                    result.message
                                }
                            }
                        },
                        onOpenApnCatalog = { apnCatalogVisible = true },
                        onApplyExpertValue = { key, value ->
                            if (ensurePrivilegedAccess()) {
                                val targetSubId = selectedSubId
                                requestConfirmation(
                                    title = context.getString(
                                        R.string.advanced_expert_confirm_title,
                                    ),
                                    message = context.getString(
                                        R.string.advanced_expert_confirm_message,
                                        key,
                                        value,
                                    ),
                                    confirmLabel = context.getString(
                                        R.string.advanced_expert_apply,
                                    ),
                                ) {
                                    runOperation(
                                        context.getString(R.string.advanced_expert_apply),
                                    ) {
                                        PixelImsCompat.applyExpertValue(
                                            context,
                                            targetSubId,
                                            key,
                                            value,
                                        ).message
                                    }
                                }
                            }
                        },
                    ),
                )

                AppDestination.DIAGNOSTICS -> DiagnosticsScreen(
                    state = DiagnosticsUiState(
                        sims = sims,
                        selectedSubId = selectedSubId,
                        log = log,
                        reapplyStatus = reapplyStatus,
                        prerequisitesMet = selectedSubId >= 0 && shizukuGranted,
                        actionsEnabled = selectedSubId >= 0 &&
                            shizukuGranted &&
                            actionsAvailable,
                    ),
                    actions = DiagnosticsActions(
                        onSelectSim = { selectSim(it) },
                        onHealthCheck = {
                            withContext(Dispatchers.IO) {
                                val result = SafetyGuard.healthCheck(context, selectedSubId)
                                val health = context.getString(
                                    if (result.allHealthy) {
                                        R.string.health_ok
                                    } else {
                                        R.string.health_abnormal
                                    },
                                )
                                context.getString(
                                    R.string.health_result,
                                    health,
                                    result.detail,
                                )
                            }
                        },
                        onCheckEpdg = {
                            withContext(Dispatchers.IO) {
                                val sim = selectedSim
                                    ?: return@withContext context.getString(R.string.please_select_sim)
                                describeEpdg(
                                    context,
                                    EpdgChecker.check(context, sim.mcc, sim.mnc),
                                )
                            }
                        },
                        onQueryIms = {
                            withContext(Dispatchers.IO) {
                                sims.joinToString("\n\n") { sim ->
                                    context.getString(
                                        R.string.ims_sim_header,
                                        sim.slotIndex + 1,
                                        sim.carrierName,
                                        sim.subscriptionId,
                                    ) + "\n" +
                                        ImsController.queryImsStatus(
                                            context,
                                            sim.subscriptionId,
                                        ).rawText
                                }
                            }
                        },
                        onDumpConfig = {
                            withContext(Dispatchers.IO) {
                                ImsController.dumpCarrierConfig(context, selectedSubId)
                            }
                        },
                        onCopyLog = {
                            val clipboard =
                                context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText(
                                    context.getString(R.string.log_title),
                                    log,
                                ),
                            )
                            publish(context.getString(R.string.log_copied))
                        },
                        onClearLog = {
                            log = context.getString(R.string.log_ready)
                            DiagFileLogger.clearSession()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.log_cleared),
                                )
                            }
                        },
                        onExportDetailLog = {
                            val file = DiagFileLogger.exportBundle(context)
                            if (file == null) {
                                publish(context.getString(R.string.log_detail_export_failed))
                            } else {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(
                                        Intent.EXTRA_SUBJECT,
                                        context.getString(R.string.log_detail_share_title),
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(
                                            share,
                                            context.getString(R.string.log_detail_share_title),
                                        ),
                                    )
                                    publish(context.getString(R.string.log_detail_exported))
                                }.onFailure {
                                    // 无分享目标时至少复制全文到剪贴板
                                    val clipboard =
                                        context.getSystemService(ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(
                                        ClipData.newPlainText(
                                            context.getString(R.string.log_detail_share_title),
                                            DiagFileLogger.buildExportText(context),
                                        ),
                                    )
                                    publish(context.getString(R.string.log_copied))
                                }
                            }
                        },
                        onReapply = {
                            if (ensurePrivilegedAccess()) {
                                val targetSubId = selectedSubId
                                runOperation(
                                    label = context.getString(R.string.action_reapply),
                                    operation = {
                                        ReapplyManager.reapply(
                                            context,
                                            ReapplyTrigger.MANUAL,
                                            targetSubId,
                                        ).message
                                    },
                                    onComplete = {
                                        reapplyStatus =
                                            ConfigStore.lastReapplyStatus(context)
                                    },
                                )
                            }
                        },
                        onExportFullConfig = {
                            if (ensurePrivilegedAccess()) {
                                runOperation(
                                    context.getString(R.string.advanced_export_action),
                                ) {
                                    PixelImsCompat.dumpFullConfig(context, selectedSubId)
                                }
                            }
                        },
                        onRunUserFacingCheck = { targetSubId ->
                            withContext(Dispatchers.IO) {
                                OneClickDiagnosticsManager.runUserFacingCheck(context, targetSubId)
                            }
                        },
                        onRunDiagnosticsCheck = { targetSubId ->
                            withContext(Dispatchers.IO) {
                                OneClickDiagnosticsManager.runCheck(context, targetSubId)
                            }
                        },
                        onAutoFixDiagnosticsItem = { targetSubId, item ->
                            OneClickDiagnosticsManager.autoFix(context, targetSubId, item)
                        },
                    ),
                )

                AppDestination.SPONSOR -> SponsorScreen(
                    onPublish = ::publish,
                )

                AppDestination.SETTINGS -> SettingsScreen(
                    state = SettingsUiState(
                        themeMode = themeMode,
                        dynamicColor = dynamicColor,
                        checkingUpdate = checkingUpdate,
                        updateInfo = updateInfo,
                    ),
                    actions = SettingsActions(
                        onThemeModeChange = onThemeModeChange,
                        onDynamicColorChange = onDynamicColorChange,
                        onOpenMembership = { membershipPaywallVisible = true },
                        onCheckUpdate = {
                            if (!checkingUpdate) {
                                checkingUpdate = true
                                scope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            UpdateChecker.checkLatest(context)
                                        }
                                    }
                                    checkingUpdate = false
                                    result.onSuccess { info ->
                                        updateInfo = info
                                        publish(info.message)
                                    }.onFailure { error ->
                                        publish(
                                            context.getString(
                                                R.string.operation_failed,
                                                OperationErrors.describe(error),
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                        onDownloadUpdate = { info ->
                            UpdateChecker.downloadAndInstall(
                                context,
                                info.downloadUrl,
                                info.latestVersion,
                            )
                            publish(
                                context.getString(
                                    R.string.download_started,
                                    info.latestVersion,
                                ),
                            )
                        },
                    ),
                )
            }
        }

    if (apnCatalogVisible) {
        ApnCatalogDialog(
            sim = selectedSim,
            sims = sims,
            selectedSubId = selectedSubId,
            onSelectSim = { selectSim(it) },
            onDismiss = { apnCatalogVisible = false },
            onCopy = { profile ->
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                val clip = ClipData.newPlainText(
                    context.getString(R.string.apn_catalog_title),
                    profile.asClipboardText(context),
                ).apply {
                    // APN 可能含共享用户名/密码，阻止系统剪贴板浮层直接预览明文。
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                }
                clipboard?.setPrimaryClip(clip)
                publish(context.getString(R.string.apn_catalog_copied))
            },
            onApply = { profile ->
                apnCatalogVisible = false
                val sim = selectedSim
                if (sim == null) {
                    publish(context.getString(R.string.please_select_sim))
                } else if (ensurePrivilegedAccess()) {
                    requestConfirmation(
                        title = context.getString(R.string.confirm_apn_title),
                        message = context.getString(
                            R.string.apn_catalog_apply_target,
                            sim.slotIndex + 1,
                            com.oneims.app.core.formatCarrierShortName(sim.carrierName),
                        ) + "\n\n" + (profile?.let { selected ->
                            context.getString(
                                R.string.confirm_apn_profile_message,
                                selected.carrier.ifBlank { selected.apn },
                                selected.apn,
                                selected.protocol.ifBlank { "—" },
                                selected.source,
                            )
                        } ?: context.getString(R.string.confirm_apn_message)),
                        confirmLabel = context.getString(R.string.apn_catalog_apply_ims),
                    ) {
                        runOperation(context.getString(R.string.tool_apn)) {
                            ImsController.createImsApn(
                                context = context,
                                subId = sim.subscriptionId,
                                carrierId = sim.carrierId,
                                mcc = sim.mcc,
                                mnc = sim.mnc,
                                profile = profile,
                            ).message
                        }
                    }
                }
            },
        )
    }

    confirmation?.let { request ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(request.title) },
            text = { Text(request.message) },
            confirmButton = {
                OneImsPrimaryButton(
                    text = request.confirmLabel,
                    onClick = {
                        confirmation = null
                        request.onConfirm()
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) {
                    Text(context.getString(R.string.action_cancel))
                }
            },
        )
    }

    if (coreMissingDialogVisible) {
        AlertDialog(
            onDismissRequest = { coreMissingDialogVisible = false },
            title = { Text(context.getString(R.string.onekuku_core_missing_title)) },
            text = {
                Text(
                    text = context.getString(R.string.onekuku_core_missing_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                OneImsPrimaryButton(
                    text = context.getString(R.string.onekuku_core_missing_install),
                    onClick = {
                        coreMissingDialogVisible = false
                        prepareOneKukuCore()
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { coreMissingDialogVisible = false }) {
                    Text(context.getString(R.string.action_cancel))
                }
            },
        )
    }

    // 默认流程禁止 App 内六位码弹窗；配对只走通知栏 RemoteInput。
}
}

private fun describeEpdg(
    context: android.content.Context,
    result: EpdgResult,
): String = when (result) {
    is EpdgResult.Reachable -> context.getString(R.string.epdg_reachable, result.ip)
    is EpdgResult.DnsFail -> context.getString(R.string.epdg_dns_fail)
    is EpdgResult.PortUnreachable ->
        context.getString(R.string.epdg_port_unreachable, result.ip)
    is EpdgResult.Unavailable -> context.getString(R.string.epdg_unavailable, result.reason)
}

private fun ApnCatalogEntry.asClipboardText(context: android.content.Context): String =
    context.getString(
        R.string.apn_catalog_clipboard,
        carrier.ifBlank { apn },
        countryCode,
        mcc + mnc,
        apn,
        types.ifBlank { "*" },
        protocol.ifBlank { "—" },
        user.ifBlank { "—" },
        password.ifBlank { "—" },
        source,
    )
