package com.onetools.app.special.broker

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.os.Process
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import com.onetools.app.special.privilege.SpecialPrivilege
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.UUID

/**
 * OneTools 特色功能系统 API 代理：CarrierConfig override + 默认数据卡切换。
 * 非 root 走 [SpecialBrokerInstrumentation]；root 直调 ICarrierConfigLoader。
 */
@SuppressLint("PrivateApi")
object SpecialBroker {
    private const val INSTRUMENTATION_FLAG_NO_RESTART = 1 shl 3
    private const val PER_USER_UID_RANGE = 100_000
    private const val OVERRIDE_READBACK_TIMEOUT_MS = 5_000L
    private const val RESULT_POLL_INTERVAL_MS = 100L

    @Volatile
    private var exempted = false

    private fun ensureExempt() {
        if (!exempted) {
            HiddenApiBypass.addHiddenApiExemptions("")
            exempted = true
        }
    }

    internal fun startShellPermissionDelegation(permissions: Set<String>) {
        require(permissions.isNotEmpty()) { "Delegated permission set is empty" }
        ensureExempt()
        val manager = activityManager()
        resolveStartDelegateMethod().invoke(manager, Process.myUid(), permissions.toTypedArray())
    }

    internal fun stopShellPermissionDelegation() {
        ensureExempt()
        val manager = activityManager()
        val stop = resolveStopDelegateMethod() ?: return
        stop.invoke(manager)
    }

    private fun activityManager(): Any {
        val binder: IBinder = SpecialPrivilege.wrapSystemService("activity")
        val stub = Class.forName("android.app.IActivityManager\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
    }

    private fun resolveStartDelegateMethod() =
        Class.forName("android.app.IActivityManager").methods.first { method ->
            method.name == "startDelegateShellPermissionIdentity" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                method.parameterTypes[1].isArray
        }

    private fun resolveStopDelegateMethod() =
        Class.forName("android.app.IActivityManager").methods.firstOrNull { method ->
            method.name.contains("Delegate", ignoreCase = true) &&
                method.name.contains("Shell", ignoreCase = true) &&
                (
                    method.name.contains("stop", ignoreCase = true) ||
                        method.name.contains("clear", ignoreCase = true) ||
                        method.name.contains("drop", ignoreCase = true)
                    ) &&
                method.parameterTypes.isEmpty()
        }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun getCarrierConfig(context: Context, subId: Int): PersistableBundle? {
        val manager = context.getSystemService(CarrierConfigManager::class.java) ?: return null
        return runCatching { manager.getConfigForSubId(subId) }.getOrNull()
    }

    fun overrideConfig(
        context: Context,
        subId: Int,
        bundle: PersistableBundle?,
        persistent: Boolean,
    ) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
        if (SpecialPrivilege.getUid() == 0) {
            shizukuOverride(subId, bundle, persistent)
        } else {
            executeBroker(context, SpecialBrokerProtocol.OP_OVERRIDE_CONFIG) {
                putInt(SpecialBrokerProtocol.ARG_SUB_ID, subId)
                putParcelable(SpecialBrokerProtocol.ARG_OVERRIDES, bundle)
                putInt(SpecialBrokerProtocol.ARG_PERSISTENT, if (persistent) 1 else 0)
            }
        }
        if (bundle != null) {
            awaitOverrideReadback(context, subId, bundle)
        }
    }

    /** 优先 persistent；被拒绝时回退 temporary。 */
    fun overrideConfigBestEffort(
        context: Context,
        subId: Int,
        bundle: PersistableBundle,
    ): Boolean {
        return runCatching {
            overrideConfig(context, subId, bundle, persistent = true)
            true
        }.getOrElse {
            overrideConfig(context, subId, bundle, persistent = false)
            false
        }
    }

