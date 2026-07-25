package com.onetools.app.meter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.roundToInt

/**
 * Notification / QS glyphs — aligned with Pixel Meter [NotificationHelper] (Apache-2.0):
 * - Icon canvas ≈ 24dp (min 48px)
 * - Bitmap mode draws **value** (0.65×size) over **unit** (0.35×size)
 * - BOTH / TOTAL modes use combined throughput on the glyph; shade text shows ↓/↑ lines
 *
 * Status-bar [smallIcon] must stay alpha-only white (Android ignores RGB otherwise).
 */
object MeterDynamicIcon {
    fun createSilhouette(
        downBps: Long,
        upBps: Long,
        mode: MeterDisplayMode,
        density: Float,
    ): Bitmap {
        val bytes = valueFor(mode, downBps, upBps)
        val (value, unit) = formatValueUnit(bytes)
        return draw(value, unit, density, silhouette = true)
    }

    fun create(
        downBps: Long,
        upBps: Long,
        mode: MeterDisplayMode,
        density: Float,
    ): Bitmap {
        val bytes = valueFor(mode, downBps, upBps)
        val (value, unit) = formatValueUnit(bytes)
        return draw(value, unit, density, silhouette = false)
    }

    /** BOTH → total (Pixel Meter bitmap path). */
    private fun valueFor(mode: MeterDisplayMode, down: Long, up: Long): Long = when (mode) {
        MeterDisplayMode.UP -> up
        MeterDisplayMode.DOWN -> down
        MeterDisplayMode.TOTAL, MeterDisplayMode.BOTH -> down + up
    }

    private fun draw(
        valueText: String,
        unitText: String,
        density: Float,
        silhouette: Boolean,
    ): Bitmap {
        // Pixel Meter: (density * 24).coerceAtLeast(48)
        val size = (density * 24f).roundToInt().coerceAtLeast(48)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (!silhouette) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 0x11, 0x13, 0x18) }
            canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.2f, size * 0.2f, bg)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = size * 0.65f
        }
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = size * 0.35f
        }
        val cx = size / 2f
        // Pixel Meter cyValue / cyUnit
        canvas.drawText(valueText, cx, size * 0.5f, textPaint)
        canvas.drawText(unitText, cx, size * 0.95f, unitPaint)
        return bmp
    }

    /**
     * Split numeric value and unit for stacked glyph (Pixel Meter formatSpeedText shape).
     * Returns e.g. ("1.2","MB/s") / ("856","KB/s").
     */
    fun formatValueUnit(bytesPerSec: Long): Pair<String, String> {
        val bytes = bytesPerSec.coerceAtLeast(0L)
        if (bytes < 1024L) return bytes.toString() to "B/s"
        val kb = bytes / 1024.0
        if (kb < 1000) return "%.0f".format(kb) to "KB/s"
        val mb = kb / 1024.0
        if (mb < 1000) {
            return if (mb < 10) "%.1f".format(mb) to "MB/s" else "%.0f".format(mb) to "MB/s"
        }
        val gb = mb / 1024.0
        return if (gb < 10) "%.1f".format(gb) to "GB/s" else "%.0f".format(gb) to "GB/s"
    }

    /** Compact single-token label (chip / legacy). */
    fun shortLabel(bytesPerSec: Long): String {
        val (v, u) = formatValueUnit(bytesPerSec)
        return when {
            u.startsWith("GB") -> "${v}G"
            u.startsWith("MB") -> "${v}M"
            u.startsWith("KB") -> "${v}K"
            else -> "${v}B"
        }
    }
}
