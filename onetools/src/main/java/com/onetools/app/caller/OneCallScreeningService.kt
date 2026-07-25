package com.onetools.app.caller

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * Clean-room CallScreeningService — OneCaller.
 * Pipeline aligned with Telo's screening path (rules → spam pack → optional network),
 * without copying Telo source and without incoming-call overlay.
 */
class OneCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        val builder = CallResponse.Builder()
        runBlocking {
            try {
                val prefs = CallerPrefs(applicationContext)
                val notifyOnly = prefs.notifyOnly()
                val result = CallerCheckEngine.check(applicationContext, number)
                Log.i(
                    TAG,
                    "screen number=$number shouldBlock=${result.shouldBlock} " +
                        "type=${result.resultType} notifyOnly=$notifyOnly " +
                        "force=${result.forceBlock} label=${result.label} " +
                        "local=${result.localCostMs}ms net=${result.networkCostMs}ms",
                )
                when {
                    result.shouldBlock && result.forceBlock -> reject(builder)
                    result.shouldBlock && !notifyOnly -> reject(builder)
                    else -> allow(builder)
                }
            } catch (e: Exception) {
                Log.e(TAG, "screen failed — allow call", e)
                allow(builder)
            } finally {
                respondToCall(callDetails, builder.build())
            }
        }
    }

    private fun reject(builder: CallResponse.Builder) {
        builder.setDisallowCall(true)
        builder.setRejectCall(true)
        builder.setSkipCallLog(false)
        builder.setSkipNotification(true)
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { builder.setSilenceCall(true) }
        }
    }

    private fun allow(builder: CallResponse.Builder) {
        builder.setDisallowCall(false)
        builder.setRejectCall(false)
        builder.setSkipCallLog(false)
    }

    companion object {
        private const val TAG = "OneCallScreening"
    }
}
