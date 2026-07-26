package com.onetools.app.live.capsule

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import kotlin.math.abs

/**
 * 展开/收起弹簧曲线（干净室）。
 * 控制点与时长来自 MT 学习包的心智对照（`CapsuleAnimParams` 量级），自写常量，不引用第三方类。
 */
object CapsuleMotion {
    /** ≈ MT dExpanding12 */
    const val EXPAND_MS = 300

    /** ≈ MT dNoBounceClose */
    const val COLLAPSE_MS = 260

    /** ≈ MT dExpanding22 / dCollapsedMain */
    const val PILL_PULSE_MS = 228

    /** ≈ MT fadeBlurAnimDuration */
    const val CROSSFADE_MS = 180

    /** 展开瞬间轻过冲（MT scaleAnim → 1.08） */
    const val PILL_OVERSHOOT = 1.08f

    // Cubic Bezier control points (x1,y1,x2,y2)
    private val EXPAND = floatArrayOf(0.20f, 0.00f, 0.00f, 1.00f) // easing12
    private val COLLAPSE = floatArrayOf(0.40f, 0.00f, 0.20f, 1.00f) // noBounceClose
    private val BOUNCE = floatArrayOf(0.14f, 0.00f, 0.14f, 1.07f) // bounceWidth
    private val OPEN_SOFT = floatArrayOf(0.20f, 0.15f, 0.00f, 1.00f) // noBounceOpen

    fun expandInterpolator(): Interpolator = PathInterpolator(EXPAND[0], EXPAND[1], EXPAND[2], EXPAND[3])

    fun collapseInterpolator(): Interpolator =
        PathInterpolator(COLLAPSE[0], COLLAPSE[1], COLLAPSE[2], COLLAPSE[3])

    fun bounceInterpolator(): Interpolator = PathInterpolator(BOUNCE[0], BOUNCE[1], BOUNCE[2], BOUNCE[3])

    fun softOpenInterpolator(): Interpolator =
        PathInterpolator(OPEN_SOFT[0], OPEN_SOFT[1], OPEN_SOFT[2], OPEN_SOFT[3])

    /**
     * 纯函数贝塞尔 Y（给定 t∈[0,1]），供 JVM 单测；用牛顿法求 x→t。
     */
    fun cubicBezierY(
        tInput: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Float {
        val t = tInput.coerceIn(0f, 1f)
        val solved = solveTForX(t, x1, x2)
        return bezierCoord(solved, y1, y2)
    }

    fun expandCurveY(t: Float): Float = cubicBezierY(t, EXPAND[0], EXPAND[1], EXPAND[2], EXPAND[3])

    fun collapseCurveY(t: Float): Float =
        cubicBezierY(t, COLLAPSE[0], COLLAPSE[1], COLLAPSE[2], COLLAPSE[3])

    private fun bezierCoord(t: Float, c1: Float, c2: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * c1 + 3f * u * t * t * c2 + t * t * t
    }

    private fun solveTForX(x: Float, x1: Float, x2: Float): Float {
        var t = x
        repeat(8) {
            val xAt = bezierCoord(t, x1, x2)
            val d = dx(t, x1, x2)
            if (abs(d) < 1e-6f) return t
            t = (t - (xAt - x) / d).coerceIn(0f, 1f)
        }
        return t
    }

    private fun dx(t: Float, x1: Float, x2: Float): Float {
        val u = 1f - t
        return 3f * u * u * x1 + 6f * u * t * (x2 - x1) + 3f * t * t * (1f - x2)
    }
}
