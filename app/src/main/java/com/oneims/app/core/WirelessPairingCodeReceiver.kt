package com.oneims.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 接收通知栏 RemoteInput 的六位配对码，走内嵌无线调试拉起通道。
 */
class WirelessPairingCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != WirelessPairingNotifier.ACTION_SUBMIT_CODE) return
        val remote = RemoteInput.getResultsFromIntent(intent)
        val code = (
            remote?.getCharSequence(WirelessPairingNotifier.KEY_REMOTE_INPUT)?.toString()
                ?: intent.getStringExtra(WirelessPairingNotifier.EXTRA_PAIRING_CODE)
                ?: ""
            ).filter { it.isDigit() }.take(6)
        if (code.length < 6) {
            Toast.makeText(
                context,
                context.getString(com.oneims.app.R.string.wireless_pairing_need_six_digits),
                Toast.LENGTH_SHORT,
            ).show()
            WirelessPairingNotifier.showPairingPrompt(context)
            return
        }

        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                Toast.makeText(
                    app,
                    app.getString(com.oneims.app.R.string.onekuku_msg_embedded_adb_starting),
                    Toast.LENGTH_SHORT,
                ).show()
                when (
                    val outcome = OneKukuEmbeddedAdbActivator.activate(app, pairingCode = code)
                ) {
                    is OneKukuEmbeddedAdbActivator.Outcome.Success -> {
                        WirelessPairingNotifier.showResult(
                            app,
                            ok = true,
                            detail = app.getString(com.oneims.app.R.string.onekuku_msg_embedded_adb_ok),
                        )
                        if (OneKukuManager.isRunning() && !OneKukuManager.isGranted()) {
                            OneKukuManager.requestActivation()
                        }
                    }
                    is OneKukuEmbeddedAdbActivator.Outcome.NeedPairingCode -> {
                        WirelessPairingNotifier.showPairingPrompt(app)
                        Toast.makeText(
                            app,
                            app.getString(com.oneims.app.R.string.onekuku_msg_need_pairing_code),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    is OneKukuEmbeddedAdbActivator.Outcome.Failed -> {
                        val msg = when (outcome.reason) {
                            "wifi_sta_required" ->
                                app.getString(com.oneims.app.R.string.onekuku_msg_wifi_sta_required)
                            else ->
                                app.getString(
                                    com.oneims.app.R.string.onekuku_msg_embedded_adb_fallback,
                                    outcome.reason,
                                )
                        }
                        WirelessPairingNotifier.showResult(app, ok = false, detail = msg)
                        WirelessPairingNotifier.showPairingPrompt(app)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
