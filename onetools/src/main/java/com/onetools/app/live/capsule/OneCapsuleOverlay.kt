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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.onetools.app.live.LiveStatusPrefs
import com.onetools.app.live.LiveStatusSource
import kotlin.math.abs

/**
 * One Capsule 悬浮岛。
 * 一体圆角扁胶囊（文字可在摄像头处留缝，壳不分叶）+ 参考图风格展开卡。
 */
class OneCapsuleOverlay private constructor(context: Context) {
    private val extraTouchDp = 12
    private val app = context.applicationContext
    private val prefs = LiveStatusPrefs(app)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wm = app.getSystemService(WindowManager::class.java)

    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    /** 一体壳：单一背景，内部可排 icon / 主文 / 避摄缝 / 副文。 */
    private var pillShell: LinearLayout? = null
    private var pillIcon: ImageView? = null
    private var pillPrimary: TextView? = null
    private var pillTextGap: Space? = null
    private var pillSecondary: TextView? = null
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

        val shell = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
        }
        val icon = ImageView(app).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val primary = pillTextView()
        val gap = Space(app)
        val secondary = pillTextView()
        shell.addView(icon, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(6) })
        shell.addView(
            primary,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        shell.addView(gap, LinearLayout.LayoutParams(0, 1))
        shell.addView(
            secondary,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val hint = TextView(app).apply {
            setTextColor(Color.parseColor("#88FFFFFF"))
            textSize = 10f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val expanded = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(18), dp(16), dp(18), dp(16))
            alpha = 0f
        }

        rootLl.addView(
            shell,
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
                topMargin = dp(10)
                marginStart = dp(20)
                marginEnd = dp(20)
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
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }

        runCatching {
            wm.addView(rootLl, lp)
            root = rootLl
            pillShell = shell
            pillIcon = icon
            pillPrimary = primary
            pillTextGap = gap
            pillSecondary = secondary
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
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        isSingleLine = true
    }

    private fun detach() {
        val r = root
        if (r != null) runCatching { wm?.removeView(r) }
        root = null
        params = null
        pillShell = null
        pillIcon = null
        pillPrimary = null
        pillTextGap = null
        pillSecondary = null
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
        val shell = pillShell ?: return
        val w = prefs.capsuleWidthScale
        val h = prefs.capsuleHeightScale
        val padH = dp((14f * w).toInt().coerceIn(10, 36))
        val padV = dp((5f * h).toInt().coerceIn(3, 14))
        val textSp = (11f * ((w + h) * 0.5f)).coerceIn(9f, 15f)
        // 真胶囊：圆角始终 = 高度一半，高低调节也不会变直角。
        val minH = dp((24f * h).toInt().coerceIn(22, 44)).coerceAtLeast(dp(32))
        val radius = minH / 2f
        val fill = CapsuleThemeColors.pillFill(app, prefs.dynamicColorEnabled)
        val stroke = CapsuleThemeColors.stroke(prefs.dynamicColorEnabled, session.accentColor)
        shell.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            setStroke(dp(1), stroke)
        }
        shell.setPadding(padH, padV, padH, padV)
        shell.minimumHeight = minH.coerceAtLeast(dp(48))
        shell.minimumWidth = dp((128f * w).toInt().coerceIn(96, 320))

        val slots = session.toSlots()
        bindAppIcon(pillIcon, session.source, dp(18))
        pillPrimary?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            setTextColor(Color.WHITE)
            // 主文含品牌短称；副文单独放右侧（壳仍一体）。
            text = if (slots.secondary.isNullOrBlank()) {
                slots.primary
            } else {
                slots.primary
            }
        }

        val useTextGap = exclusionMode() == CameraExclusionMode.CAMERA_CENTER &&
            !slots.secondary.isNullOrBlank()
        val gapLp = pillTextGap?.layoutParams as? LinearLayout.LayoutParams
        if (useTextGap) {
            val gapW = CameraAnchorResolver.resolve(app).let {
                (it.width + dp(10)).coerceAtLeast(dp(18))
            }
            gapLp?.width = gapW
            gapLp?.height = 1
            pillTextGap?.layoutParams = gapLp
            pillTextGap?.visibility = View.VISIBLE
            pillSecondary?.visibility = View.VISIBLE
            pillSecondary?.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
                setTextColor(Color.WHITE)
                text = slots.secondary
            }
        } else {
            gapLp?.width = 0
            pillTextGap?.layoutParams = gapLp
            pillTextGap?.visibility = View.GONE
            if (slots.secondary.isNullOrBlank()) {
                pillSecondary?.visibility = View.GONE
            } else {
                pillSecondary?.visibility = View.VISIBLE
                pillSecondary?.apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
                    setTextColor(Color.parseColor("#CCFFFFFF"))
                    text = " · ${slots.secondary}"
                }
            }
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
            if (box.visibility == View.VISIBLE) {
                box.animate()
                    .alpha(0f)
                    .translationY((-dp(10)).toFloat())
                    .scaleX(0.98f)
                    .scaleY(0.98f)
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
        // 参考海报：浅色圆角大卡 + 品牌色点缀。
        val softFill = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(session.accentColor, Color.WHITE, 0.86f),
            0xF8,
        )
        val onCard = Color.parseColor("#FF1A1C1E")
        val onCardMuted = Color.parseColor("#99000000")
        box.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(28).toFloat()
            setColor(softFill)
            setStroke(dp(1), ColorUtils.setAlphaComponent(session.accentColor, 0x33))
        }

        val header = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerIcon = ImageView(app).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        bindAppIcon(headerIcon, session.source, dp(28))
        header.addView(
            headerIcon,
            LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) },
        )
        val titles = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titles.addView(label(session.title, 16f, true, onCard))
        if (session.subtitle.isNotBlank()) {
            val sub = label(session.subtitle, 13f, false, onCardMuted)
            (sub.layoutParams as LinearLayout.LayoutParams).topMargin = dp(2)
            titles.addView(sub)
        }
        header.addView(titles)
        box.addView(header)

        when (session.expandTemplate) {
            CapsuleExpandTemplate.PROGRESS_CARD -> {
                val row = LinearLayout(app).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val lpRow = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(16) }
                session.stages.forEachIndexed { index, stage ->
                    val col = LinearLayout(app).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f,
                        )
                    }
                    val filled = index <= session.activeStageIndex || stage.done
                    col.addView(
                        View(app).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(
                                    if (filled) session.accentColor
                                    else ColorUtils.setAlphaComponent(onCard, 0x33),
                                )
                            }
                            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10))
                        },
                    )
                    val stageLabel = label(stage.label, 10f, false, onCardMuted)
                    (stageLabel.layoutParams as LinearLayout.LayoutParams).topMargin = dp(6)
                    col.addView(stageLabel)
                    row.addView(col)
                }
                box.addView(row, lpRow)
            }
            CapsuleExpandTemplate.DETAIL_CARD -> {
                session.detailRows.forEach { (k, v) ->
                    val line = label("$k  $v", 13f, false, onCard)
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
                ).apply { topMargin = dp(16) }
                listOfNotNull(session.actionSecondary, session.actionPrimary).forEach { text ->
                    val chip = label(text, 12f, true, Color.WHITE)
                    chip.setPadding(dp(14), dp(10), dp(14), dp(10))
                    chip.background = GradientDrawable().apply {
                        cornerRadius = dp(18).toFloat()
                        setColor(session.accentColor)
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
        val tip = label(gestureHintText(), 10f, false, onCardMuted)
        (tip.layoutParams as LinearLayout.LayoutParams).topMargin = dp(12)
        box.addView(tip)

        val animatingIn = lastMode != CapsuleDisplayMode.EXPANDED
        box.visibility = View.VISIBLE
        if (animatingIn) {
            box.alpha = 0f
            box.translationY = dp(14).toFloat()
            box.scaleX = 0.96f
            box.scaleY = 0.96f
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

    private fun pulsePill(expanding: Boolean) {
        val v = pillShell?.takeIf { it.visibility == View.VISIBLE } ?: return
        val peak = if (expanding) CapsuleMotion.PILL_OVERSHOOT else 0.96f
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

    private fun bindAppIcon(view: ImageView?, source: LiveStatusSource, sizePx: Int) {
        view ?: return
        val pkg = source.packages.firstOrNull()
        val icon = pkg?.let {
            runCatching { app.packageManager.getApplicationIcon(it) }.getOrNull()
        }
        if (icon != null) {
            view.setImageDrawable(icon)
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            view.setPadding(dp(1), dp(1), dp(1), dp(1))
        } else {
            // 未安装时用品牌色圆点 + 字，避免空白。
            view.setImageDrawable(null)
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(sourceFallbackAccent(source))
            }
            view.setPadding(0, 0, 0, 0)
        }
        view.layoutParams = (view.layoutParams as? LinearLayout.LayoutParams)
            ?.apply {
                width = sizePx
                height = sizePx
            }
            ?: LinearLayout.LayoutParams(sizePx, sizePx)
    }

    private fun sourceFallbackAccent(source: LiveStatusSource): Int = when (source) {
        LiveStatusSource.MEITUAN, LiveStatusSource.ELEME -> 0xFFFF6A00.toInt()
        LiveStatusSource.DIDI -> 0xFF00B87A.toInt()
        LiveStatusSource.CAINIAO -> 0xFF1677FF.toInt()
        else -> 0xFF607D8B.toInt()
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
        val pillH = ((24f * prefs.capsuleHeightScale).toInt().coerceIn(22, 44)).let { dp(it) }
        val exclusion = exclusionMode()
        val anchor = CameraAnchorResolver.resolve(app)
        val bounds = CameraAwareCapsuleLayout.compute(
            anchor = anchor,
            mode = mode,
            pillHeightPx = pillH,
            offsetYPx = dp(prefs.capsuleOffsetYDp),
            exclusion = exclusion,
        )
        // 水平居中：只叠用户左右微调，不再按挖孔把整岛拽偏。
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.x = dp(prefs.capsuleOffsetXDp)
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
