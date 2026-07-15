package com.oneims.bridge.server

import android.annotation.SuppressLint
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * app_process 入口：持有 [BridgeBinder] 并向 OneIMS Provider 投递。
 */
object BridgeService {
    private const val TAG = "OneBridge"

    @JvmStatic
    fun main(args: Array<String>) {
        // 拉起系统 Context，供 PackageManager 白名单与 Provider 投递使用
        runCatching {
            Class.forName("android.app.ActivityThread")
                .getDeclaredMethod("systemMain")
                .invoke(null)
        }.onFailure { Log.w(TAG, "ActivityThread.systemMain failed", it) }
        Looper.prepareMainLooper()
        val binder = BridgeBinder()
        BridgeBinder.logReady()
        val sent = runCatching {
            BinderDistributor.sendToClient(binder)
            true
        }.getOrElse {
            Log.e(TAG, "send binder failed", it)
            false
        }
        if (!sent) {
            Log.e(TAG, "binder NOT delivered to ${BridgeProtocol.CLIENT_PROVIDER_AUTHORITY}")
        }
        Looper.loop()
    }
}

/**
 * 经 IActivityManager.getContentProviderExternal 把 binder 塞进 OneIMS。
 */
@SuppressLint("PrivateApi")
object BinderDistributor {
    private const val TAG = "OneBridge"

    fun sendToClient(binder: IBinder) {
        val userId = 0
        val auth = BridgeProtocol.CLIENT_PROVIDER_AUTHORITY
        val token = BinderToken
        val am = activityManager()
        val holder = am.javaClass.methods.first { m ->
            m.name == "getContentProviderExternal" && m.parameterTypes.size >= 3
        }.let { method ->
            when (method.parameterTypes.size) {
                4 -> method.invoke(am, auth, userId, token, auth)
                3 -> method.invoke(am, auth, userId, token)
                else -> method.invoke(am, auth, userId, token, auth)
            }
        } ?: error("getContentProviderExternal returned null")

        val provider = holder.javaClass.fields
            .firstOrNull { it.name == "provider" }
            ?.get(holder) as? IBinder
            ?: error("provider binder missing")

        val extra = Bundle().apply {
            putBinder("binder", binder)
        }
        callProvider(provider, auth, "sendBinder", extra)
        Log.i(TAG, "binder sent to $auth")
        runCatching {
            am.javaClass.methods.first { it.name.startsWith("removeContentProviderExternal") }
                .also { m ->
                    if (m.parameterTypes.size >= 2) m.invoke(am, auth, token)
                }
        }
    }

    private fun activityManager(): Any {
        val clazz = Class.forName("android.app.ActivityManager")
        val getService = clazz.getDeclaredMethod("getService")
        getService.isAccessible = true
        return getService.invoke(null)!!
    }

    private fun callProvider(provider: IBinder, auth: String, method: String, extras: Bundle) {
        // IContentProvider.call 在各 API 签名不同；用通用 transact 不稳定。
        // 优先反射 asInterface + call(...)
        val stub = Class.forName("android.content.IContentProvider\$Stub")
        val asInterface = stub.getMethod("asInterface", IBinder::class.java)
        val iface = asInterface.invoke(null, provider)!!
        val callMethods = iface.javaClass.methods.filter { it.name == "call" }
        val argsVariants: List<Array<Any?>> = listOf(
            arrayOf(null, auth, method, null, extras),
            arrayOf(null, "com.oneims.app", auth, method, null, extras),
            arrayOf("com.oneims.app", null, auth, method, null, extras),
            arrayOf(auth, method, null, extras),
        )
        var last: Throwable? = null
        for (m in callMethods.sortedByDescending { it.parameterTypes.size }) {
            for (args in argsVariants) {
                if (args.size != m.parameterTypes.size) continue
                try {
                    m.invoke(iface, *args)
                    return
                } catch (t: Throwable) {
                    last = t
                }
            }
        }
        throw IllegalStateException("IContentProvider.call failed", last)
    }

    /** 占位 token，满足 getContentProviderExternal 非空要求。 */
    private val BinderToken = object : Binder() {}
}
