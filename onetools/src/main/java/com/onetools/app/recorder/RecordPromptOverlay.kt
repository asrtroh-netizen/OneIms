package com.onetools.app.recorder

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import com.onetools.app.R

/**
 * On-demand call-record FAB (circular mic / stop), Material-like floating control.
 * Click handler must stay non-blocking — heavy work is done by the controller off-main.
 */
class RecordPromptOverlay(
    private val context: Context,
    private val onToggleRecord: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var iconView: ImageView? = null
    private var busyRing: ProgressBar? = null
    private var recording = false
    private var busy = false

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun show(isRecording: Boolean) {
        runOnMain {
            recording = isRecording
            if (!canDraw() || wm == null) return@runOnMain
            if (root == null) {
                val created = createFab()
                runCatching {
                    wm.addView(created, layoutParams())
                    root = created
                }
            }
            applyVisual()
        }
    }

    fun setRecording(isRecording: Boolean) {
        runOnMain {
            recording = isRecording
            busy = false
            applyVisual()
        }
    }

    fun setBusy(value: Boolean) {
        runOnMain {
            busy = value
            applyVisual()
        }
    }

    fun hide() {
        runOnMain {
            val v = root ?: return@runOnMain
            runCatching { wm?.removeView(v) }
            root = null
            iconView = null
            busyRing = null
            recording = false
            busy = false
        }
    }

    private fun createFab(): FrameLayout {
        val density = context.resources.displayMetrics.density
        val size = (56 * density).toInt()
        val pad = (14 * density).toInt()
        val frame = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE0D6242F.toInt())
                setStroke((2 * density).toInt(), 0x66FFFFFF)
            }
            elevation = 10 * density
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.recorder_overlay_start)
            setOnClickListener {
                if (busy) return@setOnClickListener
                // Visual press feedback only; work is async in controller.
                animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                }.start()
                onToggleRecord()
            }
        }
        val icon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(0xFFFFFFFF.toInt())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(pad, pad, pad, pad)
        }
        val ring = ProgressBar(context).apply {
            isIndeterminate = true
            visibility = View.GONE
            alpha = 0.85f
        }
        frame.addView(icon, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        frame.addView(ring, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        iconView = icon
        busyRing = ring
        return frame
    }

    private fun applyVisual() {
        val frame = root ?: return
        val density = context.resources.displayMetrics.density
        val gd = (frame.background as? GradientDrawable) ?: GradientDrawable().also {
            it.shape = GradientDrawable.OVAL
            frame.background = it
        }
        if (recording) {
            gd.setColor(0xE0B3261E.toInt())
            iconView?.setImageResource(android.R.drawable.ic_media_pause)
            frame.contentDescription = context.getString(R.string.recorder_overlay_stop)
            // Soft pulse while recording
            frame.animate().cancel()
            frame.animate().scaleX(1.06f).scaleY(1.06f).setDuration(500).withEndAction {
                frame.animate().scaleX(1f).scaleY(1f).setDuration(500).start()
            }.start()
        } else {
            gd.setColor(0xE0D6242F.toInt())
            iconView?.setImageResource(android.R.drawable.ic_btn_speak_now)
            frame.contentDescription = context.getString(R.string.recorder_overlay_start)
            frame.animate().cancel()
            frame.scaleX = 1f
            frame.scaleY = 1f
        }
        gd.setStroke((2 * density).toInt(), 0x66FFFFFF)
        busyRing?.visibility = if (busy) View.VISIBLE else View.GONE
        iconView?.alpha = if (busy) 0.35f else 1f
        frame.isEnabled = !busy
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val density = context.resources.displayMetrics.density
        val size = (56 * density).toInt()
        return WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER or Gravity.END
            x = (16 * density).toInt()
            y = 0
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
