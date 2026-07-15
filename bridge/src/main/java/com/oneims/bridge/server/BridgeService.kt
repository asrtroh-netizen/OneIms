package com.oneims.bridge.server

import android.annotation.SuppressLint
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.util.Log

/**
 * app_process 入口：持有 [BridgeBinder] 并向 OneIMS Provider 投递。
 */
object BridgeService {
    private const val TAG = "OneBridge"
    private const val RESEND_INTERVAL_MS = 3_000L

    @JvmStatic
    fun main(args: Array<String>) {
        // 必须先 prepareMainLooper，再 systemMain；否则 ActivityThread 建 Handler 会炸，
        // 后续 getContentProviderExternal 虽可能返回 holder，上下文却不完整。
        Looper.prepareMainLooper()
        runCatching {
            Class.forName("android.app.ActivityThread")
                .getDeclaredMethod("systemMain")
                .invoke(null)
        }.onFailure { Log.w(TAG, "ActivityThread.systemMain failed", it) }
        val binder = BridgeBinder()
        BridgeBinder.logReady()
        fun trySend(): Boolean =
            runCatching {
                BinderDistributor.sendToClient(binder)
                true
            }.getOrElse {
                Log.w(TAG, "send binder failed: ${it.message}")
                false
            }
        if (!trySend()) {
            Log.e(TAG, "binder NOT delivered to ${BridgeProtocol.CLIENT_PROVIDER_AUTHORITY}")
        }
        // 对齐 Shizuku：App 被划掉后进程仍在；新 App 起来后靠周期重投拿回 binder。
        Thread(
            {
                while (true) {
                    try {
                        Thread.sleep(RESEND_INTERVAL_MS)
                    } catch (_: InterruptedException) {
                        break
                    }
                    trySend()
                }
            },
            "onebridge-resend",
        ).apply {
            isDaemon = true
            start()
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

        val providerBinder = extractProviderBinder(holder)
            ?: error(
                "provider binder missing; holder=${holder.javaClass.name} " +
                    "fields=${holder.javaClass.fields.joinToString { it.name }}",
            )

        val extra = Bundle().apply {
            putBinder("binder", binder)
        }
        callProvider(providerBinder, auth, "sendBinder", extra)
        Log.i(TAG, "binder sent to $auth")
        runCatching {
            am.javaClass.methods.first { it.name.startsWith("removeContentProviderExternal") }
                .also { m ->
                    if (m.parameterTypes.size >= 2) m.invoke(am, auth, token)
                }
        }
    }

    /**
     * ContentProviderHolder.provider 类型是 [android.content.IContentProvider]，
     * 不是 [IBinder]。旧代码 `as? IBinder` 会恒为 null，误报 provider binder missing。
     */
    private fun extractProviderBinder(holder: Any): IBinder? {
        val field = (
            holder.javaClass.fields + holder.javaClass.declaredFields
            ).firstOrNull { it.name == "provider" }
            ?: return null
        field.isAccessible = true
        val value = field.get(holder) ?: return null
        return when (value) {
            is IBinder -> value
            is IInterface -> value.asBinder()
            else -> runCatching {
                value.javaClass.methods
                    .first { it.name == "asBinder" && it.parameterTypes.isEmpty() }
                    .invoke(value) as IBinder
            }.getOrNull()
        }
    }

    private fun activityManager(): Any {
        val clazz = Class.forName("android.app.ActivityManager")
        val getService = clazz.getDeclaredMethod("getService")
        getService.isAccessible = true
        return getService.invoke(null)!!
    }

    private fun callProvider(provider: IBinder, auth: String, method: String, extras: Bundle) {
        // 现代 Android 无 IContentProvider$Stub；framework 用 ContentProviderNative.asInterface。
        val iface = runCatching {
            Class.forName("android.content.ContentProviderNative")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, provider)
        }.recoverCatching {
            Class.forName("android.content.IContentProvider\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, provider)
        }.getOrThrow()!!

        val attribution = buildShellAttributionSource()
        val callMethods = iface.javaClass.methods.filter { it.name == "call" }
        val attempts = mutableListOf<Pair<java.lang.reflect.Method, Array<Any?>>>()
        for (m in callMethods.sortedByDescending { it.parameterTypes.size }) {
            val types = m.parameterTypes
            fun isAttr(c: Class<*>) = c.name == "android.content.AttributionSource"
            fun isStr(c: Class<*>) = c == String::class.java
            fun isBundle(c: Class<*>) = c == Bundle::class.java
            when {
                // call(AttributionSource, String authority, String method, String arg, Bundle)
                types.size == 5 && isAttr(types[0]) && isStr(types[1]) && isStr(types[2]) &&
                    (types[3] == String::class.java || types[3].name == "java.lang.String") &&
                    isBundle(types[4]) && attribution != null ->
                    attempts += m to arrayOf(attribution, auth, method, null, extras)

                // call(AttributionSource, String featureId, String authority, String method, String arg, Bundle)
                types.size == 6 && isAttr(types[0]) && isStr(types[1]) && isStr(types[2]) &&
                    isStr(types[3]) && isBundle(types[5]) && attribution != null ->
                    attempts += m to arrayOf(attribution, null, auth, method, null, extras)

                // call(String callingPkg, String authority, String method, String arg, Bundle)
                types.size == 5 && isStr(types[0]) && isStr(types[1]) && isStr(types[2]) &&
                    isBundle(types[4]) ->
                    attempts += m to arrayOf("com.android.shell", auth, method, null, extras)

                // call(String callingPkg, String featureId, String authority, String method, String arg, Bundle)
                types.size == 6 && isStr(types[0]) && isStr(types[2]) && isStr(types[3]) &&
                    isBundle(types[5]) ->
                    attempts += m to arrayOf("com.android.shell", null, auth, method, null, extras)

                // call(String authority, String method, String arg, Bundle) legacy
                types.size == 4 && isStr(types[0]) && isStr(types[1]) && isBundle(types[3]) ->
                    attempts += m to arrayOf(auth, method, null, extras)
            }
        }

        var last: Throwable? = null
        for ((m, args) in attempts) {
            try {
                m.invoke(iface, *args)
                Log.i(TAG, "provider.call ok via ${m.toGenericString()}")
                return
            } catch (t: Throwable) {
                last = t.cause ?: t
                Log.w(TAG, "provider.call fail ${m.parameterTypes.joinToString { it.simpleName }}: ${last.message}")
            }
        }
        throw IllegalStateException(
            "IContentProvider.call failed; tried=${attempts.size} methods=${callMethods.size}",
            last,
        )
    }

    private fun buildShellAttributionSource(): Any? = runCatching {
        val builderClz = Class.forName("android.content.AttributionSource\$Builder")
        val builder = builderClz.getConstructor(Int::class.javaPrimitiveType)
            .newInstance(2000)
        builderClz.getMethod("setPackageName", String::class.java)
            .invoke(builder, "com.android.shell")
        builderClz.getMethod("build").invoke(builder)
    }.onFailure {
        Log.w(TAG, "AttributionSource.Builder failed", it)
    }.getOrNull()


    /** 占位 token，满足 getContentProviderExternal 非空要求。 */
    private val BinderToken = object : Binder() {}
}
