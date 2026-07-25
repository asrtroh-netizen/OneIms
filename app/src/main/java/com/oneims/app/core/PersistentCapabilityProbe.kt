package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.PersistableBundle
import android.util.Log

/**
 * 只读探测本机 Telephony 是否仍可能允许非 system 的 CarrierConfig 持久覆盖。
 *
 * 决策对齐社区 vvb2060/Ims 的 `canPersistent()`：反射
 * `com.android.phone.CarrierConfigLoader` 上是否存在 `isSystemApp` /
 * `isSdkSandboxUidInternal` 等门闩，**不绕过、不写入**。
 */
object PersistentCapabilityProbe {
    private const val TAG = "OneIMS-PersistProbe"
    private const val PHONE_PKG = "com.android.phone"
    private const val LOADER_CLASS = "com.android.phone.CarrierConfigLoader"

    enum class Outcome {
        /** 平台信号显示仍可尝试 persistent（老系统或沙盒持久路径未锁死）。 */
        LIKELY_ALLOWED,

        /** 平台已出现 isSystemApp + sandbox 内部校验，非 system 持久大概率被拒。 */
        LIKELY_BLOCKED,

        /** 反射/包上下文失败，无法判断。 */
        UNKNOWN,
    }

    data class Signals(
        val hasIsSystemApp: Boolean,
        val hasSecureOverrideConfig: Boolean,
        val hasIsSdkSandboxUidInternal: Boolean,
    )

    data class Result(
        val outcome: Outcome,
        val signals: Signals?,
        val errorHint: String? = null,
    )

    /**
     * 纯决策：便于单测。规则与 vvb2060 `ShizukuProvider.canPersistent` 同构：
     * - 无 `isSystemApp` → ALLOWED
     * - 有 `isSystemApp` 且有 `isSdkSandboxUidInternal` → BLOCKED
     * - 有 `isSystemApp` 但无 `isSdkSandboxUidInternal` → ALLOWED（仍可试沙盒持久）
     * - 无 `secureOverrideConfig` 时整段探测视为失败 → UNKNOWN（调用方用 null signals）
     */
    fun decide(signals: Signals): Outcome {
        if (!signals.hasIsSystemApp) return Outcome.LIKELY_ALLOWED
        if (!signals.hasSecureOverrideConfig) return Outcome.UNKNOWN
        return if (signals.hasIsSdkSandboxUidInternal) {
            Outcome.LIKELY_BLOCKED
        } else {
            Outcome.LIKELY_ALLOWED
        }
    }

    @SuppressLint("PrivateApi")
    fun probe(context: Context): Result {
        return try {
            val phone = context.createPackageContext(
                PHONE_PKG,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            )
            val clazz = phone.classLoader.loadClass(LOADER_CLASS)
            val hasIsSystemApp = hasDeclaredMethod(clazz, "isSystemApp")
            if (!hasIsSystemApp) {
                val signals = Signals(
                    hasIsSystemApp = false,
                    hasSecureOverrideConfig = false,
                    hasIsSdkSandboxUidInternal = false,
                )
                return Result(Outcome.LIKELY_ALLOWED, signals)
            }
            val hasSecure = hasDeclaredMethod(
                clazz,
                "secureOverrideConfig",
                PersistableBundle::class.java,
                Boolean::class.javaPrimitiveType,
            )
            if (!hasSecure) {
                return Result(
                    outcome = Outcome.UNKNOWN,
                    signals = Signals(
                        hasIsSystemApp = true,
                        hasSecureOverrideConfig = false,
                        hasIsSdkSandboxUidInternal = false,
                    ),
                    errorHint = "secureOverrideConfig missing",
                )
            }
            val hasSandboxUid = hasDeclaredMethod(
                clazz,
                "isSdkSandboxUidInternal",
                Int::class.javaPrimitiveType,
            )
            val signals = Signals(
                hasIsSystemApp = true,
                hasSecureOverrideConfig = true,
                hasIsSdkSandboxUidInternal = hasSandboxUid,
            )
            Result(decide(signals), signals)
        } catch (error: Throwable) {
            Log.i(TAG, "probe failed: ${error.javaClass.simpleName}: ${error.message}")
            Result(
                outcome = Outcome.UNKNOWN,
                signals = null,
                errorHint = error.javaClass.simpleName,
            )
        }
    }

    private fun hasDeclaredMethod(
        clazz: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>?,
    ): Boolean {
        return try {
            clazz.getDeclaredMethod(name, *parameterTypes)
            true
        } catch (_: NoSuchMethodException) {
            false
        }
    }
}
