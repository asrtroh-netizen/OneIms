package com.oneims.app.core

import android.content.Context

/**
 * OneLink 桩：无内嵌 ADB。真实实现仅存在于 onekuku flavor。
 */
object OneKukuEmbeddedAdbActivator {
    sealed class Outcome {
        data object NeedPairingCode : Outcome()
        data class Success(val detail: String) : Outcome()
        data class Failed(val reason: String) : Outcome()
    }

    fun hasPairedOnce(context: Context): Boolean = false

    fun markPairedOnce(context: Context) = Unit

    suspend fun activate(
        context: Context,
        pairingCode: String? = null,
        pairPortOverride: Int? = null,
        forceRestart: Boolean = false,
    ): Outcome = Outcome.Failed("onelink_no_embedded_adb")

    data class ShellExecResult(
        val ok: Boolean,
        val output: String,
        val reason: String = "",
    )

    suspend fun execWhitelistedShell(
        context: Context,
        command: String,
        timeoutMs: Long = 120_000L,
    ): ShellExecResult = ShellExecResult(false, "", "onelink_no_embedded_adb")
}
