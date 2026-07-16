package com.oneims.app.core

import android.content.Context

/**
 * OneLink 桩：无 Mini ADB。真实实现仅存在于 onekuku flavor。
 */
object OneKukuMiniAdbClient {
    data class PairingInput(
        val code: String,
        val pairPortOverride: Int? = null,
    )

    sealed class Outcome {
        data object NeedPairingCode : Outcome()
        data class Success(val detail: String) : Outcome()
        data class Failed(val reason: String) : Outcome()
    }

    fun parsePairingInput(raw: String): PairingInput? = null

    suspend fun checkTransport(context: Context): Boolean = false

    suspend fun activateExistingOrNeedPair(
        context: Context,
        forceRestart: Boolean = false,
    ): Outcome = Outcome.Failed("onelink_no_embedded_adb")

    suspend fun pairConnectAndStart(context: Context, rawInput: String): Outcome =
        Outcome.Failed("onelink_no_embedded_adb")

    fun isWhitelistedShell(command: String): Boolean = false

    fun hostLoopback(): String = "127.0.0.1"
}
