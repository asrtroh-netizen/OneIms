package com.onetools.app.recorder

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import com.onetools.app.R

/**
 * On-demand call-record prompt: floating button while OFFHOOK.
 * Tap toggles start/stop; never auto-records without user action.
 */
class RecordPromptOverlay(
    private val context: Context,
    private val onToggleRecord: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wm = context.getSystemService(WindowManager::class.java)
    private var button: Button? = null
    private var recording = false

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun show(isRecording: Boolean) {
        runOnMain {
            recording = isRecording
            if (!canDraw() || wm == null) return@runOnMain
            val btn = button ?: createButton().also {
                runCatching {
                    wm.addView(it, layoutParams())
                    button = it
                }
            }
            applyLabel(btn)
        }
    }

    fun setRecording(isRecording: Boolean) {
        runOnMain {
            recording = isRecording
            button?.let { applyLabel(it) }
        }
    }

    fun hide() {
        runOnMain {
            val v = button ?: return@runOnMain
            runCatching { wm?.removeView(v) }
            button = null
            recording = false
        }
    }

    private fun createButton(): Button {
        val density = context.resources.displayMetrics.density
        return Button(context).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                cornerRadius = 28f * density
                setColor(0xE0D6242F.toInt())
            }
            val padH = (16 * density).toInt()
            val padV = (10 * density).toInt()
            setPadding(padH, padV, padH, padV)
            setOnClickListener { onToggleRecord() }
            applyLabel(this)
        }
    }

    private fun applyLabel(btn: Button) {
        btn.text = if (recording) {
            context.getString(R.string.recorder_overlay_stop)
        } else {
            context.getString(R.string.recorder_overlay_start)
        }
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val density = context.resources.displayMetrics.density
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER or Gravity.END
            x = (12 * density).toInt()
            y = 0
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
