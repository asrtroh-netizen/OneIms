package com.oneims.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.oneims.app.MainActivity
import com.oneims.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 通知栏 RemoteInput → Mini ADB pair/connect/start。
 * 提交有效六位码后立刻把 OneIMS 拉回前台，方便用户看激活进度。
 */
class WirelessPairingCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != OneKukuPairingNotification.ACTION_SUBMIT_CODE) return
        val remote = RemoteInput.getResultsFromIntent(intent)
        val raw = (
            remote?.getCharSequence(OneKukuPairingNotification.KEY_REMOTE_INPUT)?.toString()
                ?: intent.getStringExtra(OneKukuPairingNotification.EXTRA_PAIRING_CODE)
                ?: ""
            ).trim()
        if (OneKukuMiniAdbClient.parsePairingInput(raw) == null) {
            Toast.makeText(
                context,
                context.getString(R.string.onekuku_pair_need_six_digits),
                Toast.LENGTH_SHORT,
            ).show()
            OneKukuPairingNotification.showWaiting(context)
            return
        }

        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                OneKukuActivationUi.setPhase(OneKukuActivationPhase.PAIRING)
                OneKukuPairingNotification.showPairingInProgress(app)
                // 输完码立刻回 App：通知栏提交算用户手势，允许拉起前台。
                bringAppToForeground(app)
                when (val outcome = OneKukuMiniAdbClient.pairConnectAndStart(app, raw)) {
                    is OneKukuMiniAdbClient.Outcome.Success -> {
                        OneKukuActivationUi.setPhase(OneKukuActivationPhase.ACTIVE)
                        OneKukuPairingNotification.showSuccess(app)
                        OneKukuPairingNotification.cancel(app)
                        if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
                            OneKukuManager.requestActivation()
                        }
                        // 挂起的一键恢复：发广播让前台续跑（若 Activity 存活）
                        if (OneKukuActivationUi.pendingRestoreAfterPair) {
                            app.sendBroadcast(
                                Intent(ACTION_CONTINUE_RESTORE).setPackage(app.packageName),
                            )
                        }
                        // 管道相位清回 IDLE，首页改走 resolve（就绪/休眠），避免一直显示激活中。
                        OneKukuActivationUi.setPhase(OneKukuActivationPhase.IDLE)
                        bringAppToForeground(app)
                    }
                    is OneKukuMiniAdbClient.Outcome.NeedPairingCode -> {
                        OneKukuActivationUi.setPhase(OneKukuActivationPhase.WAITING_PAIR)
                        OneKukuPairingNotification.showWaiting(app)
                        bringAppToForeground(app)
                    }
                    is OneKukuMiniAdbClient.Outcome.Failed -> {
                        OneKukuActivationUi.setPhase(
                            OneKukuActivationPhase.FAILED,
                            failure = outcome.reason,
                        )
                        val msg = when (outcome.reason) {
                            "wifi_sta_required" ->
                                app.getString(R.string.onekuku_msg_wifi_sta_required)
                            "invalid_pairing_input" ->
                                app.getString(R.string.onekuku_pair_need_six_digits)
                            "pair_failed" ->
                                app.getString(R.string.onekuku_pair_reason_pair_failed)
                            "pair_port_missing" ->
                                app.getString(R.string.onekuku_pair_reason_port_missing)
                            "connect_failed" ->
                                app.getString(R.string.onekuku_pair_reason_connect_failed)
                            "start_failed" ->
                                app.getString(R.string.onekuku_pair_reason_start_failed)
                            "binder_not_received" ->
                                app.getString(R.string.onekuku_pair_reason_binder)
                            else -> outcome.reason
                        }
                        OneKukuPairingNotification.showFailure(app, msg)
                        bringAppToForeground(app)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CONTINUE_RESTORE = "com.oneims.app.action.CONTINUE_CALL_RESTORE"
        private const val TAG = "OneIMS-PairReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun bringAppToForeground(app: Context) {
            val launch = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            runCatching { app.startActivity(launch) }
                .onFailure { Log.w(TAG, "bringAppToForeground failed", it) }
        }
    }
}
