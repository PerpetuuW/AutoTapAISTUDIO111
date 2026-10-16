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
                    iconColor = Color.parseColor("#38BDF8")
                    bgColorStart = Color.parseColor("#162032")
                    bgColorEnd = Color.parseColor("#0F172A")
                    strokeColor = Color.parseColor("#38BDF8")
                }
                OverlayVisibilityMode.TRANSPARENT -> {
                    iconColor = Color.parseColor("#A78BFA")
                    bgColorStart = Color.parseColor("#2E123D")
                    bgColorEnd = Color.parseColor("#170924")
                    strokeColor = Color.parseColor("#A78BFA")
                }
                OverlayVisibilityMode.HIDDEN -> {
                    iconColor = Color.parseColor("#FBBF24")
                    bgColorStart = Color.parseColor("#3B2606")
                    bgColorEnd = Color.parseColor("#1F1403")
                    strokeColor = Color.parseColor("#F59E0B")
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

        val container = FrameLayout(context)
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
        // РЯД 1: ГЛАВНЫЙ КОМПАКТНЫЙ ТУЛБАР (8 ДЕЙСТВИЙ, С КРЕСТИКОМ X СПРАВА СВЕРХУ)
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
        val btnAdd = createBtn(VectorIconDrawer.IconType.PLUS, "#58A6FF", 32, "BTN_ADD") { showAddActionTypeMenu(mainRow) }
        mainRow.addView(btnAdd)

        // 4. Запись жестов
        val btnRecord = createBtn(VectorIconDrawer.IconType.RECORD, "#F43F5E", 32, "BTN_RECORD") { showRecordContextMenu(mainRow) }
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

        // 6. Глаз скрытия меток
        val btnHide = createBtn(VectorIconDrawer.IconType.VISIBILITY, if (isNumbersHiddenState) "#FBBF24" else "#38BDF8", 32, "BTN_HIDE") {
            listener.onToggleNumbersClicked()
        }
        btnHideRef = btnHide
        mainRow.addView(btnHide)

        // 7. Настройки
        val btnSettings = createBtn(VectorIconDrawer.IconType.SETTINGS, "#9CA3AF", 32, "BTN_SETTINGS") { listener.onSettingsClicked() }
        mainRow.addView(btnSettings)

        // 8. КРЕСТИК ЗАКРЫТИЯ X (СПРАВА СВЕРХУ)
        val btnClose = createBtn(VectorIconDrawer.IconType.CLOSE, "#F04438", 32, "BTN_CLOSE") { listener.onCloseClicked() }
        mainRow.addView(btnClose)

        attachDragListener(mainRow)
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
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun showAddActionTypeMenu(anchorView: View) {
        com.example.autotap.infrastructure.overlay.dialog.ActionTypeSelectionDialog(
            context = context,
            overlayWindowManager = overlayWindowManager,
            onActionSelected = { type ->
                listener.onAddActionSelected(type)
            }
        ).show()
    }

    private fun showRecordContextMenu(anchorView: View) {
        com.example.autotap.infrastructure.overlay.dialog.RecordModeSelectionDialog(
            context = context,
            overlayWindowManager = overlayWindowManager,
            onSmartRecord = { listener.onSmartRecordClicked() },
            onNormalRecord = { listener.onRecordClicked() }
        ).show()
    }
}
