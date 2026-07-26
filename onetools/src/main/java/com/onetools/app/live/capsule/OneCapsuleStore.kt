package com.onetools.app.live.capsule

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 多会话队列 + 展示模式。线程安全的简单内存真源。
 */
object OneCapsuleStore {
    private val sessions = CopyOnWriteArrayList<CapsuleSession>()
    private var activeIndex: Int = 0
    private var mode: CapsuleDisplayMode = CapsuleDisplayMode.HIDDEN
    private val listeners = CopyOnWriteArrayList<(CapsuleUiSnapshot) -> Unit>()

    fun snapshot(): CapsuleUiSnapshot =
        CapsuleUiSnapshot(mode, sessions.toList(), activeIndex)

    fun observe(listener: (CapsuleUiSnapshot) -> Unit) {
        listeners.add(listener)
        listener(snapshot())
    }

    fun removeObserver(listener: (CapsuleUiSnapshot) -> Unit) {
        listeners.remove(listener)
    }

    fun upsert(session: CapsuleSession, expand: Boolean = false) {
        val refreshed = session.copy(updatedAtMs = System.currentTimeMillis())
        val idx = sessions.indexOfFirst { it.id == refreshed.id }
        if (idx >= 0) sessions[idx] = refreshed else sessions.add(refreshed)
        activeIndex = sessions.indexOfFirst { it.id == refreshed.id }.coerceAtLeast(0)
        mode = if (expand) CapsuleDisplayMode.EXPANDED else CapsuleDisplayMode.PILL
        if (sessions.isEmpty()) mode = CapsuleDisplayMode.HIDDEN
        CapsuleLifecycle.onSessionTouched()
        emit()
    }

    fun remove(id: String) {
        val removedActive = sessions.getOrNull(activeIndex)?.id == id
        sessions.removeAll { it.id == id }
        if (sessions.isEmpty()) {
            activeIndex = 0
            mode = CapsuleDisplayMode.HIDDEN
        } else {
            if (removedActive) activeIndex = activeIndex.coerceIn(0, sessions.lastIndex)
            if (mode == CapsuleDisplayMode.HIDDEN) mode = CapsuleDisplayMode.PILL
        }
        emit()
    }

    fun clear() {
        sessions.clear()
        activeIndex = 0
        mode = CapsuleDisplayMode.HIDDEN
        emit()
    }

    fun setMode(newMode: CapsuleDisplayMode) {
        mode = when {
            sessions.isEmpty() -> CapsuleDisplayMode.HIDDEN
            newMode == CapsuleDisplayMode.HIDDEN -> CapsuleDisplayMode.HIDDEN
            else -> newMode
        }
        emit()
    }

    fun expand() {
        if (sessions.isNotEmpty()) setMode(CapsuleDisplayMode.EXPANDED)
    }

    fun collapse() {
        if (sessions.isNotEmpty()) setMode(CapsuleDisplayMode.PILL)
    }

    fun next() {
        if (sessions.size <= 1) return
        activeIndex = (activeIndex + 1) % sessions.size
        emit()
    }

    fun prev() {
        if (sessions.size <= 1) return
        activeIndex = if (activeIndex == 0) sessions.lastIndex else activeIndex - 1
        emit()
    }

    private fun emit() {
        val snap = snapshot()
        listeners.forEach { runCatching { it(snap) } }
    }
}
