package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.os.Process
import android.telephony.CarrierConfigManager
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.UUID

internal fun chooseDefaultDataSubId(hiddenResult: Int, publicResult: Int): Int =
    when {
        hiddenResult >= 0 -> hiddenResult
        publicResult >= 0 -> publicResult
        else -> android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

/**
 * 系统隐藏 API 代理层 —— OneIms 的多系统兼容核心。
 *
 * 2025-10 起系统禁止 shell UID 直接覆盖 CarrierConfig。普通 App 进程也不能自行调用
 * `startDelegateShellPermissionIdentity`，该 API 只接受由 shell 启动的活动 Instrumentation。
 * 本类因此负责启动短生命周期 [BrokerInstrumentation]，等待真实结果并回读验证；root
 * Shizuku 则仍可直接调用。所有 hidden API 都经反射访问，保持标准 Android SDK 可编译。
 */
@SuppressLint("PrivateApi")
object SystemApiBroker {
    private const val INSTRUMENTATION_FLAG_NO_RESTART = 1 shl 3
    private const val PER_USER_UID_RANGE = 100_000
    private const val OVERRIDE_READBACK_TIMEOUT_MS = 5_000L
    private const val RESULT_POLL_INTERVAL_MS = 100L

    /** 最近一次特权写入实际走的策略，供设备信息/诊断展示。初值 "unused" 为内部 token，展示时本地化。 */
    @Volatile
    var lastStrategy: String = "unused"
        private set

    @Volatile
    private var exempted = false

    private fun ensureExempt() {
        if (!exempted) {
            HiddenApiBypass.addHiddenApiExemptions("")
            exempted = true
        }
    }

    /** 当前系统是否具备 Instrumentation 权限代理所需的 framework 能力。 */
    fun supportsDelegate(): Boolean = runCatching {
        ensureExempt()
        resolveStartDelegateMethod()
        // stop 在部分预览/OEM 上可能改名或暂不可见；Instrumentation 结束仍会清委托，
        // 因此 supportsDelegate 只强制要求 start + startInstrumentation + UiAutomationConnection。
        check(
            Class.forName("android.app.IActivityManager").methods.any { method ->
                method.name == "startInstrumentation" && method.parameterTypes.size == 8
            },
        ) {
            "8-argument startInstrumentation API is unavailable"
        }
        Class.forName("android.app.UiAutomationConnection").getDeclaredConstructor()
        true
    }.getOrDefault(false)

    private fun activityManager(): Any {
        val binder: IBinder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity"))
        val stub = Class.forName("android.app.IActivityManager\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
    }

    private fun activityManagerInterface(): Class<*> =
        Class.forName("android.app.IActivityManager")

    private fun resolveStartDelegateMethod(): java.lang.reflect.Method {
        val iface = activityManagerInterface()
        val byBypass = runCatching {
            HiddenApiBypass.getDeclaredMethod(
                iface,
                "startDelegateShellPermissionIdentity",
                Int::class.javaPrimitiveType,
                Array<String>::class.java,
            )
        }.getOrNull()
        if (byBypass != null) return byBypass
        val byExact = runCatching {
            iface.getMethod(
                "startDelegateShellPermissionIdentity",
                Int::class.javaPrimitiveType,
                Array<String>::class.java,
            )
        }.getOrNull()
        if (byExact != null) return byExact
        val candidates = mutableListOf<java.lang.reflect.Method>()
        runCatching { HiddenApiBypass.getDeclaredMethods(iface) }.getOrNull()
            ?.filterIsInstance<java.lang.reflect.Method>()
            ?.let { candidates.addAll(it) }
        candidates.addAll(iface.methods)
        val fuzzy = candidates.firstOrNull { method ->
            method.name == "startDelegateShellPermissionIdentity" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                method.parameterTypes[1].isArray
        }
        return checkNotNull(fuzzy) {
            "startDelegateShellPermissionIdentity is unavailable"
        }
    }

    private fun resolveStopDelegateMethod(): java.lang.reflect.Method? {
        val iface = activityManagerInterface()
        val names = listOf(
            "stopDelegateShellPermissionIdentity",
            "clearDelegateShellPermissionIdentity",
            "dropDelegateShellPermissionIdentity",
        )
        // API 37+ 上标准 getMethod 常被 hidden-API 过滤成 NoSuchMethodException；
        // 先走 HiddenApiBypass，再回退公开反射与模糊名扫描。
        names.forEach { name ->
            runCatching {
                HiddenApiBypass.getDeclaredMethod(iface, name)
            }.getOrNull()?.let { return it }
            runCatching { iface.getMethod(name) }.getOrNull()?.let { return it }
            runCatching { iface.getDeclaredMethod(name) }.getOrNull()?.let { method ->
                method.isAccessible = true
                return method
            }
        }
        val candidates = mutableListOf<java.lang.reflect.Method>()
        runCatching { HiddenApiBypass.getDeclaredMethods(iface) }.getOrNull()
            ?.filterIsInstance<java.lang.reflect.Method>()
            ?.let { candidates.addAll(it) }
        candidates.addAll(iface.methods)
        runCatching { iface.declaredMethods.toList() }.getOrNull()?.let { candidates.addAll(it) }
        return candidates.firstOrNull { method ->
            method.parameterCount == 0 &&
                (
                    method.name.equals("stopDelegateShellPermissionIdentity", ignoreCase = true) ||
                        (
                            method.name.contains("Delegate", ignoreCase = true) &&
                                method.name.contains("Shell", ignoreCase = true) &&
                                (
                                    method.name.contains("stop", ignoreCase = true) ||
                                        method.name.contains("clear", ignoreCase = true) ||
                                        method.name.contains("drop", ignoreCase = true)
                                    )
                            )
                    )
        }
    }

    /**
     * 由 shell 启动的活动 Instrumentation 在自身进程内请求权限委托。
     * 这里经 ShizukuBinderWrapper 以 shell 身份直接调用 AMS，不能先走 UiAutomation：
     * 后者在建立连接时就要求应用持有 RETRIEVE_WINDOW_CONTENT，权限委托尚未开始会形成循环依赖。
     */
    internal fun startShellPermissionDelegation(permissions: Set<String>) {
        require(permissions.isNotEmpty()) { "Delegated permission set is empty" }
        ensureExempt()
        val manager = activityManager()
        resolveStartDelegateMethod().invoke(manager, Process.myUid(), permissions.toTypedArray())
    }

    /**
     * 结束 shell 权限委托。Android 17 / 部分 OEM 上 stop API 可能缺失或改名：
     * 清理失败不得拖垮已成功的写入——Instrumentation 退出时 AMS 仍会回收委托。
     */
    internal fun stopShellPermissionDelegation() {
        ensureExempt()
        val manager = activityManager()
        val stop = resolveStopDelegateMethod()
        if (stop == null) {
            android.util.Log.w(
                "SystemApiBroker",
                "stopDelegateShellPermissionIdentity unavailable; rely on Instrumentation teardown",
            )
            return
        }
        stop.invoke(manager)
    }

    @Synchronized
    private fun executeBroker(
        context: Context,
        operation: String,
        configure: Bundle.() -> Unit = {},
    ): String {
        try {
            ensureExempt()
            check(Shizuku.pingBinder()) {
                "OneKuku core service is not running"
            }
            check(
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED,
            ) {
                "OneKuku is not activated"
            }
        } catch (error: Throwable) {
            throw BrokerExecutionException(
                message = OperationErrors.describe(error),
                operationStarted = false,
                cause = error,
            )
        }
        val appContext = context.applicationContext
        val requestId = UUID.randomUUID().toString()
        val arguments = Bundle().apply {
            putString(BrokerProtocol.ARG_OPERATION, operation)
            putString(BrokerProtocol.ARG_REQUEST_ID, requestId)
            configure()
        }
        BrokerResultBus.register(requestId)
        BrokerResultStore.prepare(appContext, requestId)
        return try {
            val started = try {
                startBrokerInstrumentation(appContext, arguments)
            } catch (error: Throwable) {
                throw BrokerExecutionException(
                    message = OperationErrors.describe(error),
                    operationStarted = false,
                    cause = error,
                )
            }
            if (!started) {
                throw BrokerExecutionException(
                    message = "ActivityManager rejected BrokerInstrumentation",
                    operationStarted = false,
                )
            }
            val deadline = System.currentTimeMillis() + BrokerProtocol.RESULT_TIMEOUT_MS
            var result: BrokerResult? = null
            while (result == null && System.currentTimeMillis() < deadline) {
                result = BrokerResultBus.await(requestId, RESULT_POLL_INTERVAL_MS)
                    ?: BrokerResultStore.read(appContext, requestId)
            }
            val completed = result ?: throw BrokerExecutionException(
                message =
                    "BrokerInstrumentation timed out after ${BrokerProtocol.RESULT_TIMEOUT_MS} ms",
                operationStarted = true,
            )
            if (!completed.success) {
                throw BrokerExecutionException(
                    message = completed.message,
                    operationStarted = completed.operationStarted,
                )
            }
            completed.message
        } finally {
            BrokerResultBus.remove(requestId)
            BrokerResultStore.remove(appContext, requestId)
        }
    }

    private fun startBrokerInstrumentation(context: Context, arguments: Bundle): Boolean {
        val manager = activityManager()
        val managerClass = Class.forName("android.app.IActivityManager")
        val candidates = managerClass.methods.filter { method ->
            method.name == "startInstrumentation"
        }
        val method = candidates.firstOrNull { method ->
            method.parameterTypes.size == 8
        } ?: error(
            "Compatible startInstrumentation API is unavailable; signatures=" +
                candidates.joinToString { candidate ->
                    candidate.parameterTypes.joinToString(
                        prefix = "(",
                        postfix = ")",
                    ) { type -> type.simpleName }
                },
        )
        // AMS 以“非空 IUiAutomationConnection”判定该 Instrumentation 确由 shell/root 启动，
        // 权限委托依赖这个标记；Broker 本身绝不连接或调用 getUiAutomation()。
        val connection = Class.forName("android.app.UiAutomationConnection")
            .getDeclaredConstructor()
            .newInstance()
        val userId = Process.myUid() / PER_USER_UID_RANGE
        return method.invoke(
            manager,
            ComponentName(context, BrokerInstrumentation::class.java),
            null,
            INSTRUMENTATION_FLAG_NO_RESTART,
            arguments,
            null,
            connection,
            userId,
            null,
        ) as Boolean
    }

    /** root 模式不受 shell UID 限制，可直接调用，避免 root 无法创建 shell 委托。 */
    private fun shizukuOverride(subId: Int, bundle: PersistableBundle?, persistent: Boolean) {
        ensureExempt()
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("carrier_config"))
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
        val m2 = cls.getMethod("overrideConfig", Int::class.javaPrimitiveType, PersistableBundle::class.java)
        m2.invoke(loader, subId, bundle)
    }

    /**
     * bundle=null 表示清空覆盖；非 root 一律走活动 Instrumentation，并在返回前验证目标键。
     * OneKuku 业务写入应优先经 [CarrierConfigOverrideWriter]（默认 persistent=true）。
     */
    fun overrideConfig(context: Context, subId: Int, bundle: PersistableBundle?, persistent: Boolean) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
        if (runCatching { Shizuku.getUid() }.getOrDefault(-1) == 0) {
            shizukuOverride(subId, bundle, persistent)
            lastStrategy = "shizuku-root"
        } else {
            lastStrategy = "instrumentation-shell-delegate"
            executeBroker(context, BrokerProtocol.OP_OVERRIDE_CONFIG) {
                putInt(BrokerProtocol.ARG_SUB_ID, subId)
                putParcelable(BrokerProtocol.ARG_OVERRIDES, bundle)
                putInt(BrokerProtocol.ARG_PERSISTENT, if (persistent) 1 else 0)
            }
        }
        if (bundle != null) {
            awaitOverrideReadback(context, subId, bundle)
        }
    }

    fun writeGlobalInt(context: Context, key: String, value: Int) {
        require(key.isNotBlank()) { "Global setting key must not be blank" }
        lastStrategy = "instrumentation-shell-delegate"
        executeBroker(context, BrokerProtocol.OP_WRITE_GLOBAL_INT) {
            putString(BrokerProtocol.ARG_SETTING_KEY, key)
            putInt(BrokerProtocol.ARG_SETTING_VALUE, value)
        }
    }

    fun ensureImsApn(context: Context, values: ContentValues): String {
        lastStrategy = "instrumentation-shell-delegate"
        return executeBroker(context, BrokerProtocol.OP_INSERT_IMS_APN) {
            putParcelable(BrokerProtocol.ARG_CONTENT_VALUES, ContentValues(values))
        }
    }

    // ---------- provisioning（Shizuku 直调 + 返回码校验） ----------

    private fun shizukuProvision(subId: Int, key: Int, value: Int): Int {
        val tel = telephonyInterface()
        val cls = Class.forName("com.android.internal.telephony.ITelephony")
        val m = cls.getMethod(
            "setImsProvisioningInt",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        )
        return m.invoke(tel, subId, key, value) as Int
    }

    /** 返回码 0 才是真成功；旧实现只判断“没抛异常”，会把返回码 1 误报成成功。 */
    fun setProvisioningInt(subId: Int, key: Int, value: Int): Int {
        val result = shizukuProvision(subId, key, value)
        check(result == 0) { "IMS provisioning rejected key=$key, result=$result" }
        lastStrategy = "shizuku-direct"
        return result
    }

    /** 优先读用户 WFC 模式；接口缺失时退到 provisioning key 27，仍保留真实失败。 */
    fun getVoWiFiModeSetting(subId: Int): Int {
        val telephony = telephonyInterface()
        val telephonyClass = Class.forName("com.android.internal.telephony.ITelephony")
        return runCatching {
            telephonyClass.getMethod(
                "getVoWiFiModeSetting",
                Int::class.javaPrimitiveType,
            ).invoke(telephony, subId) as Int
        }.getOrElse {
            telephonyClass.getMethod(
                "getImsProvisioningInt",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).invoke(telephony, subId, ProvisioningKeys.KEY_VOICE_OVER_WIFI_MODE) as Int
        }
    }

    /**
     * 读取指定订阅当前生效的 CarrierConfig 全量键值（对齐 carrier-ims「配置全量查看」）。
     * READ_PHONE_STATE 已由 UI 正常申请，直接从 App UID 读取即可，不再误用 Instrumentation 委托。
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun getCarrierConfig(context: Context, subId: Int): PersistableBundle? {
        val manager = context.getSystemService(CarrierConfigManager::class.java) ?: return null
        return runCatching {
            manager.getConfigForSubId(subId)
        }.getOrNull()
    }

    private fun awaitOverrideReadback(
        context: Context,
        subId: Int,
        expected: PersistableBundle,
    ) {
        val deadline = System.currentTimeMillis() + OVERRIDE_READBACK_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val actual = getCarrierConfig(context, subId)
            if (actual != null && bundleContains(actual, expected)) {
                return
            }
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
        is DoubleArray -> actual is DoubleArray && expected.contentEquals(actual)
        is IntArray -> actual is IntArray && expected.contentEquals(actual)
        is LongArray -> actual is LongArray && expected.contentEquals(actual)
        is Array<*> -> actual is Array<*> && expected.contentDeepEquals(actual)
        else -> expected == actual
    }

    private fun subscriptionInterface(): Any {
        ensureExempt()
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("isub"))
        val stub = Class.forName("com.android.internal.telephony.ISub\$Stub")
        return checkNotNull(
            stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder),
        ) {
            "Subscription binder is unavailable"
        }
    }

    /**
     * 读取系统当前的默认移动数据 subId（AOSP `ISub.getDefaultDataSubId()`）。
     * 隐藏 Binder 失败或返回 INVALID 时显式回退公开 API，避免把 sentinel 当成成功读回。
     */
    fun getDefaultDataSubId(): Int {
        val hiddenResult = runCatching {
            val sub = subscriptionInterface()
            val cls = Class.forName("com.android.internal.telephony.ISub")
            cls.getMethod("getDefaultDataSubId").invoke(sub) as Int
        }.getOrDefault(android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val publicResult = runCatching {
            android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()
        }.getOrDefault(android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        return chooseDefaultDataSubId(hiddenResult, publicResult)
    }

    /**
     * 切换默认移动数据 subId（AOSP `ISub.setDefaultDataSubId(int)`）——应用内与控制中心磁贴
     * 共用的唯一真实执行路径，走与 [resetIms]/[queryImsRegistration] 同一套 Shizuku 直调 binder 模式，
     * 不经 Instrumentation 委托（该操作不受 2025 新补丁的 shell 委托限制）。
     * 调用方（[DataSimSwitchManager]）负责切换前校验与切换后延迟回读，这里只做一次系统调用。
     */
    fun setDefaultDataSubId(subId: Int) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
        val sub = subscriptionInterface()
        val cls = Class.forName("com.android.internal.telephony.ISub")
        cls.getMethod("setDefaultDataSubId", Int::class.javaPrimitiveType).invoke(sub, subId)
        lastStrategy = "shizuku-direct"
    }

    /**
     * 读取指定订阅的“用户移动数据开关”状态。优先使用当前 AOSP 的
     * `isUserDataEnabled(int)`，并兼容仍保留旧入口的系统；全部入口都不可用时返回 null，
     * 调用方不得基于未知状态擅自开启或关闭移动数据。
     */
    fun readUserMobileDataEnabled(subId: Int): Boolean? {
        if (subId < 0) return null
        val telephony = telephonyInterface()
        val cls = Class.forName("com.android.internal.telephony.ITelephony")
        val methodNames = listOf("isUserDataEnabled", "getDataEnabled", "isDataEnabled")
        methodNames.forEach { methodName ->
            val value = runCatching {
                cls.getMethod(methodName, Int::class.javaPrimitiveType)
                    .invoke(telephony, subId) as Boolean
            }.getOrNull()
            if (value != null) return value
        }
        return null
    }

    /**
     * 仅用于恢复默认数据卡切换过程中被系统意外关闭的用户移动数据开关。
     * 没有对应的 disable 方法，避免该恢复通道被误用于主动关闭数据。
     */
    fun enableUserMobileData(subId: Int) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
        val telephony = telephonyInterface()
        val cls = Class.forName("com.android.internal.telephony.ITelephony")
        val reasonUser = 0 // TelephonyManager.DATA_ENABLED_REASON_USER
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
            fourArgumentMethod.invoke(
                telephony,
                subId,
                reasonUser,
                true,
                "com.android.shell",
            )
        } else {
            cls.getMethod(
                "setDataEnabledForReason",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            ).invoke(telephony, subId, reasonUser, true)
        }
        lastStrategy = "shizuku-direct"
    }

    private fun telephonyInterface(): Any {
        ensureExempt()
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("phone"))
        val stub = Class.forName("com.android.internal.telephony.ITelephony\$Stub")
        return checkNotNull(
            stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder),
        ) {
            "Telephony binder is unavailable"
        }
    }

    /**
     * 让 telephony framework 先注销再启用指定卡槽的 IMS。
     * AOSP `ITelephony.resetIms(int slotIndex)` 只重建 IMS 注册链，不切飞行模式、
     * 不改首选网络类型；调用方仍需提示短暂通话中断风险。
     */
    fun resetIms(slotIndex: Int) {
        require(slotIndex >= 0) { "Invalid SIM slot index: $slotIndex" }
        val telephony = telephonyInterface()
        val telephonyClass = Class.forName("com.android.internal.telephony.ITelephony")
        telephonyClass.getMethod(
            "resetIms",
            Int::class.javaPrimitiveType,
        ).invoke(telephony, slotIndex)
        lastStrategy = "shizuku-direct"
    }

    /**
     * 查询 IMS 注册态（对齐 carrier-ims「IMS 注册状态查询」）。
     *
     * ⚠️ `getImsRegTechnologyForMmTel(int)` 定义在 `ITelephony`（AIDL 内部接口）上，**不是** `TelephonyManager`
     * 的方法；故复用项目既有的 Shizuku 直调模式（同 [shizukuProvision]）——包 "phone" 服务 binder，以 shell 身份调。
     * 返回值对齐 AOSP `REGISTRATION_TECH_*`：>=0 表示 MmTel（VoLTE/VoWiFi/VoNR）已注册；只读探测，读不到返回 [ImsRegInfo.unknown]。
     */
    fun queryImsRegistration(subId: Int): ImsRegInfo {
        return runCatching {
            val tel = telephonyInterface()
            val cls = Class.forName("com.android.internal.telephony.ITelephony")
            val m = cls.getMethod("getImsRegTechnologyForMmTel", Int::class.javaPrimitiveType)
            val tech = m.invoke(tel, subId) as Int
            ImsRegInfo(
                registered = tech >= 0,
                radioTech = tech,
                querySucceeded = true,
            )
        }.getOrDefault(ImsRegInfo.unknown())
    }

    /**
     * IMS 注册态查询结果。radioTech 严格对齐 AOSP `ImsRegistrationImplBase.REGISTRATION_TECH_*`：
     * -1=未注册/未知，0=LTE，1=IWLAN，2=CROSS_SIM，3=NR，4=3G。
     * 展示名走字符串资源 [techLabelRes]，由调用方用 Context 解析以支持中/英。
     */
    data class ImsRegInfo(
        val registered: Boolean,
        val radioTech: Int,
        val querySucceeded: Boolean,
    ) {
        @androidx.annotation.StringRes
        fun techLabelRes(): Int = when (radioTech) {
            0 -> com.oneims.app.R.string.tech_lte
            1 -> com.oneims.app.R.string.tech_iwlan
            2 -> com.oneims.app.R.string.tech_crosssim
            3 -> com.oneims.app.R.string.tech_nr
            4 -> com.oneims.app.R.string.tech_3g
            else -> com.oneims.app.R.string.tech_none
        }

        companion object {
            fun unknown() = ImsRegInfo(
                registered = false,
                radioTech = -1,
                querySucceeded = false,
            )
        }
    }
}
