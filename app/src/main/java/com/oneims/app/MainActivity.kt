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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.core.util.Consumer
import com.oneims.app.core.CarrierProfiles
import com.oneims.app.core.ApnCatalogEntry
import com.oneims.app.core.CompatChecker
import com.oneims.app.core.ConfigStore
import com.oneims.app.core.DodoPaySupportClient
import com.oneims.app.core.DeviceInfo
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
import com.oneims.app.core.SafetyGuard
import com.oneims.app.core.DataSimSwitchManagerImpl
import com.oneims.app.core.DataSimSwitchResult
import com.oneims.app.core.OneClickDiagnosticsManager
import com.oneims.app.core.QuickSettingsTileHelper
import com.oneims.app.core.SimCardInfo
import com.oneims.app.core.ShizukuSetupHelper
import com.oneims.app.core.SimpleFiveGDisplayConfig
import com.oneims.app.core.SystemDisplayOverrideManager
import com.oneims.app.core.SignalBarSystemStyleManager
import com.oneims.app.core.SimCountryIsoManager
import com.oneims.app.core.UpdateChecker
import com.oneims.app.core.VoWifiNameFormatManager
import com.oneims.app.model.EpdgResult
import com.oneims.app.model.SimInfo
import com.oneims.app.model.UpdateInfo
import com.oneims.app.model.WfcMode
import com.oneims.app.core.OneKukuManager
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
import com.oneims.app.onekuku.OneKukuCommand
import com.oneims.app.onekuku.OneKukuCommandDispatcher
import com.oneims.app.onekuku.OneKukuHiddenRunner
import com.oneims.app.onekuku.OneKukuRunnerState
import com.oneims.app.onekuku.OneKukuSnapshotStore
import com.oneims.app.core.OneKukuBootRestoreService
import com.oneims.app.ui.OneKukuCardPolicy
import com.oneims.app.ui.OneKukuHomeTools
import com.oneims.app.ui.SettingsActions
import com.oneims.app.ui.SettingsScreen
import com.oneims.app.ui.SettingsUiState
import com.oneims.app.ui.SponsorScreen
import com.oneims.app.ui.ThemeMode
import com.oneims.app.ui.theme.OneImsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val MAX_DIAGNOSTIC_LOG_CHARS = 256 * 1024
private const val MAX_DIAGNOSTIC_ENTRY_CHARS = 192 * 1024

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

            OneImsTheme(
                darkTheme = darkTheme,
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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var destination by remember { mutableStateOf(AppDestination.HOME) }
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
    var fiveGDisplayConfig by remember {
        mutableStateOf(ConfigStore.fiveGDisplayConfig(context))
    }
    var signalBarDisplayMode by remember {
        mutableStateOf(ConfigStore.signalBarDisplayMode(context, selectedSubId))
    }
    var signalStrengthAdjustmentEnabled by remember {
        mutableStateOf(ConfigStore.signalStrengthAdjustmentEnabled(context, selectedSubId))
    }
    var voWifiNameFormatIndex by remember { mutableStateOf<Int?>(null) }
    var voWifiCustomCarrierName by remember { mutableStateOf("") }
    var activeDataSims by remember { mutableStateOf(emptyList<SimCardInfo>()) }

    fun refreshDataSimStatus() {
        scope.launch {
            activeDataSims = withContext(Dispatchers.IO) {
                DataSimSwitchManagerImpl.getActiveSims(context)
            }
        }
    }

    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var phonePermissionResultCount by remember { mutableIntStateOf(0) }

    val selectedSim = sims.firstOrNull { it.subscriptionId == selectedSubId }
    val actionsAvailable = busyLabel == null
    var bootUiHint by remember {
        mutableStateOf(OneKukuBootRestoreStore.readHint(context))
    }
    val bootForceInactive = bootUiHint == OneKukuBootUiHint.NEEDS_ACTIVATION
    val oneKukuState = OneKukuCardPolicy.resolve(
        serviceReady = shizukuRunning && shizukuGranted && !bootForceInactive,
        isExecuting = oneKukuRestoring ||
            bootUiHint == OneKukuBootUiHint.RESTORING ||
            OneKukuHiddenRunner.currentState() == OneKukuRunnerState.EXECUTING ||
            OneKukuHiddenRunner.currentState() == OneKukuRunnerState.STARTING,
        taskComplete = oneKukuTaskComplete || bootUiHint == OneKukuBootUiHint.RESTORE_COMPLETE,
    )
    val oneKukuDetailOverride = when {
        bootUiHint == OneKukuBootUiHint.NO_SNAPSHOT_SLEEPING ||
            OneKukuBootRestoreStore.shouldShowNoSnapshotNote(context) ->
            context.getString(R.string.onekuku_detail_no_snapshot)
        else -> null
    }

    LaunchedEffect(Unit) {
        if (ConfigStore.isOneKukuBootAutoCheck(context)) {
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
            kotlinx.coroutines.delay(2_000L)
            val latest = OneKukuBootRestoreStore.readHint(context)
            if (latest != bootUiHint) {
                bootUiHint = latest
            }
        }
    }

    LaunchedEffect(bootUiHint) {
        when (bootUiHint) {
            OneKukuBootUiHint.RESTORING -> {
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

    fun selectSim(subId: Int) {
        selectedSubId = subId
        ConfigStore.setSelectedSubId(context, subId)
    }

    fun refreshAll(showFeedback: Boolean = false) {
        sims = ImsController.listSims(context)
        if (sims.none { it.subscriptionId == selectedSubId }) {
            val restored = ConfigStore.getSelectedSubId(context)
            val fallback = sims.firstOrNull { it.subscriptionId == restored }?.subscriptionId
                ?: sims.firstOrNull()?.subscriptionId
                ?: -1
            selectSim(fallback)
        }
        shizukuRunning = OneKukuManager.isRunning()
        shizukuGranted = OneKukuManager.isGranted()
        deviceInfo = DeviceInfo.summary(context)
        if (showFeedback) {
            publish(context.getString(R.string.refreshed))
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

    fun switchDataSim(targetSubId: Int) {
        val sim = activeDataSims.firstOrNull { it.subId == targetSubId }
            ?: run {
                publish(context.getString(R.string.data_switch_invalid_target))
                return
            }
        if (targetSubId == DataSimSwitchManagerImpl.getDefaultDataSubId()) {
            publish(context.getString(R.string.data_switch_already_current))
            return
        }
        if (activeDataSims.size <= 1) {
            publish(context.getString(R.string.data_switch_single_sim))
            return
        }
        val executeSwitch: () -> Unit = {
            if (busyLabel != null) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.operation_already_running),
                    )
                }
            } else {
                scope.launch {
                    busyLabel = context.getString(R.string.data_switch_switching)
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            DataSimSwitchManagerImpl.switchDefaultDataSubId(context, targetSubId)
                        }.getOrElse { error ->
                            DataSimSwitchResult.Failed(
                                error.message ?: context.getString(R.string.operation_unknown_error),
                            )
                        }
                    }
                    busyLabel = null
                    when (result) {
                        is DataSimSwitchResult.Success -> {
                            refreshDataSimStatus()
                            publish(
                                result.warning ?: context.getString(
                                    R.string.data_switch_success,
                                    sim.slotIndex + 1,
                                ),
                            )
                        }
                        is DataSimSwitchResult.Failed -> {
                            publish(
                                context.getString(
                                    R.string.data_switch_failed,
                                    result.reason,
                                ),
                            )
                        }
                    }
                }
            }
        }
        requestConfirmation(
            title = context.getString(
                R.string.data_switch_confirm_title,
                sim.slotIndex + 1,
                sim.carrierName,
            ),
            message = context.getString(R.string.data_switch_confirm_message),
            confirmLabel = context.getString(R.string.data_switch_confirm_action),
            onConfirm = executeSwitch,
        )
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
        signalBarDisplayMode = ConfigStore.signalBarDisplayMode(context, selectedSubId)
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

    // 应用内切卡和控制中心磁贴都以同一份活动 SIM 列表为依据。
    LaunchedEffect(sims) {
        activeDataSims = withContext(Dispatchers.IO) {
            DataSimSwitchManagerImpl.getActiveSims(context)
        }
    }

    DisposableEffect(Unit) {
        val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
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
        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            refreshAll()
        }
        val binderDeadListener = Shizuku.OnBinderDeadListener {
            shizukuRunning = false
            shizukuGranted = false
            oneKukuTaskComplete = false
            oneKukuRestoring = false
        }

        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceivedListener) }
        runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }

        onDispose {
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
            runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
            runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
        }
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
                        deviceInfo = deviceInfo,
                        sims = sims,
                        selectedSubId = selectedSubId,
                        selectedSim = selectedSim,
                        actionsEnabled = actionsAvailable,
                        reapplyStatus = reapplyStatus,
                        bootAutoCheck = oneKukuBootAutoCheck,
                        autoSleep = oneKukuAutoSleep,
                        oneKukuDetailOverride = oneKukuDetailOverride,
                    ),
                    actions = HomeActions(
                        onSelectSim = { selectSim(it) },
                        onActivateOneKuku = {
                            when {
                                !OneKukuManager.isRunning() -> {
                                    ShizukuSetupHelper.openShizukuApp(context)
                                    publish(context.getString(R.string.onekuku_msg_need_prepare))
                                }
                                OneKukuManager.isGranted() -> {
                                    refreshAll()
                                    publish(context.getString(R.string.onekuku_msg_already_active))
                                }
                                else -> {
                                    OneKukuManager.requestActivation()
                                    publish(context.getString(R.string.onekuku_msg_permission_requested))
                                }
                            }
                        },
                        onRestoreCallConfig = {
                            shizukuRunning = OneKukuManager.isRunning()
                            shizukuGranted = OneKukuManager.isGranted()
                            val targetSubId = selectedSubId
                            val simReady = targetSubId >= 0 &&
                                sims.any { it.subscriptionId == targetSubId }
                            when {
                                !simReady ->
                                    publish(context.getString(R.string.onekuku_restore_need_sim))
                                !OneKukuHomeTools.hasConfigSnapshot(context, targetSubId) ->
                                    publish(context.getString(R.string.onekuku_restore_no_snapshot))
                                !shizukuRunning || !shizukuGranted -> {
                                    oneKukuTaskComplete = false
                                    requestConfirmation(
                                        title = context.getString(
                                            R.string.onekuku_restore_need_active_title,
                                        ),
                                        message = context.getString(
                                            R.string.onekuku_restore_need_active_body,
                                        ),
                                        confirmLabel = context.getString(
                                            R.string.onekuku_action_activate,
                                        ),
                                    ) {
                                        when {
                                            !OneKukuManager.isRunning() -> {
                                                ShizukuSetupHelper.openShizukuApp(context)
                                                publish(
                                                    context.getString(
                                                        R.string.onekuku_msg_need_prepare,
                                                    ),
                                                )
                                            }
                                            OneKukuManager.isGranted() -> {
                                                refreshAll()
                                                publish(
                                                    context.getString(
                                                        R.string.onekuku_msg_already_active,
                                                    ),
                                                )
                                            }
                                            else -> {
                                                OneKukuManager.requestActivation()
                                                publish(
                                                    context.getString(
                                                        R.string.onekuku_msg_permission_requested,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                                busyLabel != null ->
                                    publish(context.getString(R.string.operation_already_running))
                                else -> {
                                    val restoreOutcome =
                                        arrayOf(OneKukuHomeTools.RestoreOutcome.FAILURE)
                                    oneKukuTaskComplete = false
                                    oneKukuRestoring = true
                                    runOperation(
                                        label = context.getString(R.string.onekuku_busy_restore),
                                        onComplete = {
                                            oneKukuRestoring = false
                                            reapplyStatus =
                                                ConfigStore.lastReapplyStatus(context)
                                            when (restoreOutcome[0]) {
                                                OneKukuHomeTools.RestoreOutcome.SUCCESS,
                                                OneKukuHomeTools.RestoreOutcome.PARTIAL,
                                                -> {
                                                    oneKukuTaskComplete = true
                                                    OneKukuBootRestoreStore.writeHint(
                                                        context,
                                                        OneKukuBootUiHint.RESTORE_COMPLETE,
                                                    )
                                                    bootUiHint = OneKukuBootUiHint.RESTORE_COMPLETE
                                                }
                                                OneKukuHomeTools.RestoreOutcome.FAILURE ->
                                                    oneKukuTaskComplete = false
                                            }
                                        },
                                    ) {
                                        // 旧 ConfigStore 快照迁移到 OneKukuSnapshotStore，避免恢复空跑。
                                        if (OneKukuSnapshotStore.load(context, targetSubId) == null) {
                                            sims.firstOrNull {
                                                it.subscriptionId == targetSubId
                                            }?.let { sim ->
                                                OneKukuSnapshotStore.save(
                                                    context,
                                                    OneKukuSnapshotFactory.fromCurrent(
                                                        context,
                                                        sim,
                                                    ),
                                                )
                                            }
                                        }
                                        // OneKuku 白名单恢复：按快照重放，不走通用 shell / APN / 切卡。
                                        val result = OneKukuCommandDispatcher.dispatch(
                                            context = context,
                                            command = OneKukuCommand.RESTORE_ALL_CALL_CONFIGS,
                                            subId = targetSubId,
                                        )
                                        val detailOk = result.detail.values.count { it }
                                        val detailTotal = result.detail.size
                                        val outcome = when {
                                            result.success &&
                                                detailTotal > 0 &&
                                                detailOk == detailTotal ->
                                                OneKukuHomeTools.RestoreOutcome.SUCCESS
                                            result.success ->
                                                OneKukuHomeTools.RestoreOutcome.PARTIAL
                                            else -> OneKukuHomeTools.RestoreOutcome.FAILURE
                                        }
                                        restoreOutcome[0] = outcome
                                        val reason = OneKukuHomeTools.sanitizeUserText(
                                            result.message,
                                        ).let { raw ->
                                            if (raw.contains(OneKukuSnapshotStore.MSG_NO_MATCHING_SIM)) {
                                                context.getString(R.string.onekuku_restore_no_matching_sim)
                                            } else {
                                                raw
                                            }
                                        }
                                        when (outcome) {
                                            OneKukuHomeTools.RestoreOutcome.SUCCESS ->
                                                context.getString(
                                                    R.string.onekuku_restore_toast_success,
                                                )
                                            OneKukuHomeTools.RestoreOutcome.PARTIAL ->
                                                context.getString(
                                                    R.string.onekuku_restore_toast_partial,
                                                )
                                            OneKukuHomeTools.RestoreOutcome.FAILURE ->
                                                context.getString(
                                                    R.string.onekuku_restore_toast_failed,
                                                    reason.ifBlank {
                                                        context.getString(
                                                            R.string.onekuku_history_no_reason,
                                                        )
                                                    },
                                                )
                                        }
                                    }
                                }
                            }
                        },
                        onCheckOneKukuStatus = {
                            oneKukuTaskComplete = false
                            refreshAll()
                            publish(context.getString(R.string.onekuku_msg_status_ok))
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
                                            val fiveG = ConfigStore.fiveGDisplayConfig(context)
                                            val fiveGLine = context.getString(
                                                R.string.onekuku_status_5g,
                                                if (fiveG.enabled) {
                                                    context.getString(R.string.onekuku_value_on)
                                                } else {
                                                    context.getString(R.string.onekuku_value_off)
                                                },
                                            )
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
                                                append(fiveGLine)
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
                    ),
                )

                AppDestination.CAPABILITIES -> CapabilitiesScreen(
                    state = CapabilitiesUiState(
                        sims = sims,
                        selectedSubId = selectedSubId,
                        selectedSim = selectedSim,
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
                        onApplyRecommended = {
                            val sim = selectedSim
                            if (!ensurePrivilegedAccess()) {
                                Unit
                            } else if (sim == null) {
                                publish(context.getString(R.string.please_select_sim))
                            } else {
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
                        },
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
                            runOperation(context.getString(R.string.action_apply_core)) {
                                val coreResult = ImsController.applyAll(
                                    context,
                                    targetSubId,
                                    targetVolte,
                                    targetVowifi,
                                    targetVonr,
                                    targetWfcMode,
                                )
                                if (!coreResult.success) {
                                    throw IllegalStateException(coreResult.message)
                                }
                                // 同区「5G NR + 信号阈值」并入一键；格子样式由独家页独立应用。
                                val nrResult = ImsController.apply5g(
                                    context,
                                    targetSubId,
                                    targetNr5g,
                                )
                                if (!nrResult.success) {
                                    throw IllegalStateException(nrResult.message)
                                }
                                val signalMessage = when {
                                    targetSignal && !targetNr5g -> {
                                        SystemDisplayOverrideManager.applySignalStrengthAdjustment(
                                            context = context,
                                            subId = targetSubId,
                                            enabled = false,
                                            preferenceEnabled = true,
                                        )
                                        context.getString(R.string.signal_bar_needs_nr_enabled)
                                    }
                                    else -> SystemDisplayOverrideManager.applySignalStrengthAdjustment(
                                        context = context,
                                        subId = targetSubId,
                                        enabled = targetSignal && targetNr5g,
                                        preferenceEnabled = targetSignal,
                                    )
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
                        fiveGDisplayConfig = fiveGDisplayConfig,
                        signalBarDisplayMode = signalBarDisplayMode,
                        nr5g = nr5g,
                        activeDataSims = activeDataSims,
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
                        onOpenApnCatalog = { apnCatalogVisible = true },
                        onFiveGDisplayConfigChange = { config: SimpleFiveGDisplayConfig ->
                            fiveGDisplayConfig = config
                        },
                        onApplyFiveGDisplay = {
                            val targetSubId = selectedSubId
                            val configToApply = fiveGDisplayConfig
                            runOperation(context.getString(R.string.five_g_apply)) {
                                SystemDisplayOverrideManager.applyFiveGDisplay(
                                    context = context,
                                    subId = targetSubId,
                                    config = configToApply,
                                )
                            }
                        },
                        onSignalBarDisplayModeChange = { mode ->
                            signalBarDisplayMode = mode
                            if (selectedSubId >= 0) {
                                ConfigStore.setSignalBarDisplayMode(
                                    context,
                                    selectedSubId,
                                    mode,
                                )
                            }
                        },
                        onApplySignalBarStyle = {
                            val targetSubId = selectedSubId
                            val targetMode = signalBarDisplayMode
                            runOperation(context.getString(R.string.signal_bar_style_apply)) {
                                // 系统全局尝试：只写 UI 当前 selectedSubId，不默认卡1/数据卡/slot0。
                                SignalBarSystemStyleManager.apply(
                                    context = context,
                                    subId = targetSubId,
                                    mode = targetMode,
                                )
                            }
                        },
                        onRefreshDataSims = { refreshDataSimStatus() },
                        onSwitchDataSim = { targetSubId -> switchDataSim(targetSubId) },
                        onOpenTileSettings = {
                            if (!QuickSettingsTileHelper.openTileEditor(context)) {
                                publish(context.getString(R.string.qs_tile_manual_guide))
                            }
                        },
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
                            runOperation(context.getString(R.string.tool_health)) {
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
                            val sim = selectedSim
                            if (sim == null) {
                                publish(context.getString(R.string.please_select_sim))
                            } else {
                                runOperation(context.getString(R.string.tool_epdg)) {
                                    describeEpdg(
                                        context,
                                        EpdgChecker.check(context, sim.mcc, sim.mnc),
                                    )
                                }
                            }
                        },
                        onQueryIms = {
                            runOperation(context.getString(R.string.tool_diag)) {
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
                            runOperation(context.getString(R.string.tool_config)) {
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
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.log_cleared),
                                )
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
                        onOpenShizuku = {
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
                        onCopySetupCommand = {
                            ShizukuSetupHelper.copyToClipboard(
                                context,
                                context.getString(R.string.app_name),
                                context.getString(R.string.termux_hint),
                            )
                            publish(context.getString(R.string.log_cmd_copied))
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
                        onOpenTileSettings = {
                            if (!QuickSettingsTileHelper.openTileEditor(context)) {
                                publish(context.getString(R.string.qs_tile_manual_guide))
                            }
                        },
                        onOpenSupportAuthor = {
                            destination = AppDestination.SPONSOR
                        },
                    ),
                )
            }
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
                            sim.subscriptionId,
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
