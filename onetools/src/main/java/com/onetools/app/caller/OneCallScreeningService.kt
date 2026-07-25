package com.onetools.app.caller

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import kotlinx.coroutines.runBlocking

/**
 * Clean-room CallScreeningService — OneCaller.
 * Does not copy Pixel Telo source; uses public Telecom APIs only.
 */
class OneCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        val store = CallRuleStore(applicationContext)
        val rules = runBlocking { store.snapshot() }
        val decision = NumberMatcher.decide(rules, number)

        val builder = CallResponse.Builder()
        when (decision) {
            NumberMatcher.Decision.BLOCK -> {
                builder.setDisallowCall(true)
                builder.setRejectCall(true)
                builder.setSkipCallLog(false)
                builder.setSkipNotification(true)
                if (Build.VERSION.SDK_INT >= 29) {
                    runCatching { builder.setSilenceCall(true) }
                }
            }
            NumberMatcher.Decision.ALLOW_LIST,
            NumberMatcher.Decision.ALLOW_UNKNOWN,
            -> {
                builder.setDisallowCall(false)
                builder.setRejectCall(false)
            }
        }
        respondToCall(callDetails, builder.build())
    }
}
