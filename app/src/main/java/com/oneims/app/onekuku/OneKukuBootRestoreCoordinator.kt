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
import com.oneims.app.core.ConfigStore
import com.oneims.app.core.ImsController
import com.oneims.app.core.OneKukuAdbMdns
import com.oneims.app.core.OneKukuEmbeddedAdbActivator
import com.oneims.app.core.OneKukuManager
import com.oneims.app.core.OneKukuMiniAdbClient
import com.oneims.app.core.OneKukuPairingNotification
import com.oneims.app.core.OneKukuPrivilegeBridgeImpl
import com.oneims.app.core.ReapplyManager
import com.oneims.app.core.ReapplyTrigger
import com.oneims.app.core.ShizukuSetupHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 重启后自动检查/恢复编排（不关数据、不切卡、不飞行、不碰 radio、不恢复 APN）。
 */
object OneKukuBootRestoreCoordinator {
    private const val TAG = "OneIMS-OneKuku"
    private const val STABLE_WAIT_MS = 2_000L
    /** SIM 已稳定后再短等，给 telephony/IMS 收口；原 20s 体感过慢。 */
    private const val POST_READY_DELAY_MS = 5_000L
    private const val SIM_POLL_MS = 1_500L
    private const val SIM_WAIT_MAX_MS = 90_000L
    /** 开机已配对：先等系统连上记住的 Wi‑Fi，再静默开无线调试。 */
    private const val BOOT_WIFI_WAIT_MS = 60_000L
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
        if (!waitUntilSimsStable(context)) {
            Log.i(TAG, "SIM not ready/stable")
            return
        }

        delay(POST_READY_DELAY_MS)

        // 延迟后再占一次「本开机」名额，避免过早标记导致永远不跑
        if (OneKukuBootRestoreStore.hasAttemptedThisBoot(context)) return
        OneKukuBootRestoreStore.markAttemptedThisBoot(context)

        // 临时 CarrierConfig 覆盖在重启后会丢：先拉通道再重放上次成功的能力页配置。
        when (val ready = ensureOneKukuReadyForBoot(context)) {
            BootReady.WAITING_WIFI -> {
                // Wi‑Fi 未就绪：清占位，连上 STA 后 BootReceiver 再 enqueue。
                OneKukuBootRestoreStore.clearAttemptedThisBoot(context)
                OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.WAITING_WIFI)
                Log.i(TAG, "boot: waiting Wi‑Fi, will retry when STA connects")
                return
            }
            BootReady.NEED_USER -> {
                Log.w(TAG, "OneKuku unavailable before capability reapply")
            }
            BootReady.READY -> {
                reapplyLastCapabilityProfileAssumingReady(context)
            }
        }

        val snapshots = OneKukuSnapshotStore.loadAll(context)
        if (snapshots.isEmpty()) {
            OneKukuBootRestoreStore.setNoSnapshotNote(context, true)
            OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.NO_SNAPSHOT_SLEEPING)
            OneKukuSleepController.sleepIfEnabled(context)
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
            OneKukuSleepController.sleepIfEnabled(context)
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
                Log.w(TAG, "OneKuku unavailable for auto restore after silent activate")
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
                OneKukuSleepController.sleepIfEnabled(context)
            }
            anySuccess -> {
                OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.RESTORE_COMPLETE)
                OneKukuSleepController.sleepIfEnabled(context)
            }
            else -> {
                OneKukuBootRestoreStore.writeHint(context, OneKukuBootUiHint.NEEDS_ACTIVATION)
                notifyNeedsRestore(context)
                OneKukuSleepController.sleepIfEnabled(context)
            }
        }
    }

    fun isSnapshotEffective(
        context: Context,
        subId: Int,
        snapshot: OneKukuSnapshot,
    ): Boolean {
        val expected = PersistableBundle()
        var hasExpected = false
        snapshot.entries
            .filter { it.configGroup == "ims" || it.configGroup == "nr5g" }
            .forEach { entry ->
                when {
                    entry.configGroup == "ims" && entry.configKey == "volte" &&
                        entry.configValue.toBooleanStrictOrNull() == true -> {
                        expected.putBoolean(CarrierConfigKeys.VOLTE_AVAILABLE, true)
                        hasExpected = true
                    }
                    entry.configGroup == "ims" && entry.configKey == "vowifi" &&
                        entry.configValue.toBooleanStrictOrNull() == true -> {
                        expected.putBoolean(CarrierConfigKeys.WFC_IMS_AVAILABLE, true)
                        hasExpected = true
                    }
                    entry.configGroup == "nr5g" && entry.configKey == "enabled" &&
                        entry.configValue.toBooleanStrictOrNull() == true -> {
                        hasExpected = true
                    }
                }
            }
        if (!hasExpected) {
            return CarrierConfigOverrideWriter.readConfigForSubId(context, subId, emptyList()) != null
        }
        if (expected.keySet().isEmpty()) {
            return true
        }
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

    /** 通道已就绪时重放能力页配置（不再二次 ensure，避免重复激活）。 */
    private suspend fun reapplyLastCapabilityProfileAssumingReady(context: Context) {
        if (ConfigStore.lastApplied(context) == null) {
            Log.i(TAG, "no lastApplied profile, skip capability reapply")
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
     * 开机恢复前尽量把通道拉起来：已配对时 **Wi‑Fi 前置** → 静默开无线调试 → 无码直连。
     * 仅从未配对 / 真需填码时返回 [BootReady.NEED_USER]。
     */
    private suspend fun ensureOneKukuReadyForBoot(context: Context): BootReady {
        OneKukuHiddenRunner.installBridge(OneKukuPrivilegeBridgeImpl)
        if (OneKukuManager.isReady()) return BootReady.READY

        val wake = OneKukuHiddenRunner.wake()
        if (wake.success && OneKukuManager.isReady()) return BootReady.READY
        if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
            OneKukuManager.requestActivation()
            if (OneKukuManager.isReady()) return BootReady.READY
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

            val enabled = ShizukuSetupHelper.tryEnableAdbWifi(context)
            Log.i(TAG, "boot: tryEnableAdbWifi=$enabled")
            if (enabled) delay(3_000L)
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
                // 已配对但直连失败：再开无线调试短试；仍失败才要用户（新网/身份失效）。
                if (pairedBefore) {
                    if (!ShizukuSetupHelper.hasWriteSecureSettings(context)) {
                        ShizukuSetupHelper.openWirelessDebugging(context)
                    } else {
                        ShizukuSetupHelper.tryEnableAdbWifi(context)
                    }
                    delay(8_000L)
                    when (
                        val retry = OneKukuMiniAdbClient.activateExistingOrNeedPair(context)
                    ) {
                        is OneKukuMiniAdbClient.Outcome.Success -> {
                            if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
                                OneKukuManager.requestActivation()
                            }
                            val ready = OneKukuManager.isReady()
                            Log.i(TAG, "boot: retry after wireless enable ready=$ready")
                            if (ready) return BootReady.READY
                        }
                        is OneKukuMiniAdbClient.Outcome.Failed -> {
                            if (retry.reason == "wifi_sta_required") {
                                return BootReady.WAITING_WIFI
                            }
                        }
                        else -> Log.w(TAG, "boot: retry still need user action")
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
