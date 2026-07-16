package com.oneims.app.core

import android.content.Context

/**
 * OneLink 桩：无六位码配对通知。真实实现仅存在于 onekuku flavor。
 * 常量保留同名，避免 shared 代码引用时编译失败。
 */
object OneKukuPairingNotification {
    const val CHANNEL_ID = "onekuku_pairing"
    const val NOTIFICATION_ID = 71_001
    const val ACTION_SUBMIT_CODE = "com.oneims.app.action.SUBMIT_WIRELESS_PAIRING_CODE"
    const val KEY_REMOTE_INPUT = "wireless_pairing_code"
    const val EXTRA_PAIRING_CODE = "pairing_code"

    fun ensureChannel(context: Context) = Unit

    fun showWaiting(context: Context) = Unit

    fun showPairingInProgress(context: Context) = Unit

    fun showSuccess(context: Context) = Unit

    fun showFailure(context: Context, reason: String) = Unit

    fun cancel(context: Context) = Unit
}
