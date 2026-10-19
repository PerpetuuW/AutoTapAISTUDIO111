package com.example.autotap.infrastructure.overlay.panel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.toColorInt

import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.model.OverlayVisibilityMode
import com.example.autotap.infrastructure.overlay.model.PanelDisplayMode
import com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay
import com.example.autotap.infrastructure.overlay.ui.CanvasIconButton
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import kotlin.math.hypot

/**
 * Высокопроизводительный нативный Kotlin Canvas оверлей панели управления (120 FPS).
 * Нулевые аллокации в цикле рендеринга, мгновенный отклик и 100% стабильная геометрия.
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class ControlPanelOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val listener: ControlPanelListener
) {
    private var rootView: View? = null
    val containerView: View? get() = rootView
    private var rootLinearView: LinearLayout? = null
    private var currentMode: PanelDisplayMode = PanelDisplayMode.COMPACT_SINGLE_ROW
    private var isPlayingState: Boolean = false
    private var visibilityModeState: OverlayVisibilityMode = OverlayVisibilityMode.FULL
    private var isNumbersHiddenState: Boolean = false
    private var btnHideRef: View? = null

    private val dm = context.resources.displayMetrics
    private fun dp(value: Int): Int = (value * dm.density).toInt()
    private fun dpF(value: Float): Float = value * dm.density

    private var posX = dp(20)
    private var posY = dp(140)

    fun getTutorialRect(tagKey: String): android.graphics.Rect? {
        val view = rootView?.findViewWithTag<View>(tagKey)
        if (view != null && view.visibility == View.VISIBLE && view.width > 0 && view.height > 0) {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            return android.graphics.Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)
        }
        return android.graphics.Rect(posX, posY, posX + dp(280), posY + dp(80))
    }

    fun updateVisibilityMode(mode: OverlayVisibilityMode) {
        visibilityModeState = mode
        isNumbersHiddenState = (mode == OverlayVisibilityMode.HIDDEN)
        val targetAlpha = if (mode == OverlayVisibilityMode.TRANSPARENT) 0.40f else 1.0f
        rootLinearView?.alpha = targetAlpha
        (btnHideRef as? CanvasIconButton)?.apply {
            when (mode) {
                OverlayVisibilityMode.FULL -> {
                    iconColor = "#38BDF8".toColorInt()
                    bgColorStart = "#162032".toColorInt()
                    bgColorEnd = "#0F172A".toColorInt()
                    strokeColor = "#38BDF8".toColorInt()
                }
                OverlayVisibilityMode.TRANSPARENT -> {
                    iconColor = "#A78BFA".toColorInt()
                    bgColorStart = "#2E123D".toColorInt()
                    bgColorEnd = "#170924".toColorInt()
                    strokeColor = "#A78BFA".toColorInt()
                }
                OverlayVisibilityMode.HIDDEN -> {
                    iconColor = "#FBBF24".toColorInt()
                    bgColorStart = "#3B2606".toColorInt()
                    bgColorEnd = "#1F1403".toColorInt()
                    strokeColor = "#F59E0B".toColorInt()
                }
            }
        }
        btnHideRef?.invalidate()
    }

    fun updateNumbersHiddenState(isHidden: Boolean) {
        updateVisibilityMode(if (isHidden) OverlayVisibilityMode.HIDDEN else OverlayVisibilityMode.FULL)
    }

    fun show() {
        rootView?.let { overlayWindowManager.removeViewSafe(it) }
        rootView = null
        rootLinearView = null
        btnHideRef = null

        val container = object : FrameLayout(context) {
            private var startRawX = 0f
            private var startRawY = 0f
            private var initLpX = 0
            private var initLpY = 0
            private var isDragging = false
            private val touchSlop = dpF(4f)

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawX = ev.rawX
                        startRawY = ev.rawY
                        initLpX = posX
                        initLpY = posY
                        isDragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - startRawX
                        val dy = ev.rawY - startRawY
                        if (hypot(dx, dy) > touchSlop) {
                            isDragging = true
                            return true
                        }
                    }
                }
                return super.onInterceptTouchEvent(ev)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                val lp = layoutParams as? WindowManager.LayoutParams ?: return super.onTouchEvent(event)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawX = event.rawX
                        startRawY = event.rawY
                        initLpX = posX
                        initLpY = posY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startRawX
                        val dy = event.rawY - startRawY
                        if (!isDragging && Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            isDragging = true
                        }
                        if (isDragging) {
                            val maxX = (dm.widthPixels - dp(50)).coerceAtLeast(0)
                            posX = (initLpX + dx.toInt()).coerceIn(0, maxX)
                            posY = (initLpY + dy.toInt()).coerceIn(dp(20), dm.heightPixels - dp(80))
                            lp.x = posX
                            lp.y = posY
                            overlayWindowManager.updateViewSafe(this, lp)
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            isDragging = false
                            return true
                        }
                    }
                }
                return super.onTouchEvent(event)
            }
        }
        rootView = container

        buildViewHierarchy(container)

        val layoutParams = overlayWindowManager.createLayoutParams().apply {
            this.x = posX
            this.y = posY
        }
        overlayWindowManager.addViewSafe(container, layoutParams)
        applyDisplayMode(currentMode)
        updateVisibilityMode(visibilityModeState)
        AppLogger.log(context, "PANEL", "Плавающая панель управления открыта (120 FPS Native Canvas)")
    }

    fun hide() {
        rootView?.let {
            overlayWindowManager.removeViewSafe(it)
            rootView = null
            rootLinearView = null
            btnHideRef = null
        }
    }

    fun setPlayState(isPlaying: Boolean) {
        isPlayingState = isPlaying

        (rootView?.findViewWithTag<View>("BTN_PLAY_VIEW") as? CanvasIconButton)?.apply {
            iconType = if (isPlaying) VectorIconDrawer.IconType.PAUSE else VectorIconDrawer.IconType.PLAY
            bgColorStart = Color.parseColor(if (isPlaying) "#3A1016" else "#0D2E1E")
            bgColorEnd = Color.parseColor(if (isPlaying) "#1C080F" else "#061A10")
            strokeColor = Color.parseColor(if (isPlaying) "#F43F5E" else "#34D399")
            invalidate()
        }

        (rootView?.findViewWithTag<View>("MINI_PLAY_BTN") as? CanvasIconButton)?.apply {
            iconType = if (isPlaying) VectorIconDrawer.IconType.PAUSE else VectorIconDrawer.IconType.PLAY
            bgColorStart = Color.parseColor(if (isPlaying) "#3A1016" else "#0D2E1E")
            bgColorEnd = Color.parseColor(if (isPlaying) "#1C080F" else "#061A10")
            strokeColor = Color.parseColor(if (isPlaying) "#F43F5E" else "#34D399")
            invalidate()
        }
    }

    fun applyDisplayMode(mode: PanelDisplayMode) {
        currentMode = mode
        val mainRow = rootView?.findViewWithTag<LinearLayout>("MAIN_ROW")
        val subRow = rootView?.findViewWithTag<LinearLayout>("SUB_ROW")
        val bubble = rootView?.findViewWithTag<View>("MINI_BUBBLE")

        when (mode) {
            PanelDisplayMode.EXPANDED -> {
                rootLinearView?.visibility = View.VISIBLE
                mainRow?.visibility = View.VISIBLE
                subRow?.visibility = View.VISIBLE
                bubble?.visibility = View.GONE
            }
            PanelDisplayMode.COMPACT_SINGLE_ROW -> {
                rootLinearView?.visibility = View.VISIBLE
                mainRow?.visibility = View.VISIBLE
                subRow?.visibility = View.GONE
                bubble?.visibility = View.GONE
            }
            PanelDisplayMode.MINI_BUBBLE -> {
                rootLinearView?.visibility = View.GONE
                bubble?.visibility = View.VISIBLE
            }
        }

        rootView?.findViewWithTag<View>("BTN_TOGGLE_VIEW")?.invalidate()

        rootView?.let { v ->
            val lp = overlayWindowManager.createLayoutParams().apply {
                this.x = posX
                this.y = posY
            }
            overlayWindowManager.updateViewSafe(v, lp)
        }
    }

    private fun buildViewHierarchy(container: FrameLayout) {
        val rootLinear = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F80F1218"), Color.parseColor("#F8161B24"))
            ).apply {
                cornerRadius = dpF(20f)
                setStroke(dp(1), Color.parseColor("#262D3D"))
            }
            val p = dp(5)
            setPadding(p, p, p, p)
            elevation = dpF(18f)
        }
        rootLinearView = rootLinear

        // =========================================================================
        // МИНИ-БАБЛ (2-кнопочная пилюля)
        // =========================================================================
        val miniBubbleLayout = LinearLayout(context).apply {
            tag = "MINI_BUBBLE"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#F80F1218"), Color.parseColor("#F8161B24"))
            ).apply {
                cornerRadius = dpF(20f)
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            val p = dp(3)
            setPadding(p, p, p, p)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(dp(76), dp(38))
        }

        val btnMiniExpand = CanvasIconButton(context).apply {
            tag = "BTN_MINI_EXPAND"
            iconType = VectorIconDrawer.IconType.PANEL_EXPAND
            iconColor = Color.parseColor("#38BDF8")
            bgColorStart = Color.parseColor("#1A202C")
            bgColorEnd = Color.parseColor("#10141D")
            strokeColor = Color.parseColor("#38BDF8")
            strokeWidthPx = dpF(1.2f)
            cornerRadiusPx = dpF(16f)
            iconPaddingPx = dpF(6f)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(4) }
            setOnClickListener { applyDisplayMode(PanelDisplayMode.EXPANDED) }
        }
        miniBubbleLayout.addView(btnMiniExpand)

        val btnMiniPlay = CanvasIconButton(context).apply {
            tag = "MINI_PLAY_BTN"
            iconType = if (isPlayingState) VectorIconDrawer.IconType.PAUSE else VectorIconDrawer.IconType.PLAY
            iconColor = Color.WHITE
            bgColorStart = Color.parseColor(if (isPlayingState) "#3A1016" else "#0D2E1E")
            bgColorEnd = Color.parseColor(if (isPlayingState) "#1C080B" else "#061A10")
            strokeColor = Color.parseColor(if (isPlayingState) "#F43F5E" else "#34D399")
            strokeWidthPx = dpF(1.2f)
            isCircleShape = true
            iconPaddingPx = dpF(6f)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            setOnClickListener { listener.onPlayClicked() }
            setOnLongClickListener {
                listener.onPlayLongClicked()
                true
            }
        }
        miniBubbleLayout.addView(btnMiniPlay)
        container.addView(miniBubbleLayout)

        // =========================================================================
        // РЯД 1: ГЛАВНЫЙ КОМПАКТНЫЙ ТУЛБАР (6 ДЕЙСТВИЙ, С КРЕСТИКОМ X СПРАВА СВЕРХУ)
        // =========================================================================
        val mainRow = LinearLayout(context).apply {
            tag = "MAIN_ROW"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun createBtn(type: VectorIconDrawer.IconType, colorHex: String, sizeDp: Int, tagStr: String, onClick: () -> Unit): CanvasIconButton {
            return CanvasIconButton(context).apply {
                tag = tagStr
                iconType = type
                iconColor = Color.parseColor(colorHex)
                bgColorStart = Color.parseColor("#162032")
                bgColorEnd = Color.parseColor("#0F172A")
                strokeColor = Color.parseColor(colorHex)
                strokeWidthPx = dpF(1.2f)
                cornerRadiusPx = dpF(12f)
                iconPaddingPx = dpF(6f)
                val s = dp(sizeDp)
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    val m = dp(2)
                    setMargins(m, m, m, m)
                }
                setOnClickListener { onClick() }
            }
        }

        // 1. Пуск / Стоп
        val btnPlay = CanvasIconButton(context).apply {
            tag = "BTN_PLAY_VIEW"
            iconType = if (isPlayingState) VectorIconDrawer.IconType.PAUSE else VectorIconDrawer.IconType.PLAY
            iconColor = Color.WHITE
            bgColorStart = Color.parseColor(if (isPlayingState) "#3A1016" else "#0D2E1E")
            bgColorEnd = Color.parseColor(if (isPlayingState) "#1C080F" else "#061A10")
            strokeColor = Color.parseColor(if (isPlayingState) "#F43F5E" else "#34D399")
            strokeWidthPx = dpF(1.2f)
            cornerRadiusPx = dpF(12f)
            iconPaddingPx = dpF(6f)
            val s = dp(32)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                val m = dp(2)
                setMargins(m, m, m, m)
            }
            setOnClickListener { listener.onPlayClicked() }
            setOnLongClickListener {
                listener.onPlayLongClicked()
                true
            }
        }
        mainRow.addView(btnPlay)

        // 2. Снимок шаблона ИИ (Вырезка — сразу за Play)
        val btnCapture = createBtn(VectorIconDrawer.IconType.CAPTURE, "#38BDF8", 32, "BTN_CAPTURE") { listener.onCaptureClicked() }
        mainRow.addView(btnCapture)

        // 3. Добавить шаг [+]
        val btnAdd = createBtn(VectorIconDrawer.IconType.PLUS, "#58A6FF", 32, "BTN_ADD") { showAddActionTypeMenu() }
        mainRow.addView(btnAdd)

        // 4. Запись жестов
        val btnRecord = createBtn(VectorIconDrawer.IconType.RECORD, "#F43F5E", 32, "BTN_RECORD") { showRecordContextMenu() }
        mainRow.addView(btnRecord)

        // 5. Кнопка шторки (Раскрыть / Свернуть)
        val btnToggle = createBtn(VectorIconDrawer.IconType.PANEL_EXPAND, "#94A3B8", 32, "BTN_TOGGLE_VIEW") {
            val next = when (currentMode) {
                PanelDisplayMode.EXPANDED -> PanelDisplayMode.COMPACT_SINGLE_ROW
                PanelDisplayMode.COMPACT_SINGLE_ROW -> PanelDisplayMode.MINI_BUBBLE
                PanelDisplayMode.MINI_BUBBLE -> PanelDisplayMode.EXPANDED
            }
            applyDisplayMode(next)
        }
        mainRow.addView(btnToggle)

        // 6. КРЕСТИК ЗАКРЫТИЯ X (СПРАВА СВЕРХУ)
        val btnClose = createBtn(VectorIconDrawer.IconType.CLOSE, "#F04438", 32, "BTN_CLOSE") { listener.onCloseClicked() }
        mainRow.addView(btnClose)

        rootLinear.addView(mainRow)

        // =========================================================================
        // РЯД 2: ВЫДВИЖНАЯ ШТОРКА ИНСТРУМЕНТОВ
        // =========================================================================
        val subRow = LinearLayout(context).apply {
            tag = "SUB_ROW"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, 0)
        }

        // Глаз скрытия меток
        val btnHide = createBtn(VectorIconDrawer.IconType.VISIBILITY, if (isNumbersHiddenState) "#FBBF24" else "#38BDF8", 30, "BTN_HIDE") {
            listener.onToggleNumbersClicked()
        }
        btnHideRef = btnHide
        subRow.addView(btnHide)

        // Настройки
        val btnSettings = createBtn(VectorIconDrawer.IconType.SETTINGS, "#9CA3AF", 30, "BTN_SETTINGS") { listener.onSettingsClicked() }
        subRow.addView(btnSettings)

        val btnLock = createBtn(VectorIconDrawer.IconType.LOCK, "#94A3B8", 30, "BTN_LOCK") { listener.onScreenLockClicked() }
        val btnClear = createBtn(VectorIconDrawer.IconType.CLEAR, "#F04438", 30, "BTN_CLEAR") { listener.onClearAllClicked() }
        val btnScripts = createBtn(VectorIconDrawer.IconType.SCRIPTS, "#38BDF8", 30, "BTN_SCRIPTS") { listener.onScriptsClicked() }
        val btnHelp = createBtn(VectorIconDrawer.IconType.HELP, "#9CA3AF", 30, "BTN_HELP") {
            InteractiveTutorialOverlay(
                context = context,
                overlayWindowManager = overlayWindowManager,
                mode = InteractiveTutorialOverlay.TutorialMode.CONTROL_PANEL,
                hostViewProvider = { rootView }
            ).show()
        }

        subRow.addView(btnLock)
        subRow.addView(btnClear)
        subRow.addView(btnScripts)
        subRow.addView(btnHelp)

        rootLinear.addView(subRow)

        actionTypeMenu = buildActionTypeMenu()
        rootLinear.addView(actionTypeMenu)

        recordModeMenu = buildRecordModeMenu()
        rootLinear.addView(recordModeMenu)

        container.addView(rootLinear)
    }

    private fun buildActionTypeMenu(): View {
        val rootMenu = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#161B22"), Color.parseColor("#0D1117"))
            ).apply {
                cornerRadius = dpF(12f)
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            val p = dp(6)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            visibility = View.GONE
        }

        val tvTitle = android.widget.TextView(context).apply {
            text = "ВЫБОР ТИПА ДЕЙСТВИЯ:"
            textSize = 9f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, dp(4))
        }
        rootMenu.addView(tvTitle)

        val types = listOf(
            com.example.autotap.domain.model.ActionType.CLICK to "КЛИК",
            com.example.autotap.domain.model.ActionType.LONG_PRESS to "УДЕРЖАНИЕ",
            com.example.autotap.domain.model.ActionType.SWIPE to "СВАЙП",
            com.example.autotap.domain.model.ActionType.PATH to "ПУТЬ",
            com.example.autotap.domain.model.ActionType.TRIGGER to "ИИ ТРИГГЕР",
            com.example.autotap.domain.model.ActionType.OCR to "OCR ТЕКСТ",
            com.example.autotap.domain.model.ActionType.COLOR_CHECK to "ЦВЕТ",
            com.example.autotap.domain.model.ActionType.SUBROUTINE to "ПОДПРОГРАММА",
            com.example.autotap.domain.model.ActionType.DELAY to "ЗАДЕРЖКА",
            com.example.autotap.domain.model.ActionType.GLOBAL_BACK to "НАЗАД",
            com.example.autotap.domain.model.ActionType.GLOBAL_HOME to "ДОМОЙ"
        )

        types.chunked(3).forEach { rowTypes ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, dp(2))
            }
            rowTypes.forEach { (type, label) ->
                val btn = android.widget.Button(context).apply {
                    text = label
                    textSize = 8f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#21262D"))
                        cornerRadius = dpF(4f)
                        setStroke(dp(1), Color.parseColor("#30363D"))
                    }
                    minHeight = 0; minimumHeight = 0
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    setOnClickListener {
                        actionTypeMenu?.visibility = View.GONE
                        applyDisplayMode(currentMode)
                        listener.onAddActionSelected(type)
                    }
                }
                row.addView(btn, LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginEnd = dp(4) })
            }
            if (rowTypes.size < 3) {
                val diff = 3 - rowTypes.size
                repeat(diff) {
                    row.addView(View(context), LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginEnd = dp(4) })
                }
            }
            rootMenu.addView(row)
        }
        return rootMenu
    }

    private fun buildRecordModeMenu(): View {
        val rootMenu = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#161B22"), Color.parseColor("#0D1117"))
            ).apply {
                cornerRadius = dpF(12f)
                setStroke(dp(1), Color.parseColor("#F43F5E"))
            }
            val p = dp(6)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
            visibility = View.GONE
        }

        val tvTitle = android.widget.TextView(context).apply {
            text = "ВЫБОР РЕЖИМА ЗАПИСИ:"
            textSize = 9f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F43F5E"))
            setPadding(0, 0, 0, dp(4))
        }
        rootMenu.addView(tvTitle)

        val btnSmart = android.widget.Button(context).apply {
            text = "УМНАЯ ЗАПИСЬ (ИИ-шаблоны)"
            textSize = 9f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.parseColor("#86EFAC"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12201A"))
                cornerRadius = dpF(6f)
                setStroke(dp(1), Color.parseColor("#1E3E30"))
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                recordModeMenu?.visibility = View.GONE
                applyDisplayMode(currentMode)
                listener.onSmartRecordClicked()
            }
        }
        rootMenu.addView(btnSmart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)).apply { bottomMargin = dp(4) })

        val btnNormal = android.widget.Button(context).apply {
            text = "ОБЫЧНАЯ ЗАПИСЬ (Живые жесты)"
            textSize = 9f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.parseColor("#C4B5FD"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#22183D"))
                cornerRadius = dpF(6f)
                setStroke(dp(1), Color.parseColor("#3E2B6B"))
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                recordModeMenu?.visibility = View.GONE
                applyDisplayMode(currentMode)
                listener.onRecordClicked()
            }
        }
        rootMenu.addView(btnNormal, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)))

        return rootMenu
    }

    private fun attachDragListener(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0
            private var initY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var isMoved = false

            override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                val root = rootView ?: return false
                val lp = root.layoutParams as? WindowManager.LayoutParams ?: return false
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initX = lp.x
                        initY = lp.y
                        touchX = event.rawX
                        touchY = event.rawY
                        isMoved = false
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchX
                        val dy = event.rawY - touchY
                        if (Math.hypot(dx.toDouble(), dy.toDouble()) > dpF(4f)) {
                            isMoved = true
                        }
                        if (isMoved) {
                            lp.x = (initX + dx.toInt()).coerceIn(0, dm.widthPixels - dp(50))
                            lp.y = (initY + dy.toInt()).coerceIn(0, dm.heightPixels - dp(50))
                            posX = lp.x
                            posY = lp.y
                            overlayWindowManager.updateViewSafe(root, lp)
                        }
                        return true
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        if (!isMoved) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private var actionTypeMenu: View? = null
    private var recordModeMenu: View? = null

    private fun showAddActionTypeMenu() {
        recordModeMenu?.visibility = View.GONE
        if (actionTypeMenu?.visibility == View.VISIBLE) {
            actionTypeMenu?.visibility = View.GONE
        } else {
            actionTypeMenu?.visibility = View.VISIBLE
        }
        applyDisplayMode(currentMode)
    }

    private fun showRecordContextMenu() {
        actionTypeMenu?.visibility = View.GONE
        if (recordModeMenu?.visibility == View.VISIBLE) {
            recordModeMenu?.visibility = View.GONE
        } else {
            recordModeMenu?.visibility = View.VISIBLE
        }
        applyDisplayMode(currentMode)
    }
}
