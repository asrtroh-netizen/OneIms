package com.oneims.app.core.privilege

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process

/**
 * OneBridge 客户端实现。binder 未送达时 [isRunning] 为 false，由 [FallbackPrivilegeBridge] 回落 Shizuku。
 */
class OneBridgePrivilegeBridge : PrivilegeBridge {
    override fun isRunning(): Boolean = BridgeBinderHolder.get() != null

    override fun isGranted(): Boolean {
        val remote = BridgeBinderHolder.get() ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(OneBridgeProtocol.DESCRIPTOR)
            data.writeInt(Process.myUid())
            remote.transact(OneBridgeProtocol.TRANSACTION_CHECK_PERMISSION, data, reply, 0)
            reply.readException()
            reply.readInt() == 1
        } catch (_: Throwable) {
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    override fun requestPermission(requestCode: Int) {
        // MVP：服务端按包名/uid 放行，无需弹窗；保留扩展点。
    }

    override fun getUid(): Int {
        val remote = BridgeBinderHolder.get() ?: return -1
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(OneBridgeProtocol.DESCRIPTOR)
            remote.transact(OneBridgeProtocol.TRANSACTION_GET_UID, data, reply, 0)
            reply.readException()
            reply.readInt()
        } catch (_: Throwable) {
            -1
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    override fun wrapSystemService(name: String): IBinder {
        require(name in PrivilegeBridge.MVP_SYSTEM_SERVICES) {
            "PrivilegeBridge MVP does not expose system service: $name"
        }
        val remote = checkNotNull(BridgeBinderHolder.get()) {
            "OneBridge is not running"
        }
        check(isGranted()) { "OneBridge is not granted" }
        return RemoteSystemServiceBinder(remote, name)
    }

    override fun addBinderReceivedListener(listener: () -> Unit, sticky: Boolean) {
        BridgeBinderHolder.addReceivedListener(listener, sticky)
    }

    override fun removeBinderReceivedListener(listener: () -> Unit) {
        BridgeBinderHolder.removeReceivedListener(listener)
    }

    override fun addBinderDeadListener(listener: () -> Unit) {
        BridgeBinderHolder.addDeadListener(listener)
    }

    override fun removeBinderDeadListener(listener: () -> Unit) {
        BridgeBinderHolder.removeDeadListener(listener)
    }

    override fun addRequestPermissionResultListener(
        listener: PrivilegeBridge.PermissionResultListener,
    ) {
        // OneBridge MVP：按包名/uid 静默放行，无授权弹窗回调。
    }

    override fun removeRequestPermissionResultListener(
        listener: PrivilegeBridge.PermissionResultListener,
    ) = Unit
}

/**
 * 把对 system service 的 transact 转发到 OneBridge TRANSACTION_TRANSACT_REMOTE。
 */
private class RemoteSystemServiceBinder(
    private val bridge: IBinder,
    private val serviceName: String,
) : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        val out = Parcel.obtain()
        val result = Parcel.obtain()
        return try {
            out.writeInterfaceToken(OneBridgeProtocol.DESCRIPTOR)
            out.writeString(serviceName)
            out.writeInt(code)
            out.writeInt(flags)
            out.appendFrom(data, 0, data.dataSize())
            bridge.transact(OneBridgeProtocol.TRANSACTION_TRANSACT_REMOTE, out, result, 0)
            result.readException()
            if (result.readInt() != 1) return false
            reply?.appendFrom(result, result.dataPosition(), result.dataAvail())
            true
        } catch (_: Throwable) {
            false
        } finally {
            out.recycle()
            result.recycle()
        }
    }

    override fun pingBinder(): Boolean = bridge.pingBinder()
}
