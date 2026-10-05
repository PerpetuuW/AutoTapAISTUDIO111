package com.example.autotap.infrastructure.overlay.panel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable

import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.model.PanelDisplayMode
import com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class ControlPanelOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val listener: ControlPanelListener
) {
    private val d: Float get() = context.resources.displayMetrics.density

    private var rootView: View? = null
    val containerView: View? get() = rootView
    private var rootLinearView: LinearLayout? = null
    // [V35.0] По умолчанию легкий однорядный компактный режим — 0 загромождения экрана
    private var currentMode: PanelDisplayMode = PanelDisplayMode.COMPACT_SINGLE_ROW
    private var isPlayingState: Boolean = false
    private var isNumbersHiddenState: Boolean = false
    private var btnHideRef: View? = null

    private val dm = context.resources.displayMetrics
    private fun dp(value: Int): Int = (value * dm.density).toInt()
    private fun dpF(value: Float): Float = value * dm.density

    private var posX = dp(20)
    private var posY = dp(140)

    fun updateNumbersHiddenState(isHidden: Boolean) {
        isNumbersHiddenState = isHidden
        (btnHideRef as? com.example.autotap.infrastructure.overlay.ui.CanvasIconButton)?.apply {
            if (isHidden) {
                iconColor = Color.parseColor("#FBBF24")
                bgColorStart = Color.parseColor("#3B2606")
                bgColorEnd = Color.parseColor("#1F1403")
                strokeColor = Color.parseColor("#F59E0B")
            } else {
                iconColor = Color.parseColor("#94A3B8")
                bgColorStart = Color.parseColor("#1A202C")
                bgColorEnd = Color.parseColor("#10141D")
                strokeColor = Color.parseColor("#2D3748")
            }
        }
        btnHideRef?.invalidate()
    }

    fun show() {
        // [V35.2] Гарантированный сброс старого кэша: верстка всегда пересоздается актуальной
        rootView?.let { overlayWindowManager.removeViewSafe(it) }
        rootView = null
        rootLinearView = null
        btnHideRef = null

        val container = FrameLayout(context)
        rootView = container

        buildViewHierarchy(container)

        val layoutParams = overlayWindowManager.createLayoutParams().apply {
            this.x = posX
            this.y = posY
        }
        overlayWindowManager.addViewSafe(container, layoutParams)
        applyDisplayMode(currentMode)
        AppLogger.log(context, "PANEL", "Плавающая панель управления открыта")
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

        // [V38.0] Синхронизация состояний CanvasIconButton для большой кнопки
        (rootView?.findViewWithTag<View>("BTN_PLAY_VIEW") as? com.example.autotap.infrastructure.overlay.ui.CanvasIconButton)?.apply {
            iconType = if (isPlaying) com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer.IconType.PAUSE else com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer.IconType.PLAY
            bgColorStart = Color.parseColor(if (isPlaying) "#3A1016" else "#0D2E1E")
            bgColorEnd = Color.parseColor(if (isPlaying) "#1C080F" else "#061A10")
            strokeColor = Color.parseColor(if (isPlaying) "#F43F5E" else "#34D399")
            invalidate()
        }

        // Синхронизация состояний CanvasIconButton для мини-пилюли
        (rootView?.findViewWithTag<View>("MINI_PLAY_BTN") as? com.example.autotap.infrastructure.overlay.ui.CanvasIconButton)?.apply {
            iconType = if (isPlaying) com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer.IconType.PAUSE else com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer.IconType.PLAY
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

        // [V38.0] Топологически корректное переключение состояний 2-кнопочной пилюли
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
                // Скрываем основной корпус пульта, показываем плавающую пилюлю
                rootLinearView?.visibility = View.GONE
                bubble?.visibility = View.VISIBLE
            }
        }

        // Форсированное визуальное обновление шеврона при смене состояния
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
        // [V35.0] Обтекаемая капсула: цельный глубокий матовый корпус с микро-скруглением 22dp
        val rootLinear = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F80F1218"), Color.parseColor("#F8161B24"))
            ).apply {
                cornerRadius = dpF(22f)
                setStroke(dp(1), Color.parseColor("#262D3D"))
            }
            val p = dp(5)
            setPadding(p, p, p, p)
            elevation = dpF(18f)
        }
        rootLinearView = rootLinear

        // [V32.0] Автономный драг без смещения: touchDown фиксирует точные абсолютные координаты
        val miniBubbleLayout = object : LinearLayout(context) {
            private var startRawX = 0f
            private var startRawY = 0f
            private var initLpX = 0
            private var initLpY = 0
            private var isDragging = false
            private val touchSlop = dpF(6f)

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawX = ev.rawX
                        startRawY = ev.rawY
                        val lp = rootView?.layoutParams as? WindowManager.LayoutParams
                        initLpX = lp?.x ?: posX
                        initLpY = lp?.y ?: posY
                        isDragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - startRawX
                        val dy = ev.rawY - startRawY
                        if (Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            isDragging = true
                            startRawX = ev.rawX
                            startRawY = ev.rawY
                            val lp = rootView?.layoutParams as? WindowManager.LayoutParams
                            initLpX = lp?.x ?: posX
                            initLpY = lp?.y ?: posY
                            return true
                        }
                    }
                }
                return super.onInterceptTouchEvent(ev)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                val root = rootView ?: return super.onTouchEvent(event)
                val lp = root.layoutParams as? WindowManager.LayoutParams ?: return super.onTouchEvent(event)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawX = event.rawX
                        startRawY = event.rawY
                        initLpX = lp.x
                        initLpY = lp.y
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startRawX
                        val dy = event.rawY - startRawY
                        if (!isDragging && Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            isDragging = true
                            startRawX = event.rawX
                            startRawY = event.rawY
                            initLpX = lp.x
                            initLpY = lp.y
                        }
                        if (isDragging) {
                            val moveDx = event.rawX - startRawX
                            val moveDy = event.rawY - startRawY
                            lp.x = (initLpX + moveDx).toInt()
                            lp.y = (initLpY + moveDy).toInt()
                            posX = lp.x
                            posY = lp.y
                            overlayWindowManager.updateViewSafe(root, lp)
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            posX = lp.x
                            posY = lp.y
                            isDragging = false
                            return true
                        }
                    }
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            tag = "MINI_BUBBLE"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#F80F1218"), Color.parseColor("#F8161B24"))
            ).apply {
                cornerRadius = dpF(22f)
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            val p = dp(3)
            setPadding(p, p, p, p)
            elevation = dpF(12f)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(dp(88), dp(44))
        }

        // Кнопка разворота в мини-бабле: яркий шеврон ВВЕРХ (^) для раскрытия пульта
        // [V37.0] 2-кнопочная плавающая пилюля на CanvasIconButton
        val btnMiniExpand = com.example.autotap.infrastructure.overlay.ui.CanvasIconButton(context).apply {
            tag = "BTN_MINI_EXPAND"
            iconType = VectorIconDrawer.IconType.PANEL_EXPAND
            iconColor = Color.parseColor("#38BDF8")
            bgColorStart = Color.parseColor("#1A202C")
            bgColorEnd = Color.parseColor("#10141D")
            strokeColor = Color.parseColor("#38BDF8")
            strokeWidthPx = dpF(1.2f)
            cornerRadiusPx = dpF(19f)
            iconPaddingPx = dpF(8f)
            elevation = dpF(3f)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(6) }
            setOnClickListener { applyDisplayMode(PanelDisplayMode.EXPANDED) }
        }
        miniBubbleLayout.addView(btnMiniExpand)

        val btnMiniPlay = com.example.autotap.infrastructure.overlay.ui.CanvasIconButton(context).apply {
            tag = "MINI_PLAY_BTN"
            iconType = if (isPlayingState) VectorIconDrawer.IconType.PAUSE else VectorIconDrawer.IconType.PLAY
            iconColor = Color.WHITE
            bgColorStart = Color.parseColor(if (isPlayingState) "#3A1016" else "#0D2E1E")
            bgColorEnd = Color.parseColor(if (isPlayingState) "#1C080B" else "#061A10")
            strokeColor = Color.parseColor(if (isPlayingState) "#F43F5E" else "#34D399")
            strokeWidthPx = dpF(1.2f)
            cornerRadiusPx = dpF(12f)
            iconPaddingPx = dpF(8f)
            elevation = dpF(3f)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setOnClickListener { listener.onPlayClicked() }
            setOnLongClickListener {
                listener.onPlayLongClicked()
                true
            }
        }
        miniBubbleLayout.addView(btnMiniPlay)

        container.addView(miniBubbleLayout)

        // Микро-разделитель между функциональными кластерами
        fun createSeparatorView(): View = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(20)).apply {
                val m = dp(3)
                setMargins(m, dp(9), m, dp(9))
            }
            setBackgroundColor(Color.parseColor("#262D3D"))
        }

        // =========================================================================
        // РЯД 1: ГЛАВНЫЙ КОМПАКТНЫЙ ТУЛБАР (7 ЭРГОНОМИЧНЫХ ДЕЙСТВИЙ)
        // =========================================================================
        val mainRow = LinearLayout(context).apply {
            tag = "MAIN_ROW"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // 1. Драг-хэндл
        val dragHandle = createIconButton(VectorIconDrawer.IconType.DRAG_HANDLE, "", "#38BDF8", dp(38), "BTN_DRAG") {}
        attachDragListener(dragHandle)
        mainRow.addView(dragHandle)
        mainRow.addView(createSeparatorView())

        // 2. Пуск / Стоп (акцентный эргономичный пилл)
        val playIconView = object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(9f), dpF(9f), width - dpF(9f), height - dpF(9f))
                val icon = if (isPlayingState) VectorIconDrawer.IconType.PAUSE else VectorIconDrawer.IconType.PLAY
                VectorIconDrawer.drawIcon(canvas, icon, bounds, Color.WHITE, dpF(2.5f))
            }
        }.apply {
            tag = "BTN_PLAY_VIEW"
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor(if (isPlayingState) "#3A1016" else "#0D2E1E"), Color.parseColor(if (isPlayingState) "#1C080B" else "#061A10"))
            ).apply {
                cornerRadius = dpF(12f)
                setStroke(dp(1), Color.parseColor(if (isPlayingState) "#F43F5E" else "#34D399"))
            }
            elevation = dpF(2f)
            val size = dp(38)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                val m = dp(1)
                setMargins(m, m, m, m)
            }
            setOnClickListener { listener.onPlayClicked() }
            setOnLongClickListener {
                listener.onPlayLongClicked()
                true
            }
        }
        mainRow.addView(playIconView)
        // 3. Снимок шаблона (увеличенная кнопка 44dp, тонкая синяя обводка #38BDF8)
        val btnCapture = createIconButton(VectorIconDrawer.IconType.CAPTURE, "", "#38BDF8", dp(44), "BTN_CAPTURE") { listener.onCaptureClicked() }
        mainRow.addView(btnCapture)
        // 4. Запись (умная запись шаблонов ИИ и живые жесты)
        val btnRecord = createIconButton(VectorIconDrawer.IconType.RECORD, "", "#F43F5E", dp(38), "BTN_RECORD") {
            showRecordContextMenu(mainRow)
        }
        mainRow.addView(btnRecord)
        // 5. Добавить шаг [+]
        val btnAdd = createIconButton(VectorIconDrawer.IconType.PLUS, "", "#38BDF8", dp(38), "BTN_ADD") {
            showAddActionTypeMenu(mainRow)
        }
        mainRow.addView(btnAdd)
        mainRow.addView(createSeparatorView())

        // 6. Кнопка шторки инструментов:
        // Стрелка вниз 'v' — раскрыть инструменты; стрелка вверх '^' — свернуть в одну строку
        val btnToggle = object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(9f), dpF(9f), width - dpF(9f), height - dpF(9f))
                val icon = if (currentMode == PanelDisplayMode.EXPANDED) VectorIconDrawer.IconType.PANEL_COLLAPSE else VectorIconDrawer.IconType.PANEL_EXPAND
                VectorIconDrawer.drawIcon(canvas, icon, bounds, Color.parseColor("#F8FAFC"), dpF(2.5f))
            }
        }.apply {
            tag = "BTN_TOGGLE_VIEW"
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161B24"))
                cornerRadius = dpF(10f)
                setStroke(dp(1), Color.parseColor("#262D3D"))
            }
            val size = dp(38)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                val m = dp(1)
                setMargins(m, m, m, m)
            }
            setOnClickListener {
                val next = when (currentMode) {
                    PanelDisplayMode.EXPANDED -> PanelDisplayMode.COMPACT_SINGLE_ROW
                    PanelDisplayMode.COMPACT_SINGLE_ROW -> PanelDisplayMode.MINI_BUBBLE
                    PanelDisplayMode.MINI_BUBBLE -> PanelDisplayMode.EXPANDED
                }
                applyDisplayMode(next)
            }
            setOnLongClickListener {
                applyDisplayMode(PanelDisplayMode.EXPANDED)
                true
            }
        }
        mainRow.addView(btnToggle)

        // 7. Закрыть пульт
            val btnClose = createIconButton(VectorIconDrawer.IconType.CLOSE, "", "#F04438", dp(38), "BTN_CLOSE") { listener.onCloseClicked() }
            mainRow.addView(btnClose)

        rootLinear.addView(mainRow)

        // =========================================================================
        // РЯД 2: ВЫДВИЖНАЯ ШТОРКА ИНСТРУМЕНТОВ (ВЫДВИГАЕТСЯ ПО ШЕВРОНУ)
        // =========================================================================
        val subRow = LinearLayout(context).apply {
            tag = "SUB_ROW"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        // 1. Граф (Активный лазурный синий)
        val btnGraph = createIconButton(VectorIconDrawer.IconType.GRAPH_TREE, "", "#38BDF8", dp(36), "BTN_GRAPH") {
            listener.onGraphClicked()
        }
        subRow.addView(btnGraph)

        // 2. Сценарии (Активный лазурный синий)
        val btnScripts = createIconButton(VectorIconDrawer.IconType.SCRIPTS, "", "#38BDF8", dp(36), "BTN_SCRIPTS") { listener.onScriptsClicked() }
        subRow.addView(btnScripts)

        // 3. Замок AFK (Активный лазурный синий)
        val btnLock = createIconButton(VectorIconDrawer.IconType.LOCK, "", "#38BDF8", dp(36), "BTN_LOCK") { listener.onScreenLockClicked() }
        subRow.addView(btnLock)

        // 4. Глаз скрытия (акцентный янтарный при скрытии, лазурный синий по умолчанию)
        val btnHide = createIconButton(VectorIconDrawer.IconType.VISIBILITY, "", if (isNumbersHiddenState) "#FBBF24" else "#38BDF8", dp(36), "BTN_HIDE") {
            listener.onToggleNumbersClicked()
        }
        btnHideRef = btnHide
        subRow.addView(btnHide)

        // 5. Очистить все (Активный лазурный синий)
        val btnClear = createIconButton(VectorIconDrawer.IconType.CLEAR, "", "#38BDF8", dp(36), "BTN_CLEAR") { listener.onClearAllClicked() }
        subRow.addView(btnClear)

        // 6. Журнал логов (Активный лазурный синий)
        val btnLogs = createIconButton(VectorIconDrawer.IconType.LOGS, "", "#38BDF8", dp(36), "BTN_LOGS") {
            listener.onLogsClicked()
        }
        subRow.addView(btnLogs)

        // 7. Настройки (Активный лазурный синий)
        val btnSettings = createIconButton(VectorIconDrawer.IconType.SETTINGS, "", "#38BDF8", dp(36), "BTN_SETTINGS") { listener.onSettingsClicked() }
        subRow.addView(btnSettings)

        // 8. Обучение (?) (Активный лазурный синий)
        val btnTut = createIconButton(VectorIconDrawer.IconType.HELP, "", "#38BDF8", dp(36), "BTN_TUTORIAL") {
            InteractiveTutorialOverlay(
                context = context,
                overlayWindowManager = overlayWindowManager,
                mode = InteractiveTutorialOverlay.TutorialMode.CONTROL_PANEL,
                hostViewProvider = { rootView }
            ).show()
        }
        subRow.addView(btnTut)

        rootLinear.addView(subRow)
        container.addView(rootLinear)
    }

    private fun attachDragListener(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0
            private var initY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var isMoved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val root = rootView ?: return false
                val lp = root.layoutParams as? WindowManager.LayoutParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = lp.x
                        initY = lp.y
                        touchX = event.rawX
                        touchY = event.rawY
                        isMoved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
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
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!isMoved) {
                            v.performClick()
                        } else {
                            posX = lp.x
                            posY = lp.y
                        }
                        isMoved = false
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun showAddActionTypeMenu(anchorView: View) {
        val dm = context.resources.displayMetrics
        fun dp(v: Int): Int = (v * dm.density).toInt()
        fun dpF(v: Float): Float = v * dm.density

        val menuW = dp(320)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F80F1218"), Color.parseColor("#F8161B24"))
            ).apply {
                cornerRadius = dpF(20f)
                setStroke(dp(1), Color.parseColor("#262D3D"))
            }
            elevation = dpF(24f)
            val p = dp(14)
            setPadding(p, p, p, p)
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }

        val headerTextLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvHeader = android.widget.TextView(context).apply {
            text = "ДОБАВИТЬ ШАГ"
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#EDE9FE"))
        }
        val tvSub = android.widget.TextView(context).apply {
            text = "Интерактивный конструктор сценария"
            textSize = 8.5f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        headerTextLayout.addView(tvHeader)
        headerTextLayout.addView(tvSub)
        headerRow.addView(headerTextLayout)

        val btnHelpAction = android.widget.TextView(context).apply {
            text = "?"
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#161B24"))
                setStroke(dp(1), Color.parseColor("#262D3D"))
            }
            setOnClickListener {
                InteractiveTutorialOverlay(
                    context = context,
                    overlayWindowManager = overlayWindowManager,
                    mode = InteractiveTutorialOverlay.TutorialMode.EDIT_STEP
                ).show()
            }
            }
            headerRow.addView(btnHelpAction, LinearLayout.LayoutParams(dp(24), dp(24)))
        container.addView(headerRow)

        var dialogRef: View? = null

        data class MenuItemInfo(
            val label: String,
            val subtext: String,
            val type: com.example.autotap.domain.model.ActionType,
            val icon: VectorIconDrawer.IconType,
            val colorHex: String,
            val isNeural: Boolean = false
        )

        fun createCategoryTitle(title: String): View {
            return android.widget.TextView(context).apply {
                text = title
                textSize = 8.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(dp(2), dp(8), 0, dp(4))
            }
        }

        fun createActionTile(item: MenuItemInfo): View {
            val parsedColor = Color.parseColor(item.colorHex)
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#161B24"))
                    cornerRadius = dpF(9f)
                    setStroke(dp(1), Color.parseColor("#262D3D"))
                }
                val p = dp(7)
                setPadding(p, p, p, p)
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    val m = dp(3)
                    setMargins(m, m, m, m)
                }

                val iconView = object : View(context) {
                    private val iconBounds = RectF()
                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        val s = dpF(20f)
                        val cx = width / 2f
                        val cy = height / 2f
                        iconBounds.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f)
                        VectorIconDrawer.drawIcon(canvas, item.icon, iconBounds, parsedColor, dpF(2f))
                    }
                }
                addView(iconView, LinearLayout.LayoutParams(dp(26), dp(26)))

                val textLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(6), 0, 0, 0)
                }
                val tvT = android.widget.TextView(context).apply {
                    text = item.label
                    textSize = 8.5f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(parsedColor)
                    includeFontPadding = false
                }
                val tvD = android.widget.TextView(context).apply {
                    text = item.subtext
                    textSize = 7.5f
                    setTextColor(Color.parseColor("#94A3B8"))
                    includeFontPadding = false
                }
                textLayout.addView(tvT)
                textLayout.addView(tvD)
                addView(textLayout)

                setOnClickListener {
                    dialogRef?.let { d -> overlayWindowManager.removeViewSafe(d) }
                    listener.onAddActionSelected(item.type, item.isNeural)
                }
            }
        }

        container.addView(createCategoryTitle("КОМПЬЮТЕРНОЕ ЗРЕНИЕ (OPENCV ⇄ AI)"))
        val visionRow1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        visionRow1.addView(createActionTile(MenuItemInfo("OPENCV", "Поиск шаблона", com.example.autotap.domain.model.ActionType.TRIGGER, VectorIconDrawer.IconType.ACTION_TRIGGER, "#F59E0B", false)))
        visionRow1.addView(createActionTile(MenuItemInfo("AI", "Нейропоиск", com.example.autotap.domain.model.ActionType.TRIGGER, VectorIconDrawer.IconType.ACTION_TRIGGER, "#818CF8", true)))
        container.addView(visionRow1)

        val visionRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        visionRow2.addView(createActionTile(MenuItemInfo("OCR ТЕКСТ", "Поиск надписи", com.example.autotap.domain.model.ActionType.OCR, VectorIconDrawer.IconType.ACTION_OCR, "#38BDF8")))
        visionRow2.addView(createActionTile(MenuItemInfo("ЦВЕТ", "Пипетка пикселя", com.example.autotap.domain.model.ActionType.COLOR_CHECK, VectorIconDrawer.IconType.ACTION_COLOR, "#EC4899")))
        container.addView(visionRow2)

        container.addView(createCategoryTitle("ФИЗИЧЕСКИЕ ЖЕСТЫ"))
        val gestureRow1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        gestureRow1.addView(createActionTile(MenuItemInfo("КЛИК", "Одиночный тап", com.example.autotap.domain.model.ActionType.CLICK, VectorIconDrawer.IconType.ACTION_CLICK, "#8B5CF6")))
        gestureRow1.addView(createActionTile(MenuItemInfo("УДЕРЖАНИЕ", "Долгий нажим", com.example.autotap.domain.model.ActionType.LONG_PRESS, VectorIconDrawer.IconType.ACTION_LONG_PRESS, "#A78BFA")))
        container.addView(gestureRow1)

        val gestureRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        gestureRow2.addView(createActionTile(MenuItemInfo("СВАЙП", "Прямой жест", com.example.autotap.domain.model.ActionType.SWIPE, VectorIconDrawer.IconType.ACTION_SWIPE, "#C084FC")))
        gestureRow2.addView(createActionTile(MenuItemInfo("ПУТЬ", "Траектория", com.example.autotap.domain.model.ActionType.PATH, VectorIconDrawer.IconType.ACTION_PATH, "#A78BFA")))
        container.addView(gestureRow2)

        val gestureRow3 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        gestureRow3.addView(createActionTile(MenuItemInfo("ПИНЧ", "Масштабирование", com.example.autotap.domain.model.ActionType.PINCH, VectorIconDrawer.IconType.ACTION_PINCH, "#C084FC")))
        gestureRow3.addView(createActionTile(MenuItemInfo("СЦЕНАРИЙ", "Подпрограмма", com.example.autotap.domain.model.ActionType.SUBROUTINE, VectorIconDrawer.IconType.ACTION_SUBROUTINE, "#10B981")))
        container.addView(gestureRow3)

        val btnCancel = android.widget.Button(context).apply {
            text = "ЗАКРЫТЬ"
            textSize = 9f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161B24"))
                cornerRadius = dpF(8f)
                setStroke(dp(1), Color.parseColor("#262D3D"))
            }
            setOnClickListener { dialogRef?.let { d -> overlayWindowManager.removeViewSafe(d) } }
        }
        container.addView(btnCancel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply {
            topMargin = dp(8)
        })

        dialogRef = container
        val p = overlayWindowManager.createDialogLayoutParams(width = menuW, height = WindowManager.LayoutParams.WRAP_CONTENT)
        overlayWindowManager.addViewSafe(container, p)
    }

    private fun showRecordContextMenu(anchorView: View) {
        val dm = context.resources.displayMetrics
        fun dp(v: Int): Int = (v * dm.density).toInt()
        fun dpF(v: Float): Float = v * dm.density

        val menuW = dp(270)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F1218"))
                cornerRadius = dpF(16f)
                setStroke(dp(1), Color.parseColor("#262D3D"))
            }
            elevation = dpF(18f)
            val p = dp(12)
            setPadding(p, p, p, p)
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        val tvHeader = android.widget.TextView(context).apply {
            text = "РЕЖИМ ЗАПИСИ"
            textSize = 10.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
        }
        headerRow.addView(tvHeader, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnHelpRec = android.widget.TextView(context).apply {
            text = "?"
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#161B24"))
                setStroke(dp(1), Color.parseColor("#262D3D"))
            }
            setOnClickListener {
                android.widget.Toast.makeText(context, "Умная: авто-захват кнопок ИИ\nЖесты: живые свайпы и тапы", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        headerRow.addView(btnHelpRec, LinearLayout.LayoutParams(dp(22), dp(22)))
        container.addView(headerRow)

        var dialogRef: View? = null

        fun createModeItem(title: String, desc: String, colorHex: String, onClickAction: () -> Unit): View {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#161B24"))
                    cornerRadius = dpF(8f)
                    setStroke(dp(1), Color.parseColor("#262D3D"))
                }
                val p = dp(8)
                setPadding(p, p, p, p)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6)
                }
                val tvT = android.widget.TextView(context).apply {
                    text = title
                    textSize = 9.5f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor(colorHex))
                }
                val tvD = android.widget.TextView(context).apply {
                    text = desc
                    textSize = 7.5f
                    setTextColor(Color.parseColor("#94A3B8"))
                }
                addView(tvT)
                addView(tvD)
                setOnClickListener {
                    dialogRef?.let { overlayWindowManager.removeViewSafe(it) }
                    onClickAction()
                }
            }
        }

        container.addView(createModeItem("УМНАЯ ЗАПИСЬ (ШАБЛОНЫ ИИ)", "Вырезает кнопки вокруг тапа и добавляет поиск ИИ", "#F59E0B") {
            listener.onSmartRecordClicked()
        })

        container.addView(createModeItem("ЗАПИСЬ ЖЕСТОВ (ТАПЫ И СВАЙПЫ)", "Пишет физические нажатия и траектории пальца", "#8B5CF6") {
            listener.onRecordClicked()
        })

        val btnCancel = android.widget.Button(context).apply {
            text = "ОТМЕНА"
            textSize = 9f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161B24"))
                cornerRadius = dpF(8f)
            }
            setOnClickListener { dialogRef?.let { overlayWindowManager.removeViewSafe(it) } }
        }
        container.addView(btnCancel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)).apply {
            topMargin = dp(2)
        })

        dialogRef = container
        val p = overlayWindowManager.createDialogLayoutParams(width = menuW, height = WindowManager.LayoutParams.WRAP_CONTENT)
        overlayWindowManager.addViewSafe(container, p)
    }

    private fun enforceControlPanelStyles(root: android.view.View?) {}

    private fun applyPermanentOverlayStyles(root: android.view.View?) {}

    // [V36.0] Современный аппаратный Canvas-компонент с анимацией нажатия и тактильным откликом
    private fun createIconButton(
        iconType: com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer.IconType,
        bgColor: String,
        iconColor: String,
        sizeDp: Int,
        tag: String,
        onClick: (android.view.View) -> Unit
    ): View {
        val density = context.resources.displayMetrics.density
        val effectiveSizeDp = if (sizeDp in 24..48) sizeDp else 38
        val btnSizePx = (effectiveSizeDp * density).toInt()

        val isCloseBtn = tag.contains("close", ignoreCase = true) || tag.contains("exit", ignoreCase = true)
        val isCaptureBtn = tag.contains("capture", ignoreCase = true) || tag.contains("snap", ignoreCase = true)
        val colorInt = Color.parseColor(if (iconColor.isNotBlank() && iconColor != "#2A2D35") iconColor else "#38BDF8")
        val isCustomBg = bgColor.isNotBlank()
        val customBgInt = if (isCustomBg) Color.parseColor(bgColor) else 0
        return com.example.autotap.infrastructure.overlay.ui.CanvasIconButton(context).apply {
            this.tag = tag
            this.iconType = iconType
            this.iconColor = colorInt
            this.bgColorStart = if (isCustomBg) customBgInt else (if (isCloseBtn) Color.parseColor("#2E121B") else Color.parseColor("#162032"))
            this.bgColorEnd = if (isCustomBg) customBgInt else (if (isCloseBtn) Color.parseColor("#1C080F") else Color.parseColor("#0F172A"))
            // [V210.0] Тонкая синяя обводка 1dp на всех кнопках оверлея (Play сохраняет зеленую, Close - красную)
            this.strokeColor = if (isCloseBtn) Color.parseColor("#F43F5E") else Color.parseColor("#38BDF8")
            this.strokeWidthPx = 1.0f * density
            this.cornerRadiusPx = 10f * density
            this.iconPaddingPx = if (isCaptureBtn) 8.5f * density else 7.5f * density
            this.elevation = 2f * density
            layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                val mPx = (1 * density).toInt()
                setMargins(mPx, mPx, mPx, mPx)
            }
            setOnClickListener { onClick(this) }
            }
            }
            }
            
