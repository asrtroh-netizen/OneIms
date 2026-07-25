package com.onetools.app.special.broker

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object SpecialBrokerProtocol {
    const val OP_OVERRIDE_CONFIG = "override_config"
    const val PERMISSION_MODIFY_PHONE_STATE = "android.permission.MODIFY_PHONE_STATE"

    const val ARG_OPERATION = "broker_operation"
    const val ARG_REQUEST_ID = "broker_request_id"
    const val ARG_SUB_ID = "broker_sub_id"
    const val ARG_OVERRIDES = "broker_overrides"
    const val ARG_PERSISTENT = "broker_persistent"

    const val RESULT_TIMEOUT_MS = 15_000L

    fun requiredPermissions(operation: String?): Set<String> = when (operation) {
        OP_OVERRIDE_CONFIG -> setOf(PERMISSION_MODIFY_PHONE_STATE)
        else -> emptySet()
    }
}

internal data class SpecialBrokerResult(
    val success: Boolean,
    val message: String,
    val operationStarted: Boolean = false,
)

internal class SpecialBrokerException(
    message: String,
    val operationStarted: Boolean,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

internal object SpecialBrokerCompletion {
    fun finishBeforePublishing(
        finishInstrumentation: () -> Unit,
        publishResult: () -> Unit,
    ): Throwable? {
        val finishError = runCatching(finishInstrumentation).exceptionOrNull()
        publishResult()
        return finishError
    }
}

internal object SpecialBrokerResultBus {
    private class Pending {
        private val latch = CountDownLatch(1)

        @Volatile
        var result: SpecialBrokerResult? = null
            private set

        fun complete(value: SpecialBrokerResult) {
            result = value
            latch.countDown()
        }

        fun await(timeoutMs: Long): SpecialBrokerResult? {
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

    fun await(requestId: String, timeoutMs: Long): SpecialBrokerResult? =
        pending[requestId]?.await(timeoutMs)

    fun complete(requestId: String, result: SpecialBrokerResult) {
        pending[requestId]?.complete(result)
    }

    fun remove(requestId: String) {
        pending.remove(requestId)
    }
}

internal object SpecialBrokerResultStore {
    private const val DIRECTORY = "special-broker-results"
    private const val MAX_MESSAGE_BYTES = 32 * 1024
    private const val STALE_RESULT_MS = 24 * 60 * 60 * 1000L

    fun prepare(context: Context, requestId: String) {
        val directory = resultDirectory(context)
        directory.mkdirs()
        val now = System.currentTimeMillis()
        directory.listFiles()?.forEach { file ->
            if (now - file.lastModified() > STALE_RESULT_MS) file.delete()
        }
        resultFile(context, requestId).delete()
    }

    fun write(context: Context, requestId: String, result: SpecialBrokerResult) {
        val destination = resultFile(context, requestId)
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        val messageBytes = result.message.toByteArray(Charsets.UTF_8)
            .let { bytes ->
                if (bytes.size <= MAX_MESSAGE_BYTES) bytes else bytes.copyOf(MAX_MESSAGE_BYTES)
            }
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

    fun read(context: Context, requestId: String): SpecialBrokerResult? {
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
                SpecialBrokerResult(
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

internal object SpecialErrors {
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
