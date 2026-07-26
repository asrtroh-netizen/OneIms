package com.onetools.app.live

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * 顶栏「灵动岛」胶囊：可调大小 / 左右 / 高度。
 */
class LiveStatusCapsuleOverlay(private val context: Context) {
    private val app = context.applicationContext
    private val prefs = LiveStatusPrefs(app)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wm = app.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastText: String = "···"

    fun canDraw(): Boolean = Settings.canDrawOverlays(app)

    fun show(text: String) {
        runOnMain {
            if (!canDraw() || wm == null) return@runOnMain
            lastText = text.ifBlank { "···" }
            if (view != null) {
                applyAppearanceLocked()
                applyPositionLocked()
                return@runOnMain
            }
            val tv = TextView(app).apply {
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                includeFontPadding = false
                letterSpacing = 0.04f
                gravity = Gravity.CENTER
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
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            runCatching {
                wm.addView(tv, lp)
                view = tv
                params = lp
                applyAppearanceLocked()
                applyPositionLocked()
            }
        }
    }

    fun update(text: String) {
        runOnMain {
            lastText = text.ifBlank { "···" }
            if (view == null) show(lastText) else {
                view?.text = lastText
            }
        }
    }

    /** Live Lab 滑条改参后立即重绘。 */
    fun applyLayoutFromPrefs() {
        runOnMain {
            if (view == null) return@runOnMain
            applyAppearanceLocked()
            applyPositionLocked()
        }
    }

    fun hide() {
        runOnMain {
            val v = view ?: return@runOnMain
            runCatching { wm?.removeView(v) }
            view = null
            params = null
        }
    }

    private fun applyAppearanceLocked() {
        val tv = view ?: return
        val scale = prefs.capsuleScale.coerceIn(0.7f, 1.6f)
        val padH = dp((18f * scale).toInt().coerceAtLeast(10))
        val padV = dp((8f * scale).toInt().coerceAtLeast(4))
        tv.text = lastText
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * scale)
        tv.setPadding(padH, padV, padH, padV)
        tv.minWidth = dp((88f * scale).toInt().coerceAtLeast(56))
        tv.elevation = dp((10f * scale).toInt().coerceAtLeast(4)).toFloat()
        tv.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp((20f * scale).toInt().coerceAtLeast(12)).toFloat()
            setColor(Color.parseColor("#E6111318"))
            setStroke(dp(1), Color.parseColor("#33FFFFFF"))
        }
    }

    private fun applyPositionLocked() {
        val lp = params ?: return
        val v = view ?: return
        val wmRef = wm ?: return
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.x = dp(prefs.capsuleOffsetXDp)
        lp.y = statusBarInset() + dp(prefs.capsuleOffsetYDp)
        runCatching { wmRef.updateViewLayout(v, lp) }
    }

    private fun statusBarInset(): Int {
        val resId = app.resources.getIdentifier("status_bar_height", "dimen", "android")
        val bar = if (resId > 0) app.resources.getDimensionPixelSize(resId) else dp(28)
        return (bar * 0.35f).toInt().coerceAtLeast(dp(6))
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            app.resources.displayMetrics,
        ).toInt()

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }

    companion object {
        @Volatile
        private var instance: LiveStatusCapsuleOverlay? = null

        fun get(context: Context): LiveStatusCapsuleOverlay {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: LiveStatusCapsuleOverlay(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
