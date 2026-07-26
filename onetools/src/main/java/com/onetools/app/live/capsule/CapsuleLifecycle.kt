package com.onetools.app.live.capsule

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.onetools.app.live.LiveStatusHub

/**
 * 会话生命周期：超时淡出、撤通知收岛。
 * Handler 懒创建，避免 JVM 单测在 clinit 触碰 Looper。
 */
object CapsuleLifecycle {
    const val STALE_MS = 15 * 60 * 1000L

    private var mainHandler: Handler? = null
    private var tickerStarted = false
    private var appContext: Context? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            pruneStale(System.currentTimeMillis())
            val h = mainHandler
            if (h != null && OneCapsuleStore.snapshot().sessions.isNotEmpty()) {
                h.postDelayed(this, 60_000L)
            } else {
                tickerStarted = false
            }
        }
    }

    fun attach(context: Context) {
        appContext = context.applicationContext
        ensureTicker()
    }

    fun onSessionTouched() {
        ensureTicker()
    }

    fun onNotificationRemoved(sessionId: String) {
        OneCapsuleStore.remove(sessionId)
        if (OneCapsuleStore.snapshot().sessions.isEmpty()) {
            appContext?.let { LiveStatusHub.clear(it) }
        }
    }

    fun pruneStale(nowMs: Long = System.currentTimeMillis()) {
        val staleIds = OneCapsuleStore.snapshot().sessions
            .filter { nowMs - it.updatedAtMs > STALE_MS }
            .map { it.id }
        staleIds.forEach { OneCapsuleStore.remove(it) }
        if (OneCapsuleStore.snapshot().sessions.isEmpty() && staleIds.isNotEmpty()) {
            appContext?.let { LiveStatusHub.clear(it) }
        }
    }

    private fun ensureTicker() {
        if (tickerStarted) return
        val h = handlerOrNull() ?: return
        tickerStarted = true
        h.removeCallbacks(tickRunnable)
        h.postDelayed(tickRunnable, 60_000L)
    }

    private fun handlerOrNull(): Handler? {
        mainHandler?.let { return it }
        return runCatching {
            Handler(Looper.getMainLooper()).also { mainHandler = it }
        }.getOrNull()
    }
}
