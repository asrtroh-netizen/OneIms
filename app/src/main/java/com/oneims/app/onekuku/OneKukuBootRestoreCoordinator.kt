package com.oneims.app.onekuku

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.os.UserManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.oneims.app.MainActivity
import com.oneims.app.R
import com.oneims.app.core.CarrierConfigKeys
import com.oneims.app.core.CarrierConfigOverrideWriter
import com.oneims.app.core.ChannelLine
import com.oneims.app.core.ConfigStore
import com.oneims.app.core.ImsController
import com.oneims.app.core.OneKukuAdbEnvironment
import com.oneims.app.core.OneKukuAdbMdns
import com.oneims.app.core.OneKukuEmbeddedAdbActivator
import com.oneims.app.core.OneKukuHostServerBootstrap
import com.oneims.app.core.OneKukuManager
import com.oneims.app.core.OneKukuMiniAdbClient
import com.oneims.app.core.OneKukuPairingNotification
import com.oneims.app.core.OneKukuPrivilegeBridgeImpl
import com.oneims.app.core.ReapplyManager
import com.oneims.app.core.ReapplyTrigger
import com.oneims.app.core.ShizukuSetupHelper
import com.oneims.app.core.SystemUpdateShield
import com.oneims.app.core.privilege.ChannelEngine
import com.oneims.app.core.privilege.PrivilegeBridges
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 重启后自动检查/恢复编排（不关数据、不切卡、不飞行、不碰 radio、不恢复 APN）。
 */
object OneKukuBootRestoreCoordinator {
    private const val TAG = "OneIMS-OneKuku"
    private const val STABLE_WAIT_MS = 2_000L
    /** SIM 已稳定后再短等，给 telephony/IMS 收口。 */
    private const val POST_READY_DELAY_MS = 2_500L
    /** OneLink：配置不依赖内嵌 ADB，SIM 稳定后少等一会。 */
    private const val POST_READY_DELAY_SHIZUKU_MS = 800L
    private const val SIM_POLL_MS = 1_500L
    private const val SIM_WAIT_MAX_MS = 90_000L
    /**
     * 开机已配对：等系统连上记住的 Wi‑Fi。
     * 60s 会把「激活中」钉很久；超时改回 WAITING_WIFI 由前台/下次编排再试。
     */
    private const val BOOT_WIFI_WAIT_MS = 20_000L
    /** OneLink：Wi‑Fi 仅作 Shizuku 晚起兜底，勿先空等堵死 binder。 */
    private const val BOOT_WIFI_WAIT_SHIZUKU_MS = 12_000L
    /** 直连失败后刚打开无线调试，短等端口起来即可，不必空等 8s。 */
    private const val POST_WIRELESS_ENABLE_MS = 2_500L
    /** 已配对且 adb_wifi 已开：仍要等 tcpip/TLS 口真正 LISTEN（重启冷窗）。 */
    private const val PAIRED_ALREADY_ON_PORT_WAIT_MS = 35_000L
    /** 已配对误报要码时的额外重试次数（每次间隔见下）。 */
    private const val PAIRED_NEED_CODE_RETRIES = 3
    private const val PAIRED_NEED_CODE_RETRY_GAP_MS = 5_000L
    /**
     * OneLink：先等 binder；失败后再等 Wi‑Fi + 第二段 binder。
     * 旧逻辑先卡满 Wi‑Fi 再等 binder，Shizuku 已 Active 也会被拖慢。
     */
    private const val BOOT_BRIDGE_WAIT_EARLY_MS = 12_000L
    private const val BOOT_BRIDGE_WAIT_AFTER_WIFI_MS = 12_000L
    private const val CHANNEL = "oneims_boot_restore"
    private const val NOTIF_ID = 1002

    /** [ensureOneKukuReadyForBoot] 结果：区分 Wi‑Fi 晚到（可再试）与真需手点。 */
    private enum class BootReady {
        READY,
        WAITING_WIFI,
        NEED_USER,
    }

