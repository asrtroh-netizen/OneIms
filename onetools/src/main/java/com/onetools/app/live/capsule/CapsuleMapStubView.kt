package com.onetools.app.live.capsule

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * 海报级「迷你地图」示意：不接真地图 SDK，只画路线与定位点。
 */
class CapsuleMapStubView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var accentColor: Int = 0xFF00B87A.toInt()
        set(value) {
            field = value
            invalidate()
        }

    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        fillPaint.color = ColorUtils.blendARGB(accentColor, 0xFFE8F5E9.toInt(), 0.82f)
        canvas.drawRoundRect(0f, 0f, w, h, h * 0.18f, h * 0.18f, fillPaint)

        // 淡网格
        fillPaint.color = ColorUtils.setAlphaComponent(0xFF000000.toInt(), 0x12)
        var x = w * 0.18f
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, fillPaint)
            x += w * 0.18f
        }
        var y = h * 0.22f
        while (y < h) {
            canvas.drawLine(0f, y, w, y, fillPaint)
            y += h * 0.22f
        }

        path.reset()
        path.moveTo(w * 0.12f, h * 0.72f)
        path.cubicTo(w * 0.28f, h * 0.2f, w * 0.55f, h * 0.85f, w * 0.82f, h * 0.28f)
        roadPaint.color = ColorUtils.setAlphaComponent(accentColor, 0x55)
        roadPaint.strokeWidth = 10f
        canvas.drawPath(path, roadPaint)
        roadPaint.color = accentColor
        roadPaint.strokeWidth = 4.5f
        canvas.drawPath(path, roadPaint)

        fillPaint.color = accentColor
        canvas.drawCircle(w * 0.12f, h * 0.72f, 7f, fillPaint)
        canvas.drawCircle(w * 0.82f, h * 0.28f, 9f, fillPaint)
        fillPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(w * 0.82f, h * 0.28f, 4f, fillPaint)
    }
}
