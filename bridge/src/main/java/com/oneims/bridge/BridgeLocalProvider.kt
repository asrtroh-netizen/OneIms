package com.oneims.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log

/**
 * 桥 APK 本机 Provider：便于自检；真正客户端投递目标是 OneIMS 的 onebridge authority。
 */
class BridgeLocalProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

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
