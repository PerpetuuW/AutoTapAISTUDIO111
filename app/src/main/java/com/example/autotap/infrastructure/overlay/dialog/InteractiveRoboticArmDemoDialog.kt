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
 * Высокодетализированная интерактивная демонстрация работы автокликера и роботизированной руки.
 * Архитектура манипулятора в точности соответствует стилистике иконки приложения:
 * кибернетический шасси, гидравлические поршни, оптоволоконные неоновые линии питания,
 * поворотные шарниры с реакторным свечением и 2-палый прецизионный захват со стилусом.
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
    private var playbackSpeed = 1.0f
    private var isCleanRecordingMode = false
    private var animProgress = 0f
    private var selectedManualStage: Int? = null // null = auto loop, 0..5 = manual stage lock

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
                color = "#121D2F".toColorInt()
                strokeWidth = dpF(1f)
            }
            private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#E60F172A".toColorInt()
            }
            private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.5f)
            }
            private val textTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dpF(10.5f)
                typeface = Typeface.DEFAULT_BOLD
            }
            private val textSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#94A3B8".toColorInt()
                textSize = dpF(8f)
                typeface = Typeface.MONOSPACE
            }
            private val highlightBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2.2f)
                color = "#10B981".toColorInt()
            }
            private val highlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#2010B981".toColorInt()
            }
            private val roiDashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.8f)
                pathEffect = DashPathEffect(floatArrayOf(dpF(6f), dpF(4f)), 0f)
                color = "#F59E0B".toColorInt()
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
                textSize = dpF(9f)
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
                strokeWidth = dpF(2.2f)
                color = "#64748B".toColorInt()
            }
            private val jointCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#00F0FF".toColorInt()
            }
            private val pistonRodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(3.5f)
                color = "#E2E8F0".toColorInt()
            }
            private val pistonCylinderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(6f)
                color = "#334155".toColorInt()
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
                strokeWidth = dpF(3f)
                color = "#00F0FF".toColorInt()
            }
            private val tapCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
            }
            private val simGameElementPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
                val step = dpF(32f)
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

                // 2. Верхний дашборд функций (Примеры пайплайна AutoTap)
                // --- CARD 1: ШАБЛОН [ИКОНКА КАРТИНКИ] ---
                val c1 = RectF(w * 0.05f, h * 0.15f, w * 0.48f, h * 0.25f)
                cardBorderPaint.color = "#38BDF8".toColorInt()
                canvas.drawRoundRect(c1, dpF(8f), dpF(8f), cardBgPaint)
                canvas.drawRoundRect(c1, dpF(8f), dpF(8f), cardBorderPaint)
                iconBounds.set(c1.left + dpF(8f), c1.top + dpF(8f), c1.left + dpF(28f), c1.top + dpF(28f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.IMAGE, iconBounds, "#38BDF8".toColorInt(), dpF(1.6f))
                canvas.drawText("ШАБЛОН", c1.left + dpF(32f), c1.top + dpF(18f), textTitlePaint)
                canvas.drawText("Маска формы · 85%", c1.left + dpF(32f), c1.top + dpF(32f), textSubPaint)

                // --- CARD 2: OCR ТЕКСТ [ИКОНКА ТЕКСТА] ---
                val c2 = RectF(w * 0.52f, h * 0.15f, w * 0.95f, h * 0.25f)
                cardBorderPaint.color = "#818CF8".toColorInt()
                canvas.drawRoundRect(c2, dpF(8f), dpF(8f), cardBgPaint)
                canvas.drawRoundRect(c2, dpF(8f), dpF(8f), cardBorderPaint)
                iconBounds.set(c2.left + dpF(8f), c2.top + dpF(8f), c2.left + dpF(28f), c2.top + dpF(28f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.ACTION_OCR, iconBounds, "#818CF8".toColorInt(), dpF(1.6f))
                canvas.drawText("OCR ТЕКСТ", c2.left + dpF(32f), c2.top + dpF(18f), textTitlePaint)
                canvas.drawText("'Награда x100'", c2.left + dpF(32f), c2.top + dpF(32f), textSubPaint)

                // --- CARD 3: ЗОНА ROI [ИКОНКА РАМКИ] ---
                val c3 = RectF(w * 0.05f, h * 0.27f, w * 0.48f, h * 0.37f)
                cardBorderPaint.color = "#F59E0B".toColorInt()
                canvas.drawRoundRect(c3, dpF(8f), dpF(8f), cardBgPaint)
                canvas.drawRoundRect(c3, dpF(8f), dpF(8f), cardBorderPaint)
                iconBounds.set(c3.left + dpF(8f), c3.top + dpF(8f), c3.left + dpF(28f), c3.top + dpF(28f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.SHAPE_RECT, iconBounds, "#F59E0B".toColorInt(), dpF(1.6f))
                canvas.drawText("ОБЛАСТЬ ROI", c3.left + dpF(32f), c3.top + dpF(18f), textTitlePaint)
                canvas.drawText("Ускорение 0-3мс", c3.left + dpF(32f), c3.top + dpF(32f), textSubPaint)

                // --- CARD 4: ПОИСК И ЗАПУСК [ИКОНКА ПУСКА] ---
                val c4 = RectF(w * 0.52f, h * 0.27f, w * 0.95f, h * 0.37f)
                cardBorderPaint.color = "#10B981".toColorInt()
                canvas.drawRoundRect(c4, dpF(8f), dpF(8f), cardBgPaint)
                canvas.drawRoundRect(c4, dpF(8f), dpF(8f), cardBorderPaint)
                iconBounds.set(c4.left + dpF(8f), c4.top + dpF(8f), c4.left + dpF(28f), c4.top + dpF(28f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.TEST, iconBounds, "#10B981".toColorInt(), dpF(1.6f))
                canvas.drawText("ПОИСК ⚡", c4.left + dpF(32f), c4.top + dpF(18f), textTitlePaint)
                canvas.drawText("ИИ Сканирование", c4.left + dpF(32f), c4.top + dpF(32f), textSubPaint)

                // 3. Симуляция экрана игры / приложения (Игровая сцена)
                val sceneRect = RectF(w * 0.05f, h * 0.40f, w * 0.95f, h * 0.82f)
                cardBorderPaint.color = "#334155".toColorInt()
                simGameElementPaint.shader = LinearGradient(
                    sceneRect.left, sceneRect.top, sceneRect.right, sceneRect.bottom,
                    intArrayOf("#1A202C".toColorInt(), "#0F172A".toColorInt()), null, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(sceneRect, dpF(10f), dpF(10f), simGameElementPaint)
                canvas.drawRoundRect(sceneRect, dpF(10f), dpF(10f), cardBorderPaint)

                // Игровой фон и элементы внутри сцены
                // Зона поиска ROI внутри экрана
                val simRoiRect = RectF(sceneRect.left + dpF(16f), sceneRect.top + dpF(20f), sceneRect.right - dpF(16f), sceneRect.centerY() + dpF(20f))
                canvas.drawRoundRect(simRoiRect, dpF(6f), dpF(6f), roiDashedPaint)
                canvas.drawText("ОБЛАСТЬ ROI: СУНДУК И СТРЕЛКА", simRoiRect.left + dpF(8f), simRoiRect.top - dpF(4f), textSubPaint)

                // Элемент 1: Полупрозрачная стрелка с шумом (Цель шаблона)
                val simArrowTarget = RectF(simRoiRect.left + dpF(30f), simRoiRect.centerY() - dpF(22f), simRoiRect.left + dpF(80f), simRoiRect.centerY() + dpF(22f))
                simGameElementPaint.shader = null
                simGameElementPaint.color = "#4038BDF8".toColorInt()
                canvas.drawRoundRect(simArrowTarget, dpF(6f), dpF(6f), simGameElementPaint)
                // Рисуем векторную стрелку ▶|
                reusablePath.rewind()
                reusablePath.moveTo(simArrowTarget.left + dpF(14f), simArrowTarget.top + dpF(10f))
                reusablePath.lineTo(simArrowTarget.left + dpF(28f), simArrowTarget.centerY())
                reusablePath.lineTo(simArrowTarget.left + dpF(14f), simArrowTarget.bottom - dpF(10f))
                reusablePath.close()
                simGameElementPaint.color = Color.WHITE
                canvas.drawPath(reusablePath, simGameElementPaint)

                // Элемент 2: Кнопка OCR "Награда x100"
                val simOcrTarget = RectF(simRoiRect.right - dpF(110f), simRoiRect.centerY() - dpF(18f), simRoiRect.right - dpF(10f), simRoiRect.centerY() + dpF(18f))
                simGameElementPaint.color = "#2E1B4E".toColorInt()
                canvas.drawRoundRect(simOcrTarget, dpF(6f), dpF(6f), simGameElementPaint)
                cardBorderPaint.color = "#818CF8".toColorInt()
                canvas.drawRoundRect(simOcrTarget, dpF(6f), dpF(6f), cardBorderPaint)
                canvas.drawText("НАГРАДА x100", simOcrTarget.left + dpF(12f), simOcrTarget.centerY() + dpF(4f), textTitlePaint)

                // Элемент 3: Нижняя панель действий Графа сценария (#1 -> #2 -> #3)
                val graphBar = RectF(sceneRect.left + dpF(16f), sceneRect.bottom - dpF(45f), sceneRect.right - dpF(16f), sceneRect.bottom - dpF(10f))
                cardBorderPaint.color = "#475569".toColorInt()
                canvas.drawRoundRect(graphBar, dpF(6f), dpF(6f), cardBorderPaint)

                val stepW = (graphBar.width() - dpF(24f)) / 3f
                val s1 = RectF(graphBar.left + dpF(6f), graphBar.top + dpF(6f), graphBar.left + dpF(6f) + stepW, graphBar.bottom - dpF(6f))
                val s2 = RectF(s1.right + dpF(6f), graphBar.top + dpF(6f), s1.right + dpF(6f) + stepW, graphBar.bottom - dpF(6f))
                val s3 = RectF(s2.right + dpF(6f), graphBar.top + dpF(6f), s2.right + dpF(6f) + stepW, graphBar.bottom - dpF(6f))

                cardBorderPaint.color = "#38BDF8".toColorInt()
                canvas.drawRoundRect(s1, dpF(4f), dpF(4f), cardBorderPaint)
                canvas.drawText("1. ШАБЛОН", s1.centerX() - dpF(18f), s1.centerY() + dpF(3f), textSubPaint)

                cardBorderPaint.color = "#818CF8".toColorInt()
                canvas.drawRoundRect(s2, dpF(4f), dpF(4f), cardBorderPaint)
                canvas.drawText("2. OCR", s2.centerX() - dpF(12f), s2.centerY() + dpF(3f), textSubPaint)

                cardBorderPaint.color = "#10B981".toColorInt()
                canvas.drawRoundRect(s3, dpF(4f), dpF(4f), cardBorderPaint)
                canvas.drawText("3. КЛИК", s3.centerX() - dpF(14f), s3.centerY() + dpF(3f), textSubPaint)

                // 4. Логика движения роботизированной руки по стадиям
                val armBaseX = w * 0.92f
                val armBaseY = h * 0.08f

                val currentStage = selectedManualStage ?: when {
                    animProgress < 0.16f -> 0 // 1. Выбор карточки Шаблона
                    animProgress < 0.32f -> 1 // 2. Выбор карточки OCR
                    animProgress < 0.48f -> 2 // 3. Настройка ROI зоны
                    animProgress < 0.64f -> 3 // 4. Нажатие на кнопку [ПОИСК ⚡]
                    animProgress < 0.82f -> 4 // 5. Сканирование и клик по стрелке на экране
                    else -> 5                 // 6. Выполнение цепочки графа
                }

                val targetX: Float
                val targetY: Float
                val isScanning: Boolean
                val isTapping: Boolean
                val statusText: String
                val matchScorePct: Int

                when (currentStage) {
                    0 -> {
                        val progressInStage = if (selectedManualStage != null) (now % 2500) / 2500f else (animProgress / 0.16f)
                        targetX = c1.centerX()
                        targetY = c1.centerY()
                        isScanning = progressInStage in 0.2f..0.65f
                        isTapping = progressInStage > 0.75f
                        matchScorePct = 96
                        statusText = "1. ШАБЛОН: Создан шаблон [▶ Стрелка], выделена форма без шума"
                    }
                    1 -> {
                        val progressInStage = if (selectedManualStage != null) (now % 2500) / 2500f else ((animProgress - 0.16f) / 0.16f)
                        targetX = c2.centerX()
                        targetY = c2.centerY()
                        isScanning = progressInStage in 0.2f..0.65f
                        isTapping = progressInStage > 0.75f
                        matchScorePct = 100
                        statusText = "2. OCR: Распознавание текста 'Награда x100' по нейросетевым боксам"
                    }
                    2 -> {
                        val progressInStage = if (selectedManualStage != null) (now % 2500) / 2500f else ((animProgress - 0.32f) / 0.16f)
                        targetX = c3.centerX()
                        targetY = c3.centerY()
                        isScanning = progressInStage in 0.2f..0.65f
                        isTapping = progressInStage > 0.75f
                        matchScorePct = 99
                        statusText = "3. ROI ЗОНА: Ограничение области поиска для мгновенного отклика (0мс)"
                    }
                    3 -> {
                        val progressInStage = if (selectedManualStage != null) (now % 2500) / 2500f else ((animProgress - 0.48f) / 0.16f)
                        targetX = c4.centerX()
                        targetY = c4.centerY()
                        isScanning = false
                        isTapping = progressInStage > 0.65f
                        matchScorePct = 100
                        statusText = "4. ЗАПУСК ПОИСКА: Рука нажимает [ПОИСК ⚡] для старта распознавания"
                    }
                    4 -> {
                        val progressInStage = if (selectedManualStage != null) (now % 2800) / 2800f else ((animProgress - 0.64f) / 0.18f)
                        targetX = simArrowTarget.centerX()
                        targetY = simArrowTarget.centerY()
                        isScanning = progressInStage < 0.50f
                        isTapping = progressInStage >= 0.65f
                        matchScorePct = 98
                        statusText = "5. ДЕТЕКЦИЯ И КЛИК: Полупрозрачная стрелка найдена по форме (98%)!"
                    }
                    else -> {
                        val progressInStage = if (selectedManualStage != null) (now % 3000) / 3000f else ((animProgress - 0.82f) / 0.18f)
                        if (progressInStage < 0.33f) {
                            targetX = s1.centerX()
                            targetY = s1.centerY()
                            isTapping = (progressInStage / 0.33f) > 0.6f
                        } else if (progressInStage < 0.66f) {
                            targetX = s2.centerX()
                            targetY = s2.centerY()
                            isTapping = ((progressInStage - 0.33f) / 0.33f) > 0.6f
                        } else {
                            targetX = s3.centerX()
                            targetY = s3.centerY()
                            isTapping = ((progressInStage - 0.66f) / 0.34f) > 0.6f
                        }
                        isScanning = false
                        matchScorePct = 100
                        statusText = "6. ГРАФ СЦЕНАРИЯ: Автоматическое выполнение цепочки шагов #1 ➔ #2 ➔ #3"
                    }
                }

                // 5. Подсветка активной цели и градиентный бейдж уверенности
                val activeTargetRect = when (currentStage) {
                    0 -> c1
                    1 -> c2
                    2 -> c3
                    3 -> c4
                    4 -> simArrowTarget
                    else -> if (targetX < s2.left) s1 else if (targetX < s3.left) s2 else s3
                }

                if (isScanning || isTapping) {
                    highlightFillPaint.shader = LinearGradient(
                        activeTargetRect.left, activeTargetRect.top, activeTargetRect.right, activeTargetRect.bottom,
                        intArrayOf("#3010B981".toColorInt(), "#1000F0FF".toColorInt()), null, Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(activeTargetRect, dpF(8f), dpF(8f), highlightFillPaint)
                    canvas.drawRoundRect(activeTargetRect, dpF(8f), dpF(8f), highlightBoxPaint)

                    // Score Pill Badge
                    val badgeW = dpF(38f)
                    val badgeH = dpF(17f)
                    val badgeX = activeTargetRect.left
                    val badgeY = (activeTargetRect.top - badgeH - dpF(3f)).coerceAtLeast(dpF(4f))
                    reusableRectF.set(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH)
                    canvas.drawRoundRect(reusableRectF, dpF(8f), dpF(8f), scoreBadgePaint)
                    canvas.drawRoundRect(reusableRectF, dpF(8f), dpF(8f), scoreBadgeBorder)
                    canvas.drawText("$matchScorePct%", reusableRectF.centerX(), reusableRectF.centerY() + dpF(3.5f), scoreTextPaint)
                }

                // 6. Отрисовка высокоточного кибернетического манипулятора (РОБОРУКА КАК В ИКОНКЕ)
                // Расчет 2-звенной инверсной кинематики
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

                // 6.1 Гидравлический поршень между базой и плечом
                val pMidX = (armBaseX + elbowX) / 2f - dpF(10f)
                val pMidY = (armBaseY + elbowY) / 2f - dpF(10f)
                canvas.drawLine(armBaseX, armBaseY, pMidX, pMidY, pistonCylinderPaint)
                canvas.drawLine(pMidX, pMidY, elbowX, elbowY, pistonRodPaint)

                // 6.2 Плечевой сегмент (Arm Link 1) с металлическим градиентом
                armChassisPaint.shader = LinearGradient(
                    armBaseX, armBaseY, elbowX, elbowY,
                    intArrayOf("#475569".toColorInt(), "#0F172A".toColorInt(), "#1E293B".toColorInt()),
                    null, Shader.TileMode.CLAMP
                )
                drawArmSegment(canvas, armBaseX, armBaseY, elbowX, elbowY, dpF(20f), dpF(15f))

                // Неоновый световод питания вдоль плеча
                neonConduitPaint.color = if (isScanning) "#EC4899".toColorInt() else "#00F0FF".toColorInt()
                canvas.drawLine(armBaseX, armBaseY, elbowX, elbowY, neonGlowPaint)
                canvas.drawLine(armBaseX, armBaseY, elbowX, elbowY, neonConduitPaint)

                // 6.3 Предплечье (Arm Link 2)
                armChassisPaint.shader = LinearGradient(
                    elbowX, elbowY, wristX, wristY,
                    intArrayOf("#1E293B".toColorInt(), "#0B132B".toColorInt(), "#334155".toColorInt()),
                    null, Shader.TileMode.CLAMP
                )
                drawArmSegment(canvas, elbowX, elbowY, wristX, wristY, dpF(15f), dpF(11f))
                canvas.drawLine(elbowX, elbowY, wristX, wristY, neonGlowPaint)
                canvas.drawLine(elbowX, elbowY, wristX, wristY, neonConduitPaint)

                // 6.4 Поворотные серво-шарниры (База, Локоть, Запястье)
                // Базовая турель
                canvas.drawCircle(armBaseX, armBaseY, dpF(20f), jointBasePaint)
                canvas.drawCircle(armBaseX, armBaseY, dpF(20f), jointBevelPaint)
                canvas.drawCircle(armBaseX, armBaseY, dpF(8f), jointCorePaint)

                // Локтевой сервопривод
                canvas.drawCircle(elbowX, elbowY, dpF(15f), jointBasePaint)
                canvas.drawCircle(elbowX, elbowY, dpF(15f), jointBevelPaint)
                canvas.drawCircle(elbowX, elbowY, dpF(6f), jointCorePaint)

                // Запястный шарнир
                canvas.drawCircle(wristX, wristY, dpF(11f), jointBasePaint)
                canvas.drawCircle(wristX, wristY, dpF(11f), jointBevelPaint)

                // 6.5 Двупалый кибернетический захват и стилус (В точности как на иконке)
                val fingerAngle = atan2((targetY - wristY).toDouble(), (targetX - wristX).toDouble()).toFloat()
                val fPerp = fingerAngle + (PI / 2f).toFloat()

                // Левый палец захвата
                val j1BaseX = wristX + cos(fPerp.toDouble()).toFloat() * dpF(7f)
                val j1BaseY = wristY + sin(fPerp.toDouble()).toFloat() * dpF(7f)
                reusablePath.rewind()
                reusablePath.moveTo(j1BaseX, j1BaseY)
                reusablePath.lineTo(targetX + cos(fPerp.toDouble()).toFloat() * dpF(3f), targetY + sin(fPerp.toDouble()).toFloat() * dpF(3f))
                reusablePath.lineTo(targetX, targetY)
                reusablePath.close()
                canvas.drawPath(reusablePath, gripperJawPaint)
                canvas.drawPath(reusablePath, armBorderPaint)

                // Правый палец захвата
                val j2BaseX = wristX - cos(fPerp.toDouble()).toFloat() * dpF(7f)
                val j2BaseY = wristY - sin(fPerp.toDouble()).toFloat() * dpF(7f)
                reusablePath.rewind()
                reusablePath.moveTo(j2BaseX, j2BaseY)
                reusablePath.lineTo(targetX - cos(fPerp.toDouble()).toFloat() * dpF(3f), targetY - sin(fPerp.toDouble()).toFloat() * dpF(3f))
                reusablePath.lineTo(targetX, targetY)
                reusablePath.close()
                canvas.drawPath(reusablePath, gripperJawPaint)
                canvas.drawPath(reusablePath, armBorderPaint)

                // Светящийся наконечник стилуса / лазерный излучатель
                val tipColor = if (isTapping) "#00F0FF".toColorInt() else if (isScanning) "#EC4899".toColorInt() else "#10B981".toColorInt()
                jointCorePaint.color = tipColor
                canvas.drawCircle(targetX, targetY, dpF(6f), jointCorePaint)

                // 7. Лазерный радар сканирования
                if (isScanning) {
                    laserScanPaint.shader = RadialGradient(
                        targetX, targetY, dpF(85f),
                        intArrayOf("#5000F0FF".toColorInt(), "#1000F0FF".toColorInt(), Color.TRANSPARENT),
                        floatArrayOf(0f, 0.6f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(targetX, targetY, dpF(80f), laserScanPaint)

                    val scanAngle = (now % 1000) / 1000f * (2 * PI.toFloat())
                    val scanLineX = targetX + cos(scanAngle.toDouble()).toFloat() * dpF(70f)
                    val scanLineY = targetY + sin(scanAngle.toDouble()).toFloat() * dpF(70f)
                    canvas.drawLine(targetX, targetY, scanLineX, scanLineY, laserLinePaint)
                }

                // 8. Импульсные волны и клик-анимация
                if (isTapping) {
                    val pulsePhase = (now % 500) / 500f
                    val r1 = pulsePhase * dpF(42f)
                    val alpha1 = ((1f - pulsePhase) * 255).toInt().coerceIn(0, 255)
                    tapPulsePaint.alpha = alpha1
                    canvas.drawCircle(targetX, targetY, r1, tapPulsePaint)

                    val r2 = (pulsePhase * 0.6f) * dpF(42f)
                    val alpha2 = ((1f - pulsePhase * 0.6f) * 255).toInt().coerceIn(0, 255)
                    tapPulsePaint.alpha = alpha2
                    canvas.drawCircle(targetX, targetY, r2, tapPulsePaint)

                    canvas.drawCircle(targetX, targetY, dpF(4f), tapCorePaint)
                }

                // 9. Живая информационная плашка статуса (Внизу экрана)
                if (!isCleanRecordingMode) {
                    val bannerH = dpF(34f)
                    val bannerY = h - bannerH - dpF(12f)
                    reusableRectF.set(dpF(12f), bannerY, w - dpF(12f), bannerY + bannerH)
                    canvas.drawRoundRect(reusableRectF, dpF(8f), dpF(8f), cardBgPaint)
                    cardBorderPaint.color = "#38BDF8".toColorInt()
                    canvas.drawRoundRect(reusableRectF, dpF(8f), dpF(8f), cardBorderPaint)
                    canvas.drawText(statusText, dpF(24f), bannerY + dpF(21f), textTitlePaint)
                }
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

        // --- Верхняя панель управления ---
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }

        val tvTitleHeader = TextView(context).apply {
            text = "🤖 ДЕМО РОБОРУКИ AUTOTAP"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#38BDF8".toColorInt())
        }
        headerRow.addView(tvTitleHeader, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnSpeed = Button(context).apply {
            text = "СКОРОСТЬ 1x"
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
                playbackSpeed = if (playbackSpeed == 1.0f) 2.0f else if (playbackSpeed == 2.0f) 0.5f else 1.0f
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

        val btnRecMode = Button(context).apply {
            text = "ЗАПИСЬ (15с)"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            minHeight = 0; minimumHeight = 0
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#8B5CF6".toColorInt())
                cornerRadius = dpF(4f)
            }
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener {
                isCleanRecordingMode = true
                topControlsBar.visibility = View.GONE
                handler.postDelayed({
                    isCleanRecordingMode = false
                    topControlsBar.visibility = View.VISIBLE
                }, 15000L)
            }
        }
        headerRow.addView(btnRecMode, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(24)).apply { marginEnd = dp(4) })

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

        // Чипы быстрого выбора шагов демонстрации
        val chipScroll = HorizontalScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
        val chipRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val chipTitles = listOf("⚡ ВСЕ", "1. ШАБЛОН", "2. OCR", "3. ROI", "4. ПОИСК", "5. ДЕТЕКЦИЯ", "6. ГРАФ")
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

        // Анимационный цикл
        animRunnable = object : Runnable {
            override fun run() {
                if (isPlaying) {
                    animProgress = (animProgress + 0.004f * playbackSpeed) % 1.0f
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