    suspend fun run(context: Context) {
        if (!ConfigStore.isOneKukuBootAutoCheck(context)) {
            Log.i(TAG, "boot auto-check disabled")
            return
        }
        if (OneKukuBootRestoreStore.hasAttemptedThisBoot(context)) {
            Log.i(TAG, "boot restore already attempted this boot")
            return
        }

        OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)

        if (!waitUntilUnlocked(context)) {
            Log.i(TAG, "user not unlocked yet")
            return
        }

        // Shizuku 体感：通道不依赖 SIM，先静默激活，打开 App 时尽量已就绪。
        // SIM 稳定 / 配置恢复放后面，避免「等卡」拖住通道。
        when (val ready = ensureOneKukuReadyForBoot(context)) {
            BootReady.WAITING_WIFI -> {
                OneKukuBootRestoreStore.clearAttemptedThisBoot(context)
                OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.WAITING_WIFI)
                Log.i(TAG, "boot: waiting Wi‑Fi before SIM/config, will retry when STA connects")
                return
            }
            BootReady.NEED_USER -> {
                // 已配对冷窗失败：勿占「本开机已尝试」，否则 USER_PRESENT/Watchdog 重试无法再重放。
                OneKukuBootRestoreStore.clearAttemptedThisBoot(context)
                Log.w(TAG, "OneKuku unavailable before capability reapply (cleared attempted)")
            }
            BootReady.READY -> {
                reapplyLastCapabilityProfileAssumingReady(context)
                applySystemUpdateShieldIfEnabled(context)
                // 先把通道就绪写进 hint，打开 App 不必再等 SIM/配置段。
                OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.READY_SLEEPING)
            }
        }

        if (!waitUntilSimsStable(context)) {
            Log.i(TAG, "SIM not ready/stable; channel may already be ready")
            // 通道已就绪但 SIM 未稳：prefs 重放可能已跑过；不 markAttempted，
            // 便于 Wi‑Fi / 打开 App 再 enqueue 把快照段跑完，避免本开机永久占位漏恢复。
            if (OneKukuManager.isReady()) {
                OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.READY_SLEEPING)
                sleepAfterBootConfig(context)
            }
            return
        }

        delay(
            if (ChannelLine.usesShizuku) POST_READY_DELAY_SHIZUKU_MS else POST_READY_DELAY_MS,
        )

        // 延迟后再占一次「本开机」名额，避免过早标记导致永远不跑
        if (OneKukuBootRestoreStore.hasAttemptedThisBoot(context)) return
        OneKukuBootRestoreStore.markAttemptedThisBoot(context)

        // 临时 CarrierConfig 覆盖在重启后会丢：通道已尽量就绪，再按快照恢复。
        when (val ready = ensureOneKukuReadyForBoot(context)) {
            BootReady.WAITING_WIFI -> {
                // Wi‑Fi 未就绪：清占位，连上 STA 后 BootReceiver 再 enqueue。
                OneKukuBootRestoreStore.clearAttemptedThisBoot(context)
                OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.WAITING_WIFI)
                Log.i(TAG, "boot: waiting Wi‑Fi, will retry when STA connects")
                return
            }
            BootReady.NEED_USER -> {
                OneKukuBootRestoreStore.clearAttemptedThisBoot(context)
                Log.w(TAG, "OneKuku unavailable before capability reapply (cleared attempted)")
            }
            BootReady.READY -> {
                reapplyLastCapabilityProfileAssumingReady(context)
                applySystemUpdateShieldIfEnabled(context)
            }
        }

        val snapshots = OneKukuSnapshotStore.loadAll(context)
        if (snapshots.isEmpty()) {
            OneKukuBootRestoreStore.setNoSnapshotNote(context, true)
            OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.NO_SNAPSHOT_SLEEPING)
            sleepAfterBootConfig(context)
            return
        }
        OneKukuBootRestoreStore.setNoSnapshotNote(context, false)

        val sims = ImsController.listSims(context)
        if (sims.isEmpty()) {
            OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.NEEDS_ACTIVATION)
            return
        }

        var needsRestore = false
        val targets = linkedMapOf<Int, OneKukuSnapshot>()
        for (sim in sims) {
            val snap = OneKukuSnapshotStore.findForSim(context, sim) ?: continue
            val writeSubId = sim.subscriptionId
            if (!isSnapshotEffective(context, writeSubId, snap)) {
                needsRestore = true
                targets[writeSubId] = snap
            }
        }

        if (!needsRestore) {
            Log.i(TAG, "configs still valid, stay sleeping")
            OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.READY_SLEEPING)
            sleepAfterBootConfig(context)
            return
        }

        if (!ConfigStore.isOneKukuAutoRestore(context)) {
            Log.i(TAG, "config invalid but auto-restore disabled")
            OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.NEEDS_ACTIVATION)
            return
        }

        when (val ready = ensureOneKukuReadyForBoot(context)) {
            BootReady.WAITING_WIFI -> {
                OneKukuBootRestoreStore.clearAttemptedThisBoot(context)
                OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.WAITING_WIFI)
                Log.i(TAG, "boot: need restore but waiting Wi‑Fi")
                return
            }
            BootReady.NEED_USER -> {
                OneKukuBootRestoreStore.clearAttemptedThisBoot(context)
                Log.w(TAG, "OneKuku unavailable for auto restore after silent activate (cleared attempted)")
                OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.NEEDS_ACTIVATION)
                notifyNeedsRestore(context)
                return
            }
            BootReady.READY -> Unit
        }

        OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.RESTORING)
        var anySuccess = false
        var anyFailure = false
        for ((subId, _) in targets) {
            // RestoreManager 内已对单项最多重试 2 次
            val result = OneKukuCommandDispatcher.dispatch(
                context = context,
                command = OneKukuCommand.RESTORE_ALL_CALL_CONFIGS,
                subId = subId,
            )
            if (result.success) anySuccess = true else anyFailure = true
        }

        when {
            anySuccess && !anyFailure -> {
                OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.RESTORE_COMPLETE)
                sleepAfterBootConfig(context)
            }
            anySuccess -> {
                OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.RESTORE_COMPLETE)
                sleepAfterBootConfig(context)
            }
            else -> {
                OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.NEEDS_ACTIVATION)
                notifyNeedsRestore(context)
                sleepAfterBootConfig(context)
            }
        }
    }

    /** 开机编排收尾：写过配置或确认仍有效后，一律进入休眠标签。 */
    private fun sleepAfterBootConfig(context: Context) {
        OneKukuSleepController.sleep(context)
    }

    /**
     * 等特权桥 binder + 授权。用于 OneLink：旧网已连、Shizuku 稍晚自启的窗口。
     */
    private suspend fun awaitBridgeReady(timeoutMs: Long): Boolean {
        if (OneKukuManager.isReady()) return true
        val bridge = PrivilegeBridges.current
        val ready = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                lateinit var listener: () -> Unit
                listener = {
                    if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
                        OneKukuManager.requestActivation()
                    }
                    if (OneKukuManager.isReady() && cont.isActive) {
                        bridge.removeBinderReceivedListener(listener)
                        cont.resume(true)
                    }
                }
                bridge.addBinderReceivedListener(listener, sticky = true)
                cont.invokeOnCancellation {
                    bridge.removeBinderReceivedListener(listener)
                }
                // sticky 可能已同步回调；再主动 wake 一次兜底。
                OneKukuHiddenRunner.wake()
                if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
                    OneKukuManager.requestActivation()
                }
                if (OneKukuManager.isReady() && cont.isActive) {
                    bridge.removeBinderReceivedListener(listener)
                    cont.resume(true)
                }
            }
        } == true
        if (ready) return true
        OneKukuHiddenRunner.wake()
        if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
            OneKukuManager.requestActivation()
        }
        return OneKukuManager.isReady()
    }

    fun isSnapshotEffective(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): Boolean {
        val expected = PersistableBundle()
        var hasCarrierExpectation = false
        snapshot.entries
            .filter { it.configGroup == "ims" || it.configGroup == "nr5g" }
            .forEach { entry ->
                when {
                    entry.configGroup == "ims" && entry.configKey == "volte" &&
                        entry.configValue.toBooleanStrictOrNull() == true -> {
                        expected.putBoolean(CarrierConfigKeys.VOLTE_AVAILABLE, true)
                        hasCarrierExpectation = true
                    }
                    entry.configGroup == "ims" && entry.configKey == "vowifi" &&
                        entry.configValue.toBooleanStrictOrNull() == true -> {
                        expected.putBoolean(CarrierConfigKeys.WFC_IMS_AVAILABLE, true)
                        hasCarrierExpectation = true
                    }
                    entry.configGroup == "nr5g" && entry.configKey == "enabled" &&
                        entry.configValue.toBooleanStrictOrNull() == true -> {
                        expected.putIntArray(
                            CarrierConfigKeys.NR_AVAILABILITIES_INT_ARRAY,
                            intArrayOf(1, 2),
                        )
                        hasCarrierExpectation = true
                    }
                }
            }
        if (hasCarrierExpectation && expected.keySet().isNotEmpty()) {
            if (!CarrierConfigOverrideWriter.verifyOverride(context, subId, expected)) {
                return false
            }
        }
        // soft 组（identity / 信号名 / 高级等）多为 temporary 覆盖，冷开后常丢；
        // 有 soft 条目则本开机需要走一次 RESTORE（markAttempted 防同轮循环）。
        val softGroups = setOf(
            "identity",
            "vowifi_name",
            "advanced",
            "extras",
        )
        val hasSoftRestore = snapshot.entries.any { entry ->
            entry.configGroup in softGroups && entry.configValue.isNotBlank()
        }
        if (hasSoftRestore) return false
        // 无 soft、且无 carrier 期望（或 carrier 已校验通过）→ 视为仍有效
        return true
    }

    /**
     * 快照恢复末尾校验：仅在有 CarrierConfig 可校验期望时严格核对；
     * soft-only 快照在写入步骤成功后视为通过，避免 verify 永假拖成 partial。
     */
    fun isSnapshotCarrierVerified(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): Boolean {
        val expected = PersistableBundle()
        snapshot.entries
            .filter { it.configGroup == "ims" || it.configGroup == "nr5g" }
            .forEach { entry ->
                when {
                    entry.configGroup == "ims" && entry.configKey == "volte" &&
                        entry.configValue.toBooleanStrictOrNull() == true ->
                        expected.putBoolean(CarrierConfigKeys.VOLTE_AVAILABLE, true)
                    entry.configGroup == "ims" && entry.configKey == "vowifi" &&
                        entry.configValue.toBooleanStrictOrNull() == true ->
                        expected.putBoolean(CarrierConfigKeys.WFC_IMS_AVAILABLE, true)
                    entry.configGroup == "nr5g" && entry.configKey == "enabled" &&
                        entry.configValue.toBooleanStrictOrNull() == true ->
                        expected.putIntArray(
                            CarrierConfigKeys.NR_AVAILABILITIES_INT_ARRAY,
                            intArrayOf(1, 2),
                        )
                }
            }
        if (expected.keySet().isEmpty()) return true
        return CarrierConfigOverrideWriter.verifyOverride(context, subId, expected)
    }

    private suspend fun waitUntilUnlocked(context: Context): Boolean {
        val um = context.getSystemService(UserManager::class.java) ?: return true
        val deadline = System.currentTimeMillis() + SIM_WAIT_MAX_MS
        while (System.currentTimeMillis() < deadline) {
            if (um.isUserUnlocked) return true
            delay(SIM_POLL_MS)
        }
        return um.isUserUnlocked
    }

    private suspend fun waitUntilSimsStable(context: Context): Boolean {
        val deadline = System.currentTimeMillis() + SIM_WAIT_MAX_MS
        var lastIds: List<Int> = emptyList()
        var stableSince = 0L
        while (System.currentTimeMillis() < deadline) {
            if (!areSimsReady(context)) {
                lastIds = emptyList()
                stableSince = 0L
                delay(SIM_POLL_MS)
                continue
            }
            val ids = ImsController.listSims(context).map { it.subscriptionId }.sorted()
            if (ids.isEmpty()) {
                delay(SIM_POLL_MS)
                continue
            }
            if (ids == lastIds) {
                if (stableSince == 0L) stableSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - stableSince >= STABLE_WAIT_MS) {
                    return true
                }
            } else {
                lastIds = ids
                stableSince = System.currentTimeMillis()
            }
            delay(SIM_POLL_MS)
        }
        return ImsController.listSims(context).isNotEmpty() && areSimsReady(context)
    }

    private fun areSimsReady(context: Context): Boolean {
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return false
        val list = runCatching { sm.activeSubscriptionInfoList }.getOrNull() ?: return false
        if (list.isEmpty()) return false
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return false
        return list.any { info ->
            runCatching {
                tm.createForSubscriptionId(info.subscriptionId).simState ==
                    TelephonyManager.SIM_STATE_READY
            }.getOrDefault(false)
        }
    }

    /**
     * 通道已就绪时重放持久配置（核心 / 高级选项 / extras / 5G 显示 / 信号等）。
     * 与 [ReapplyManager] 同一契约；无任何重放源才跳过。
     */
    private suspend fun reapplyLastCapabilityProfileAssumingReady(context: Context) {
        if (!ReapplyManager.hasPersistedReapplySource(context)) {
            Log.i(TAG, "no persisted reapply source, skip capability reapply")
            return
        }
        runCatching {
            val result = ReapplyManager.reapply(context, ReapplyTrigger.BOOT)
            Log.i(
                TAG,
                "boot capability reapply success=${result.success} msg=${result.message}",
            )
        }.onFailure {
            Log.w(TAG, "boot capability reapply failed: ${it.message}")
        }
    }

    /**
     * 开机恢复前尽量把通道拉起来。
     * - OneLink：轻壳，只认官方 Shizuku 是否就绪，绝不走内嵌 ADB / 六位码。
     * - OneKuku：已配对时 Wi‑Fi 前置 → 静默开无线调试 → 无码直连。
     */
    private suspend fun ensureOneKukuReadyForBoot(context: Context): BootReady {
        OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)
        // CARE_MIN：外置 V15 binder 让 isReady()=true 仍不够，必须拉起 onekuku_server。
        val careMin = !ChannelLine.usesShizuku && ChannelEngine.current() == ChannelEngine.CARE_MIN
        if (careMin) {
            val hostUp = withContext(Dispatchers.IO) {
                OneKukuHostServerBootstrap.ensureRunning(context)
            }
            Log.i(TAG, "boot CARE_MIN host server up=$hostUp")
            if (hostUp) {
                val wakeHost = OneKukuHiddenRunner.wake()
                if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
                    OneKukuManager.requestActivation()
                }
                if ((wakeHost.success || OneKukuManager.isReady()) &&
                    OneKukuHostServerBootstrap.isHostServerAlive()
                ) {
                    return BootReady.READY
                }
            }
        } else if (OneKukuManager.isReady()) {
            return BootReady.READY
        }

        val wake = OneKukuHiddenRunner.wake()
        if (wake.success && OneKukuManager.isReady()) {
            if (!careMin || OneKukuHostServerBootstrap.isHostServerAlive()) {
                return BootReady.READY
            }
        }
        if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
            OneKukuManager.requestActivation()
            if (OneKukuManager.isReady() &&
                (!careMin || OneKukuHostServerBootstrap.isHostServerAlive())
            ) {
                return BootReady.READY
            }
        }

        if (ChannelLine.usesShizuku) {
            // OneLink：优先等 Shizuku binder，禁止「先卡满 Wi‑Fi」把已 Active 的窗口拖死。
            if (awaitBridgeReady(BOOT_BRIDGE_WAIT_EARLY_MS)) {
                Log.i(TAG, "boot onelink: bridge ready (early, before Wi‑Fi wait)")
                return BootReady.READY
            }
            val wifiOk = withContext(Dispatchers.IO) {
                OneKukuAdbMdns.waitForWifiClient(context, BOOT_WIFI_WAIT_SHIZUKU_MS)
            }
            Log.i(TAG, "boot onelink: wait Wi‑Fi ok=$wifiOk (fallback after binder miss)")
            val wakeAgain = OneKukuHiddenRunner.wake()
            if ((wakeAgain.success || OneKukuManager.isRunning()) && !OneKukuManager.isGranted()) {
                OneKukuManager.requestActivation()
            }
            if (OneKukuManager.isReady()) return BootReady.READY
            if (awaitBridgeReady(BOOT_BRIDGE_WAIT_AFTER_WIFI_MS)) {
                Log.i(TAG, "boot onelink: bridge ready after Wi‑Fi")
                return BootReady.READY
            }
            if (!wifiOk) {
                OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.WAITING_WIFI)
                return BootReady.WAITING_WIFI
            }
            Log.i(TAG, "boot onelink: Wi‑Fi up but Shizuku not ready — need user")
            return BootReady.NEED_USER
        }

        val pairedBefore = OneKukuEmbeddedAdbActivator.hasPairedOnce(context)
        if (pairedBefore) {
            val wifiOk = withContext(Dispatchers.IO) {
                OneKukuAdbMdns.waitForWifiClient(context, BOOT_WIFI_WAIT_MS)
            }
            Log.i(TAG, "boot: pre-wait Wi‑Fi ok=$wifiOk")
            if (!wifiOk) {
                // 仅真未 STA 时钉等待态，避免已连旧网先闪 WAITING_WIFI。
                OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.WAITING_WIFI)
                return BootReady.WAITING_WIFI
            }

            when (val wifi = ShizukuSetupHelper.ensureAdbWifiEnabled(context)) {
                ShizukuSetupHelper.AdbWifiEnsureResult.ENABLED_NOW -> {
                    Log.i(TAG, "boot: adb_wifi enabled now, wait adbd port")
                    // 无线调试 TLS 口起来比 Secure Settings 落盘慢。
                    withContext(Dispatchers.IO) {
                        OneKukuAdbEnvironment.pollStartableConnectPort(
                            context,
                            timeoutMs = PAIRED_ALREADY_ON_PORT_WAIT_MS,
                            pollMs = 500L,
                        )
                    }
                }
                ShizukuSetupHelper.AdbWifiEnsureResult.ALREADY_ON -> {
                    // 重启后 setting=1 不等于口已 LISTEN；绝不能 skip wait。
                    Log.i(TAG, "boot: adb_wifi already on, still poll adbd port")
                    withContext(Dispatchers.IO) {
                        OneKukuAdbEnvironment.pollStartableConnectPort(
                            context,
                            timeoutMs = PAIRED_ALREADY_ON_PORT_WAIT_MS,
                            pollMs = 500L,
                        )
                    }
                }
                ShizukuSetupHelper.AdbWifiEnsureResult.FAILED ->
                    Log.i(TAG, "boot: ensureAdbWifi failed")
            }
        }

        Log.i(TAG, "boot: silent MiniAdb activateExistingOrNeedPair")
        return when (val outcome = OneKukuMiniAdbClient.activateExistingOrNeedPair(context)) {
            is OneKukuMiniAdbClient.Outcome.Success -> {
                if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
                    OneKukuManager.requestActivation()
                }
                val ready = OneKukuManager.isReady()
                Log.i(TAG, "boot: silent activate success ready=$ready detail=${outcome.detail}")
                if (ready) BootReady.READY else BootReady.NEED_USER
            }
            is OneKukuMiniAdbClient.Outcome.NeedPairingCode -> {
                Log.w(TAG, "boot: need pairing code for activate")
                // 已配对但直连失败：多轮等口 + 重试；仍失败才要用户（新网/身份失效）。
                if (pairedBefore) {
                    if (!ShizukuSetupHelper.hasWriteSecureSettings(context)) {
                        ShizukuSetupHelper.openWirelessDebugging(context)
                    } else {
                        ShizukuSetupHelper.tryEnableAdbWifi(context)
                    }
                    repeat(PAIRED_NEED_CODE_RETRIES) { attempt ->
                        delay(POST_WIRELESS_ENABLE_MS)
                        withContext(Dispatchers.IO) {
                            OneKukuAdbEnvironment.pollStartableConnectPort(
                                context,
                                timeoutMs = PAIRED_NEED_CODE_RETRY_GAP_MS.toLong(),
                                pollMs = 500L,
                            )
                        }
                        when (
                            val retry = OneKukuMiniAdbClient.activateExistingOrNeedPair(context)
                        ) {
                            is OneKukuMiniAdbClient.Outcome.Success -> {
                                if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
                                    OneKukuManager.requestActivation()
                                }
                                val ready = OneKukuManager.isReady()
                                Log.i(
                                    TAG,
                                    "boot: paired retry#$attempt ready=$ready detail=${retry.detail}",
                                )
                                if (ready) return BootReady.READY
                            }
                            is OneKukuMiniAdbClient.Outcome.Failed -> {
                                if (retry.reason == "wifi_sta_required") {
                                    return BootReady.WAITING_WIFI
                                }
                                Log.w(TAG, "boot: paired retry#$attempt failed=${retry.reason}")
                            }
                            else -> Log.w(TAG, "boot: paired retry#$attempt still need code")
                        }
                    }
                }
                OneKukuPairingNotification.showWaiting(context)
                BootReady.NEED_USER
            }
            is OneKukuMiniAdbClient.Outcome.Failed -> {
                Log.w(TAG, "boot: silent activate failed reason=${outcome.reason}")
                if (outcome.reason == "wifi_sta_required") {
                    return BootReady.WAITING_WIFI
                }
                if (pairedBefore) {
                    if (!ShizukuSetupHelper.tryEnableAdbWifi(context)) {
                        ShizukuSetupHelper.openWirelessDebugging(context)
                    }
                }
                BootReady.NEED_USER
            }
        }
    }

    private fun applySystemUpdateShieldIfEnabled(context: Context) {
        if (!SystemUpdateShield.isEnabled(context)) return
        runCatching {
            val result = SystemUpdateShield.apply(context)
            Log.i(TAG, "boot update-shield: ${result.message}")
        }.onFailure {
            Log.w(TAG, "boot update-shield failed: ${it.message}")
        }
    }

    private fun notifyNeedsRestore(context: Context) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                val ch = NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.onekuku_boot_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
                ch.description = context.getString(R.string.onekuku_boot_channel_desc)
                nm.createNotificationChannel(ch)
            }
            val launch = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_RESTORE, true)
            }
            val pi = PendingIntent.getActivity(
                context,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notif = Notification.Builder(context, CHANNEL)
                .setContentTitle(context.getString(R.string.onekuku_boot_notif_title))
                .setContentText(context.getString(R.string.onekuku_boot_notif_text))
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_ID, notif)
        }.onFailure {
            Log.w(TAG, "notify failed: ${it.message}")
        }
    }

    const val EXTRA_OPEN_RESTORE = "onekuku_open_restore"
}
