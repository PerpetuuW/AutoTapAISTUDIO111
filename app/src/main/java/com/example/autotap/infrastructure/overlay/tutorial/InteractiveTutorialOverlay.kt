package com.example.autotap.infrastructure.overlay.tutorial

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import kotlin.math.sin

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class InteractiveTutorialOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val mode: TutorialMode = TutorialMode.CONTROL_PANEL,
    private val hostViewProvider: (() -> View?)? = null,
    private val onHighlightStep: ((stepIndex: Int) -> Unit)? = null,
    private val onFinished: (() -> Unit)? = null
) {

    enum class TutorialMode {
        CONTROL_PANEL, CALIBRATION, CAPTURE, ROI_SELECTOR, EDIT_STEP, SETTINGS, SCRIPTS, JOYSTICK, GRAPH
    }

    private var rootView: FrameLayout? = null
    private var spotlightView: SpotlightCanvasView? = null
    private var cardView: LinearLayout? = null
    private var currentStep = 0

    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    private val steps: List<Triple<String, String, String>> = when (mode) {
        TutorialMode.CONTROL_PANEL -> listOf(
            // РЯД 1: Основные инструменты управления (строго слева направо)
            Triple("РУЧКА ПЕРЕМЕЩЕНИЯ", "Потяните за эту кнопку, чтобы свободно переместить пульт в любое удобное место экрана.", "BTN_DRAG"),
            Triple("СТАРТ И ПАУЗА", "Запуск и остановка макроса. Долгое нажатие открывает пошаговый отладчик.", "BTN_PLAY_VIEW"),
            Triple("ВЫРЕЗКА ШАБЛОНА", "Открывает сенсорный видоискатель для захвата графических объектов для OpenCV и ИИ.", "BTN_CAPTURE"),
            Triple("ЗАПИСЬ ЖЕСТОВ", "Включает меню выбора записи: Умная запись с ИИ-шаблонами или живые жесты.", "BTN_RECORD"),
            Triple("ДОБАВИТЬ ШАГ", "Добавляет новую мишень на экран. Долгое нажатие открывает быстрое меню типов действий.", "BTN_ADD"),
            Triple("СВОРАЧИВАНИЕ В ПИЛЮЛЮ", "Одиночный клик мгновенно сворачивает пульт в компактную 2-кнопочную пилюлю у края экрана.", "BTN_TOGGLE_VIEW"),
            Triple("ЗАКРЫТЬ", "Скрывает плавающие оверлеи и выключает пульт управления.", "BTN_CLOSE"),

            // РЯД 2: Шторка профессиональных инструментов
            Triple("РЕДАКТОР ГРАФА", "Открывает визуальный редактор блок-схем: создание циклов, ветвлений и нелинейной логики.", "BTN_GRAPH"),
            Triple("МЕНЕДЖЕР СЦЕНАРИЕВ", "Сохранение схем в JSON, экспорт/импорт ZIP-архивов и автозапуск сценария по фокусу игры.", "BTN_SCRIPTS"),
            Triple("AFK ЗАМОК", "AMOLED-режим защиты экрана от ложных нажатий с сохранением фоновых кликов (удержание 3с).", "BTN_LOCK"),
            Triple("ТОТАЛЬНОЕ СКРЫТИЕ", "Полностью скрывает все метки, кольца, превью шаблонов и номера, очищая экран для обзора игры.", "BTN_HIDE"),
            Triple("ОЧИСТИТЬ ВСЕ", "Удаляет все установленные мишени текущего сценария с экрана.", "BTN_CLEAR"),
            Triple("ЖУРНАЛ ЛОГОВ", "Открывает консоль отладки и аудита телеметрии в реальном времени.", "BTN_LOGS"),
            Triple("ГЛОБАЛЬНЫЕ НАСТРОЙКИ", "Настройка базовой длительности кликов, скорости свайпов и параметров безопасности.", "BTN_SETTINGS"),
            Triple("ОБУЧЕНИЕ И СПРАВКА", "Интерактивное пошаговое руководство с подсветкой кнопок пульта.", "BTN_TUTORIAL")
        )
        TutorialMode.CALIBRATION -> listOf(
            Triple("ПЕРЕМЕЩЕНИЕ И СТУДИЯ", "Тяните за шапку для сдвига окна или нажмите [СТУДИЯ] для попиксельной доработки маски.", "HEADER"),
            Triple("ТРИАДНЫЙ МОНИТОР", "Слева — «СЫРОЙ ЭТАЛОН», по центру — «МАСКА ИИ», справа — найденная цель «ЭКРАН 1:1».", "PREVIEW"),
            Triple("РЕЖИМЫ (КОНТУР / КАРТИНКА)", "Переключает умное выделение контура для видеорекламы, цельное сопоставление или круг.", "MODES"),
            Triple("ЧУВСТВИТЕЛЬНОСТЬ КОНТУРА", "Регулирует границы внешнего контура. Все внутренние пиксели сохраняются целиком.", "CONTOUR"),
            Triple("ПОРОГ СОВПАДЕНИЯ", "Минимальный порог уверенности ИИ. Все найденные цели на экране подсвечиваются живыми рамками.", "SIMILARITY"),
            Triple("ТЕСТ КЛИКА", "Моментально нажимает по найденной цели для живой проверки прямо из калибровки.", "TEST"),
            Triple("СОХРАНИТЬ ЦЕЛЬ", "Записывает настроенный шаблон и координаты в сценарий кликера.", "APPLY")
        )
        TutorialMode.CAPTURE -> listOf(
            Triple("СЕНСОРНЫЙ ТРЕКПАД", "Касайтесь в любой точке экрана и двигайте рамку для точной наводки.", "TRACKPAD"),
            Triple("МАНИПУЛЯТОР РАЗМЕРА", "Вынесен за правый нижний угол (+18dp). Тяните за синий кружок для изменения размера.", "RESIZE"),
            Triple("ВЫРЕЗАТЬ ШАБЛОН", "Делает моментальный снимок выделенной области и передает в калибровку.", "SNAP"),
            Triple("ЗОНА ПОИСКА (ROI)", "Векторный прицел: фиксирует границы рамки как постоянную область ускоренного поиска.", "ROI"),
            Triple("ФОРМА РАМКИ", "Переключает форму выделения между прямоугольником и кругом.", "SHAPE"),
            Triple("ПОМОЩЬ", "Показывает подсказки по работе с рамкой захвата.", "HELP"),
            Triple("ОТМЕНА", "Закрывает рамку и возвращает панель управления.", "CANCEL")
        )
        TutorialMode.ROI_SELECTOR -> listOf(
            Triple("ЗОНА ИНТЕРЕСА (ROI)", "Ограничивает область поиска на экране, ускоряя распознавание в 10-15 раз.", "ROI_BOX"),
            Triple("МАНИПУЛЯТОР РАЗМЕРА", "Тяните за угол рамки для изменения границ зоны поиска.", "RESIZE"),
            Triple("ПОДТВЕРДИТЬ", "Фиксирует выбранную область для текущего шага OCR или триггера.", "OK"),
            Triple("СБРОСИТЬ", "Сбрасывает ограничение и возвращает поиск по всему экрану.", "RESET"),
            Triple("ОТМЕНА", "Закрывает выбор без сохранения изменений.", "CANCEL")
        )
        TutorialMode.EDIT_STEP -> listOf(
            Triple("НАВИГАЦИЯ ШАГОВ", "Быстрое переключение между шагами сценария кнопками Влево/Вправо.", "NAV"),
            Triple("3-ВКЛАДОЧНАЯ СИСТЕМА", "[ДЕЙСТВИЕ] — базовые жесты, [ЗРЕНИЕ] — шаблоны и маски, [ПЕРЕХОДЫ] — ветвление логики.", "TYPES"),
            Triple("ШАБЛОНЫ И ПАПКИ", "Библиотека шаблонов с полными параметрами. Кнопка [В ПАПКУ] группирует маски по каталогам.", "CAROUSEL"),
            Triple("ПАРАМЕТРЫ ТАЙМИНГОВ", "Пауза перед шагом, длительность удержания касания, повторы и радиус разброса.", "TIMINGS"),
            Triple("ВЕТВЛЕНИЯ И ТАЙМАУТЫ", "Настройка шагов перехода при успешном нахождении цели или по истечении таймаута.", "JUMPS_SECTION"),
            Triple("СОХРАНЕНИЕ ШАГА", "Кнопка [СОХРАНИТЬ ШАГ] фиксирует параметры, а [ТЕСТ] мгновенно проверяет действие.", "ACTIONS")
        )
        TutorialMode.SETTINGS -> listOf(
            Triple("ГЛОБАЛЬНЫЕ НАСТРОЙКИ", "Параметры скорости и безопасности, действующие на все шаги сценария.", "HEADER"),
            Triple("ДЛИТЕЛЬНОСТЬ КЛИКА", "Базовое время удержания касания на экране в миллисекундах (100-150 мс).", "CLICK"),
            Triple("ДЛИТЕЛЬНОСТЬ СВАЙПА", "Базовое время выполнения свайпов и скольжения по кривым Безье (300-500 мс).", "SWIPE"),
            Triple("СОХРАНИТЬ", "Применяет глобальные параметры для текущей сессии.", "SAVE"),
            Triple("ОТМЕНА", "Закрывает настройки без сохранения изменений.", "CANCEL")
        )
        TutorialMode.SCRIPTS -> listOf(
            Triple("ИМЯ СЦЕНАРИЯ", "Введите имя схемы для сохранения текущих мишеней на диск с защитой от перезаписи.", "NAME"),
            Triple("СОХРАНИТЬ СХЕМУ", "Записывает все координаты, шаблоны и логику переходов в файл сценария.", "SAVE"),
            Triple("КАРТОЧКА СЦЕНАРИЯ", "[ШАГИ] — вывод на экран, [ГРАФ] — редактор, [ПЕРЕЗАПИСАТЬ] — обновление, [АВТОЗАПУСК] — привязка к игре.", "LIST")
        )
        TutorialMode.JOYSTICK -> listOf(
            Triple("ЗАГОЛОВОК ДЖОЙСТИКА", "Потяните за верхнюю планку для перемещения стика в удобное место экрана.", "HEADER"),
            Triple("ВИРТУАЛЬНЫЙ СТИК", "Аналоговый джойстик (360 градусов) для плавного непрерывного управления персонажем.", "STICK"),
            Triple("ЗАПИСЬ ПУТИ", "Записывает векторную траекторию движения стика и сохраняет ее в действие PATH.", "RECORD")
        )
        TutorialMode.GRAPH -> listOf(
            Triple("БЛОК-УЗЕЛ (NODE)", "Это базовый элемент сценария. Тяните за шапку узла, чтобы перемещать его по холсту.", "NODE_HEADER"),
            Triple("ПОРТЫ СВЯЗИ", "Зеленые кружки справа — выходы. Тяните от них линию к другому узлу для создания логической связи.", "NODE_PORT"),
            Triple("УСЛОВИЯ И ТРИГГЕРЫ", "Кликните по превью шаблона, чтобы открыть редактор и изменить настройки распознавания ИИ.", "NODE_PREVIEW"),
            Triple("РАСШИРЕННЫЕ ПЕРЕХОДЫ", "ТАЙМАУТ сработает при ошибке, ИТЕРАЦИЯ — в цикле. Подключайте их для создания умных ботов.", "NODE_LOGIC"),
            Triple("КАБЕЛЬ В ПУСТОТУ", "Протяните связь от порта в пустое место холста, чтобы быстро создать новый узел.", "CANVAS_EMPTY")
        )
        }

        private inner class SpotlightCanvasView(context: Context) : View(context) {
        private val maskPaint = Paint().apply {
            color = "#C80B0F17".toColorInt()
            style = Paint.Style.FILL
        }
        private val neonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#6366F1".toColorInt()
            style = Paint.Style.STROKE
            strokeWidth = dpF(2.5f)
        }
        private val sonarPulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#818CF8".toColorInt()
            style = Paint.Style.STROKE
            strokeWidth = dpF(2f)
        }
        private val laserPointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#58A6FF".toColorInt()
            style = Paint.Style.STROKE
            strokeWidth = dpF(1.8f)
            pathEffect = DashPathEffect(floatArrayOf(dpF(6f), dpF(4f)), 0f)
        }
        private val laserDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#6366F1".toColorInt()
            style = Paint.Style.FILL
        }

        private val dimPath = Path()
        private val targetRectF = RectF()
        private val pulseRect = RectF()
        private val cardLoc = IntArray(2)

        var currentHighlightRect: Rect? = null
            set(value) {
                field = value
                invalidate()
            }

        private var animTime = 0f

        init {
            setWillNotDraw(false)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            animTime += 0.05f

            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            dimPath.rewind()
            dimPath.fillType = Path.FillType.EVEN_ODD
            dimPath.addRect(0f, 0f, w, h, Path.Direction.CW)

            val rect = currentHighlightRect
            if (rect != null) {
                val pad = dpF(6f)
                targetRectF.set(
                    rect.left.toFloat() - pad,
                    rect.top.toFloat() - pad,
                    rect.right.toFloat() + pad,
                    rect.bottom.toFloat() + pad
                )
                val radius = dpF(10f)
                dimPath.addRoundRect(targetRectF, radius, radius, Path.Direction.CW)
                canvas.drawPath(dimPath, maskPaint)

                canvas.drawRoundRect(targetRectF, radius, radius, neonBorderPaint)

                val pulsePhase = (sin(animTime.toDouble()).toFloat() + 1f) / 2f
                val pulseExpand = pulsePhase * dpF(16f)
                val pulseAlpha = ((1f - pulsePhase) * 220).toInt().coerceIn(0, 255)
                sonarPulsePaint.alpha = pulseAlpha
                pulseRect.set(
                    targetRectF.left - pulseExpand,
                    targetRectF.top - pulseExpand,
                    targetRectF.right + pulseExpand,
                    targetRectF.bottom + pulseExpand
                )
                canvas.drawRoundRect(pulseRect, radius + pulseExpand, radius + pulseExpand, sonarPulsePaint)

                cardView?.let { card ->
                    card.getLocationOnScreen(cardLoc)
                    val cardCenterX = cardLoc[0] + card.width / 2f
                    val cardY = if (cardLoc[1] > targetRectF.bottom) cardLoc[1].toFloat() else (cardLoc[1] + card.height).toFloat()
                    val targetCenterX = targetRectF.centerX()
                    val targetY = if (cardLoc[1] > targetRectF.bottom) targetRectF.bottom else targetRectF.top

                    canvas.drawLine(cardCenterX, cardY, targetCenterX, targetY, laserPointerPaint)
                    canvas.drawCircle(targetCenterX, targetY, dpF(4f), laserDotPaint)
                }
            } else {
                canvas.drawPath(dimPath, maskPaint)
            }

            postInvalidateOnAnimation()
        }
    }

    fun show() {
        if (rootView != null) return

        val root = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        rootView = root

        val spotlight = SpotlightCanvasView(context)
        spotlightView = spotlight
        root.addView(spotlight, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val screenW = dm.widthPixels
        val cardW = dp(300).coerceAtMost((screenW * 0.94f).toInt())

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8161B22".toColorInt(), "#F80D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(16f)
                setStroke(dp(1), "#6366F1".toColorInt())
            }
            val p = dp(12)
            setPadding(p, p, p, p)
            elevation = dpF(24f)
            setOnTouchListener { _, _ -> true }
        }
        cardView = card

        val tvTitle = TextView(context).apply {
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#A78BFA".toColorInt())
            setPadding(0, 0, 0, dp(4))
        }
        card.addView(tvTitle)

        val tvDesc = TextView(context).apply {
            textSize = 10f
            includeFontPadding = false
            setTextColor(Color.WHITE)
            setLineSpacing(dpF(2.5f), 1.1f)
            setPadding(0, 0, 0, dp(10))
        }
        card.addView(tvDesc)

        val navRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnPrev = Button(context).apply {
            text = "НАЗАД"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#21262D".toColorInt(), "#161B22".toColorInt())
            ).apply {
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        navRow.addView(btnPrev, LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(3) })

        val btnNext = Button(context).apply {
            text = "ДАЛЕЕ"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#1F6FEB".toColorInt(), "#161B22".toColorInt())
            ).apply {
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#58A6FF".toColorInt())
            }
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        navRow.addView(btnNext, LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(3) })

        val btnClose = Button(context).apply {
            text = "ЗАКРЫТЬ"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#F04438".toColorInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#21262D".toColorInt(), "#161B22".toColorInt())
            ).apply {
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#F04438".toColorInt())
            }
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener { dismiss() }
        }
        navRow.addView(btnClose, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)))
        card.addView(navRow)

        val cardLayoutParams = FrameLayout.LayoutParams(cardW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
        root.addView(card, cardLayoutParams)

        val targetLoc = IntArray(2)

        fun resolveHighlightRect(): Rect? {
            val host = hostViewProvider?.invoke() ?: return null
            if (!host.isAttachedToWindow) return null

            val tagOrKey = steps.getOrNull(currentStep)?.third ?: ""
            if (tagOrKey.isEmpty()) return null

            if (host is com.example.autotap.infrastructure.overlay.graph.GraphCanvasView) {
                val canvasRect = host.getTutorialRect(tagOrKey)
                if (canvasRect != null) return canvasRect
            }

            val targetView = host.findViewWithTag<View>(tagOrKey)
            if (targetView != null && targetView.visibility == View.VISIBLE && targetView.width > 0 && targetView.height > 0) {
                var parent = targetView.parent
                while (parent != null) {
                    if (parent is ScrollView) {
                        parent.requestChildFocus(targetView, targetView)
                        break
                    }
                    parent = parent.parent
                }

                targetView.getLocationOnScreen(targetLoc)
                val left = targetLoc[0]
                val top = targetLoc[1]
                val right = left + targetView.width
                val bottom = top + targetView.height
                return Rect(left, top, right, bottom)
            }

            if (host.tag == tagOrKey && host.width > 0 && host.height > 0) {
                host.getLocationOnScreen(targetLoc)
                return Rect(targetLoc[0], targetLoc[1], targetLoc[0] + host.width, targetLoc[1] + host.height)
            }

            return null
        }

        fun updateStepVisuals() {
            val (title, desc, _) = steps[currentStep]
            tvTitle.text = "[${currentStep + 1}/${steps.size}] $title"
            tvDesc.text = desc
            btnNext.text = if (currentStep == steps.size - 1) "ГОТОВО" else "ДАЛЕЕ"

            val targetRect = resolveHighlightRect()
            spotlightView?.currentHighlightRect = targetRect

            val screenWidth = dm.widthPixels
            val screenHeight = dm.heightPixels
            val margin = dp(14)
            val safeTop = dp(35)
            val cardHeightEstimate = dp(140)

            if (targetRect != null) {
                val posX = (targetRect.centerX() - cardW / 2).coerceIn(margin, screenWidth - cardW - margin)
                val posY = if (targetRect.bottom + cardHeightEstimate + margin <= screenHeight - dp(30)) {
                    targetRect.bottom + dp(16)
                } else {
                    (targetRect.top - cardHeightEstimate - dp(16)).coerceIn(safeTop, (screenHeight - cardHeightEstimate - dp(30)).coerceAtLeast(safeTop))
                }
                cardLayoutParams.leftMargin = posX
                cardLayoutParams.topMargin = posY
            } else {
                cardLayoutParams.leftMargin = (screenWidth - cardW) / 2
                cardLayoutParams.topMargin = (screenHeight - cardHeightEstimate) / 2
            }
            card.layoutParams = cardLayoutParams

            onHighlightStep?.invoke(currentStep)
        }

        btnPrev.setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                updateStepVisuals()
                root.postDelayed({ updateStepVisuals() }, 150L)
            }
        }

        btnNext.setOnClickListener {
            if (currentStep < steps.size - 1) {
                currentStep++
                updateStepVisuals()
                root.postDelayed({ updateStepVisuals() }, 150L)
            } else {
                dismiss()
            }
        }

        val params = overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            gravity = Gravity.TOP or Gravity.START
        ).apply {
            this.flags = this.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }

        overlayWindowManager.addViewSafe(root, params)
        root.post {
            updateStepVisuals()
            root.postDelayed({ updateStepVisuals() }, 150L)
        }
    }

    fun dismiss() {
        rootView?.let {
            overlayWindowManager.removeViewSafe(it)
            rootView = null
            spotlightView = null
            cardView = null
            onFinished?.invoke()
        }
    }
}