    private fun shizukuOverride(subId: Int, bundle: PersistableBundle?, persistent: Boolean) {
        ensureExempt()
        val binder = SpecialPrivilege.wrapSystemService("carrier_config")
        val stub = Class.forName("com.android.internal.telephony.ICarrierConfigLoader\$Stub")
        val loader = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val cls = Class.forName("com.android.internal.telephony.ICarrierConfigLoader")
        val m3 = cls.methods.firstOrNull { method ->
            method.name == "overrideConfig" &&
                method.parameterTypes.size == 3 &&
                method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                method.parameterTypes[1] == PersistableBundle::class.java
        }
        if (m3 != null) {
            val overrideMode = when (m3.parameterTypes[2]) {
                Boolean::class.javaPrimitiveType -> persistent
                Int::class.javaPrimitiveType -> if (persistent) 1 else 0
                else -> error("Unsupported overrideConfig third parameter")
            }
            m3.invoke(loader, subId, bundle, overrideMode)
            return
        }
        cls.getMethod("overrideConfig", Int::class.javaPrimitiveType, PersistableBundle::class.java)
            .invoke(loader, subId, bundle)
    }

    private fun awaitOverrideReadback(
        context: Context,
        subId: Int,
        expected: PersistableBundle,
    ) {
        val deadline = System.currentTimeMillis() + OVERRIDE_READBACK_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val actual = getCarrierConfig(context, subId)
            if (actual != null && bundleContains(actual, expected)) return
            Thread.sleep(RESULT_POLL_INTERVAL_MS)
        }
        error("CarrierConfig write accepted, but readback did not match within 5 seconds")
    }

    @Suppress("DEPRECATION")
    private fun bundleContains(actual: PersistableBundle, expected: PersistableBundle): Boolean =
        expected.keySet().all { key ->
            if (!actual.containsKey(key)) return@all false
            valuesEqual(expected.get(key), actual.get(key))
        }

    private fun valuesEqual(expected: Any?, actual: Any?): Boolean = when (expected) {
        is BooleanArray -> actual is BooleanArray && expected.contentEquals(actual)
        is IntArray -> actual is IntArray && expected.contentEquals(actual)
        is Array<*> -> actual is Array<*> && expected.contentDeepEquals(actual)
        else -> expected == actual
    }

    @Synchronized
    private fun executeBroker(
        context: Context,
        operation: String,
        configure: Bundle.() -> Unit = {},
    ): String {
        try {
            ensureExempt()
            check(SpecialPrivilege.isRunning()) { "Shizuku is not running" }
            check(SpecialPrivilege.isGranted()) { "Shizuku is not granted" }
        } catch (error: Throwable) {
            throw SpecialBrokerException(
                message = SpecialErrors.describe(error),
                operationStarted = false,
                cause = error,
            )
        }
        val appContext = context.applicationContext
        val requestId = UUID.randomUUID().toString()
        val arguments = Bundle().apply {
            putString(SpecialBrokerProtocol.ARG_OPERATION, operation)
            putString(SpecialBrokerProtocol.ARG_REQUEST_ID, requestId)
            configure()
        }
        SpecialBrokerResultBus.register(requestId)
        SpecialBrokerResultStore.prepare(appContext, requestId)
        return try {
            val started = try {
                startBrokerInstrumentation(appContext, arguments)
            } catch (error: Throwable) {
                throw SpecialBrokerException(
                    message = SpecialErrors.describe(error),
                    operationStarted = false,
                    cause = error,
                )
            }
            if (!started) {
                throw SpecialBrokerException(
                    message =
                        "ActivityManager rejected SpecialBrokerInstrumentation" +
                            " (bridgeUid=${SpecialPrivilege.getUid()}, appUid=${Process.myUid()})",
                    operationStarted = false,
                )
            }
            val deadline = System.currentTimeMillis() + SpecialBrokerProtocol.RESULT_TIMEOUT_MS
            var result: SpecialBrokerResult? = null
            while (result == null && System.currentTimeMillis() < deadline) {
                result = SpecialBrokerResultBus.await(requestId, RESULT_POLL_INTERVAL_MS)
                    ?: SpecialBrokerResultStore.read(appContext, requestId)
            }
            val completed = result ?: throw SpecialBrokerException(
                message = "SpecialBrokerInstrumentation timed out",
                operationStarted = true,
            )
            if (!completed.success) {
                throw SpecialBrokerException(
                    message = completed.message,
                    operationStarted = completed.operationStarted,
                )
            }
            completed.message
        } finally {
            SpecialBrokerResultBus.remove(requestId)
            SpecialBrokerResultStore.remove(appContext, requestId)
        }
    }

    private fun startBrokerInstrumentation(context: Context, arguments: Bundle): Boolean {
        val manager = activityManager()
        val managerClass = Class.forName("android.app.IActivityManager")
        val method = managerClass.methods.firstOrNull { method ->
            method.name == "startInstrumentation" && method.parameterTypes.size == 8
        } ?: error("Compatible startInstrumentation API is unavailable")
        val connection = Class.forName("android.app.UiAutomationConnection")
            .getDeclaredConstructor()
            .newInstance()
        val userId = Process.myUid() / PER_USER_UID_RANGE
        return method.invoke(
            manager,
            ComponentName(context, SpecialBrokerInstrumentation::class.java),
            null,
            INSTRUMENTATION_FLAG_NO_RESTART,
            arguments,
            null,
            connection,
            userId,
            null,
        ) as Boolean
    }

    private fun subscriptionInterface(): Any {
        ensureExempt()
        val binder = SpecialPrivilege.wrapSystemService("isub")
        val stub = Class.forName("com.android.internal.telephony.ISub\$Stub")
        return checkNotNull(
            stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder),
        ) { "Subscription binder is unavailable" }
    }

    private fun telephonyInterface(): Any {
        ensureExempt()
        val binder = SpecialPrivilege.wrapSystemService("phone")
        val stub = Class.forName("com.android.internal.telephony.ITelephony\$Stub")
        return checkNotNull(
            stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder),
        ) { "Telephony binder is unavailable" }
    }

    fun getDefaultDataSubId(): Int {
        val hiddenResult = runCatching {
            val sub = subscriptionInterface()
            val cls = Class.forName("com.android.internal.telephony.ISub")
            cls.getMethod("getDefaultDataSubId").invoke(sub) as Int
        }.getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val publicResult = runCatching {
            SubscriptionManager.getDefaultDataSubscriptionId()
        }.getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        return when {
            hiddenResult >= 0 -> hiddenResult
            publicResult >= 0 -> publicResult
            else -> SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }

    fun setDefaultDataSubId(subId: Int) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
        val sub = subscriptionInterface()
        val cls = Class.forName("com.android.internal.telephony.ISub")
        cls.getMethod("setDefaultDataSubId", Int::class.javaPrimitiveType).invoke(sub, subId)
    }

    fun readUserMobileDataEnabled(subId: Int): Boolean? {
        if (subId < 0) return null
        val telephony = telephonyInterface()
        val cls = Class.forName("com.android.internal.telephony.ITelephony")
        listOf("isUserDataEnabled", "getDataEnabled", "isDataEnabled").forEach { methodName ->
            val value = runCatching {
                cls.getMethod(methodName, Int::class.javaPrimitiveType)
                    .invoke(telephony, subId) as Boolean
            }.getOrNull()
            if (value != null) return value
        }
        return null
    }

    fun enableUserMobileData(subId: Int) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
        val telephony = telephonyInterface()
        val cls = Class.forName("com.android.internal.telephony.ITelephony")
        val reasonUser = 0
        val fourArgumentMethod = runCatching {
            cls.getMethod(
                "setDataEnabledForReason",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                String::class.java,
            )
        }.getOrNull()
        if (fourArgumentMethod != null) {
            fourArgumentMethod.invoke(telephony, subId, reasonUser, true, "onetools-special")
            return
        }
        runCatching {
            cls.getMethod(
                "setDataEnabledForReason",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            ).invoke(telephony, subId, reasonUser, true)
        }.getOrElse {
            cls.getMethod(
                "setUserDataEnabled",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            ).invoke(telephony, subId, true)
        }
    }
}
