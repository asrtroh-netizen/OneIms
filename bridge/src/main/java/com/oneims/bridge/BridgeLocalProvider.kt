package com.oneims.bridge

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * 桥 APK 本机 Provider：便于自检；真正客户端投递目标是 OneIMS 的 onebridge authority。
 */
class BridgeLocalProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        // 任意组件拉起进程时尽量写出 start.sh（双保险；OneIMS 现已可内联 boot）
        val app = context?.applicationContext
        if (app is Application) {
            runCatching { BridgeStarter.installStartScript(app) }
                .onFailure { Log.w(TAG, "install start.sh from provider failed", it) }
        }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "ping") {
            return Bundle().apply { putBoolean("ok", true) }
        }
        if (method == "sendBinder") {
            val b = extras?.getBinder("binder")
            Log.i(TAG, "local received binder=${b != null}")
            return Bundle().apply { putBoolean("ok", b != null) }
        }
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val TAG = "OneBridge"
    }
}
