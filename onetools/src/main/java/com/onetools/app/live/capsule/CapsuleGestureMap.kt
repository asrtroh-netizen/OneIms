package com.onetools.app.live.capsule

/**
 * 干净室手势表：对照 OneCapsule/MT「手势 → 动作」心智，默认值保持现网行为。
 */
enum class CapsuleGestureSlot(val prefKey: String, val labelZh: String) {
    TAP("gesture_tap", "单击"),
    SWIPE_UP("gesture_swipe_up", "上滑"),
    SWIPE_DOWN("gesture_swipe_down", "下滑"),
    SWIPE_LEFT("gesture_swipe_left", "左滑"),
    SWIPE_RIGHT("gesture_swipe_right", "右滑"),
}

enum class CapsuleGestureAction(val labelZh: String) {
    NONE("无动作"),
    EXPAND("展开"),
    COLLAPSE("收起"),
    TOGGLE("展开/收起"),
    TOGGLE_LONG("短/长胶囊"),
    NEXT("下一会话"),
    PREV("上一会话"),
    ;

    companion object {
        fun fromPref(raw: String?, fallback: CapsuleGestureAction): CapsuleGestureAction =
            entries.find { it.name == raw } ?: fallback
    }
}

object CapsuleGestureDefaults {
    fun actionFor(slot: CapsuleGestureSlot): CapsuleGestureAction = when (slot) {
        // 单击留给系统/预留；展开大框走双击（Overlay.onDoubleTap）。
        CapsuleGestureSlot.TAP -> CapsuleGestureAction.NONE
        CapsuleGestureSlot.SWIPE_UP -> CapsuleGestureAction.COLLAPSE
        CapsuleGestureSlot.SWIPE_DOWN -> CapsuleGestureAction.EXPAND
        CapsuleGestureSlot.SWIPE_LEFT -> CapsuleGestureAction.TOGGLE_LONG
        CapsuleGestureSlot.SWIPE_RIGHT -> CapsuleGestureAction.PREV
    }

    fun cycle(current: CapsuleGestureAction): CapsuleGestureAction {
        val all = CapsuleGestureAction.entries
        return all[(all.indexOf(current) + 1) % all.size]
    }
}

object CapsuleGestureDispatcher {
    /** @return true if an action was dispatched */
    fun dispatch(action: CapsuleGestureAction): Boolean {
        when (action) {
            CapsuleGestureAction.NONE -> return false
            CapsuleGestureAction.EXPAND -> OneCapsuleStore.expand()
            CapsuleGestureAction.COLLAPSE -> OneCapsuleStore.collapse()
            CapsuleGestureAction.TOGGLE -> {
                val mode = OneCapsuleStore.snapshot().mode
                if (mode == CapsuleDisplayMode.PILL) OneCapsuleStore.expand()
                else if (mode == CapsuleDisplayMode.EXPANDED) OneCapsuleStore.collapse()
            }
            CapsuleGestureAction.TOGGLE_LONG -> OneCapsuleStore.togglePillSize()
            CapsuleGestureAction.NEXT -> OneCapsuleStore.next()
            CapsuleGestureAction.PREV -> OneCapsuleStore.prev()
        }
        return true
    }
}
