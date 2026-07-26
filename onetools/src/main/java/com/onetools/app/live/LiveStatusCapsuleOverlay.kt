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
 * 顶栏居中「灵动岛」胶囊：黑底圆角、短文案，不依赖 ROM 是否提升 Live Update 芯片。
 */
class LiveStatusCapsuleOverlay(private val context: Context) {
    private val app = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wm = app.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    fun canDraw(): Boolean = Settings.canDrawOverlays(app)

    fun show(text: String) {
        runOnMain {
            if (!canDraw() || wm == null) return@runOnMain
            val label = text.ifBlank { "···" }
            if (view != null) {
                view?.text = label
                return@runOnMain
            }
            val tv = TextView(app).apply {
                this.text = label
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                includeFontPadding = false
                letterSpacing = 0.04f
                setPadding(dp(18), dp(8), dp(18), dp(8))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(20).toFloat()
                    setColor(Color.parseColor("#E6111318"))
                    setStroke(dp(1), Color.parseColor("#33FFFFFF"))
                }
                elevation = dp(10).toFloat()
                minWidth = dp(88)
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
                x = 0
                y = statusBarInset()
            }
            runCatching {
                wm.addView(tv, lp)
                view = tv
                params = lp
            }
        }
    }

    fun update(text: String) {
        runOnMain {
            val label = text.ifBlank { "···" }
            if (view == null) {
                show(label)
            } else {
                view?.text = label
            }
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

    private fun statusBarInset(): Int {
        val resId = app.resources.getIdentifier("status_bar_height", "dimen", "android")
        val bar = if (resId > 0) app.resources.getDimensionPixelSize(resId) else dp(28)
        // 略压进状态栏下方中央，近似灵动岛位置。
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
                instance ?: LiveStatusCapsuleOverlay(context.applicationContext).also { instance = it }
            }
        }
    }
}
