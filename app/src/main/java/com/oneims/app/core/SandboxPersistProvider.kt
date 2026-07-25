package com.oneims.app.core

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.os.Process
import android.system.Os
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 主进程握手入口：校验调用方为 SDK sandbox / shell / root 后，
 * 将 shell 权限委托给 sandbox uid 并触发对方 binder 写配置。
 */
class SandboxPersistProvider : ContentProvider() {
    private companion object {
        const val TAG = "SandboxPersistProvider"
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != SandboxPersistSupport.METHOD_HANDSHAKE) {
            return Bundle().apply { putBoolean("ok", false) }
        }
        HiddenApiBypass.addHiddenApiExemptions("")
        val binder = extras?.getBinder(SandboxPersistSupport.EXTRA_BINDER)
            ?: return Bundle().apply { putBoolean("ok", false) }
        if (!callerAllowed()) {
            Log.w(TAG, "reject caller uid=${Binder.getCallingUid()}")
            return Bundle().apply { putBoolean("ok", false) }
        }
        val sdkUid = resolveSdkSandboxUid()
        return try {
            SystemApiBroker.startShellPermissionDelegationForUid(sdkUid, null)
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                binder.transact(SandboxPersistSupport.TRANSACT_WRITE, data, reply, 0)
                reply.readException()
                SandboxPersistBridge.complete(true)
                Bundle().apply { putBoolean("ok", true) }
            } finally {
                data.recycle()
                reply.recycle()
                runCatching { SystemApiBroker.stopShellPermissionDelegation() }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "handshake failed: ${error.message}")
            SandboxPersistBridge.complete(false)
            Bundle().apply { putBoolean("ok", false) }
        }
    }

    private fun callerAllowed(): Boolean {
        val calling = Binder.getCallingUid()
        val sdkUid = resolveSdkSandboxUid()
        // 与南宫一致：仅允许 sandbox / shell / root，不放行同包普通 uid。
        return calling == sdkUid ||
            calling == Process.SHELL_UID ||
            calling == Process.ROOT_UID
    }

    private fun resolveSdkSandboxUid(): Int {
        return runCatching {
            Process::class.java
                .getMethod("toSdkSandboxUid", Int::class.javaPrimitiveType)
                .invoke(null, Os.getuid()) as Int
        }.getOrElse {
            // 旧系统无 sandbox uid API：回落自身 uid（此时沙盒路径通常不会被探测放行）。
            Process.myUid()
        }
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
}
