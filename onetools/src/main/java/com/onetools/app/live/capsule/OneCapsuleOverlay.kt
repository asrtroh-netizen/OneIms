package com.onetools.app.live.capsule

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.onetools.app.live.LiveStatusPrefs
import kotlin.math.abs

/**
 * One Capsule 悬浮岛：轻提醒扁胶囊 + 展开卡 + 手势。
 * 下展开 / 上收起 / 左右切会话。
 */
class OneCapsuleOverlay private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = LiveStatusPrefs(app)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wm = app.getSystemService(WindowManager::class.java)

    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var pillView: TextView? = null
    private var expandedView: LinearLayout? = null
    private var pageHint: TextView? = null
    private var attached = false

    private val storeListener: (CapsuleUiSnapshot) -> Unit = { snap ->
        runOnMain { render(snap) }
    }

    private val gestureDetector by lazy {
        GestureDetector(
            app,
            object : GestureDetector.SimpleOnGestureListener() {
                private val min = dp(28).toFloat()
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    if (e1 == null) return false
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    if (abs(dx) > abs(dy) && abs(dx) > min) {
                        if (dx < 0) OneCapsuleStore.next() else OneCapsuleStore.prev()
                        return true
                    }
                    if (abs(dy) > abs(dx) && abs(dy) > min) {
                        if (dy > 0) OneCapsuleStore.expand() else OneCapsuleStore.collapse()
                        return true
                    }
                    return false
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    val mode = OneCapsuleStore.snapshot().mode
                    if (mode == CapsuleDisplayMode.PILL) OneCapsuleStore.expand()
                    else if (mode == CapsuleDisplayMode.EXPANDED) OneCapsuleStore.collapse()
                    return true
                }
            },
        )
    }

    fun canDraw(): Boolean = Settings.canDrawOverlays(app)

    fun start() {
        runOnMain {
            OneCapsuleStore.observe(storeListener)
            render(OneCapsuleStore.snapshot())
        }
    }

    fun stop() {
        runOnMain {
            OneCapsuleStore.removeObserver(storeListener)
            detach()
        }
    }

    fun applyLayoutFromPrefs() {
        runOnMain {
            render(OneCapsuleStore.snapshot())
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureAttached() {
        if (attached || wm == null || !canDraw()) return
        val rootLl = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnTouchListener { _, ev -> gestureDetector.onTouchEvent(ev) }
        }
        val pill = TextView(app).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            gravity = Gravity.CENTER
            maxLines = 1
            isSingleLine = true
        }
        val hint = TextView(app).apply {
            setTextColor(Color.parseColor("#88FFFFFF"))
            textSize = 10f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val expanded = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        rootLl.addView(
            pill,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        rootLl.addView(
            hint,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(4) },
        )
        rootLl.addView(
            expanded,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
                marginStart = dp(16)
                marginEnd = dp(16)
            },
        )

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        runCatching {
            wm.addView(rootLl, lp)
            root = rootLl
            pillView = pill
            pageHint = hint
            expandedView = expanded
            params = lp
            attached = true
        }
    }

    private fun detach() {
        val r = root
        if (r != null) runCatching { wm?.removeView(r) }
        root = null
        pillView = null
        pageHint = null
        expandedView = null
        params = null
        attached = false
    }

    private fun render(snap: CapsuleUiSnapshot) {
        if (snap.mode == CapsuleDisplayMode.HIDDEN || snap.active == null) {
            detach()
            return
        }
        ensureAttached()
        if (!attached) return
        val session = snap.active ?: return
        applyPill(session, snap)
        applyExpanded(session, snap.mode == CapsuleDisplayMode.EXPANDED)
        applyPosition()
    }

    private fun applyPill(session: CapsuleSession, snap: CapsuleUiSnapshot) {
        val tv = pillView ?: return
        val w = prefs.capsuleWidthScale
        val h = prefs.capsuleHeightScale
        val padH = dp((16f * w).toInt().coerceIn(10, 40))
        val padV = dp((4f * h).toInt().coerceIn(2, 12))
        val textSp = (11f * ((w + h) * 0.5f)).coerceIn(9f, 15f)
        tv.text = session.pillText()
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
        tv.setPadding(padH, padV, padH, padV)
        tv.minWidth = dp((120f * w).toInt().coerceIn(72, 300))
        tv.minHeight = dp((22f * h).toInt().coerceIn(18, 40))
        tv.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp((14f * h).toInt().coerceIn(10, 22)).toFloat()
            setColor(Color.parseColor("#F2000000"))
            setStroke(dp(1), Color.parseColor("#22FFFFFF"))
        }
        val hint = pageHint ?: return
        if (snap.sessions.size > 1) {
            hint.visibility = View.VISIBLE
            hint.text = "${snap.activeIndex + 1}/${snap.sessions.size} · 左右切换"
        } else {
            hint.visibility = View.GONE
        }
    }

    private fun applyExpanded(session: CapsuleSession, show: Boolean) {
        val box = expandedView ?: return
        if (!show) {
            box.visibility = View.GONE
            box.removeAllViews()
            return
        }
        box.visibility = View.VISIBLE
        box.removeAllViews()
        box.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(Color.parseColor("#F214161C"))
            setStroke(dp(1), Color.parseColor("#33FFFFFF"))
        }

        box.addView(label(session.title, 15f, true, Color.WHITE))
        if (session.subtitle.isNotBlank()) {
            box.addView(
                label(session.subtitle, 12f, false, Color.parseColor("#B3FFFFFF")).also {
                    (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(4)
                },
            )
        }

        when (session.expandTemplate) {
            CapsuleExpandTemplate.PROGRESS_CARD -> {
                val row = LinearLayout(app).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val lpRow = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(14) }
                session.stages.forEachIndexed { index, stage ->
                    val col = LinearLayout(app).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val filled = index <= session.activeStageIndex || stage.done
                    val dot = View(app).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(if (filled) session.accentColor else Color.parseColor("#44FFFFFF"))
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(10), dp(10))
                    }
                    col.addView(dot)
                    val stageLabel = label(stage.label, 10f, false, Color.parseColor("#CCFFFFFF"))
                    (stageLabel.layoutParams as LinearLayout.LayoutParams).topMargin = dp(6)
                    col.addView(stageLabel)
                    row.addView(col)
                }
                box.addView(row, lpRow)
            }
            CapsuleExpandTemplate.DETAIL_CARD -> {
                session.detailRows.forEach { (k, v) ->
                    val line = label("$k  $v", 13f, false, Color.parseColor("#E6FFFFFF"))
                    (line.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10)
                    box.addView(line)
                }
                val actions = LinearLayout(app).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                }
                val alp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(14) }
                listOfNotNull(session.actionSecondary, session.actionPrimary).forEach { text ->
                    actions.addView(
                        label(text, 12f, true, session.accentColor).also { t ->
                            t.setPadding(dp(12), dp(8), dp(12), dp(8)
                            )
                            t.background = GradientDrawable().apply {
                                cornerRadius = dp(16).toFloat()
                                setColor(Color.parseColor("#22FFFFFF"))
                            }
                            val mlp = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ).apply { marginStart = dp(8) }
                            t.layoutParams = mlp
                        },
                    )
                }
                box.addView(actions, alp)
            }
        }

        box.addView(
            label("上滑收起 · 点按切换", 10f, false, Color.parseColor("#66FFFFFF")).also {
                (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(12)
            },
        )
    }

    private fun label(
        text: String,
        sp: Float,
        bold: Boolean,
        color: Int,
    ): TextView = TextView(app).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = Typeface.create(
            "sans-serif-medium",
            if (bold) Typeface.BOLD else Typeface.NORMAL,
        )
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun applyPosition() {
        val lp = params ?: return
        val r = root ?: return
        lp.x = dp(prefs.capsuleOffsetXDp)
        lp.y = statusBarHeight() + dp(prefs.capsuleOffsetYDp)
        runCatching { wm?.updateViewLayout(r, lp) }
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
        private var instance: OneCapsuleOverlay? = null

        fun get(context: Context): OneCapsuleOverlay {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: OneCapsuleOverlay(context.applicationContext).also { instance = it }
            }
        }
    }
}
