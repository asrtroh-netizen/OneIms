package com.oneims.app.core

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.os.PersistableBundle
import android.os.Process
import android.os.RemoteException
import android.telephony.CarrierConfigManager
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 在 SDK Sandbox 进程内执行的短生命周期 Instrumentation（对齐 vvb2060 PrivilegedProcess）。
 *
 * 流程：sandbox → ContentProvider.handshake(binder) → 主进程 shell 委托 sandbox uid →
 * binder.transact → 本进程 overrideConfig(persistent=true)。
 */
class SandboxPersistInstrumentation : Instrumentation() {
    private companion object {
        const val TAG = "SandboxPersistInstr"
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        HiddenApiBypass.addHiddenApiExemptions("")
        val args = arguments ?: Bundle()
        val subId = args.getInt(SandboxPersistSupport.EXTRA_SUB_ID, -1)
        @Suppress("DEPRECATION")
        val overrides = args.getParcelable<PersistableBundle>(SandboxPersistSupport.EXTRA_BUNDLE)
        // 结果只能由主进程 Provider 经 SandboxPersistBridge 回传（跨进程无共享静态）。
        val ok = runCatching {
            require(subId >= 0) { "invalid subId" }
            require(overrides != null && overrides.keySet().isNotEmpty()) { "empty overrides" }
            check(isSdkSandboxProcess()) { "not sdk sandbox process" }
            handshakeAndWrite(subId, overrides)
        }.getOrElse { error ->
            Log.w(TAG, "sandbox instr failed: ${error.message}")
            false
        }
        finish(if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle())
    }

    private fun handshakeAndWrite(subId: Int, overrides: PersistableBundle): Boolean {
        val context = targetContext.applicationContext
        val extras = Bundle()
        extras.putBinder(
            SandboxPersistSupport.EXTRA_BINDER,
            object : Binder() {
                override fun onTransact(
                    code: Int,
                    data: Parcel,
                    reply: Parcel?,
                    flags: Int,
                ): Boolean {
                    if (code != SandboxPersistSupport.TRANSACT_WRITE) {
                        return super.onTransact(code, data, reply, flags)
                    }
                    return try {
                        writePersistent(context, subId, overrides)
                        reply?.writeNoException()
                        true
                    } catch (error: Throwable) {
                        Log.w(TAG, "transact write failed: ${error.message}")
                        reply?.writeException(RemoteException(error.message))
                        true
                    }
                }
            },
        )
        val authority = context.packageName + SandboxPersistSupport.PROVIDER_SUFFIX
        val result = context.contentResolver.call(
            authority,
            SandboxPersistSupport.METHOD_HANDSHAKE,
            null,
            extras,
        )
        return result?.getBoolean("ok", false) == true
    }

    private fun writePersistent(
        context: Context,
        subId: Int,
        overrides: PersistableBundle,
    ) {
        val manager = context.getSystemService(CarrierConfigManager::class.java)
            ?: error("CarrierConfigManager unavailable")
        val method = CarrierConfigManager::class.java.methods.firstOrNull { candidate ->
            candidate.name == "overrideConfig" &&
                candidate.parameterTypes.size == 3 &&
                candidate.parameterTypes[0] == Int::class.javaPrimitiveType &&
                candidate.parameterTypes[1] == PersistableBundle::class.java
        } ?: error("overrideConfig(3) unavailable")
        val mode = when (method.parameterTypes[2]) {
            Boolean::class.javaPrimitiveType -> true
            Int::class.javaPrimitiveType -> 1
            else -> error("unsupported overrideConfig third param")
        }
        method.invoke(manager, subId, overrides, mode)
        Log.i(TAG, "overrideConfig persistent ok subId=$subId keys=${overrides.keySet()}")
    }

    private fun isSdkSandboxProcess(): Boolean {
        return runCatching {
            Process::class.java.getMethod("isSdkSandbox").invoke(null) as Boolean
        }.getOrDefault(false)
    }
}
