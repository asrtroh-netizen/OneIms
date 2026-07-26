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
            // 触控热区在 root；视觉壳保持扁胶囊高度，勿硬抬到 48dp 变「圆角矩形」。
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
        val padH = dp((16f * w).toInt().coerceIn(12, 40))
        val padV = dp((4f * h).toInt().coerceIn(2, 10))
        val textSp = (11f * ((w + h) * 0.5f)).coerceIn(9f, 14f)
        // 扁胶囊高度（视觉）；超大 cornerRadius 会被系统钳成「短边一半」= 正胶囊/跑道形。
        val visualH = dp((22f * h).toInt().coerceIn(20, 36))
        val fill = CapsuleThemeColors.pillFill(app, prefs.dynamicColorEnabled)
        val stroke = CapsuleThemeColors.stroke(prefs.dynamicColorEnabled, session.accentColor)
        shell.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 9999f
            setColor(fill)
            setStroke(dp(1), stroke)
        }
        shell.setPadding(padH, padV, padH, padV)
        shell.minimumHeight = visualH
        shell.minimumWidth = dp((140f * w).toInt().coerceIn(110, 340))
        // 固定视觉高度，避免内容撑高后圆角相对变钝。
        shell.layoutParams = (shell.layoutParams as? LinearLayout.LayoutParams)?.apply {
            height = visualH + padV * 2
            width = LinearLayout.LayoutParams.WRAP_CONTENT
        } ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            visualH + padV * 2,
        )

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
        // 海报浅色大卡：不用动态色压成黑底。
        val softFill = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(session.accentColor, Color.WHITE, 0.90f),
            0xFA,
        )
        val onCard = Color.parseColor("#FF1A1C1E")
        val onCardMuted = Color.parseColor("#99000000")
        box.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(28).toFloat()
            setColor(softFill)
            setStroke(dp(1), ColorUtils.setAlphaComponent(session.accentColor, 0x40))
        }

        when (session.expandTemplate) {
            CapsuleExpandTemplate.PROGRESS_CARD ->
                buildProgressExpand(box, session, onCard, onCardMuted)
            CapsuleExpandTemplate.DETAIL_CARD ->
                buildDetailExpand(box, session, onCard, onCardMuted)
        }
        val tip = label(gestureHintText(), 10f, false, onCardMuted)
        (tip.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10)
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

    private fun buildProgressExpand(
        box: LinearLayout,
        session: CapsuleSession,
        onCard: Int,
        onCardMuted: Int,
    ) {
        val top = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val texts = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val brandRow = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brandIcon = ImageView(app).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        bindAppIcon(brandIcon, session.source, dp(22))
        brandRow.addView(brandIcon, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) })
        brandRow.addView(label(session.title, 14f, true, onCard))
        texts.addView(brandRow)
        val status = label(
            session.subtitle.substringBefore('·').trim().ifBlank { session.pillPrimary },
            18f,
            true,
            onCard,
        )
        (status.layoutParams as LinearLayout.LayoutParams).topMargin = dp(8)
        texts.addView(status)
        if (session.subtitle.contains('·') || session.pillSecondary != null) {
            val eta = label(
                session.subtitle.substringAfter('·', session.pillSecondary ?: "").trim()
                    .ifBlank { session.subtitle },
                13f,
                false,
                onCardMuted,
            )
            (eta.layoutParams as LinearLayout.LayoutParams).topMargin = dp(4)
            texts.addView(eta)
        }
        top.addView(texts)
        val hero = ImageView(app).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        bindAppIcon(hero, session.source, dp(64))
        top.addView(hero, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginStart = dp(8) })
        box.addView(top)

        val stagesRow = LinearLayout(app).apply {
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
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val filled = index <= session.activeStageIndex || stage.done
            col.addView(
                View(app).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(
                            if (filled) session.accentColor
                            else ColorUtils.setAlphaComponent(onCard, 0x28),
                        )
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(11), dp(11))
                },
            )
            val stageLabel = label(stage.label, 10f, filled, if (filled) onCard else onCardMuted)
            (stageLabel.layoutParams as LinearLayout.LayoutParams).topMargin = dp(6)
            col.addView(stageLabel)
            stagesRow.addView(col)
        }
        box.addView(stagesRow, lpRow)
    }

    private fun buildDetailExpand(
        box: LinearLayout,
        session: CapsuleSession,
        onCard: Int,
        onCardMuted: Int,
    ) {
        val header = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerIcon = ImageView(app).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        bindAppIcon(headerIcon, session.source, dp(26))
        header.addView(headerIcon, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(10) })
        header.addView(label(session.title, 15f, true, onCard))
        box.addView(header)

        val distance = session.detailRows.firstOrNull { it.first.contains("距离") }?.second
            ?: session.detailRows.firstOrNull()?.second
        val status = session.detailRows.firstOrNull { it.first.contains("状态") }?.second
            ?: session.subtitle.substringBefore('·').trim()
        if (!distance.isNullOrBlank()) {
            val d = label(distance, 13f, false, onCardMuted)
            (d.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10)
            box.addView(d)
        }
        val statusTv = label(status.ifBlank { session.pillPrimary }, 18f, true, onCard)
        (statusTv.layoutParams as LinearLayout.LayoutParams).topMargin = dp(4)
        box.addView(statusTv)

        val map = CapsuleMapStubView(app).apply { accentColor = session.accentColor }
        box.addView(
            map,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(110),
            ).apply { topMargin = dp(12) },
        )

        val driverName = session.detailRows.firstOrNull { it.first.contains("司机") }?.second ?: "司机"
        val plate = session.detailRows.firstOrNull { it.first.contains("车牌") }?.second ?: ""
        val person = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatar = TextView(app).apply {
            text = driverName.take(1)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(session.accentColor)
            }
        }
        person.addView(avatar, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
        val personText = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL }
        personText.addView(label(driverName, 15f, true, onCard))
        if (plate.isNotBlank()) {
            val p = label(plate, 13f, false, onCardMuted)
            (p.layoutParams as LinearLayout.LayoutParams).topMargin = dp(2)
            personText.addView(p)
        }
        person.addView(personText)
        box.addView(
            person,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(14) },
        )

        val actions = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val actionSpecs = listOfNotNull(
            session.actionSecondary?.let { "↗ $it" to it },
            session.actionPrimary?.let { "☎ $it" to it },
        ).ifEmpty {
            listOf("☎ 联系" to "联系", "↗ 分享" to "分享")
        }
        actionSpecs.forEachIndexed { index, (title, _) ->
            val chip = label(title, 13f, true, Color.WHITE)
            chip.gravity = Gravity.CENTER
            chip.setPadding(dp(16), dp(12), dp(16), dp(12))
            chip.background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(session.accentColor)
            }
            val mlp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (index > 0) mlp.marginStart = dp(10)
            actions.addView(chip, mlp)
        }
        box.addView(
            actions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(14) },
        )
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
