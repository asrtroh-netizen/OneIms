package com.onetools.app.live

import android.content.Context
import com.onetools.app.live.capsule.OneCapsuleOverlay

/**
 * 兼容门面：旧调用点转到 [OneCapsuleOverlay]。
 */
class LiveStatusCapsuleOverlay private constructor(context: Context) {
    private val capsule = OneCapsuleOverlay.get(context)

    fun canDraw(): Boolean = capsule.canDraw()

    fun show(text: String) {
        // 旧 API：仅刷新布局；会话由 Hub / Store 驱动。
        capsule.start()
        capsule.applyLayoutFromPrefs()
    }

    fun update(text: String) {
        show(text)
    }

    fun applyLayoutFromPrefs() {
        capsule.applyLayoutFromPrefs()
    }

    fun hide() {
        capsule.stop()
    }

    companion object {
        fun get(context: Context): LiveStatusCapsuleOverlay =
            LiveStatusCapsuleOverlay(context.applicationContext)
    }
}
