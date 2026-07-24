package com.onetools.app.meter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.roundToInt

/**
 * Renders a compact speed glyph for notification smallIcon (Pixel Meter–style dynamic icon).
 */
object MeterDynamicIcon {
    fun create(downBps: Long, upBps: Long, mode: MeterDisplayMode): Bitmap {
        val value = when (mode) {
            MeterDisplayMode.UP -> upBps
            MeterDisplayMode.DOWN, MeterDisplayMode.BOTH, MeterDisplayMode.TOTAL ->
                if (mode == MeterDisplayMode.TOTAL) downBps + upBps else downBps
        }
        val label = shortLabel(value)
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 28, 27, 31) }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 18f, 18f, bg)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = if (label.length <= 3) 36f else 28f
        }
        val y = size / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, size / 2f, y, text)
        return bmp
    }

    fun shortLabel(bytesPerSec: Long): String {
        if (bytesPerSec < 0) return "0"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 10 -> "${mb.roundToInt()}M"
            mb >= 1 -> String.format("%.1fM", mb)
            kb >= 1 -> "${kb.roundToInt()}K"
            else -> "${bytesPerSec}B"
        }
    }
}
