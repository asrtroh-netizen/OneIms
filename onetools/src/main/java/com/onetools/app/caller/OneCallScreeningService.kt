package com.onetools.app.caller

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * Pass-through CallScreeningService.
 * Product decision (2026-07-25 feedback): no call blocking — Directory attribution only.
 * Kept registered so devices that already set OneTools as the screening role do not break calls.
 */
class OneCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        Log.i(TAG, "screen pass-through number=$number (blocking disabled)")
        val builder = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
        respondToCall(callDetails, builder.build())
    }

    companion object {
        private const val TAG = "OneCallScreening"
    }
}
