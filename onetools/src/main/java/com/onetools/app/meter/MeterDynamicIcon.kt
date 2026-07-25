package com.onetools.app.meter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.roundToInt

/**
 * Notification / QS glyphs.
 * Status-bar [smallIcon] MUST be alpha-only white silhouette (Android ignores RGB → black/white block otherwise).
 * Shade largeIcon / QS can use the tinted variant.
 *
 * BOTH mode draws stacked ↓ / ↑ lines so the ~24dp status glyph still shows both directions.
 */
object MeterDynamicIcon {
    fun createSilhouette(
        downBps: Long,
        upBps: Long,
        mode: MeterDisplayMode,
        order: MeterSpeedOrder = MeterSpeedOrder.DOWN_THEN_UP,
    ): Bitmap = draw(linesFor(mode, downBps, upBps, order), silhouette = true)

    fun create(
        downBps: Long,
        upBps: Long,
        mode: MeterDisplayMode,
        order: MeterSpeedOrder = MeterSpeedOrder.DOWN_THEN_UP,
    ): Bitmap = draw(linesFor(mode, downBps, upBps, order), silhouette = false)

    private fun linesFor(
        mode: MeterDisplayMode,
        down: Long,
        up: Long,
        order: MeterSpeedOrder,
    ): List<String> {
        val downLine = "↓${shortLabel(down)}"
        val upLine = "↑${shortLabel(up)}"
        return when (mode) {
            MeterDisplayMode.BOTH -> when (order) {
                MeterSpeedOrder.DOWN_THEN_UP -> listOf(downLine, upLine)
                MeterSpeedOrder.UP_THEN_DOWN -> listOf(upLine, downLine)
            }
            MeterDisplayMode.DOWN -> listOf(downLine)
            MeterDisplayMode.UP -> listOf(upLine)
            MeterDisplayMode.TOTAL -> listOf(shortLabel(down + up))
        }
    }

    private fun draw(lines: List<String>, silhouette: Boolean): Bitmap {
        // Larger source bitmap → after SystemUI scales to ~24dp, stroke/glyphs stay thicker.
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (!silhouette) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 0x11, 0x13, 0x18) }
            canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 36f, 36f, bg)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            // Fill most of the glyph; two-line BOTH uses slightly smaller size.
            textSize = when {
                lines.size >= 2 -> 62f
                lines.firstOrNull().orEmpty().length <= 3 -> 88f
                else -> 72f
            }
            isFakeBoldText = true
        }
        val metrics = text.fontMetrics
        val lineHeight = (metrics.descent - metrics.ascent) * 0.92f
        val blockHeight = lineHeight * lines.size
        var y = size / 2f - blockHeight / 2f - metrics.ascent
        val cx = size / 2f
        for (line in lines) {
            canvas.drawText(line, cx, y, text)
            y += lineHeight
        }
        return bmp
    }

    fun shortLabel(bytesPerSec: Long): String {
        if (bytesPerSec < 0) return "0"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 100 -> "${mb.roundToInt()}"
            mb >= 10 -> "${mb.roundToInt()}M"
            mb >= 1 -> String.format("%.1fM", mb)
            kb >= 100 -> "${kb.roundToInt()}K"
            kb >= 1 -> "${kb.roundToInt()}K"
            else -> "${bytesPerSec}B"
        }
    }
}
