package com.oneims.app.core

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Instrumentation 特权代理与调用方之间的稳定协议。 */
internal object BrokerProtocol {
    const val OP_OVERRIDE_CONFIG = "override_config"
    const val OP_WRITE_GLOBAL_INT = "write_global_int"
    const val OP_INSERT_IMS_APN = "insert_ims_apn"

    private const val PERMISSION_MODIFY_PHONE_STATE =
        "android.permission.MODIFY_PHONE_STATE"
    private const val PERMISSION_WRITE_SECURE_SETTINGS =
        "android.permission.WRITE_SECURE_SETTINGS"
    private const val PERMISSION_WRITE_APN_SETTINGS =
        "android.permission.WRITE_APN_SETTINGS"

    const val ARG_OPERATION = "broker_operation"
    const val ARG_REQUEST_ID = "broker_request_id"
    const val ARG_SUB_ID = "broker_sub_id"
    const val ARG_OVERRIDES = "broker_overrides"
    const val ARG_SETTING_KEY = "broker_setting_key"
    const val ARG_SETTING_VALUE = "broker_setting_value"
    const val ARG_CONTENT_VALUES = "broker_content_values"

    const val RESULT_TIMEOUT_MS = 15_000L

    /**
     * 每次只委托并复核当前操作真正依赖的 shell 权限，
     * 避免系统补丁或 Shizuku 异常继续以模糊的 Provider 拒绝表现出来。
     */
    fun requiredPermissions(operation: String?): Set<String> = when (operation) {
        OP_OVERRIDE_CONFIG -> setOf(PERMISSION_MODIFY_PHONE_STATE)
        OP_WRITE_GLOBAL_INT -> setOf(PERMISSION_WRITE_SECURE_SETTINGS)
        OP_INSERT_IMS_APN -> setOf(PERMISSION_WRITE_APN_SETTINGS)
        else -> emptySet()
    }
}

internal data class BrokerResult(
    val success: Boolean,
    val message: String,
    val operationStarted: Boolean = false,
)

/**
 * 调用方必须知道失败发生在真正写入之前还是之后：前置权限委托失败时没有任何状态需要回滚，
 * 而操作已经开始后的超时/异常必须按“可能已写入”保守处理。
 */
