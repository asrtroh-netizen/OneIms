package com.onetools.app.special.broker

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * OneTools 特色功能：仅承接 CarrierConfig override 的短生命周期 Instrumentation。
 * 对齐 OneIMS [BrokerInstrumentation] 的 shell 权限委托路径，不带 APN/Global 旁路。
 */
@SuppressLint("PrivateApi")
class SpecialBrokerInstrumentation : Instrumentation() {
    private companion object {
        const val TAG = "SpecialBrokerInstr"
    }

    private var startupArguments: Bundle? = null

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        startupArguments = arguments
        start()
    }

    override fun onStart() {
        val arguments = startupArguments
        val requestId = arguments?.getString(SpecialBrokerProtocol.ARG_REQUEST_ID).orEmpty()
        val appContext = targetContext.applicationContext
        val operation = arguments?.getString(SpecialBrokerProtocol.ARG_OPERATION)
        var operationStarted = false
        val result = runCatching {
            require(arguments != null) { "Broker arguments are missing" }
            require(requestId.isNotBlank()) { "Broker request id is missing" }
            val required = SpecialBrokerProtocol.requiredPermissions(operation)
            require(required.isNotEmpty()) { "Unknown broker operation: ${operation ?: "null"}" }
            withDelegatedShellIdentity(appContext, required) {
                operationStarted = true
                execute(arguments)
            }
        }.fold(
            onSuccess = { message ->
                SpecialBrokerResult(success = true, message = message, operationStarted = operationStarted)
            },
            onFailure = { error ->
                SpecialBrokerResult(
                    success = false,
                    message = "${operation ?: "unknown"}: ${SpecialErrors.describe(error)}",
                    operationStarted = operationStarted,
                )
            },
        )

        val resultCode = if (result.success) Activity.RESULT_OK else Activity.RESULT_CANCELED
        val finishError = SpecialBrokerCompletion.finishBeforePublishing(
            finishInstrumentation = { finish(resultCode, Bundle()) },
            publishResult = {
                if (requestId.isNotBlank()) {
                    runCatching { SpecialBrokerResultStore.write(appContext, requestId, result) }
                        .onFailure { Log.w(TAG, "Failed to persist broker result", it) }
                    SpecialBrokerResultBus.complete(requestId, result)
                }
            },
        )
        if (finishError != null) {
            Log.w(TAG, "Failed to finish broker instrumentation", finishError)
        }
    }

    private fun execute(arguments: Bundle): String {
        return when (arguments.getString(SpecialBrokerProtocol.ARG_OPERATION)) {
            SpecialBrokerProtocol.OP_OVERRIDE_CONFIG -> {
                val subId = arguments.getInt(SpecialBrokerProtocol.ARG_SUB_ID, -1)
                require(subId >= 0) { "Invalid subscription id: $subId" }
                @Suppress("DEPRECATION")
                val overrides = arguments.getParcelable<PersistableBundle>(
                    SpecialBrokerProtocol.ARG_OVERRIDES,
                )
                overrideConfig(
                    context = targetContext.applicationContext,
                    subId = subId,
                    overrides = overrides,
                    persistent = arguments.getInt(SpecialBrokerProtocol.ARG_PERSISTENT, 0) != 0,
                )
                "ok"
            }
            else -> error("Unknown broker operation")
        }
    }

    @SuppressLint("MissingPermission")
    private fun overrideConfig(
        context: Context,
        subId: Int,
        overrides: PersistableBundle?,
        persistent: Boolean,
    ) {
        val manager = context.getSystemService(CarrierConfigManager::class.java)
            ?: error("CarrierConfigManager is unavailable")
        val managerClass = CarrierConfigManager::class.java
        val threeArgumentMethod = managerClass.methods.firstOrNull { method ->
            method.name == "overrideConfig" &&
                method.parameterTypes.size == 3 &&
                method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                method.parameterTypes[1] == PersistableBundle::class.java
        }
        if (threeArgumentMethod != null) {
            val overrideMode = when (threeArgumentMethod.parameterTypes[2]) {
                Boolean::class.javaPrimitiveType -> persistent
                Int::class.javaPrimitiveType -> if (persistent) 1 else 0
                else -> error("Unsupported overrideConfig third parameter")
            }
            threeArgumentMethod.invoke(manager, subId, overrides, overrideMode)
            return
        }
        val twoArgumentMethod = managerClass.getMethod(
            "overrideConfig",
            Int::class.javaPrimitiveType,
            PersistableBundle::class.java,
        )
        twoArgumentMethod.invoke(manager, subId, overrides)
    }

    private inline fun <T> withDelegatedShellIdentity(
        context: Context,
        requiredPermissions: Set<String>,
        block: () -> T,
    ): T {
        HiddenApiBypass.addHiddenApiExemptions("")
        SpecialBroker.startShellPermissionDelegation(requiredPermissions)
        var operationError: Throwable? = null
        return try {
            val missing = requiredPermissions.filter { permission ->
                context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
            }
            check(missing.isEmpty()) {
                "Shell permission delegation missing: ${missing.joinToString()}"
            }
            block()
        } catch (error: Throwable) {
            operationError = error
            throw error
        } finally {
            try {
                SpecialBroker.stopShellPermissionDelegation()
            } catch (cleanupError: Throwable) {
                if (operationError != null) {
                    operationError.addSuppressed(cleanupError)
                } else {
                    Log.w(TAG, "Shell permission stop failed: ${SpecialErrors.describe(cleanupError)}")
                }
            }
        }
    }
}
