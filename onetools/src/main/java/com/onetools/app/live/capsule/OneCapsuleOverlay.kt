package com.onetools.app.live.capsule

import android.annotation.SuppressLint
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
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.onetools.app.live.LiveStatusPrefs
import kotlin.math.abs

/**
 * One Capsule 悬浮岛。
 * 布局心智借鉴 MT/OneCapsule 干净室：摄像头锚定、展开下挂、避摄模式、触控热区。
 */
class OneCapsuleOverlay private constructor(context: Context) {
    /** MT EXTRA_TOUCH 心智：视觉胶囊外扩不可见热区。 */
    private val extraTouchDp = 12
    private val app = context.applicationContext
    private val prefs = LiveStatusPrefs(app)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wm = app.getSystemService(WindowManager::class.java)

    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var pillRow: LinearLayout? = null
    private var pillLeft: TextView? = null
    private var pillGap: Space? = null
    private var pillRight: TextView? = null
    private var pillSingle: TextView? = null
    private var expandedView: LinearLayout? = null
    private var pageHint: TextView? = null
    private var attached = false
    private var lastMode: CapsuleDisplayMode = CapsuleDisplayMode.HIDDEN

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
                    val slot = when {
                        abs(dx) > abs(dy) && abs(dx) > min ->
                            if (dx < 0) CapsuleGestureSlot.SWIPE_LEFT else CapsuleGestureSlot.SWIPE_RIGHT
                        abs(dy) > abs(dx) && abs(dy) > min ->
                            if (dy > 0) CapsuleGestureSlot.SWIPE_DOWN else CapsuleGestureSlot.SWIPE_UP
                        else -> return false
                    }
                    return runGesture(slot)
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean =
                    runGesture(CapsuleGestureSlot.TAP)
            },
        )
    }

    private fun runGesture(slot: CapsuleGestureSlot): Boolean {
        val action = prefs.gestureAction(slot)
        val ok = CapsuleGestureDispatcher.dispatch(action)
        if (ok) {
            when (action) {
                CapsuleGestureAction.EXPAND,
                CapsuleGestureAction.COLLAPSE,
                CapsuleGestureAction.TOGGLE,
                -> CapsuleHaptics.confirm(root, prefs.hapticEnabled)
                CapsuleGestureAction.NEXT,
                CapsuleGestureAction.PREV,
                -> CapsuleHaptics.tick(root, prefs.hapticEnabled)
                CapsuleGestureAction.NONE -> Unit
            }
        }
        return ok
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
        runOnMain { render(OneCapsuleStore.snapshot()) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureAttached() {
        if (attached || wm == null || !canDraw()) return
        val rootLl = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = dp(extraTouchDp)
            setPadding(pad, pad, pad, pad)
            minimumHeight = dp(48)
            setOnTouchListener { _, ev -> gestureDetector.onTouchEvent(ev) }
        }
        val row = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val left = pillTextView()
        val gap = Space(app)
        val right = pillTextView()
        val single = pillTextView()
        row.addView(left)
        row.addView(gap, LinearLayout.LayoutParams(0, 1))
        row.addView(right)
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
            alpha = 0f
        }
        rootLl.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        rootLl.addView(
            single,
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
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

        runCatching {
            wm.addView(rootLl, lp)
            root = rootLl
            pillRow = row
            pillLeft = left
            pillGap = gap
            pillRight = right
            pillSingle = single
            pageHint = hint
            expandedView = expanded
            params = lp
            attached = true
        }
    }

    private fun pillTextView(): TextView = TextView(app).apply {
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
        gravity = Gravity.CENTER
        maxLines = 1
        isSingleLine = true
    }

    private fun detach() {
        val r = root
        if (r != null) runCatching { wm?.removeView(r) }
        root = null
        params = null
        pillRow = null
        pillLeft = null
        pillGap = null
        pillRight = null
        pillSingle = null
        pageHint = null
        expandedView = null
        attached = false
        lastMode = CapsuleDisplayMode.HIDDEN
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
        applyPosition(snap.mode)
        lastMode = snap.mode
    }

    private fun exclusionMode(): CameraExclusionMode =
        if (prefs.cameraExclusionMode == "CAMERA_CENTER") {
            CameraExclusionMode.CAMERA_CENTER
        } else {
            CameraExclusionMode.BELOW
        }

    private fun applyPill(session: CapsuleSession, snap: CapsuleUiSnapshot) {
        val w = prefs.capsuleWidthScale
        val h = prefs.capsuleHeightScale
        val padH = dp((14f * w).toInt().coerceIn(8, 36))
        val padV = dp((4f * h).toInt().coerceIn(2, 12))
        val textSp = (11f * ((w + h) * 0.5f)).coerceIn(9f, 15f)
        val minH = dp((22f * h).toInt().coerceIn(18, 40)).coerceAtLeast(dp(32))
        val radius = dp((14f * h).toInt().coerceIn(10, 22)).toFloat()
        val fill = CapsuleThemeColors.pillFill(app, prefs.dynamicColorEnabled)
        val stroke = CapsuleThemeColors.stroke(prefs.dynamicColorEnabled, session.accentColor)
        fun pillBg(): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            setStroke(dp(1), stroke)
        }
        val slots = session.toSlots()
        val split = exclusionMode() == CameraExclusionMode.CAMERA_CENTER &&
            !slots.secondary.isNullOrBlank()

        if (split) {
            pillRow?.visibility = View.VISIBLE
            pillSingle?.visibility = View.GONE
            styleSegment(
                pillLeft,
                "${slots.iconGlyph} ${slots.primary}",
                padH,
                padV,
                textSp,
                minH,
                pillBg(),
            )
            styleSegment(pillRight, slots.secondary ?: "", padH, padV, textSp, minH, pillBg())
            val gapLp = pillGap?.layoutParams as? LinearLayout.LayoutParams
            gapLp?.width = CameraAnchorResolver.resolve(app).let {
                (it.width + dp(12)).coerceAtLeast(dp(20))
            }
            gapLp?.height = 1
            pillGap?.layoutParams = gapLp
        } else {
            pillRow?.visibility = View.GONE
            pillSingle?.visibility = View.VISIBLE
            styleSegment(
                pillSingle,
                "${slots.iconGlyph} ${slots.pillText()}",
                padH,
                padV,
                textSp,
                minH,
                pillBg(),
            )
            pillSingle?.minWidth = dp((120f * w).toInt().coerceIn(72, 300))
        }
        // 触控热区：扁胶囊外扩，避免「看得见点不中」。
        pillRow?.minimumHeight = dp(48)
        pillSingle?.minimumHeight = dp(48)

        val hint = pageHint ?: return
        if (snap.sessions.size > 1) {
            hint.visibility = View.VISIBLE
            hint.text = "${snap.activeIndex + 1}/${snap.sessions.size} · 左右切换"
        } else {
            hint.visibility = View.GONE
        }
    }

    private fun styleSegment(
        tv: TextView?,
        text: String,
        padH: Int,
        padV: Int,
        textSp: Float,
        minH: Int,
        bg: GradientDrawable,
    ) {
        tv ?: return
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
        tv.setPadding(padH, padV, padH, padV)
        tv.minHeight = minH
        tv.background = bg
    }

    private fun applyExpanded(session: CapsuleSession, show: Boolean) {
        val box = expandedView ?: return
        if (!show) {
            if (box.visibility == View.VISIBLE) {
                box.animate()
                    .alpha(0f)
                    .translationY((-dp(10)).toFloat())
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(CapsuleMotion.COLLAPSE_MS.toLong())
                    .setInterpolator(CapsuleMotion.collapseInterpolator())
                    .withEndAction {
                        box.visibility = View.GONE
                        box.removeAllViews()
                        box.translationY = 0f
                        box.scaleX = 1f
                        box.scaleY = 1f
                    }
                    .start()
                pulsePill(expanding = false)
            } else {
                box.visibility = View.GONE
                box.removeAllViews()
            }
            return
        }
        box.removeAllViews()
        box.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(CapsuleThemeColors.cardFill(app, prefs.dynamicColorEnabled))
            setStroke(
                dp(1),
                CapsuleThemeColors.stroke(prefs.dynamicColorEnabled, session.accentColor),
            )
        }
        box.addView(label(session.title, 15f, true, Color.WHITE))
        if (session.subtitle.isNotBlank()) {
            val sub = label(session.subtitle, 12f, false, Color.parseColor("#B3FFFFFF"))
            (sub.layoutParams as LinearLayout.LayoutParams).topMargin = dp(4)
            box.addView(sub)
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
                    col.addView(
                        View(app).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(if (filled) session.accentColor else Color.parseColor("#44FFFFFF"))
                            }
                            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10))
                        },
                    )
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
                    val chip = label(text, 12f, true, session.accentColor)
                    chip.setPadding(dp(12), dp(8), dp(12), dp(8))
                    chip.background = GradientDrawable().apply {
                        cornerRadius = dp(16).toFloat()
                        setColor(Color.parseColor("#22FFFFFF"))
                    }
                    val mlp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { marginStart = dp(8) }
                    chip.layoutParams = mlp
                    actions.addView(chip)
                }
                box.addView(actions, alp)
            }
        }
        val tip = label(gestureHintText(), 10f, false, Color.parseColor("#66FFFFFF"))
        (tip.layoutParams as LinearLayout.LayoutParams).topMargin = dp(12)
        box.addView(tip)

        val animatingIn = lastMode != CapsuleDisplayMode.EXPANDED
        box.visibility = View.VISIBLE
        if (animatingIn) {
            box.alpha = 0f
            box.translationY = dp(14).toFloat()
            box.scaleX = 0.94f
            box.scaleY = 0.94f
            box.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(CapsuleMotion.EXPAND_MS.toLong())
                .setInterpolator(CapsuleMotion.expandInterpolator())
                .start()
            pulsePill(expanding = true)
        } else {
            box.alpha = 1f
            box.translationY = 0f
            box.scaleX = 1f
            box.scaleY = 1f
        }
    }

    /** 展开/收起时扁胶囊轻弹一下（过冲 1.08 → 回 1.0）。 */
    private fun pulsePill(expanding: Boolean) {
        val targets = listOfNotNull(pillSingle, pillLeft, pillRight).filter { it.visibility == View.VISIBLE }
        val peak = if (expanding) CapsuleMotion.PILL_OVERSHOOT else 0.96f
        targets.forEach { v ->
            v.animate().cancel()
            v.animate()
                .scaleX(peak)
                .scaleY(peak)
                .setDuration(CapsuleMotion.PILL_PULSE_MS.toLong())
                .setInterpolator(CapsuleMotion.bounceInterpolator())
                .withEndAction {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(CapsuleMotion.CROSSFADE_MS.toLong())
                        .setInterpolator(CapsuleMotion.softOpenInterpolator())
                        .start()
                }
                .start()
        }
    }

    private fun label(text: String, sp: Float, bold: Boolean, color: Int): TextView =
        TextView(app).apply {
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

    private fun applyPosition(mode: CapsuleDisplayMode) {
        val lp = params ?: return
        val r = root ?: return
        val pillH = ((22f * prefs.capsuleHeightScale).toInt().coerceIn(18, 40)).let { dp(it) }
        val exclusion = exclusionMode()
        val anchor = CameraAnchorResolver.resolve(app)
        val bounds = CameraAwareCapsuleLayout.compute(
            anchor = anchor,
            mode = mode,
            pillHeightPx = pillH,
            offsetYPx = dp(prefs.capsuleOffsetYDp),
            exclusion = exclusion,
        )
        val screenW = app.resources.displayMetrics.widthPixels
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        // 相对屏幕中心再叠用户左右偏移，使双叶间隙对准挖孔。
        lp.x = (bounds.centerXPx - screenW / 2) + dp(prefs.capsuleOffsetXDp)
        lp.y = bounds.topPx
        runCatching { wm?.updateViewLayout(r, lp) }
    }

    private fun gestureHintText(): String {
        val down = prefs.gestureAction(CapsuleGestureSlot.SWIPE_DOWN).labelZh
        val up = prefs.gestureAction(CapsuleGestureSlot.SWIPE_UP).labelZh
        val tap = prefs.gestureAction(CapsuleGestureSlot.TAP).labelZh
        return "下滑$down · 上滑$up · 点按$tap"
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