internal class BrokerExecutionException(
    message: String,
    val operationStarted: Boolean,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * AOSP 的 ProcessRecord 同时只保存一个活动 Instrumentation。必须先同步结束当前代理，
 * 再让调用方看到结果；否则紧接着启动的新代理会覆盖活动指针，让旧代理永久残留在 AMS。
 */
internal object BrokerCompletionOrder {
    fun finishBeforePublishing(
        finishInstrumentation: () -> Unit,
        publishResult: () -> Unit,
    ): Throwable? {
        val finishError = runCatching(finishInstrumentation).exceptionOrNull()
        publishResult()
        return finishError
    }
}

internal enum class OperationFeedbackKind {
    INLINE,
    PERMISSION_DELEGATION_FAILED,
    ROLLBACK_FAILED,
    LONG_FAILURE,
    LONG_RESULT,
}

/** 把完整诊断保留在日志里，同时避免远程堆栈占满 Snackbar 与主界面。 */
internal object OperationFeedbackPolicy {
    private const val MAX_INLINE_CHARS = 220
    private const val MAX_INLINE_LINES = 2

    fun classify(message: String): OperationFeedbackKind {
        val normalized = message.trim()
        val lower = normalized.lowercase()
        if (
            "retrieve_window_content" in lower ||
            "registeruitestautomationservice" in lower ||
            "startdelegateshellpermissionidentity" in lower ||
            "permission delegation" in lower ||
            "shell permission delegate" in lower ||
            "配置未写入" in normalized ||
            "no configuration was written" in lower
        ) {
            return OperationFeedbackKind.PERMISSION_DELEGATION_FAILED
        }
        if (
            "自动回滚也失败" in normalized ||
            "automatic rollback also failed" in lower
        ) {
            return OperationFeedbackKind.ROLLBACK_FAILED
        }

        val isLong =
            normalized.length > MAX_INLINE_CHARS ||
                normalized.count { character -> character == '\n' } >= MAX_INLINE_LINES
        if (!isLong) return OperationFeedbackKind.INLINE

        val looksLikeFailure =
            "失败" in normalized ||
                "failed" in lower ||
                "exception" in lower ||
                "error" in lower ||
                "denied" in lower
        return if (looksLikeFailure) {
            OperationFeedbackKind.LONG_FAILURE
        } else {
            OperationFeedbackKind.LONG_RESULT
        }
    }
}

/**
 * 同进程优先用内存信号回传，确保调用方在 Instrumentation 结束前收到最终结果。
 * 文件结果是多进程/厂商改动下的兜底，不把一次系统差异变成无解释超时。
 */
internal object BrokerResultBus {
    private class Pending {
        private val latch = CountDownLatch(1)

        @Volatile
        var result: BrokerResult? = null
            private set

        fun complete(value: BrokerResult) {
            result = value
            latch.countDown()
        }

        fun await(timeoutMs: Long): BrokerResult? {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            return result
        }
    }

    private val pending = ConcurrentHashMap<String, Pending>()

    fun register(requestId: String) {
        check(pending.putIfAbsent(requestId, Pending()) == null) {
            "Duplicate broker request: $requestId"
        }
    }

    fun await(requestId: String, timeoutMs: Long): BrokerResult? =
        pending[requestId]?.await(timeoutMs)

    fun complete(requestId: String, result: BrokerResult) {
        pending[requestId]?.complete(result)
    }

    fun remove(requestId: String) {
        pending.remove(requestId)
    }
}

/** 进程间结果兜底；仅写应用缓存目录，不留下持久配置或敏感数据。 */
internal object BrokerResultStore {
    private const val DIRECTORY = "broker-results"
    private const val MAX_MESSAGE_BYTES = 32 * 1024
    private const val STALE_RESULT_MS = 24 * 60 * 60 * 1000L

    fun prepare(context: Context, requestId: String) {
        val directory = resultDirectory(context)
        directory.mkdirs()
        val now = System.currentTimeMillis()
        directory.listFiles()?.forEach { file ->
            if (now - file.lastModified() > STALE_RESULT_MS) {
                file.delete()
            }
        }
        resultFile(context, requestId).delete()
    }

    fun write(context: Context, requestId: String, result: BrokerResult) {
        val destination = resultFile(context, requestId)
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        val messageBytes = result.message.toByteArray(Charsets.UTF_8)
            .let { bytes -> if (bytes.size <= MAX_MESSAGE_BYTES) bytes else bytes.copyOf(MAX_MESSAGE_BYTES) }
        DataOutputStream(temporary.outputStream().buffered()).use { output ->
            output.writeBoolean(result.success)
            output.writeBoolean(result.operationStarted)
            output.writeInt(messageBytes.size)
            output.write(messageBytes)
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
    }

    fun read(context: Context, requestId: String): BrokerResult? {
        val file = resultFile(context, requestId)
        if (!file.isFile) return null
        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val success = input.readBoolean()
                val operationStarted = input.readBoolean()
                val size = input.readInt()
                require(size in 0..MAX_MESSAGE_BYTES) { "Invalid broker result size: $size" }
                val bytes = ByteArray(size)
                input.readFully(bytes)
                BrokerResult(
                    success = success,
                    message = bytes.toString(Charsets.UTF_8),
                    operationStarted = operationStarted,
                )
            }
        }.getOrNull()
    }

    fun remove(context: Context, requestId: String) {
        resultFile(context, requestId).delete()
    }

    private fun resultDirectory(context: Context): File =
        File(context.cacheDir, DIRECTORY)

    private fun resultFile(context: Context, requestId: String): File {
        val safeId = requestId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        require(safeId == requestId && requestId.isNotBlank()) { "Invalid broker request id" }
        return File(resultDirectory(context), "$safeId.bin")
    }
}

/** 反射与 Binder 常包裹真实异常；统一解包后再给用户，避免只显示空冒号。 */
internal object OperationErrors {
    fun describe(error: Throwable): String {
        val chain = mutableListOf<Throwable>()
        val visited = Collections.newSetFromMap(ConcurrentHashMap<Throwable, Boolean>())
        var current: Throwable? = error
        while (current != null && visited.add(current)) {
            chain += current
            current = current.cause
        }

        val meaningful = chain.asReversed().firstOrNull { !it.message.isNullOrBlank() }
            ?: chain.lastOrNull()
            ?: error
        val type = meaningful.javaClass.simpleName.ifBlank { "Error" }
        val message = meaningful.message?.trim().orEmpty()
        return if (message.isEmpty()) type else "$type: $message"
    }
}
