package com.oneims.bridge.server

import android.annotation.SuppressLint
import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.util.Log

/**
 * shell 进程内的最小 Stub：ping / getUid / checkPermission / transactRemote（仅 MVP 四服务）。
 * 授权策略 MVP：调用方包名为 [BridgeProtocol.CLIENT_PACKAGE] 即放行（签名校验 Phase1.1 加固）。
 */
class BridgeBinder : Binder() {
    init {
        attachInterface(null, BridgeProtocol.DESCRIPTOR)
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            INTERFACE_TRANSACTION -> {
                reply?.writeString(BridgeProtocol.DESCRIPTOR)
                return true
            }
            BridgeProtocol.TRANSACTION_PING -> {
                data.enforceInterface(BridgeProtocol.DESCRIPTOR)
                reply?.writeNoException()
                reply?.writeInt(1)
                return true
            }
            BridgeProtocol.TRANSACTION_GET_UID -> {
                data.enforceInterface(BridgeProtocol.DESCRIPTOR)
                reply?.writeNoException()
                reply?.writeInt(Process.myUid())
                return true
            }
            BridgeProtocol.TRANSACTION_CHECK_PERMISSION -> {
                data.enforceInterface(BridgeProtocol.DESCRIPTOR)
                // 忽略 parcel 自报 uid，只信 Binder 调用方身份
                data.readInt()
                val allowed = isAllowedUid(getCallingUid())
                reply?.writeNoException()
                reply?.writeInt(if (allowed) 1 else 0)
                return true
            }
            BridgeProtocol.TRANSACTION_TRANSACT_REMOTE -> {
                data.enforceInterface(BridgeProtocol.DESCRIPTOR)
                enforceCallerIsClient()
                val serviceName = data.readString() ?: ""
                val serviceCode = data.readInt()
                val serviceFlags = data.readInt()
                require(serviceName in BridgeProtocol.MVP_SERVICES) {
                    "service not in MVP: $serviceName"
                }
                val target = checkNotNull(getSystemService(serviceName)) {
                    "system service missing: $serviceName"
                }
                val inData = Parcel.obtain()
                val outData = Parcel.obtain()
                try {
                    inData.appendFrom(data, data.dataPosition(), data.dataAvail())
                    val identity = clearCallingIdentity()
                    try {
                        target.transact(serviceCode, inData, outData, serviceFlags)
                    } finally {
                        restoreCallingIdentity(identity)
                    }
                    reply?.writeNoException()
                    reply?.writeInt(1)
                    reply?.appendFrom(outData, 0, outData.dataSize())
                } finally {
                    inData.recycle()
                    outData.recycle()
                }
                return true
            }
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun enforceCallerIsClient() {
        check(isAllowedUid(getCallingUid())) {
            "OneBridge rejects uid=${getCallingUid()}"
        }
    }

    private fun isAllowedUid(uid: Int): Boolean {
        if (uid == Process.myUid()) return true
        return packagesForUid(uid).any { it == BridgeProtocol.CLIENT_PACKAGE }
    }

    companion object {
        private const val TAG = "OneBridge"

        fun logReady() {
            Log.i(TAG, "BridgeBinder ready uid=${Process.myUid()} pid=${Process.myPid()}")
        }

        /** android.os.ServiceManager 为 hidden API，反射获取。 */
        private fun getSystemService(name: String): IBinder? = runCatching {
            val clazz = Class.forName("android.os.ServiceManager")
            clazz.getMethod("getService", String::class.java).invoke(null, name) as IBinder?
        }.getOrNull()

        @SuppressLint("PrivateApi")
        private fun packagesForUid(uid: Int): Array<String> = runCatching {
            val atClass = Class.forName("android.app.ActivityThread")
            val at = atClass.getMethod("currentActivityThread").invoke(null)
                ?: atClass.getMethod("systemMain").invoke(null)
            val ctx = at.javaClass.getMethod("getSystemContext").invoke(at) as Context
            ctx.packageManager.getPackagesForUid(uid) ?: emptyArray()
        }.getOrElse {
            Log.w(TAG, "packagesForUid($uid) failed", it)
            emptyArray()
        }
    }
}
