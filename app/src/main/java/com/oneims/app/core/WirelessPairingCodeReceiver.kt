package com.oneims.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.oneims.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 通知栏 RemoteInput → Mini ADB pair/connect/start。
 * 全程不要求用户切回 OneIMS。
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
                    }
                    is OneKukuMiniAdbClient.Outcome.NeedPairingCode -> {
                        OneKukuActivationUi.setPhase(OneKukuActivationPhase.WAITING_PAIR)
                        OneKukuPairingNotification.showWaiting(app)
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
                            else -> outcome.reason
                        }
                        OneKukuPairingNotification.showFailure(app, msg)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CONTINUE_RESTORE = "com.oneims.app.action.CONTINUE_CALL_RESTORE"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
