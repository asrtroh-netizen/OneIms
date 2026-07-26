package com.onetools.app.live.capsule

import android.view.HapticFeedbackConstants
import android.view.View

/** MT Vibration 心智：展开/切会话轻触，可关。 */
object CapsuleHaptics {
    fun tick(view: View?, enabled: Boolean) {
        if (!enabled || view == null) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm(view: View?, enabled: Boolean) {
        if (!enabled || view == null) return
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
}
