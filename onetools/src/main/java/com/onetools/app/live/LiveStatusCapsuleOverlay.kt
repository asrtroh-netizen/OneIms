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
 * 顶栏「轻提醒」扁胶囊：对齐 One Capsule 概念图的轻提醒态。
 * 长度 / 高低独立缩放；默认落在状态栏下方，避开前置摄像头。
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
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                includeFontPadding = false
                letterSpacing = 0.02f
                gravity = Gravity.CENTER
                maxLines = 1
                isSingleLine = true
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
        val w = prefs.capsuleWidthScale
        val h = prefs.capsuleHeightScale
        // 截图轻提醒：扁长黑胶囊，字小、左右留白多、上下紧。
        val padH = dp((16f * w).toInt().coerceIn(10, 36))
        val padV = dp((4f * h).toInt().coerceIn(2, 12))
        val textSp = (11f * ((w + h) * 0.5f)).coerceIn(9f, 15f)
        val minW = dp((120f * w).toInt().coerceIn(72, 280))
        val radius = dp((14f * h).toInt().coerceIn(10, 22)).toFloat()
        tv.text = lastText
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
        tv.setPadding(padH, padV, padH, padV)
        tv.minWidth = minW
        tv.minHeight = dp((22f * h).toInt().coerceIn(18, 40))
        tv.elevation = dp(6).toFloat()
        tv.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.parseColor("#F2000000"))
            setStroke(dp(1), Color.parseColor("#22FFFFFF"))
        }
    }

    private fun applyPositionLocked() {
        val lp = params ?: return
        val v = view ?: return
        val wmRef = wm ?: return
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.x = dp(prefs.capsuleOffsetXDp)
        // 基准落在状态栏底边下方，默认 +6dp，减少压住前置摄像头。
        lp.y = statusBarHeight() + dp(prefs.capsuleOffsetYDp)
        runCatching { wmRef.updateViewLayout(v, lp) }
    }

    private fun statusBarHeight(): Int {
        val resId = app.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) app.resources.getDimensionPixelSize(resId) else dp(28)
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
