package com.oneims.app.core.privilege

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.util.Log

/**
 * 接收 OneBridge shell 进程投递的 binder。仅允许 shell(2000)/root(0) 调用。
 *
 * 对齐邻仓 [rikka.shizuku.ShizukuProvider#handleSendBinder]：
 * 已有 living binder 时忽略重复 sendBinder，避免重投触发客户端全量 reapply。
 */
class BridgeBinderProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val uid = Binder.getCallingUid()
        if (uid != 0 && uid != 2000) {
            Log.w(TAG, "reject sendBinder from uid=$uid")
            return null
        }
        if (method != "sendBinder") return null

        if (BridgeBinderHolder.get() != null) {
            Log.d(TAG, "sendBinder is called when already a living binder")
            return Bundle().apply { putBoolean("ok", true) }
        }

        val binder = extras?.getBinder("binder")
        BridgeBinderHolder.onReceived(binder)
        return Bundle().apply { putBoolean("ok", binder != null) }
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
        private const val TAG = "OneBridgeClient"
    }
}
