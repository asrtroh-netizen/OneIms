package com.onetools.app.recorder

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.onetools.app.R
import java.util.Locale

/**
 * On-demand call-record FAB (circular mic / stop) with elapsed timer while recording.
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
    private var timerView: TextView? = null
    private var busyRing: ProgressBar? = null
    private var recording = false
    private var busy = false
    private var recordStartedAt = 0L
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!recording) return
            updateTimerText()
            mainHandler.postDelayed(this, 500L)
        }
    }

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun show(isRecording: Boolean) {
        runOnMain {
            recording = isRecording
            if (isRecording && recordStartedAt == 0L) {
                recordStartedAt = SystemClock.elapsedRealtime()
            }
            if (!isRecording) recordStartedAt = 0L
            if (!canDraw() || wm == null) return@runOnMain
            if (root == null) {
                val created = createFab()
                runCatching {
                    wm.addView(created, layoutParams())
                    root = created
                }
            }
            applyVisual()
            syncTicker()
        }
    }

    fun setRecording(isRecording: Boolean) {
        runOnMain {
            if (isRecording && !recording) {
                recordStartedAt = SystemClock.elapsedRealtime()
            }
            if (!isRecording) recordStartedAt = 0L
            recording = isRecording
            busy = false
            applyVisual()
            syncTicker()
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
            mainHandler.removeCallbacks(tickRunnable)
            val v = root ?: return@runOnMain
            runCatching { wm?.removeView(v) }
            root = null
            iconView = null
            timerView = null
            busyRing = null
            recording = false
            busy = false
            recordStartedAt = 0L
        }
    }

    private fun createFab(): FrameLayout {
        val density = context.resources.displayMetrics.density
        val size = (56 * density).toInt()
        val pad = (14 * density).toInt()
        val frame = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.recorder_overlay_start)
            setOnClickListener {
                if (busy) return@setOnClickListener
                animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                }.start()
                onToggleRecord()
            }
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val circle = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE0D6242F.toInt())
                setStroke((2 * density).toInt(), 0x66FFFFFF)
            }
            elevation = 10 * density
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
        circle.addView(icon, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        circle.addView(ring, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        val timer = TextView(context).apply {
            text = "00:00"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, (4 * density).toInt(), 0, 0)
            setShadowLayer(4f, 0f, 1f, 0xCC000000.toInt())
            visibility = View.GONE
        }
        column.addView(circle)
        column.addView(timer)
        frame.addView(column)
        iconView = icon
        timerView = timer
        busyRing = ring
        frame.tag = circle
        return frame
    }

    private fun applyVisual() {
        val frame = root ?: return
        val circle = frame.tag as? FrameLayout ?: return
        val density = context.resources.displayMetrics.density
        val gd = (circle.background as? GradientDrawable) ?: GradientDrawable().also {
            it.shape = GradientDrawable.OVAL
            circle.background = it
        }
        if (recording) {
            gd.setColor(0xE0B3261E.toInt())
            iconView?.setImageResource(android.R.drawable.ic_media_pause)
            frame.contentDescription = context.getString(R.string.recorder_overlay_stop)
            timerView?.visibility = View.VISIBLE
            updateTimerText()
            circle.animate().cancel()
            circle.animate().scaleX(1.06f).scaleY(1.06f).setDuration(500).withEndAction {
                circle.animate().scaleX(1f).scaleY(1f).setDuration(500).start()
            }.start()
        } else {
            gd.setColor(0xE0D6242F.toInt())
            iconView?.setImageResource(android.R.drawable.ic_btn_speak_now)
            frame.contentDescription = context.getString(R.string.recorder_overlay_start)
            timerView?.visibility = View.GONE
            timerView?.text = "00:00"
            circle.animate().cancel()
            circle.scaleX = 1f
            circle.scaleY = 1f
        }
        gd.setStroke((2 * density).toInt(), 0x66FFFFFF)
        busyRing?.visibility = if (busy) View.VISIBLE else View.GONE
        iconView?.alpha = if (busy) 0.35f else 1f
        frame.isEnabled = !busy
        runCatching { wm?.updateViewLayout(frame, layoutParams()) }
    }

    private fun syncTicker() {
        mainHandler.removeCallbacks(tickRunnable)
        if (recording) mainHandler.post(tickRunnable)
    }

    private fun updateTimerText() {
        val started = recordStartedAt
        if (started <= 0L) {
            timerView?.text = "00:00"
            return
        }
        val sec = ((SystemClock.elapsedRealtime() - started) / 1000L).coerceAtLeast(0L)
        val mm = sec / 60L
        val ss = sec % 60L
        timerView?.text = String.format(Locale.US, "%02d:%02d", mm, ss)
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
