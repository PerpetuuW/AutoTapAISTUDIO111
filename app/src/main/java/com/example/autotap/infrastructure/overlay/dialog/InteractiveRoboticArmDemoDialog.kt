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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Интерактивная демонстрация работы AutoTap (Zero-Friction & Cyber-Minimalism Showcase).
 * 4 ключевые стадии:
 * 1. ИЗОБРАЖЕНИЕ — ИИ-поиск объектов, кнопок и иконок.
 * 2. ТЕКСТ (OCR) — Распознавание надписей на экране.
 * 3. ЗАПИСЬ ДЕЙСТВИЙ — Живые жесты, свайпы и траектории.
 * 4. ОБЫЧНЫЕ КЛИКИ — Быстрая расстановка шагов и запуск сценария.
 */
@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class InteractiveRoboticArmDemoDialog(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager
) {

    private var rootFrameLayout: View? = null
    private var appDialog: android.app.Dialog? = null
    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    private var isPlaying = true
    private var playbackSpeed = 0.5f // Комфортная скорость демонстрации
    private var animProgress = 0f
    private var selectedManualStage: Int? = null

    private val handler = Handler(Looper.getMainLooper())
    private var animRunnable: Runnable? = null

    fun show() {
        if (rootFrameLayout != null) return

        val root = FrameLayout(context).apply {
            setBackgroundColor("#F80B0813".toColorInt())
        }
        rootFrameLayout = root

        val topControlsBar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#FA140F24".toColorInt(), "#FA0B0813".toColorInt())
            ).apply {
                cornerRadius = dpF(16f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            val p = dp(10)
            setPadding(p, p, p, p)
            elevation = dpF(18f)
        }

        val demoCanvasView = object : View(context) {
            private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#161324".toColorInt()
                strokeWidth = dpF(1f)
            }
            private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#F80F1218".toColorInt()
            }
            private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.5f)
                color = "#38BDF8".toColorInt()
            }
            private val gameScreenBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.5f)
                color = "#2E2250".toColorInt()
            }
            private val textTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dpF(11.5f)
                typeface = Typeface.DEFAULT_BOLD
            }
            private val textSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#94A3B8".toColorInt()
                textSize = dpF(9f)
                typeface = Typeface.MONOSPACE
            }
            private val bannerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#38BDF8".toColorInt()
                textSize = dpF(12f)
                typeface = Typeface.DEFAULT_BOLD
            }
            private val bannerPhasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#FCD34D".toColorInt()
                textSize = dpF(10f)
                typeface = Typeface.DEFAULT_BOLD
            }
            private val bannerBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dpF(10f)
                typeface = Typeface.DEFAULT
            }
            private val hintTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#A78BFA".toColorInt()
                textSize = dpF(10.5f)
                typeface = Typeface.DEFAULT_BOLD
            }
            private val hintBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#E2E8F0".toColorInt()
                textSize = dpF(9.5f)
                typeface = Typeface.DEFAULT
            }
            private val badgeCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dpF(9.5f)
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            private val laserLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                color = "#38BDF8".toColorInt()
            }
            private val swipeTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(4f)
                strokeCap = Paint.Cap.ROUND
                color = "#A78BFA".toColorInt()
            }
            private val pointerCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            private val pointerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            private val generalItemBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            private val reusablePath = Path()
            private val iconBounds = RectF()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val now = System.currentTimeMillis()

                // 1. Сетка фонового пространства
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

                // 2. Определение текущей стадии (0: Картинка, 1: OCR, 2: Запись, 3: Клики)
                val currentStage = selectedManualStage ?: when {
                    animProgress < 0.25f -> 0 // 1. ИЗОБРАЖЕНИЕ
                    animProgress < 0.50f -> 1 // 2. ТЕКСТ (OCR)
                    animProgress < 0.75f -> 2 // 3. ЗАПИСЬ ДЕЙСТВИЙ
                    else -> 3                 // 4. ОБЫЧНЫЕ КЛИКИ И ПУСК
                }

                // Координаты рабочих зон
                val bannerRect = RectF(w * 0.04f, dpF(82f), w * 0.96f, dpF(160f))
                val gameRect = RectF(w * 0.04f, bannerRect.bottom + dpF(8f), w * 0.96f, h * 0.73f)
                val panelRect = RectF(w * 0.04f, h * 0.745f, w * 0.96f, h * 0.835f)
                val hintRect = RectF(w * 0.04f, panelRect.bottom + dpF(8f), w * 0.96f, h - dpF(12f))

                // 3. Симулируемый экран приложения / игры
                generalItemBg.shader = LinearGradient(
                    gameRect.left, gameRect.top, gameRect.right, gameRect.bottom,
                    intArrayOf("#140F24".toColorInt(), "#0B0813".toColorInt()), null, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(gameRect, dpF(14f), dpF(14f), generalItemBg)
                canvas.drawRoundRect(gameRect, dpF(14f), dpF(14f), gameScreenBorderPaint)

                // Заголовок сцены игры
                val gameHeader = RectF(gameRect.left, gameRect.top, gameRect.right, gameRect.top + dpF(30f))
                generalItemBg.shader = null
                generalItemBg.color = "#201738".toColorInt()
                canvas.drawRoundRect(gameHeader, dpF(14f), dpF(14f), generalItemBg)
                canvas.drawText("СИМУЛЯЦИЯ ИНТЕРФЕЙСА · СЦЕНАРИЙ AUTOTAP", gameHeader.left + dpF(10f), gameHeader.centerY() + dpF(4f), textTitlePaint)

                // Карточка 1: Графическая цель (Сундук сокровищ / Кнопка с иконкой)
                val chestArea = RectF(gameRect.left + dpF(10f), gameHeader.bottom + dpF(10f), gameRect.left + dpF(130f), gameHeader.bottom + dpF(88f))
                generalItemBg.color = "#1F1735".toColorInt()
                canvas.drawRoundRect(chestArea, dpF(10f), dpF(10f), generalItemBg)
                canvas.drawText("ЦЕЛЬ: КАРТИНКА", chestArea.left + dpF(8f), chestArea.top + dpF(16f), textSubPaint)

                val arrowTarget = RectF(chestArea.centerX() - dpF(18f), chestArea.centerY() - dpF(4f), chestArea.centerX() + dpF(18f), chestArea.centerY() + dpF(22f))
                generalItemBg.color = "#3538BDF8".toColorInt()
                canvas.drawRoundRect(arrowTarget, dpF(6f), dpF(6f), generalItemBg)
                iconBounds.set(arrowTarget.left + dpF(5f), arrowTarget.top + dpF(4f), arrowTarget.right - dpF(5f), arrowTarget.bottom - dpF(4f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.IMAGE, iconBounds, "#38BDF8".toColorInt(), dpF(1.5f))

                // Карточка 2: Текстовая цель (OCR Кнопка "ЗАБРАТЬ +500")
                val ocrClaimBtn = RectF(gameRect.right - dpF(135f), gameHeader.bottom + dpF(14f), gameRect.right - dpF(10f), gameHeader.bottom + dpF(60f))
                generalItemBg.color = "#0D2E1E".toColorInt()
                canvas.drawRoundRect(ocrClaimBtn, dpF(8f), dpF(8f), generalItemBg)
                gameScreenBorderPaint.color = "#34D399".toColorInt()
                canvas.drawRoundRect(ocrClaimBtn, dpF(8f), dpF(8f), gameScreenBorderPaint)
                canvas.drawText("ЗАБРАТЬ +500", ocrClaimBtn.left + dpF(12f), ocrClaimBtn.centerY() + dpF(4f), textTitlePaint)

                // Карточка 3: Список элементов для кликов и свайпа
                val listArea = RectF(gameRect.left + dpF(10f), chestArea.bottom + dpF(10f), gameRect.right - dpF(10f), gameRect.bottom - dpF(10f))
                generalItemBg.color = "#161127".toColorInt()
                canvas.drawRoundRect(listArea, dpF(10f), dpF(10f), generalItemBg)
                canvas.drawText("СПИСОК ЭЛЕМЕНТОВ (ТАПЫ И СВАЙПЫ)", listArea.left + dpF(10f), listArea.top + dpF(15f), textSubPaint)

                val itemBtn1 = RectF(listArea.left + dpF(8f), listArea.top + dpF(22f), listArea.right - dpF(8f), listArea.top + dpF(44f))
                val itemBtn2 = RectF(listArea.left + dpF(8f), listArea.top + dpF(48f), listArea.right - dpF(8f), listArea.top + dpF(70f))
                val itemBtn3 = RectF(listArea.left + dpF(8f), listArea.top + dpF(74f), listArea.right - dpF(8f), listArea.top + dpF(96f))

                generalItemBg.color = "#231B3D".toColorInt()
                canvas.drawRoundRect(itemBtn1, dpF(6f), dpF(6f), generalItemBg)
                canvas.drawText("Пункт #1 — Быстрый клик", itemBtn1.left + dpF(10f), itemBtn1.centerY() + dpF(4f), textSubPaint)

                canvas.drawRoundRect(itemBtn2, dpF(6f), dpF(6f), generalItemBg)
                canvas.drawText("Пункт #2 — Быстрый клик", itemBtn2.left + dpF(10f), itemBtn2.centerY() + dpF(4f), textSubPaint)

                canvas.drawRoundRect(itemBtn3, dpF(6f), dpF(6f), generalItemBg)
                canvas.drawText("Пункт #3 — Прокрутка вверх", itemBtn3.left + dpF(10f), itemBtn3.centerY() + dpF(4f), textSubPaint)

                // 4. Плавающий пульт управления AutoTap
                canvas.drawRoundRect(panelRect, dpF(12f), dpF(12f), panelBgPaint)
                canvas.drawRoundRect(panelRect, dpF(12f), dpF(12f), panelBorderPaint)

                val btnW = (panelRect.width() - dpF(28f)) / 5f
                val btnH = panelRect.height() - dpF(10f)
                val btnY = panelRect.top + dpF(5f)

                // 4.1 Кнопка ПУСК
                val pBtnPlay = RectF(panelRect.left + dpF(5f), btnY, panelRect.left + dpF(5f) + btnW, btnY + btnH)
                generalItemBg.color = if (animProgress >= 0.75f) "#064E3B".toColorInt() else "#0D2E1E".toColorInt()
                canvas.drawRoundRect(pBtnPlay, dpF(8f), dpF(8f), generalItemBg)
                gameScreenBorderPaint.color = "#34D399".toColorInt()
                canvas.drawRoundRect(pBtnPlay, dpF(8f), dpF(8f), gameScreenBorderPaint)
                iconBounds.set(pBtnPlay.centerX() - dpF(8f), pBtnPlay.centerY() - dpF(8f), pBtnPlay.centerX() + dpF(8f), pBtnPlay.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, if (animProgress >= 0.75f) VectorIconDrawer.IconType.PAUSE else VectorIconDrawer.IconType.PLAY, iconBounds, "#34D399".toColorInt(), dpF(2f))

                // 4.2 Кнопка Снимок ИИ
                val pBtnCapture = RectF(pBtnPlay.right + dpF(4f), btnY, pBtnPlay.right + dpF(4f) + btnW, btnY + btnH)
                generalItemBg.color = "#162032".toColorInt()
                canvas.drawRoundRect(pBtnCapture, dpF(8f), dpF(8f), generalItemBg)
                gameScreenBorderPaint.color = "#38BDF8".toColorInt()
                canvas.drawRoundRect(pBtnCapture, dpF(8f), dpF(8f), gameScreenBorderPaint)
                iconBounds.set(pBtnCapture.centerX() - dpF(8f), pBtnCapture.centerY() - dpF(8f), pBtnCapture.centerX() + dpF(8f), pBtnCapture.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.CAPTURE, iconBounds, "#38BDF8".toColorInt(), dpF(1.8f))

                // 4.3 Кнопка Запись
                val pBtnRecord = RectF(pBtnCapture.right + dpF(4f), btnY, pBtnCapture.right + dpF(4f) + btnW, btnY + btnH)
                generalItemBg.color = "#2E121B".toColorInt()
                canvas.drawRoundRect(pBtnRecord, dpF(8f), dpF(8f), generalItemBg)
                gameScreenBorderPaint.color = "#F43F5E".toColorInt()
                canvas.drawRoundRect(pBtnRecord, dpF(8f), dpF(8f), gameScreenBorderPaint)
                iconBounds.set(pBtnRecord.centerX() - dpF(8f), pBtnRecord.centerY() - dpF(8f), pBtnRecord.centerX() + dpF(8f), pBtnRecord.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.RECORD, iconBounds, "#F43F5E".toColorInt(), dpF(1.8f))

                // 4.4 Кнопка Добавить шаг [+]
                val pBtnAdd = RectF(pBtnRecord.right + dpF(4f), btnY, pBtnRecord.right + dpF(4f) + btnW, btnY + btnH)
                generalItemBg.color = "#162032".toColorInt()
                canvas.drawRoundRect(pBtnAdd, dpF(8f), dpF(8f), generalItemBg)
                gameScreenBorderPaint.color = "#38BDF8".toColorInt()
                canvas.drawRoundRect(pBtnAdd, dpF(8f), dpF(8f), gameScreenBorderPaint)
                iconBounds.set(pBtnAdd.centerX() - dpF(8f), pBtnAdd.centerY() - dpF(8f), pBtnAdd.centerX() + dpF(8f), pBtnAdd.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.PLUS, iconBounds, "#38BDF8".toColorInt(), dpF(1.8f))

                // 4.5 Кнопка Глаз (3 режима)
                val pBtnEye = RectF(pBtnAdd.right + dpF(4f), btnY, pBtnAdd.right + dpF(4f) + btnW, btnY + btnH)
                generalItemBg.color = "#162032".toColorInt()
                canvas.drawRoundRect(pBtnEye, dpF(8f), dpF(8f), generalItemBg)
                gameScreenBorderPaint.color = "#38BDF8".toColorInt()
                canvas.drawRoundRect(pBtnEye, dpF(8f), dpF(8f), gameScreenBorderPaint)
                iconBounds.set(pBtnEye.centerX() - dpF(8f), pBtnEye.centerY() - dpF(8f), pBtnEye.centerX() + dpF(8f), pBtnEye.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.VISIBILITY, iconBounds, "#38BDF8".toColorInt(), dpF(1.8f))

                // 5. Логика и координаты указателя для 4 стадий
                var targetX = 0f
                var targetY = 0f
                var isScanning = false
                var isTapping = false
                var isSwiping = false
                var stageTitle = ""
                var currentActionDesc = ""
                var practicalHint = ""

                when (currentStage) {
                    0 -> {
                        stageTitle = "ЭТАП 1/4: ИЗОБРАЖЕНИЕ (ИИ-ПОИСК)"
                        val p = if (selectedManualStage != null) (now % 4500) / 4500f else (animProgress / 0.25f)
                        if (p < 0.35f) {
                            targetX = pBtnCapture.centerX()
                            targetY = pBtnCapture.centerY()
                            isTapping = p in 0.18f..0.32f
                            currentActionDesc = "ДЕЙСТВИЕ: Нажатие кнопки захвата рамки [КАМЕРА] на пульте AutoTap."
                        } else if (p < 0.70f) {
                            targetX = arrowTarget.centerX()
                            targetY = arrowTarget.centerY()
                            isScanning = true
                            currentActionDesc = "ДЕЙСТВИЕ: Сканирование экрана лазером и ИИ-поиск шаблона сундука."
                        } else {
                            targetX = arrowTarget.centerX()
                            targetY = arrowTarget.centerY()
                            isTapping = true
                            currentActionDesc = "ДЕЙСТВИЕ: Объект обнаружен со 100% совпадением! Автоклик по цели."
                        }
                        practicalHint = "КАК ПРИМЕНЯТЬ: Нажмите иконку камеры на пульте -> выделите объект рамкой -> сохраните цель."
                    }
                    1 -> {
                        stageTitle = "ЭТАП 2/4: ТЕКСТ (OCR РАСПОЗНАВАНИЕ)"
                        val p = if (selectedManualStage != null) (now % 4500) / 4500f else ((animProgress - 0.25f) / 0.25f)
                        if (p < 0.35f) {
                            targetX = pBtnAdd.centerX()
                            targetY = pBtnAdd.centerY()
                            isTapping = p in 0.18f..0.32f
                            currentActionDesc = "ДЕЙСТВИЕ: Добавление шага OCR через кнопку [+] на пульте управления."
                        } else if (p < 0.70f) {
                            targetX = ocrClaimBtn.centerX()
                            targetY = ocrClaimBtn.centerY()
                            isScanning = true
                            currentActionDesc = "ДЕЙСТВИЕ: Оптическое распознавание фраз 'ЗАБРАТЬ +500' на Русском / English."
                        } else {
                            targetX = ocrClaimBtn.centerX()
                            targetY = ocrClaimBtn.centerY()
                            isTapping = true
                            currentActionDesc = "ДЕЙСТВИЕ: Текст найден! Мгновенное нажатие по центру кнопки с наградой."
                        }
                        practicalHint = "КАК ПРИМЕНЯТЬ: Добавьте шаг -> выберите тип [OCR ТЕКСТ] -> укажите фразу для клика."
                    }
                    2 -> {
                        stageTitle = "ЭТАП 3/4: ЖИВАЯ ЗАПИСЬ ЖЕСТОВ И СВАЙПОВ"
                        val p = if (selectedManualStage != null) (now % 4500) / 4500f else ((animProgress - 0.50f) / 0.25f)
                        if (p < 0.30f) {
                            targetX = pBtnRecord.centerX()
                            targetY = pBtnRecord.centerY()
                            isTapping = p in 0.15f..0.28f
                            currentActionDesc = "ДЕЙСТВИЕ: Включение записи жестов красной кнопкой [КРУГ] на пульте."
                        } else {
                            val swipeP = ((p - 0.30f) / 0.70f).coerceIn(0f, 1f)
                            targetX = listArea.centerX()
                            targetY = (listArea.bottom - dpF(18f)) - swipeP * dpF(58f)
                            isSwiping = true
                            currentActionDesc = "ДЕЙСТВИЕ: Проведение пальцем снизу вверх по списку для прокрутки экрана."
                        }
                        practicalHint = "КАК ПРИМЕНЯТЬ: Нажмите [ЗАПИСЬ] -> выполните любые жесты на экране -> нажмите [СТОП]."
                    }
                    else -> {
                        stageTitle = "ЭТАП 4/4: РАССТАНОВКА КЛИКОВ И АВТОЗАПУСК"
                        val p = if (selectedManualStage != null) (now % 4500) / 4500f else ((animProgress - 0.75f) / 0.25f)
                        if (p < 0.35f) {
                            targetX = itemBtn1.centerX()
                            targetY = itemBtn1.centerY()
                            isTapping = p in 0.20f..0.30f
                            currentActionDesc = "ДЕЙСТВИЕ: Клик по первому пункту списка (Шаг #1 макроса)."
                        } else if (p < 0.70f) {
                            targetX = itemBtn2.centerX()
                            targetY = itemBtn2.centerY()
                            isTapping = p in 0.52f..0.62f
                            currentActionDesc = "ДЕЙСТВИЕ: Клик по второму пункту списка (Шаг #2 макроса)."
                        } else {
                            targetX = pBtnPlay.centerX()
                            targetY = pBtnPlay.centerY()
                            isTapping = p > 0.82f
                            currentActionDesc = "ДЕЙСТВИЕ: Нажатие зеленой кнопки [ПУСК] — запуск непрерывного макроса."
                        }
                        practicalHint = "КАК ПРИМЕНЯТЬ: Расставьте точки кликов по экрану и нажмите зеленую кнопку [ПУСК]."
                    }
                }

                // 6. Отрисовка верхнего информационного баннера с описанием текущего действия
                generalItemBg.shader = LinearGradient(
                    bannerRect.left, bannerRect.top, bannerRect.right, bannerRect.bottom,
                    intArrayOf("#25140F24".toColorInt(), "#FA140F24".toColorInt()), null, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(bannerRect, dpF(12f), dpF(12f), generalItemBg)
                gameScreenBorderPaint.color = "#8B5CF6".toColorInt()
                canvas.drawRoundRect(bannerRect, dpF(12f), dpF(12f), gameScreenBorderPaint)

                // Текст баннера
                canvas.drawText(stageTitle, bannerRect.left + dpF(12f), bannerRect.top + dpF(20f), bannerTitlePaint)
                canvas.drawText(currentActionDesc, bannerRect.left + dpF(12f), bannerRect.top + dpF(40f), bannerPhasePaint)
                canvas.drawText("Переключайте стадии чипами вверху или измените скорость воспроизведения.", bannerRect.left + dpF(12f), bannerRect.top + dpF(58f), bannerBodyPaint)

                // 7. Отрисовка нижнего руководства к действию (Actionable Guide Card)
                generalItemBg.shader = null
                generalItemBg.color = "#1A152E".toColorInt()
                canvas.drawRoundRect(hintRect, dpF(10f), dpF(10f), generalItemBg)
                gameScreenBorderPaint.color = "#38BDF8".toColorInt()
                canvas.drawRoundRect(hintRect, dpF(10f), dpF(10f), gameScreenBorderPaint)

                canvas.drawText("ИНСТРУКЦИЯ К ДЕЙСТВИЮ:", hintRect.left + dpF(10f), hintRect.top + dpF(18f), hintTitlePaint)
                canvas.drawText(practicalHint, hintRect.left + dpF(10f), hintRect.top + dpF(34f), hintBodyPaint)

                // 8. Отрисовка сканирования лазером при поиске
                if (isScanning) {
                    val scanBox = if (currentStage == 0) arrowTarget else ocrClaimBtn
                    val scanP = (now % 1000) / 1000f
                    val scanLineY = scanBox.top + scanP * scanBox.height()
                    canvas.drawLine(scanBox.left - dpF(4f), scanLineY, scanBox.right + dpF(4f), scanLineY, laserLinePaint)
                }

                // 9. Отрисовка неонового шлейфа при свайпе
                if (isSwiping) {
                    val startY = listArea.bottom - dpF(18f)
                    reusablePath.rewind()
                    reusablePath.moveTo(listArea.centerX(), startY)
                    reusablePath.lineTo(targetX, targetY)
                    canvas.drawPath(reusablePath, swipeTrailPaint)
                }

                // 10. Отрисовка жетонов шагов
                val badgeX1 = arrowTarget.right + dpF(6f)
                val badgeY1 = arrowTarget.centerY()
                badgeCirclePaint.color = "#38BDF8".toColorInt()
                canvas.drawCircle(badgeX1, badgeY1, dpF(9f), badgeCirclePaint)
                canvas.drawText("1", badgeX1, badgeY1 + dpF(3.5f), badgeTextPaint)

                val badgeX2 = ocrClaimBtn.left - dpF(12f)
                val badgeY2 = ocrClaimBtn.centerY()
                badgeCirclePaint.color = "#34D399".toColorInt()
                canvas.drawCircle(badgeX2, badgeY2, dpF(9f), badgeCirclePaint)
                canvas.drawText("2", badgeX2, badgeY2 + dpF(3.5f), badgeTextPaint)

                // 11. Отрисовка плавного неонового указателя тача (указательная рука)
                if (targetX > 0f && targetY > 0f) {
                    drawClassicIndexPointerGlove(canvas, targetX, targetY, isTapping)
                }
            }

            private fun drawClassicIndexPointerGlove(canvas: Canvas, tx: Float, ty: Float, isTapping: Boolean) {
                val glowColor = if (isTapping) "#8038BDF8".toColorInt() else "#408B5CF6".toColorInt()
                val coreColor = if (isTapping) "#38BDF8".toColorInt() else Color.WHITE
                pointerGlowPaint.color = glowColor
                pointerCorePaint.color = coreColor

                val radius = if (isTapping) dpF(16f) else dpF(11f)
                canvas.drawCircle(tx, ty, radius, pointerGlowPaint)
                canvas.drawCircle(tx, ty, dpF(6f), pointerCorePaint)
            }
        }
        root.addView(demoCanvasView, FrameLayout.LayoutParams(-1, -1))

        // --- Верхняя компактная панель чипов и управления ---
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }

        val tvTitleHeader = TextView(context).apply {
            text = "ДЕМО AUTOTAP"
            textSize = 10.5f
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
                setColor("#1F1735".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener {
                playbackSpeed = if (playbackSpeed == 0.5f) 1.0f else if (playbackSpeed == 1.0f) 0.25f else 0.5f
                text = "СКОРОСТЬ ${playbackSpeed}x"
            }
        }
        headerRow.addView(btnSpeed, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(26)).apply { marginEnd = dp(6) })

        val btnPlayPause = Button(context).apply {
            text = "ПАУЗА"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            minHeight = 0; minimumHeight = 0
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#8B5CF6".toColorInt())
                cornerRadius = dpF(6f)
            }
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener {
                isPlaying = !isPlaying
                text = if (isPlaying) "ПАУЗА" else "ПУСК"
            }
        }
        headerRow.addView(btnPlayPause, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(26)).apply { marginEnd = dp(6) })

        val btnClose = Button(context).apply {
            text = "X"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setTextColor("#94A3B8".toColorInt())
            background = null
            setOnClickListener { dismiss() }
        }
        headerRow.addView(btnClose, LinearLayout.LayoutParams(dp(26), dp(26)))
        topControlsBar.addView(headerRow)

        // Чипы 4 стадий
        val chipScroll = HorizontalScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
        val chipRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val chipTitles = listOf("ПОЛНЫЙ ЦИКЛ", "1. ИЗОБРАЖЕНИЕ", "2. ТЕКСТ (OCR)", "3. ЗАПИСЬ ДЕЙСТВИЙ", "4. ОБЫЧНЫЕ КЛИКИ")
        val chipButtons = mutableListOf<Button>()

        chipTitles.forEachIndexed { idx, title ->
            val stageTarget = if (idx == 0) null else (idx - 1)
            val chip = Button(context).apply {
                text = title
                textSize = 8f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                gravity = Gravity.CENTER
                minHeight = 0; minimumHeight = 0
                val isSelected = (selectedManualStage == stageTarget)
                setTextColor(if (isSelected) Color.WHITE else "#94A3B8".toColorInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (isSelected) "#8B5CF6" else "#1F1735"))
                    cornerRadius = dpF(6f)
                    setStroke(dp(1), Color.parseColor(if (isSelected) "#A78BFA" else "#2E2250"))
                }
                setPadding(dp(8), dp(3), dp(8), dp(3))
                setOnClickListener {
                    selectedManualStage = stageTarget
                    chipButtons.forEachIndexed { cIdx, btn ->
                        val cTarget = if (cIdx == 0) null else (cIdx - 1)
                        val active = (selectedManualStage == cTarget)
                        btn.setTextColor(if (active) Color.WHITE else "#94A3B8".toColorInt())
                        (btn.background as? GradientDrawable)?.apply {
                            setColor(Color.parseColor(if (active) "#8B5CF6" else "#1F1735"))
                            setStroke(dp(1), Color.parseColor(if (active) "#A78BFA" else "#2E2250"))
                        }
                    }
                }
            }
            chipButtons.add(chip)
            chipRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(24)).apply {
                marginEnd = dp(4)
            })
        }
        chipScroll.addView(chipRow)
        topControlsBar.addView(chipScroll)

        val barLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            setMargins(dp(10), dp(16), dp(10), 0)
        }
        root.addView(topControlsBar, barLp)

        animRunnable = object : Runnable {
            override fun run() {
                if (isPlaying) {
                    animProgress = (animProgress + 0.0015f * playbackSpeed) % 1.0f
                    demoCanvasView.invalidate()
                }
                handler.postDelayed(this, 25L)
            }
        }
        animRunnable?.let { handler.post(it) }

        if (context is android.app.Activity && !context.isFinishing) {
            val dialog = android.app.Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.setContentView(root)
            dialog.setOnDismissListener {
                animRunnable?.let { handler.removeCallbacks(it) }
            }
            appDialog = dialog
            dialog.show()
        } else {
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
    }

    fun dismiss() {
        animRunnable?.let { handler.removeCallbacks(it) }
        appDialog?.let {
            if (it.isShowing) it.dismiss()
            appDialog = null
        }
        rootFrameLayout?.let {
            overlayWindowManager.removeViewSafe(it)
            rootFrameLayout = null
        }
    }
}

