package com.oneims.bridge.server

import android.annotation.SuppressLint
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * app_process 入口：持有 [BridgeBinder] 并向 OneIMS Provider 投递。
 *
 * 投递模型对齐邻仓 Shizuku：
 * - 启动时投递一次（失败可短重试，见 [ServiceStarter]）
 * - **无**周期 sleep 重投（旧 3s 循环会打满客户端 reapply）
 * - 客户端进程/UID 起来时再投一次（对齐 [BinderSender] 的 Process/UidObserver）
 * - 客户端 Provider 侧若已有 living binder 则忽略（对齐 [ShizukuProvider.handleSendBinder]）
 */
object BridgeService {
    private const val TAG = "OneBridge"

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
            // 对齐 starter：短暂等待后再试一次（不 force-stop 用户 App）
            runCatching { Thread.sleep(1_000L) }
            if (!trySend()) {
                Log.e(TAG, "binder retry still failed")
            }
        }
        ClientBinderSender.register(onClientReady = { trySend() })
        Looper.loop()
    }
}

/**
 * 对齐 Shizuku [BinderSender]：客户端 UID/进程起来时再投递 binder，
 * 代替「死循环 sleep 重投」。
 */
@SuppressLint("PrivateApi")
object ClientBinderSender {
    private const val TAG = "OneBridge"
    private const val UID_OBSERVER_ACTIVE = 1 shl 0
    private const val UID_OBSERVER_GONE = 1 shl 1
    private const val UID_OBSERVER_IDLE = 1 shl 2
    private const val UID_OBSERVER_CACHED = 1 shl 3

    private val startedUids = CopyOnWriteArrayList<Int>()

    fun register(onClientReady: () -> Unit) {
        val am = activityManager() ?: run {
            Log.w(TAG, "ActivityManager unavailable; skip BinderSender-style observers")
            return
        }
        registerUidObserver(am, onClientReady)
        registerProcessObserver(am, onClientReady)
    }

    private fun activityManager(): Any? = runCatching {
        Class.forName("android.app.ActivityManager")
            .getDeclaredMethod("getService")
            .apply { isAccessible = true }
            .invoke(null)
    }.onFailure { Log.w(TAG, "get ActivityManager failed", it) }.getOrNull()

    private fun packagesForUid(uid: Int): List<String> = runCatching {
        val pm = Class.forName("android.app.ActivityThread")
            .getDeclaredMethod("getPackageManager")
            .invoke(null)
        @Suppress("UNCHECKED_CAST")
        val pkgs = pm.javaClass.methods
            .first { it.name == "getPackagesForUid" && it.parameterTypes.size == 1 }
            .invoke(pm, uid) as? Array<String>
        pkgs?.toList().orEmpty()
    }.getOrDefault(emptyList())

    private fun isOneImsClient(uid: Int): Boolean =
        packagesForUid(uid).any { it == BridgeProtocol.CLIENT_PACKAGE }

    private fun onUidStarts(uid: Int, onClientReady: () -> Unit) {
        if (!isOneImsClient(uid)) return
        if (startedUids.contains(uid)) {
            Log.v(TAG, "Uid $uid already starts")
            return
        }
        startedUids.add(uid)
        Log.i(TAG, "OneIMS uid=$uid starts; send binder")
        onClientReady()
    }

    private fun onUidGone(uid: Int) {
        startedUids.remove(uid)
        Log.v(TAG, "Uid $uid gone")
    }

    private fun registerUidObserver(am: Any, onClientReady: () -> Unit) {
        runCatching {
            val stubClz = Class.forName("android.app.IUidObserver\$Stub")
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                stubClz.classLoader,
                arrayOf(Class.forName("android.app.IUidObserver")),
            ) { _, method, args ->
                when (method.name) {
                    "onUidActive" -> {
                        val uid = args?.getOrNull(0) as? Int ?: return@newProxyInstance null
                        onUidStarts(uid, onClientReady)
                    }
                    "onUidIdle" -> {
                        val uid = args?.getOrNull(0) as? Int ?: return@newProxyInstance null
                        onUidStarts(uid, onClientReady)
                    }
                    "onUidCachedChanged" -> {
                        val uid = args?.getOrNull(0) as? Int ?: return@newProxyInstance null
                        val cached = args.getOrNull(1) as? Boolean ?: true
                        if (!cached) onUidStarts(uid, onClientReady)
                    }
                    "onUidGone" -> {
                        val uid = args?.getOrNull(0) as? Int ?: return@newProxyInstance null
                        onUidGone(uid)
                    }
                    "asBinder" -> Binder()
                    else -> null
                }
            }
            val which = UID_OBSERVER_ACTIVE or UID_OBSERVER_GONE or
                UID_OBSERVER_IDLE or UID_OBSERVER_CACHED
            val methods = am.javaClass.methods.filter { it.name == "registerUidObserver" }
            var ok = false
            for (m in methods.sortedByDescending { it.parameterTypes.size }) {
                val types = m.parameterTypes
                try {
                    when (types.size) {
                        4 -> m.invoke(am, proxy, which, -1, null)
                        5 -> m.invoke(am, proxy, which, -1, null, null)
                        else -> continue
                    }
                    ok = true
                    Log.i(TAG, "registerUidObserver ok via ${m.toGenericString()}")
                    break
                } catch (t: Throwable) {
                    Log.w(TAG, "registerUidObserver fail ${types.size}: ${t.message}")
                }
            }
            if (!ok) Log.w(TAG, "registerUidObserver unavailable")
        }.onFailure { Log.w(TAG, "registerUidObserver failed", it) }
    }

    private fun registerProcessObserver(am: Any, onClientReady: () -> Unit) {
        runCatching {
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                Class.forName("android.app.IProcessObserver").classLoader,
                arrayOf(Class.forName("android.app.IProcessObserver")),
            ) { _, method, args ->
                when (method.name) {
                    "onForegroundActivitiesChanged" -> {
                        val uid = args?.getOrNull(1) as? Int ?: return@newProxyInstance null
                        val fg = args.getOrNull(2) as? Boolean ?: false
                        if (fg) onUidStarts(uid, onClientReady)
                    }
                    "onProcessStateChanged" -> {
                        val uid = args?.getOrNull(1) as? Int ?: return@newProxyInstance null
                        onUidStarts(uid, onClientReady)
                    }
                    "asBinder" -> Binder()
                    else -> null
                }
            }
            val m = am.javaClass.methods.first { it.name == "registerProcessObserver" }
            m.invoke(am, proxy)
            Log.i(TAG, "registerProcessObserver ok")
        }.onFailure { Log.w(TAG, "registerProcessObserver failed", it) }
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
