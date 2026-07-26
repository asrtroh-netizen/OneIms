package com.onetools.app.live.capsule

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.WindowManager
import com.onetools.app.live.LiveStatusPrefs
import kotlin.math.abs

data class CameraAnchor(
    val centerX: Int,
    val centerY: Int,
    val width: Int,
    val height: Int,
)

enum class CameraExclusionMode {
    /** 整颗胶囊落在摄像头带下方（最安全，不挡摄）。 */
    BELOW,

    /** 胶囊竖直中心对齐摄像头（更像 MT/DI；展开卡从该带向下长）。 */
    CAMERA_CENTER,
}

/**
 * 干净室：借鉴 OneCapsule/MT「以挖孔为锚」的心智，自写实现。
 */
object CameraAnchorResolver {
    /** 含用户挖孔校准偏移（对照 OneCapsule overlayOffset）。 */
    fun resolve(context: Context): CameraAnchor {
        val raw = resolveRaw(context)
        val prefs = LiveStatusPrefs(context)
        val density = context.applicationContext.resources.displayMetrics.density.coerceAtLeast(0.5f)
        return applyCalibration(
            raw,
            calibXDp = prefs.cutoutCalibXDp,
            calibYDp = prefs.cutoutCalibYDp,
            density = density,
        )
    }

    /** 系统原始挖孔，不含用户校准（校准页展示用）。 */
    fun resolveRaw(context: Context): CameraAnchor {
        val app = context.applicationContext
        val wm = app.getSystemService(WindowManager::class.java)
        val density = app.resources.displayMetrics.density.coerceAtLeast(0.5f)
        val fallbackW = (24 * density).toInt().coerceAtLeast(16)
        val fallbackH = (24 * density).toInt().coerceAtLeast(16)
        if (wm == null) {
            val w = app.resources.displayMetrics.widthPixels
            val bar = statusBarHeight(app)
            return CameraAnchor(w / 2, bar / 2, fallbackW, fallbackH)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            val cutout = wm.currentWindowMetrics.windowInsets.displayCutout
            val rect = cutout?.boundingRects
                ?.minByOrNull { abs(it.centerX() - bounds.centerX()) }
            if (rect != null && rect.width() > 0 && rect.height() > 0) {
                return CameraAnchor(rect.centerX(), rect.centerY(), rect.width(), rect.height())
            }
            val bar = statusBarHeight(app)
            return CameraAnchor(bounds.centerX(), (bar * 0.55f).toInt().coerceAtLeast(fallbackH / 2), fallbackW, fallbackH)
        }
        val w = app.resources.displayMetrics.widthPixels
        val bar = statusBarHeight(app)
        return CameraAnchor(w / 2, (bar * 0.55f).toInt().coerceAtLeast(fallbackH / 2), fallbackW, fallbackH)
    }

    fun applyCalibration(
        raw: CameraAnchor,
        calibXDp: Int,
        calibYDp: Int,
        density: Float,
    ): CameraAnchor {
        val dx = (calibXDp * density).toInt()
        val dy = (calibYDp * density).toInt()
        return raw.copy(centerX = raw.centerX + dx, centerY = raw.centerY + dy)
    }

    private fun statusBarHeight(context: Context): Int {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) context.resources.getDimensionPixelSize(resId) else {
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                28f,
                context.resources.displayMetrics,
            ).toInt()
        }
    }
}

/**
 * 纯函数布局：便于单测，不依赖 View。
 */
object CameraAwareCapsuleLayout {
    data class Bounds(
        val topPx: Int,
        val centerXPx: Int,
        val gapWidthPx: Int,
    )

    fun compute(
        anchor: CameraAnchor,
        mode: CapsuleDisplayMode,
        pillHeightPx: Int,
        offsetYPx: Int,
        exclusion: CameraExclusionMode,
        cameraClearancePx: Int = 8,
    ): Bounds {
        val pillH = pillHeightPx.coerceAtLeast(18)
        val top = when (exclusion) {
            CameraExclusionMode.BELOW ->
                (anchor.centerY + anchor.height / 2 + cameraClearancePx + offsetYPx)
                    .coerceAtLeast(0)
            CameraExclusionMode.CAMERA_CENTER -> {
                val bandTop = (anchor.centerY - pillH / 2 + offsetYPx).coerceAtLeast(0)
                // 展开时顶边仍钉在摄像头带，内容向下长。
                bandTop
            }
        }
        val gap = when (exclusion) {
            CameraExclusionMode.CAMERA_CENTER ->
                (anchor.width + cameraClearancePx * 2).coerceAtLeast(16)
            CameraExclusionMode.BELOW -> 0
        }
        return Bounds(topPx = top, centerXPx = anchor.centerX, gapWidthPx = gap)
    }
}
