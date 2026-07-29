package com.oneims.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** OneLink：无内嵌 ADB 解锁续跑；占位以满足 main [BootReceiver] 编译。 */
class OneKukuUserPresentRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = Unit

    companion object {
        fun setEnabled(context: Context, enabled: Boolean) = Unit
    }
}
