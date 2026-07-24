package com.onetools.app.meter

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

/**
 * Draggable overlay HUD with themeable glass styles + position memory + double-tap hide.
 */
class MeterOverlayController(
    private val context: Context,
    private val onDoubleTapHide: () -> Unit = {},
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val settings = MeterSettings(context.applicationContext)
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastTapAt = 0L

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun show(initialText: String, style: MeterPrefsSnapshot) {
        if (!canDraw()) return
        if (view != null) {
            applyStyle(style)
            update(initialText)
            return
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
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = style.overlayX
            y = style.overlayY
        }
        runCatching {
            wm.addView(tv, lp)
            view = tv
            params = lp
            applyStyle(style)
        }
    }

    fun applyStyle(style: MeterPrefsSnapshot) {
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
        params?.let { lp ->
            // Keep current drag position if already shown; only seed from prefs on first add.
        }
    }

    fun update(text: String) {
        view?.text = text
    }

    fun hide() {
        val v = view ?: return
        runCatching { wm.removeView(v) }
        view = null
        params = null
    }

    /**
     * Approximate OEM status-bar net-speed slot: top-right, near system icons.
     * Third-party apps cannot draw inside SystemUI; this is the closest overlay dock.
     */
    fun moveToOemStatusSlot() {
        val (x, y) = oemSlotXy(context)
        val lp = params
        if (lp != null && view != null) {
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = x
            lp.y = y
            runCatching { wm.updateViewLayout(view, lp) }
        }
        settings.saveOverlayPositionAsync(x, y)
    }

    private fun dp(v: Float): Float = v * context.resources.displayMetrics.density

    companion object {
        fun oemSlotXy(context: Context): Pair<Int, Int> {
            val dm = context.resources.displayMetrics
            val density = dm.density
            val approxChipWidth = (72f * density).toInt()
            val margin = (6f * density).toInt()
            val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBar = if (resId > 0) {
                context.resources.getDimensionPixelSize(resId)
            } else {
                (24f * density).toInt()
            }
            val x = (dm.widthPixels - approxChipWidth - margin).coerceAtLeast(0)
            val y = (statusBar - (2f * density).toInt()).coerceAtLeast(0)
            return x to y
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
