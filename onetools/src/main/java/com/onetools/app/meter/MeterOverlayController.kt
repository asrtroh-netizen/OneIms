package com.onetools.app.meter

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.onetools.app.R

/**
 * Draggable TYPE_APPLICATION_OVERLAY speed HUD.
 * Feature parity target: Pixel Meter floating window (Apache-2.0 inspiration; clean implementation).
 */
class MeterOverlayController(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun show(initialText: String) {
        if (!canDraw()) return
        if (view != null) {
            update(initialText)
            return
        }
        val tv = TextView(context).apply {
            text = initialText
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setBackgroundColor(0xCC1C1B1F.toInt())
            setPadding(28, 16, 28, 16)
            textSize = 13f
            elevation = 8f
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        tv.setOnTouchListener { v, e ->
            val lp = params ?: return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - downX).toInt()
                    lp.y = startY + (e.rawY - downY).toInt()
                    runCatching { wm.updateViewLayout(v, lp) }
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
            x = 48
            y = 180
        }
        runCatching {
            wm.addView(tv, lp)
            view = tv
            params = lp
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
}
