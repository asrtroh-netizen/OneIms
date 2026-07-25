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
 */
object MeterDynamicIcon {
    fun createSilhouette(downBps: Long, upBps: Long, mode: MeterDisplayMode): Bitmap {
        val label = shortLabel(valueFor(mode, downBps, upBps))
        return draw(label, silhouette = true)
    }

    fun create(downBps: Long, upBps: Long, mode: MeterDisplayMode): Bitmap {
        val label = shortLabel(valueFor(mode, downBps, upBps))
        return draw(label, silhouette = false)
    }

    private fun valueFor(mode: MeterDisplayMode, down: Long, up: Long): Long = when (mode) {
        MeterDisplayMode.UP -> up
        MeterDisplayMode.TOTAL -> down + up
        MeterDisplayMode.DOWN, MeterDisplayMode.BOTH -> down
    }

    private fun draw(label: String, silhouette: Boolean): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (!silhouette) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 0x11, 0x13, 0x18) }
            canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 20f, 20f, bg)
        }
        // Silhouette: only white pixels (alpha) — transparent background.
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
