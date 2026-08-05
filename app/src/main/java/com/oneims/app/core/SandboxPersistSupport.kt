package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.os.Process
import android.util.Log
import com.oneims.app.core.privilege.PrivilegeBridges
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 南宫/vvb2060 3.1 同构的「SDK Sandbox Instrumentation 持久写」旁路。
 *
 * - 默认关；仅实验开关开启且平台探测 [PersistentCapabilityProbe.Outcome.LIKELY_ALLOWED]
 *   且未开强制临时时尝试。
 * - 失败一律回落既有 [CarrierConfigOverrideWriter] / Broker 主路径，不抬升为业务失败。
 */
object SandboxPersistSupport {
    private const val TAG = "OneIMS-SandboxPersist"
    private const val INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS = 1 shl 0
    /** AOSP ActivityManager.INSTR_FLAG_INSTRUMENT_SDK_SANDBOX */
    private const val INSTR_FLAG_INSTRUMENT_SDK_SANDBOX = 1 shl 5
    private const val START_TIMEOUT_MS = 12_000L
    private const val PER_USER_UID_RANGE = 100_000

    const val PROVIDER_SUFFIX = ".sandboxpersist"
    const val METHOD_HANDSHAKE = "sandbox_persist_handshake"
    const val EXTRA_BINDER = "binder"
    const val EXTRA_SUB_ID = "sub_id"
    const val EXTRA_BUNDLE = "overrides"
    const val TRANSACT_WRITE = 1

    fun isEnabled(context: Context): Boolean =
        ConfigStore.isSandboxPersistBypass(context)

    fun setEnabled(context: Context, enabled: Boolean) {
        ConfigStore.setSandboxPersistBypass(context, enabled)
    }

    fun shouldAttempt(context: Context): Boolean =
        evaluateAttemptGates(
            enabled = isEnabled(context),
            forceTemporary = ConfigStore.isForceTemporaryOverride(context),
            isRootUid = PrivilegeBridges.current.getUid() == 0,
            // 临时/永久 Root 任一可用时，公用「持久写」优先走 Root/XML，不走沙盒。
            hasUsableRoot = RootPresenceProbe.probe().any,
            probeOutcome = PersistentCapabilityProbe.probe(context).outcome,
        )

    /** 纯门禁逻辑，供单测；不读 Context。 */
    internal fun evaluateAttemptGates(
        enabled: Boolean,
        forceTemporary: Boolean,
        isRootUid: Boolean,
        probeOutcome: PersistentCapabilityProbe.Outcome,
        hasUsableRoot: Boolean = false,
    ): Boolean {
        if (!enabled) return false
        if (forceTemporary) return false
        if (isRootUid || hasUsableRoot) return false
        return probeOutcome == PersistentCapabilityProbe.Outcome.LIKELY_ALLOWED
    }

    /**
     * @return true=沙盒路径声明 persistent 写入成功；false=未尝试或失败（调用方应回落）。
     */
    @SuppressLint("PrivateApi")
    fun tryPersistentOverride(
        context: Context,
        subId: Int,
        bundle: PersistableBundle?,
    ): Boolean {
        if (!shouldAttempt(context)) return false
        if (bundle == null || bundle.keySet().isEmpty()) return false
        if (!PrivilegeBridges.current.isRunning() || !PrivilegeBridges.current.isGranted()) {
            Log.i(TAG, "skip sandbox persist: privilege channel not ready")
            return false
        }
        return runCatching {
            HiddenApiBypass.addHiddenApiExemptions("")
            val appContext = context.applicationContext
            val latch = CountDownLatch(1)
            val outcome = AtomicReference<Boolean?>(null)
            SandboxPersistBridge.register(latch, outcome)
            try {
                val args = Bundle().apply {
                    putInt(EXTRA_SUB_ID, subId)
                    putParcelable(EXTRA_BUNDLE, bundle)
                }
                val started = startSandboxInstrumentation(appContext, args)
                if (!started) {
                    Log.w(TAG, "startInstrumentation(sandbox) returned false")
                    return false
                }
                val done = latch.await(START_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                val ok = done && outcome.get() == true
                Log.i(TAG, "sandbox persist result ok=$ok timedOut=${!done}")
                if (ok) {
                    SystemApiBroker.markStrategy("sandbox-instrumentation-persist")
                }
                ok
            } finally {
                SandboxPersistBridge.clear()
            }
        }.getOrElse { error ->
            Log.w(TAG, "sandbox persist failed: ${error.message}")
            false
        }
    }

    @SuppressLint("PrivateApi")
    private fun startSandboxInstrumentation(context: Context, arguments: Bundle): Boolean {
        val binder = PrivilegeBridges.current.wrapSystemService("activity")
        val stub = Class.forName("android.app.IActivityManager\$Stub")
        val manager = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val managerClass = Class.forName("android.app.IActivityManager")
        val method = managerClass.methods.firstOrNull { candidate ->
            candidate.name == "startInstrumentation" && candidate.parameterTypes.size == 8
        } ?: error("startInstrumentation(8) unavailable")
        val connection = Class.forName("android.app.UiAutomationConnection")
            .getDeclaredConstructor()
            .newInstance()
        val flags = resolveSandboxFlags()
        val userId = Process.myUid() / PER_USER_UID_RANGE
        return method.invoke(
            manager,
            ComponentName(context, SandboxPersistInstrumentation::class.java),
            null,
            flags,
            arguments,
            null,
            connection,
            userId,
            null,
        ) as Boolean
    }

    private fun resolveSandboxFlags(): Int {
        val am = Class.forName("android.app.ActivityManager")
        val disableHidden = readFlag(am, "INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS")
            ?: INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS
        val sandbox = readFlag(am, "INSTR_FLAG_INSTRUMENT_SDK_SANDBOX")
            ?: INSTR_FLAG_INSTRUMENT_SDK_SANDBOX
        // 不用 NO_RESTART：与 vvb2060 一致，沙盒路径专走 INSTRUMENT_SDK_SANDBOX。
        return disableHidden or sandbox
    }

    private fun readFlag(amClass: Class<*>, name: String): Int? =
        runCatching { amClass.getField(name).getInt(null) }.getOrNull()
}

/** 进程内握手：Instrumentation / Provider 回传结果给发起方。 */
internal object SandboxPersistBridge {
    @Volatile
    private var latch: CountDownLatch? = null

    @Volatile
    private var outcome: AtomicReference<Boolean?>? = null

    fun register(latch: CountDownLatch, outcome: AtomicReference<Boolean?>) {
        this.latch = latch
        this.outcome = outcome
    }

    fun clear() {
        latch = null
        outcome = null
    }

    fun complete(success: Boolean) {
        outcome?.set(success)
        latch?.countDown()
    }
}
