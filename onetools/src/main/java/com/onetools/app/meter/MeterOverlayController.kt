package com.onetools.app.meter

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

/**
 * Draggable overlay HUD with themeable glass styles + position memory + double-tap hide.
 * All WindowManager mutations are posted to the main thread (safe from FGS coroutines).
 */
class MeterOverlayController(
    private val context: Context,
    private val onDoubleTapHide: () -> Unit = {},
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wm = context.getSystemService(WindowManager::class.java)
    private val settings = MeterSettings(context.applicationContext)
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastTapAt = 0L

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun show(initialText: String, style: MeterPrefsSnapshot) {
        runOnMain {
            if (!canDraw() || wm == null) return@runOnMain
            if (view != null) {
                applyStyleLocked(style)
                view?.text = initialText
                return@runOnMain
            }
            val tv = TextView(context).apply {
                text = initialText
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                letterSpacing = 0.02f
                includeFontPadding = false
                elevation = dp(6f)
            }
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var moved = false
            tv.setOnTouchListener { v, e ->
                val lp = params ?: return@setOnTouchListener false
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX
                        downY = e.rawY
                        startX = lp.x
                        startY = lp.y
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (dx * dx + dy * dy > 36f) moved = true
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(v, lp) }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastTapAt < 320L) {
                                onDoubleTapHide()
                                lastTapAt = 0L
                            } else {
                                lastTapAt = now
                            }
                        } else {
                            settings.saveOverlayPositionAsync(lp.x, lp.y)
                        }
                        true
                    }
                    else -> false
                }
            }
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = style.overlayX
                y = style.overlayY
                // Allow drawing into status-bar / cutout band (island-beside-camera).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            runCatching {
                wm.addView(tv, lp)
                view = tv
                params = lp
                applyStyleLocked(style)
            }
        }
    }

    fun applyStyle(style: MeterPrefsSnapshot) {
        runOnMain { applyStyleLocked(style) }
    }

    fun update(text: String) {
        runOnMain { view?.text = text }
    }

    fun hide() {
        runOnMain {
            val v = view ?: return@runOnMain
            runCatching { wm?.removeView(v) }
            view = null
            params = null
        }
    }

    /**
     * Dock beside the camera punch-hole (DisplayCutout) — closest third-party stand-in
     * for a Dynamic Island style capsule. System Live Update chips cannot be repositioned.
     */
    fun moveToOemStatusSlot() {
        runOnMain {
            val tv = view
            val lp = params
            if (lp != null && tv != null && wm != null) {
                lp.gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                val place: (Int) -> Unit = { chipW ->
                    val (x, y) = oemSlotXy(context, chipW)
                    lp.x = x
                    lp.y = y
                    runCatching { wm.updateViewLayout(tv, lp) }
                    settings.saveOverlayPositionAsync(x, y)
                }
                if (tv.width > 0) {
                    place(tv.width)
                } else {
                    val approx = (88f * context.resources.displayMetrics.density).toInt()
                    place(approx)
                    tv.post { if (tv.width > 0) place(tv.width) }
                }
            } else {
                val (x, y) = oemSlotXy(context)
                settings.saveOverlayPositionAsync(x, y)
            }
        }
    }

    private fun applyStyleLocked(style: MeterPrefsSnapshot) {
        val tv = view ?: return
        val density = context.resources.displayMetrics.density
        val padH = (style.overlayPadHDp * density).toInt()
        val padV = (style.overlayPadVDp * density).toInt()
        tv.setPadding(padH, padV, padH, padV)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.overlayTextSp)
        val (bgTop, bgBottom, fg, stroke) = colors(style.overlayTheme, style.overlayAlpha)
        tv.setTextColor(fg)
        val gd = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(bgTop, bgBottom),
        ).apply {
            cornerRadius = style.overlayCornerDp * density
            setStroke((1.2f * density).toInt().coerceAtLeast(1), stroke)
        }
        tv.background = gd
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun dp(v: Float): Float = v * context.resources.displayMetrics.density

    companion object {
        fun oemSlotXy(context: Context, chipWidthPx: Int = 0): Pair<Int, Int> {
            val dm = context.resources.displayMetrics
            val density = dm.density
            val chipW = if (chipWidthPx > 0) {
                chipWidthPx
            } else {
                (88f * density).toInt()
            }
            val gap = (6f * density).toInt()
            val cutout = primaryCutoutRect(context)
            if (cutout != null && !cutout.isEmpty) {
                val rightX = cutout.right + gap
                val leftX = cutout.left - chipW - gap
                val placeRight = rightX + chipW <= dm.widthPixels - gap
                val x = when {
                    placeRight -> rightX.coerceAtLeast(0)
                    leftX >= 0 -> leftX
                    else -> ((dm.widthPixels - chipW) / 2).coerceAtLeast(0)
                }
                // Vertically center on the punch-hole inside the status-bar band.
                val chipH = (20f * density).toInt()
                val y = (cutout.centerY() - chipH / 2).coerceAtLeast(0)
                return x to y
            }
            // No cutout info: top-center (many Pixels) as island fallback.
            val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBar = if (resId > 0) {
                context.resources.getDimensionPixelSize(resId)
            } else {
                (24f * density).toInt()
            }
            val x = ((dm.widthPixels - chipW) / 2).coerceAtLeast(0)
            val y = (statusBar / 4).coerceAtLeast(0)
            return x to y
        }

        /** Largest bounding rect — usually the camera punch-hole. */
        private fun primaryCutoutRect(context: Context): Rect? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
            val wm = context.getSystemService(WindowManager::class.java) ?: return null
            val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wm.currentWindowMetrics.windowInsets.displayCutout
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay?.cutout
            } ?: return null
            return cutout.boundingRects.maxByOrNull { it.width() * it.height() }
        }
    }

    private data class Tone(val top: Int, val bottom: Int, val fg: Int, val stroke: Int)

    private fun colors(theme: MeterOverlayTheme, alpha: Float): Tone {
        val a = (alpha * 255).toInt().coerceIn(90, 255)
        return when (theme) {
            MeterOverlayTheme.ONE_DARK -> Tone(
                Color.argb(a, 0x11, 0x13, 0x18),
                Color.argb(a, 0x1A, 0x1B, 0x20),
                Color.argb(255, 0xE2, 0xE2, 0xE9),
                Color.argb((a * 0.55f).toInt(), 255, 255, 255),
            )
            MeterOverlayTheme.ONE_MIST -> Tone(
                Color.argb(a, 0xF9, 0xF9, 0xFF),
                Color.argb(a, 0xF0, 0xF0, 0xF4),
                Color.argb(255, 0x1A, 0x1B, 0x20),
                Color.argb((a * 0.35f).toInt(), 0x74, 0x77, 0x7F),
            )
            MeterOverlayTheme.ONE_WHITE -> Tone(
                Color.argb(a, 0x11, 0x13, 0x18),
                Color.argb(a, 0x2B, 0x29, 0x30),
                Color.WHITE,
                Color.argb((a * 0.7f).toInt(), 255, 255, 255),
            )
            MeterOverlayTheme.SLATE -> Tone(
                Color.argb(a, 55, 62, 72),
                Color.argb(a, 42, 48, 56),
                Color.argb(255, 236, 240, 244),
                Color.argb((a * 0.4f).toInt(), 180, 190, 200),
            )
        }
    }
}
