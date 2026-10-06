package com.example.autotap.infrastructure.overlay.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Высокодетализированная интерактивная демонстрация работы AutoTap.
 * Полностью симулирует реальный плавающий оверлей приложения и сквозную последовательность действий:
 * 1. Создание шага поиска по шаблону (выделение формы стрелки и фиксация ROI)
 * 2. Создание шага распознавания текста OCR ('CLAIM 500x')
 * 3. Добавление шагов обычного клика и кинематического свайпа
 * 4. Нажатие кнопки ПУСК на реальной плавающей панели AutoTap
 * 5. Автоматическое ИИ-сканирование, наложение градиентного шаблона и исполнение всей цепочки сценария.
 *
 * Манипулятор в точности повторяет кибернетическую руку из иконки приложения.
 */
@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class InteractiveRoboticArmDemoDialog(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager
) {

    private var rootFrameLayout: View? = null
    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    private var isPlaying = true
    // Базовая скорость 0.5x для плавного и понятного восприятия
    private var playbackSpeed = 0.5f
    private var animProgress = 0f
    private var selectedManualStage: Int? = null // null = полный цикл, 0..4 = выбор конкретного шага

    private val handler = Handler(Looper.getMainLooper())
    private var animRunnable: Runnable? = null

    fun show() {
        if (rootFrameLayout != null) return

        val root = FrameLayout(context).apply {
            setBackgroundColor("#F8070A13".toColorInt())
        }
        rootFrameLayout = root

        val topControlsBar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F0161B28".toColorInt(), "#F00D111A".toColorInt())
            ).apply {
                cornerRadius = dpF(12f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            val p = dp(8)
            setPadding(p, p, p, p)
            elevation = dpF(16f)
        }

        val demoCanvasView = object : View(context) {
            private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#101826".toColorInt()
                strokeWidth = dpF(1f)
            }
            private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#EE0D121F".toColorInt()
            }
            private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.5f)
                color = "#38BDF8".toColorInt()
            }
            private val gameScreenBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#0B111E".toColorInt()
            }
            private val gameScreenBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.8f)
                color = "#1E293B".toColorInt()
            }
            private val textTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dpF(10f)
                typeface = Typeface.DEFAULT_BOLD
            }
            private val textSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#94A3B8".toColorInt()
                textSize = dpF(8f)
                typeface = Typeface.MONOSPACE
            }
            private val highlightBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                color = "#10B981".toColorInt()
            }
            private val highlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#2510B981".toColorInt()
            }
            private val roiDashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.8f)
                pathEffect = DashPathEffect(floatArrayOf(dpF(6f), dpF(4f)), 0f)
                color = "#F59E0B".toColorInt()
            }
            private val ocrBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.8f)
                color = "#818CF8".toColorInt()
            }
            private val swipeTrajectoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(3f)
                color = "#F59E0B".toColorInt()
                strokeCap = Paint.Cap.ROUND
                pathEffect = DashPathEffect(floatArrayOf(dpF(8f), dpF(5f)), 0f)
            }
            private val scoreBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#EE0F172A".toColorInt()
            }
            private val scoreBadgeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.2f)
                color = "#10B981".toColorInt()
            }
            private val scoreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#10B981".toColorInt()
                textSize = dpF(8.5f)
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            // --- Paints for Cybernetic Robotic Arm (Matching App Icon) ---
            private val armChassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            private val armBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.8f)
                color = "#38BDF8".toColorInt()
            }
            private val neonConduitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                color = "#00F0FF".toColorInt()
            }
            private val neonGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(5f)
                color = "#4000F0FF".toColorInt()
            }
            private val jointBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#0F172A".toColorInt()
            }
            private val jointBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                color = "#64748B".toColorInt()
            }
            private val jointCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#00F0FF".toColorInt()
            }
            private val pistonCylinderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(5f)
                color = "#334155".toColorInt()
            }
            private val pistonRodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(3f)
                color = "#E2E8F0".toColorInt()
            }
            private val gripperJawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#1E293B".toColorInt()
            }
            private val laserScanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            private val laserLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.8f)
                color = "#00F0FF".toColorInt()
            }
            private val tapPulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2.8f)
                color = "#00F0FF".toColorInt()
            }
            private val tapCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
            }
            private val generalItemBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

            private val reusableRectF = RectF()
            private val reusablePath = Path()
            private val iconBounds = RectF()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val now = System.currentTimeMillis()

                // 1. Futuristic Circuit Grid Background
                val step = dpF(28f)
                var gx = 0f
                while (gx < w) {
                    canvas.drawLine(gx, 0f, gx, h, gridPaint)
                    gx += step
                }
                var gy = 0f
                while (gy < h) {
                    canvas.drawLine(0f, gy, w, gy, gridPaint)
                    gy += step
                }

                // 2. Симулируемый экран целевой игры / приложения (Центральная область)
                val gameRect = RectF(w * 0.04f, h * 0.15f, w * 0.96f, h * 0.77f)
                generalItemBg.shader = LinearGradient(
                    gameRect.left, gameRect.top, gameRect.right, gameRect.bottom,
                    intArrayOf("#111827".toColorInt(), "#0B0F19".toColorInt()), null, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(gameRect, dpF(12f), dpF(12f), generalItemBg)
                canvas.drawRoundRect(gameRect, dpF(12f), dpF(12f), gameScreenBorderPaint)

                // Шапка игрового экрана
                val gameHeader = RectF(gameRect.left, gameRect.top, gameRect.right, gameRect.top + dpF(34f))
                generalItemBg.shader = null
                generalItemBg.color = "#1E293B".toColorInt()
                canvas.drawRoundRect(gameHeader, dpF(12f), dpF(12f), generalItemBg)
                canvas.drawText("⚔️ QUEST REWARDS · СИМУЛЯЦИЯ ИГРЫ", gameHeader.left + dpF(14f), gameHeader.centerY() + dpF(4f), textTitlePaint)

                // Игровые элементы:
                // Элемент 1: Зона с сундуком и полупрозрачной стрелкой (Цель для Шаблона + ROI)
                val chestArea = RectF(gameRect.left + dpF(14f), gameHeader.bottom + dpF(14f), gameRect.left + dpF(130f), gameHeader.bottom + dpF(100f))
                generalItemBg.color = "#1A2234".toColorInt()
                canvas.drawRoundRect(chestArea, dpF(8f), dpF(8f), generalItemBg)
                canvas.drawText("СУНДУК x1", chestArea.left + dpF(8f), chestArea.top + dpF(16f), textSubPaint)

                // Полупрозрачная стрелка ▶
                val arrowTarget = RectF(chestArea.centerX() - dpF(18f), chestArea.centerY() - dpF(10f), chestArea.centerX() + dpF(18f), chestArea.centerY() + dpF(18f))
                generalItemBg.color = "#3538BDF8".toColorInt()
                canvas.drawRoundRect(arrowTarget, dpF(4f), dpF(4f), generalItemBg)
                reusablePath.rewind()
                reusablePath.moveTo(arrowTarget.left + dpF(10f), arrowTarget.top + dpF(6f))
                reusablePath.lineTo(arrowTarget.right - dpF(10f), arrowTarget.centerY())
                reusablePath.lineTo(arrowTarget.left + dpF(10f), arrowTarget.bottom - dpF(6f))
                reusablePath.close()
                generalItemBg.color = Color.WHITE
                canvas.drawPath(reusablePath, generalItemBg)

                // Элемент 2: Кнопка "CLAIM 500x" (Цель для OCR текста)
                val ocrClaimBtn = RectF(gameRect.right - dpF(140f), gameHeader.bottom + dpF(24f), gameRect.right - dpF(14f), gameHeader.bottom + dpF(68f))
                generalItemBg.color = "#064E3B".toColorInt()
                canvas.drawRoundRect(ocrClaimBtn, dpF(6f), dpF(6f), generalItemBg)
                gameScreenBorderPaint.color = "#10B981".toColorInt()
                canvas.drawRoundRect(ocrClaimBtn, dpF(6f), dpF(6f), gameScreenBorderPaint)
                canvas.drawText("CLAIM 500x", ocrClaimBtn.left + dpF(20f), ocrClaimBtn.centerY() + dpF(4f), textTitlePaint)

                // Элемент 3: Список наград (Цель для жеста Свайпа)
                val listArea = RectF(gameRect.left + dpF(14f), chestArea.bottom + dpF(14f), gameRect.right - dpF(14f), gameRect.bottom - dpF(14f))
                generalItemBg.color = "#131C2E".toColorInt()
                canvas.drawRoundRect(listArea, dpF(8f), dpF(8f), generalItemBg)
                canvas.drawText("СПИСОК НАГРАД (СВАЙП ДЛЯ ПРОКРУТКИ)", listArea.left + dpF(10f), listArea.top + dpF(16f), textSubPaint)

                for (i in 0..2) {
                    val itemY = listArea.top + dpF(24f) + i * dpF(26f)
                    val itm = RectF(listArea.left + dpF(8f), itemY, listArea.right - dpF(8f), itemY + dpF(22f))
                    generalItemBg.color = "#1E293B".toColorInt()
                    canvas.drawRoundRect(itm, dpF(4f), dpF(4f), generalItemBg)
                    canvas.drawText("Награда #0${i + 1} — Золото +${(i + 1) * 250}", itm.left + dpF(10f), itm.centerY() + dpF(3.5f), textSubPaint)
                }

                // 3. Реальный плавающий оверлей AutoTap (Плавающая панель сбоку экрана)
                val panelRect = RectF(w * 0.06f, h * 0.79f, w * 0.94f, h * 0.88f)
                canvas.drawRoundRect(panelRect, dpF(10f), dpF(10f), panelBgPaint)
                canvas.drawRoundRect(panelRect, dpF(10f), dpF(10f), panelBorderPaint)

                // Кнопки реального пульта:
                val btnW = (panelRect.width() - dpF(28f)) / 5f
                val btnH = panelRect.height() - dpF(12f)
                val btnY = panelRect.top + dpF(6f)

                // 1. Кнопка Пуск (Зеленая)
                val pBtnPlay = RectF(panelRect.left + dpF(6f), btnY, panelRect.left + dpF(6f) + btnW, btnY + btnH)
                generalItemBg.color = "#0D2E1E".toColorInt()
                canvas.drawRoundRect(pBtnPlay, dpF(6f), dpF(6f), generalItemBg)
                gameScreenBorderPaint.color = "#34D399".toColorInt()
                canvas.drawRoundRect(pBtnPlay, dpF(6f), dpF(6f), gameScreenBorderPaint)
                iconBounds.set(pBtnPlay.centerX() - dpF(8f), pBtnPlay.centerY() - dpF(8f), pBtnPlay.centerX() + dpF(8f), pBtnPlay.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.PLAY, iconBounds, "#34D399".toColorInt(), dpF(1.5f))

                // 2. Кнопка + Клик (Синяя)
                val pBtnClick = RectF(pBtnPlay.right + dpF(4f), btnY, pBtnPlay.right + dpF(4f) + btnW, btnY + btnH)
                generalItemBg.color = "#1E293B".toColorInt()
                canvas.drawRoundRect(pBtnClick, dpF(6f), dpF(6f), generalItemBg)
                gameScreenBorderPaint.color = "#38BDF8".toColorInt()
                canvas.drawRoundRect(pBtnClick, dpF(6f), dpF(6f), gameScreenBorderPaint)
                iconBounds.set(pBtnClick.centerX() - dpF(8f), pBtnClick.centerY() - dpF(8f), pBtnClick.centerX() + dpF(8f), pBtnClick.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.ACTION_CLICK, iconBounds, "#38BDF8".toColorInt(), dpF(1.5f))

                // 3. Кнопка + Шаблон (Фиолетовая)
                val pBtnTpl = RectF(pBtnClick.right + dpF(4f), btnY, pBtnClick.right + dpF(4f) + btnW, btnY + btnH)
                generalItemBg.color = "#2E1A47".toColorInt()
                canvas.drawRoundRect(pBtnTpl, dpF(6f), dpF(6f), generalItemBg)
                gameScreenBorderPaint.color = "#A78BFA".toColorInt()
                canvas.drawRoundRect(pBtnTpl, dpF(6f), dpF(6f), gameScreenBorderPaint)
                iconBounds.set(pBtnTpl.centerX() - dpF(8f), pBtnTpl.centerY() - dpF(8f), pBtnTpl.centerX() + dpF(8f), pBtnTpl.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.CAPTURE, iconBounds, "#A78BFA".toColorInt(), dpF(1.5f))

                // 4. Кнопка + OCR (Индиго)
                val pBtnOcr = RectF(pBtnTpl.right + dpF(4f), btnY, pBtnTpl.right + dpF(4f) + btnW, btnY + btnH)
                generalItemBg.color = "#1E1B4B".toColorInt()
                canvas.drawRoundRect(pBtnOcr, dpF(6f), dpF(6f), generalItemBg)
                gameScreenBorderPaint.color = "#818CF8".toColorInt()
                canvas.drawRoundRect(pBtnOcr, dpF(6f), dpF(6f), gameScreenBorderPaint)
                iconBounds.set(pBtnOcr.centerX() - dpF(8f), pBtnOcr.centerY() - dpF(8f), pBtnOcr.centerX() + dpF(8f), pBtnOcr.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.ACTION_OCR, iconBounds, "#818CF8".toColorInt(), dpF(1.5f))

                // 5. Кнопка + Свайп (Оранжевая)
                val pBtnSwipe = RectF(pBtnOcr.right + dpF(4f), btnY, pBtnOcr.right + dpF(4f) + btnW, btnY + btnH)
                generalItemBg.color = "#38230D".toColorInt()
                canvas.drawRoundRect(pBtnSwipe, dpF(6f), dpF(6f), generalItemBg)
                gameScreenBorderPaint.color = "#F59E0B".toColorInt()
                canvas.drawRoundRect(pBtnSwipe, dpF(6f), dpF(6f), gameScreenBorderPaint)
                iconBounds.set(pBtnSwipe.centerX() - dpF(8f), pBtnSwipe.centerY() - dpF(8f), pBtnSwipe.centerX() + dpF(8f), pBtnSwipe.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.ACTION_SWIPE, iconBounds, "#F59E0B".toColorInt(), dpF(1.5f))

                // 4. Сквозная логика стадий (CUJ)
                val armBaseX = w * 0.92f
                val armBaseY = h * 0.08f

                val currentStage = selectedManualStage ?: when {
                    animProgress < 0.20f -> 0 // 1. Создание шага Шаблона + ROI
                    animProgress < 0.40f -> 1 // 2. Создание шага OCR текста
                    animProgress < 0.60f -> 2 // 3. Добавление клика и свайпа
                    animProgress < 0.75f -> 3 // 4. Нажатие на кнопку ПУСК
                    else -> 4                 // 5. ИИ-сканирование и реальное исполнение всех шагов
                }

                val targetX: Float
                val targetY: Float
                val isScanning: Boolean
                val isTapping: Boolean
                val isSwiping: Boolean
                val statusText: String
                val matchScorePct: Int

                when (currentStage) {
                    0 -> {
                        // Роборука жмет кнопку шаблона на пульте, затем видоискатель фиксирует стрелку + ROI
                        val p = if (selectedManualStage != null) (now % 3000) / 3000f else (animProgress / 0.20f)
                        if (p < 0.35f) {
                            targetX = pBtnTpl.centerX()
                            targetY = pBtnTpl.centerY()
                            isTapping = p > 0.25f
                            isScanning = false
                        } else {
                            targetX = arrowTarget.centerX()
                            targetY = arrowTarget.centerY()
                            isTapping = false
                            isScanning = true
                        }
                        isSwiping = false
                        matchScorePct = 98
                        statusText = "1. ШАБЛОН + ROI: Захват полупрозрачной стрелки и установка зоны ROI"
                    }
                    1 -> {
                        // Роборука жмет кнопку OCR на пульте, затем выделяет текстовый бокс 'CLAIM 500x'
                        val p = if (selectedManualStage != null) (now % 3000) / 3000f else ((animProgress - 0.20f) / 0.20f)
                        if (p < 0.35f) {
                            targetX = pBtnOcr.centerX()
                            targetY = pBtnOcr.centerY()
                            isTapping = p > 0.25f
                            isScanning = false
                        } else {
                            targetX = ocrClaimBtn.centerX()
                            targetY = ocrClaimBtn.centerY()
                            isTapping = false
                            isScanning = true
                        }
                        isSwiping = false
                        matchScorePct = 100
                        statusText = "2. OCR ТЕКСТ: Распознавание строки 'CLAIM 500x' по нейросетевым боксам"
                    }
                    2 -> {
                        // Роборука добавляет клик и свайп
                        val p = if (selectedManualStage != null) (now % 3000) / 3000f else ((animProgress - 0.40f) / 0.20f)
                        if (p < 0.5f) {
                            targetX = pBtnClick.centerX()
                            targetY = pBtnClick.centerY()
                            isTapping = p in 0.3f..0.45f
                        } else {
                            targetX = pBtnSwipe.centerX()
                            targetY = pBtnSwipe.centerY()
                            isTapping = p > 0.8f
                        }
                        isScanning = false
                        isSwiping = false
                        matchScorePct = 100
                        statusText = "3. ДЕЙСТВИЯ: Добавление шага обычного тапа и жеста свайпа списка"
                    }
                    3 -> {
                        // Роборука тянется к кнопке ПУСК на пульте и нажимает её
                        val p = if (selectedManualStage != null) (now % 2500) / 2500f else ((animProgress - 0.60f) / 0.15f)
                        targetX = pBtnPlay.centerX()
                        targetY = pBtnPlay.centerY()
                        isScanning = false
                        isTapping = p > 0.6f
                        isSwiping = false
                        matchScorePct = 100
                        statusText = "4. ЗАПУСК: Нажатие [▶ ПУСК] на пульте для старта автоматизации"
                    }
                    else -> {
                        // Реальное исполнение: сканирование ROI -> клик по стрелке -> клик по OCR -> свайп!
                        val p = if (selectedManualStage != null) (now % 4000) / 4000f else ((animProgress - 0.75f) / 0.25f)
                        if (p < 0.35f) {
                            // Клик по найденной стрелке
                            targetX = arrowTarget.centerX()
                            targetY = arrowTarget.centerY()
                            isScanning = p < 0.18f
                            isTapping = p >= 0.18f
                            isSwiping = false
                        } else if (p < 0.70f) {
                            // Клик по OCR кнопке
                            targetX = ocrClaimBtn.centerX()
                            targetY = ocrClaimBtn.centerY()
                            isScanning = p < 0.50f
                            isTapping = p >= 0.50f
                            isSwiping = false
                        } else {
                            // Жест свайпа списка снизу вверх
                            val swipeProg = (p - 0.70f) / 0.30f
                            targetX = listArea.centerX()
                            targetY = (listArea.bottom - dpF(20f)) - swipeProg * dpF(50f)
                            isScanning = false
                            isTapping = false
                            isSwiping = true
                        }
                        matchScorePct = 98
                        statusText = "5. ИСПОЛНЕНИЕ: Мгновенный скан, тап по стрелке, клик по OCR и свайп!"
                    }
                }

                // 5. Отрисовка видоискателей, рамок ROI и OCR на симулируемом экране
                if (currentStage == 0 || (currentStage == 4 && animProgress < 0.85f)) {
                    // Рамка ROI вокруг сундука
                    val roiBox = RectF(chestArea.left - dpF(4f), chestArea.top - dpF(4f), chestArea.right + dpF(4f), chestArea.bottom + dpF(4f))
                    canvas.drawRoundRect(roiBox, dpF(6f), dpF(6f), roiDashedPaint)
                    canvas.drawText("ROI ОБЛАСТЬ", roiBox.left + dpF(4f), roiBox.top - dpF(3f), textSubPaint)

                    // Градиентное наложение шаблона на стрелку
                    highlightFillPaint.shader = LinearGradient(
                        arrowTarget.left, arrowTarget.top, arrowTarget.right, arrowTarget.bottom,
                        intArrayOf("#4038BDF8".toColorInt(), "#2000F0FF".toColorInt()), null, Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(arrowTarget, dpF(4f), dpF(4f), highlightFillPaint)
                    canvas.drawRoundRect(arrowTarget, dpF(4f), dpF(4f), highlightBoxPaint)

                    // Score Badge
                    val badgeW = dpF(36f); val badgeH = dpF(16f)
                    val bX = arrowTarget.left; val bY = arrowTarget.top - badgeH - dpF(2f)
                    reusableRectF.set(bX, bY, bX + badgeW, bY + badgeH)
                    canvas.drawRoundRect(reusableRectF, dpF(8f), dpF(8f), scoreBadgePaint)
                    canvas.drawRoundRect(reusableRectF, dpF(8f), dpF(8f), scoreBadgeBorder)
                    canvas.drawText("$matchScorePct%", reusableRectF.centerX(), reusableRectF.centerY() + dpF(3f), scoreTextPaint)
                }

                if (currentStage == 1 || (currentStage == 4 && animProgress >= 0.85f && animProgress < 0.92f)) {
                    // OCR бокс вокруг кнопки CLAIM
                    canvas.drawRoundRect(ocrClaimBtn, dpF(6f), dpF(6f), ocrBoxPaint)
                    val badgeW = dpF(42f); val badgeH = dpF(16f)
                    val bX = ocrClaimBtn.left; val bY = ocrClaimBtn.top - badgeH - dpF(2f)
                    reusableRectF.set(bX, bY, bX + badgeW, bY + badgeH)
                    canvas.drawRoundRect(reusableRectF, dpF(8f), dpF(8f), scoreBadgePaint)
                    canvas.drawRoundRect(reusableRectF, dpF(8f), dpF(8f), scoreBadgeBorder)
                    canvas.drawText("OCR:100%", reusableRectF.centerX(), reusableRectF.centerY() + dpF(3f), scoreTextPaint)
                }

                if (isSwiping) {
                    // Отрисовка пунктирной линии свайпа с векторной стрелкой
                    canvas.drawLine(listArea.centerX(), listArea.bottom - dpF(20f), listArea.centerX(), listArea.top + dpF(20f), swipeTrajectoryPaint)
                }

                // 6. Отрисовка высокоточного кибернетического манипулятора (РОБОРУКА КАК В ИКОНКЕ)
                val dist = sqrt(((targetX - armBaseX) * (targetX - armBaseX) + (targetY - armBaseY) * (targetY - armBaseY)).toDouble()).toFloat()
                val midX = (armBaseX + targetX) / 2f
                val midY = (armBaseY + targetY) / 2f
                val angle = atan2((targetY - armBaseY).toDouble(), (targetX - armBaseX).toDouble()).toFloat()
                val perpAngle = angle + (PI / 2f).toFloat()
                val bendOffset = dpF(45f).coerceAtMost(dist * 0.28f)
                val elbowX = midX + cos(perpAngle.toDouble()).toFloat() * bendOffset
                val elbowY = midY + sin(perpAngle.toDouble()).toFloat() * bendOffset

                val wristX = targetX + cos((angle + PI).toDouble()).toFloat() * dpF(26f)
                val wristY = targetY + sin((angle + PI).toDouble()).toFloat() * dpF(26f)

                // 6.1 Гидравлический цилиндр и поршень
                val pMidX = (armBaseX + elbowX) / 2f - dpF(8f)
                val pMidY = (armBaseY + elbowY) / 2f - dpF(8f)
                canvas.drawLine(armBaseX, armBaseY, pMidX, pMidY, pistonCylinderPaint)
                canvas.drawLine(pMidX, pMidY, elbowX, elbowY, pistonRodPaint)

                // 6.2 Плечо (Arm Link 1) с металлическим титановым градиентом
                armChassisPaint.shader = LinearGradient(
                    armBaseX, armBaseY, elbowX, elbowY,
                    intArrayOf("#475569".toColorInt(), "#0F172A".toColorInt(), "#1E293B".toColorInt()),
                    null, Shader.TileMode.CLAMP
                )
                drawArmSegment(canvas, armBaseX, armBaseY, elbowX, elbowY, dpF(19f), dpF(14f))

                // Неоновый световод питания
                neonConduitPaint.color = if (isScanning) "#EC4899".toColorInt() else "#00F0FF".toColorInt()
                canvas.drawLine(armBaseX, armBaseY, elbowX, elbowY, neonGlowPaint)
                canvas.drawLine(armBaseX, armBaseY, elbowX, elbowY, neonConduitPaint)

                // 6.3 Предплечье (Arm Link 2)
                armChassisPaint.shader = LinearGradient(
                    elbowX, elbowY, wristX, wristY,
                    intArrayOf("#1E293B".toColorInt(), "#0B132B".toColorInt(), "#334155".toColorInt()),
                    null, Shader.TileMode.CLAMP
                )
                drawArmSegment(canvas, elbowX, elbowY, wristX, wristY, dpF(14f), dpF(10f))
                canvas.drawLine(elbowX, elbowY, wristX, wristY, neonGlowPaint)
                canvas.drawLine(elbowX, elbowY, wristX, wristY, neonConduitPaint)

                // 6.4 Сервоприводы и подшипники
                // База
                canvas.drawCircle(armBaseX, armBaseY, dpF(18f), jointBasePaint)
                canvas.drawCircle(armBaseX, armBaseY, dpF(18f), jointBevelPaint)
                canvas.drawCircle(armBaseX, armBaseY, dpF(7f), jointCorePaint)

                // Локоть
                canvas.drawCircle(elbowX, elbowY, dpF(14f), jointBasePaint)
                canvas.drawCircle(elbowX, elbowY, dpF(14f), jointBevelPaint)
                canvas.drawCircle(elbowX, elbowY, dpF(5f), jointCorePaint)

                // Запястье
                canvas.drawCircle(wristX, wristY, dpF(10f), jointBasePaint)
                canvas.drawCircle(wristX, wristY, dpF(10f), jointBevelPaint)

                // 6.5 Двупалый захват и емкостный стилус
                val fingerAngle = atan2((targetY - wristY).toDouble(), (targetX - wristX).toDouble()).toFloat()
                val fPerp = fingerAngle + (PI / 2f).toFloat()

                // Левый палец
                val j1BaseX = wristX + cos(fPerp.toDouble()).toFloat() * dpF(6f)
                val j1BaseY = wristY + sin(fPerp.toDouble()).toFloat() * dpF(6f)
                reusablePath.rewind()
                reusablePath.moveTo(j1BaseX, j1BaseY)
                reusablePath.lineTo(targetX + cos(fPerp.toDouble()).toFloat() * dpF(2.5f), targetY + sin(fPerp.toDouble()).toFloat() * dpF(2.5f))
                reusablePath.lineTo(targetX, targetY)
                reusablePath.close()
                canvas.drawPath(reusablePath, gripperJawPaint)
                canvas.drawPath(reusablePath, armBorderPaint)

                // Правый палец
                val j2BaseX = wristX - cos(fPerp.toDouble()).toFloat() * dpF(6f)
                val j2BaseY = wristY - sin(fPerp.toDouble()).toFloat() * dpF(6f)
                reusablePath.rewind()
                reusablePath.moveTo(j2BaseX, j2BaseY)
                reusablePath.lineTo(targetX - cos(fPerp.toDouble()).toFloat() * dpF(2.5f), targetY - sin(fPerp.toDouble()).toFloat() * dpF(2.5f))
                reusablePath.lineTo(targetX, targetY)
                reusablePath.close()
                canvas.drawPath(reusablePath, gripperJawPaint)
                canvas.drawPath(reusablePath, armBorderPaint)

                // Светящийся наконечник стилуса
                val tipColor = if (isTapping || isSwiping) "#00F0FF".toColorInt() else if (isScanning) "#EC4899".toColorInt() else "#10B981".toColorInt()
                jointCorePaint.color = tipColor
                canvas.drawCircle(targetX, targetY, dpF(5.5f), jointCorePaint)

                // 7. Лазерный радар сканирования
                if (isScanning) {
                    laserScanPaint.shader = RadialGradient(
                        targetX, targetY, dpF(80f),
                        intArrayOf("#5000F0FF".toColorInt(), "#1000F0FF".toColorInt(), Color.TRANSPARENT),
                        floatArrayOf(0f, 0.6f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(targetX, targetY, dpF(75f), laserScanPaint)

                    val scanAngle = (now % 1000) / 1000f * (2 * PI.toFloat())
                    val scanLineX = targetX + cos(scanAngle.toDouble()).toFloat() * dpF(65f)
                    val scanLineY = targetY + sin(scanAngle.toDouble()).toFloat() * dpF(65f)
                    canvas.drawLine(targetX, targetY, scanLineX, scanLineY, laserLinePaint)
                }

                // 8. Импульсные волны на клик
                if (isTapping) {
                    val pulsePhase = (now % 500) / 500f
                    val r1 = pulsePhase * dpF(38f)
                    val alpha1 = ((1f - pulsePhase) * 255).toInt().coerceIn(0, 255)
                    tapPulsePaint.alpha = alpha1
                    canvas.drawCircle(targetX, targetY, r1, tapPulsePaint)

                    val r2 = (pulsePhase * 0.6f) * dpF(38f)
                    val alpha2 = ((1f - pulsePhase * 0.6f) * 255).toInt().coerceIn(0, 255)
                    tapPulsePaint.alpha = alpha2
                    canvas.drawCircle(targetX, targetY, r2, tapPulsePaint)

                    canvas.drawCircle(targetX, targetY, dpF(4f), tapCorePaint)
                }

                // 9. Нижняя плашка со статусом текущего шага
                val bannerH = dpF(32f)
                val bannerY = h - bannerH - dpF(8f)
                reusableRectF.set(dpF(10f), bannerY, w - dpF(10f), bannerY + bannerH)
                generalItemBg.color = "#E60F172A".toColorInt()
                canvas.drawRoundRect(reusableRectF, dpF(8f), dpF(8f), generalItemBg)
                panelBorderPaint.color = "#38BDF8".toColorInt()
                canvas.drawRoundRect(reusableRectF, dpF(8f), dpF(8f), panelBorderPaint)
                canvas.drawText(statusText, dpF(20f), bannerY + dpF(20f), textTitlePaint)
            }

            private fun drawArmSegment(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w1: Float, w2: Float) {
                val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()).toFloat()
                val perp = angle + (PI / 2f).toFloat()

                val dx1 = cos(perp.toDouble()).toFloat() * (w1 / 2f)
                val dy1 = sin(perp.toDouble()).toFloat() * (w1 / 2f)
                val dx2 = cos(perp.toDouble()).toFloat() * (w2 / 2f)
                val dy2 = sin(perp.toDouble()).toFloat() * (w2 / 2f)

                reusablePath.rewind()
                reusablePath.moveTo(x1 + dx1, y1 + dy1)
                reusablePath.lineTo(x2 + dx2, y2 + dy2)
                reusablePath.lineTo(x2 - dx2, y2 - dy2)
                reusablePath.lineTo(x1 - dx1, y1 - dy1)
                reusablePath.close()

                canvas.drawPath(reusablePath, armChassisPaint)
                canvas.drawPath(reusablePath, armBorderPaint)
            }
        }
        root.addView(demoCanvasView, FrameLayout.LayoutParams(-1, -1))

        // --- Верхняя лаконичная панель управления ---
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }

        val tvTitleHeader = TextView(context).apply {
            text = "🤖 ДЕМО AUTOTAP"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#38BDF8".toColorInt())
        }
        headerRow.addView(tvTitleHeader, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnSpeed = Button(context).apply {
            text = "СКОРОСТЬ 0.5x"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            minHeight = 0; minimumHeight = 0
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#1E293B".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#64748B".toColorInt())
            }
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener {
                playbackSpeed = if (playbackSpeed == 0.5f) 1.0f else if (playbackSpeed == 1.0f) 0.25f else 0.5f
                text = "СКОРОСТЬ ${playbackSpeed}x"
            }
        }
        headerRow.addView(btnSpeed, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(24)).apply { marginEnd = dp(4) })

        val btnPlayPause = Button(context).apply {
            text = "ПАУЗА"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            minHeight = 0; minimumHeight = 0
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#0EA5E9".toColorInt())
                cornerRadius = dpF(4f)
            }
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener {
                isPlaying = !isPlaying
                text = if (isPlaying) "ПАУЗА" else "ПУСК"
            }
        }
        headerRow.addView(btnPlayPause, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(24)).apply { marginEnd = dp(4) })

        val btnClose = Button(context).apply {
            text = "✕"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setTextColor("#94A3B8".toColorInt())
            background = null
            setOnClickListener { dismiss() }
        }
        headerRow.addView(btnClose, LinearLayout.LayoutParams(dp(24), dp(24)))
        topControlsBar.addView(headerRow)

        // Чипы шагов
        val chipScroll = HorizontalScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
        val chipRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val chipTitles = listOf("⚡ ВСЕ ШАГИ", "1. ШАБЛОН+ROI", "2. OCR ТЕКСТ", "3. ДЕЙСТВИЯ", "4. ПУСК", "5. ИСПОЛНЕНИЕ")
        val chipButtons = mutableListOf<Button>()

        chipTitles.forEachIndexed { idx, title ->
            val stageTarget = if (idx == 0) null else (idx - 1)
            val chip = Button(context).apply {
                text = title
                textSize = 7.5f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                gravity = Gravity.CENTER
                minHeight = 0; minimumHeight = 0
                val isSelected = (selectedManualStage == stageTarget)
                setTextColor(if (isSelected) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (isSelected) "#38BDF8" else "#1E293B"))
                    cornerRadius = dpF(4f)
                }
                setPadding(dp(6), dp(2), dp(6), dp(2))
                setOnClickListener {
                    selectedManualStage = stageTarget
                    chipButtons.forEachIndexed { cIdx, btn ->
                        val cTarget = if (cIdx == 0) null else (cIdx - 1)
                        val active = (selectedManualStage == cTarget)
                        btn.setTextColor(if (active) Color.BLACK else Color.WHITE)
                        (btn.background as? GradientDrawable)?.setColor(Color.parseColor(if (active) "#38BDF8" else "#1E293B"))
                    }
                }
            }
            chipButtons.add(chip)
            chipRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(22)).apply {
                marginEnd = dp(4)
            })
        }
        chipScroll.addView(chipRow)
        topControlsBar.addView(chipScroll)

        val barLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            setMargins(dp(10), dp(16), dp(10), 0)
        }
        root.addView(topControlsBar, barLp)

        // Анимационный цикл с базовой скоростью 0.5x
        animRunnable = object : Runnable {
            override fun run() {
                if (isPlaying) {
                    animProgress = (animProgress + 0.0025f * playbackSpeed) % 1.0f
                    demoCanvasView.invalidate()
                }
                handler.postDelayed(this, 25L)
            }
        }
        handler.post(animRunnable!!)

        val params = overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            gravity = Gravity.TOP or Gravity.START
        )
        overlayWindowManager.addViewSafe(root, params)
    }

    fun dismiss() {
        animRunnable?.let { handler.removeCallbacks(it) }
        rootFrameLayout?.let {
            overlayWindowManager.removeViewSafe(it)
            rootFrameLayout = null
        }
    }
}
