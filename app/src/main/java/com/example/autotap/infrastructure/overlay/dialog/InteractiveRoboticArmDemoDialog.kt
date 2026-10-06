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

/**
 * Высокодетализированная интерактивная демонстрация работы AutoTap.
 * Включает классическую узнаваемую указательную руку-перчатку (Index Finger Pointer Hand).
 * Двухстрочные крупные подсказки (13.5dp) со 100% вместимостью и подложкой высокой контрастности.
 * Настроенная комфортная скорость чтения подсказок.
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
    private var playbackSpeed = 0.5f // Комфортная демонстрационная скорость по умолчанию 0.5x
    private var animProgress = 0f
    private var selectedManualStage: Int? = null

    private val handler = Handler(Looper.getMainLooper())
    private var animRunnable: Runnable? = null

    fun show() {
        if (rootFrameLayout != null) return

        val root = FrameLayout(context).apply {
            setBackgroundColor("#F6060911".toColorInt())
        }
        rootFrameLayout = root

        val topControlsBar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F2161B28".toColorInt(), "#F20D111A".toColorInt())
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
                color = "#101827".toColorInt()
                strokeWidth = dpF(1f)
            }
            private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#F00F172A".toColorInt()
            }
            private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.5f)
                color = "#38BDF8".toColorInt()
            }
            private val gameScreenBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.8f)
                color = "#1E293B".toColorInt()
            }
            private val dialogBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#F80D111A".toColorInt()
            }
            private val dialogBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.5f)
            }
            private val textTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dpF(11f)
                typeface = Typeface.DEFAULT_BOLD
            }
            private val textSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#94A3B8".toColorInt()
                textSize = dpF(8.5f)
                typeface = Typeface.MONOSPACE
            }

            // Крупный высококонтрастный шрифт подсказок
            private val bannerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dpF(13f)
                typeface = Typeface.DEFAULT_BOLD
            }
            private val stageTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#38BDF8".toColorInt()
                textSize = dpF(11f)
                typeface = Typeface.DEFAULT_BOLD
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
                strokeWidth = dpF(2f)
                pathEffect = DashPathEffect(floatArrayOf(dpF(6f), dpF(4f)), 0f)
                color = "#F59E0B".toColorInt()
            }
            private val captureCrosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2.2f)
                color = "#38BDF8".toColorInt()
            }
            private val ocrBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                color = "#818CF8".toColorInt()
            }
            private val swipeTrajectoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(3.5f)
                color = "#F59E0B".toColorInt()
                strokeCap = Paint.Cap.ROUND
                pathEffect = DashPathEffect(floatArrayOf(dpF(8f), dpF(5f)), 0f)
            }
            private val badgeCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dpF(9.5f)
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            // Paints for Cybernetic Pointing Hand & Glove
            private val handChassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            private val handBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.6f)
                color = "#38BDF8".toColorInt()
            }
            private val fingerJointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#E2E8F0".toColorInt()
            }
            private val laserScanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            private val laserLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
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
            private val generalItemBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

            private val reusableRectF = RectF()
            private val reusablePath = Path()
            private val iconBounds = RectF()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val now = System.currentTimeMillis()

                // 1. Сетка фонового интерфейса
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

                // 2. Игровой экран (Центральная симулируемая область)
                val gameRect = RectF(w * 0.04f, h * 0.13f, w * 0.96f, h * 0.70f)
                generalItemBg.shader = LinearGradient(
                    gameRect.left, gameRect.top, gameRect.right, gameRect.bottom,
                    intArrayOf("#111827".toColorInt(), "#0B0F19".toColorInt()), null, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(gameRect, dpF(12f), dpF(12f), generalItemBg)
                canvas.drawRoundRect(gameRect, dpF(12f), dpF(12f), gameScreenBorderPaint)

                // Шапка игрового экрана
                val gameHeader = RectF(gameRect.left, gameRect.top, gameRect.right, gameRect.top + dpF(32f))
                generalItemBg.shader = null
                generalItemBg.color = "#1E293B".toColorInt()
                canvas.drawRoundRect(gameHeader, dpF(12f), dpF(12f), generalItemBg)
                canvas.drawText("QUEST REWARDS * ИГРОВАЯ СЦЕНА", gameHeader.left + dpF(14f), gameHeader.centerY() + dpF(4f), textTitlePaint)

                // Элемент 1: Сундук и стрелка ▶ (Цель Шаблона)
                val chestArea = RectF(gameRect.left + dpF(12f), gameHeader.bottom + dpF(12f), gameRect.left + dpF(125f), gameHeader.bottom + dpF(95f))
                generalItemBg.color = "#1A2234".toColorInt()
                canvas.drawRoundRect(chestArea, dpF(8f), dpF(8f), generalItemBg)
                canvas.drawText("СУНДУК x1", chestArea.left + dpF(8f), chestArea.top + dpF(16f), textSubPaint)

                val arrowTarget = RectF(chestArea.centerX() - dpF(18f), chestArea.centerY() - dpF(8f), chestArea.centerX() + dpF(18f), chestArea.centerY() + dpF(20f))
                generalItemBg.color = "#3538BDF8".toColorInt()
                canvas.drawRoundRect(arrowTarget, dpF(4f), dpF(4f), generalItemBg)
                reusablePath.rewind()
                reusablePath.moveTo(arrowTarget.left + dpF(10f), arrowTarget.top + dpF(6f))
                reusablePath.lineTo(arrowTarget.right - dpF(10f), arrowTarget.centerY())
                reusablePath.lineTo(arrowTarget.left + dpF(10f), arrowTarget.bottom - dpF(6f))
                reusablePath.close()
                generalItemBg.color = Color.WHITE
                canvas.drawPath(reusablePath, generalItemBg)

                // Элемент 2: Кнопка "CLAIM 500x" (Цель OCR)
                val ocrClaimBtn = RectF(gameRect.right - dpF(135f), gameHeader.bottom + dpF(20f), gameRect.right - dpF(12f), gameHeader.bottom + dpF(64f))
                generalItemBg.color = "#064E3B".toColorInt()
                canvas.drawRoundRect(ocrClaimBtn, dpF(6f), dpF(6f), generalItemBg)
                gameScreenBorderPaint.color = "#10B981".toColorInt()
                canvas.drawRoundRect(ocrClaimBtn, dpF(6f), dpF(6f), gameScreenBorderPaint)
                canvas.drawText("CLAIM 500x", ocrClaimBtn.left + dpF(18f), ocrClaimBtn.centerY() + dpF(4f), textTitlePaint)

                // Элемент 3: Список наград (Цель простых кликов и свайпа)
                val listArea = RectF(gameRect.left + dpF(12f), chestArea.bottom + dpF(12f), gameRect.right - dpF(12f), gameRect.bottom - dpF(12f))
                generalItemBg.color = "#131C2E".toColorInt()
                canvas.drawRoundRect(listArea, dpF(8f), dpF(8f), generalItemBg)
                canvas.drawText("СПИСОК НАГРАД (КЛИКИ И ПРОКРУТКА)", listArea.left + dpF(10f), listArea.top + dpF(16f), textSubPaint)

                // Кнопки наград для демонстрации кликов
                val rewardBtn1 = RectF(listArea.left + dpF(8f), listArea.top + dpF(24f), listArea.right - dpF(8f), listArea.top + dpF(46f))
                val rewardBtn2 = RectF(listArea.left + dpF(8f), listArea.top + dpF(50f), listArea.right - dpF(8f), listArea.top + dpF(72f))
                val rewardBtn3 = RectF(listArea.left + dpF(8f), listArea.top + dpF(76f), listArea.right - dpF(8f), listArea.top + dpF(98f))

                generalItemBg.color = "#1E293B".toColorInt()
                canvas.drawRoundRect(rewardBtn1, dpF(4f), dpF(4f), generalItemBg)
                canvas.drawText("Награда #1 — [ЗАБРАТЬ +250]", rewardBtn1.left + dpF(10f), rewardBtn1.centerY() + dpF(4f), textSubPaint)

                canvas.drawRoundRect(rewardBtn2, dpF(4f), dpF(4f), generalItemBg)
                canvas.drawText("Награда #2 — [ЗАБРАТЬ +500]", rewardBtn2.left + dpF(10f), rewardBtn2.centerY() + dpF(4f), textSubPaint)

                canvas.drawRoundRect(rewardBtn3, dpF(4f), dpF(4f), generalItemBg)
                canvas.drawText("Награда #3 — [ЗАБРАТЬ +1000]", rewardBtn3.left + dpF(10f), rewardBtn3.centerY() + dpF(4f), textSubPaint)

                // 3. Плавающий пульт AutoTap (ControlPanel)
                val panelRect = RectF(w * 0.06f, h * 0.72f, w * 0.94f, h * 0.81f)
                canvas.drawRoundRect(panelRect, dpF(10f), dpF(10f), panelBgPaint)
                canvas.drawRoundRect(panelRect, dpF(10f), dpF(10f), panelBorderPaint)

                val btnW = (panelRect.width() - dpF(28f)) / 5f
                val btnH = panelRect.height() - dpF(12f)
                val btnY = panelRect.top + dpF(6f)

                // Кнопка Пуск (Зеленая)
                val pBtnPlay = RectF(panelRect.left + dpF(6f), btnY, panelRect.left + dpF(6f) + btnW, btnY + btnH)
                generalItemBg.color = if (animProgress >= 0.75f) "#064E3B".toColorInt() else "#0D2E1E".toColorInt()
                canvas.drawRoundRect(pBtnPlay, dpF(6f), dpF(6f), generalItemBg)
                gameScreenBorderPaint.color = "#34D399".toColorInt()
                canvas.drawRoundRect(pBtnPlay, dpF(6f), dpF(6f), gameScreenBorderPaint)
                iconBounds.set(pBtnPlay.centerX() - dpF(8f), pBtnPlay.centerY() - dpF(8f), pBtnPlay.centerX() + dpF(8f), pBtnPlay.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, if (animProgress >= 0.75f) VectorIconDrawer.IconType.PAUSE else VectorIconDrawer.IconType.PLAY, iconBounds, "#34D399".toColorInt(), dpF(1.5f))

                // Кнопка + Клик (Голубая)
                val pBtnClick = RectF(pBtnPlay.right + dpF(4f), btnY, pBtnPlay.right + dpF(4f) + btnW, btnY + btnH)
                generalItemBg.color = "#1E293B".toColorInt()
                canvas.drawRoundRect(pBtnClick, dpF(6f), dpF(6f), generalItemBg)
                gameScreenBorderPaint.color = "#38BDF8".toColorInt()
                canvas.drawRoundRect(pBtnClick, dpF(6f), dpF(6f), gameScreenBorderPaint)
                iconBounds.set(pBtnClick.centerX() - dpF(8f), pBtnClick.centerY() - dpF(8f), pBtnClick.centerX() + dpF(8f), pBtnClick.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.ACTION_CLICK, iconBounds, "#38BDF8".toColorInt(), dpF(1.5f))

                // Кнопка + Шаблон (Фиолетовая)
                val pBtnTpl = RectF(pBtnClick.right + dpF(4f), btnY, pBtnClick.right + dpF(4f) + btnW, btnY + btnH)
                generalItemBg.color = "#2E1A47".toColorInt()
                canvas.drawRoundRect(pBtnTpl, dpF(6f), dpF(6f), generalItemBg)
                gameScreenBorderPaint.color = "#A78BFA".toColorInt()
                canvas.drawRoundRect(pBtnTpl, dpF(6f), dpF(6f), gameScreenBorderPaint)
                iconBounds.set(pBtnTpl.centerX() - dpF(8f), pBtnTpl.centerY() - dpF(8f), pBtnTpl.centerX() + dpF(8f), pBtnTpl.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.CAPTURE, iconBounds, "#A78BFA".toColorInt(), dpF(1.5f))

                // Кнопка + OCR (Индиго)
                val pBtnOcr = RectF(pBtnTpl.right + dpF(4f), btnY, pBtnTpl.right + dpF(4f) + btnW, btnY + btnH)
                generalItemBg.color = "#1E1B4B".toColorInt()
                canvas.drawRoundRect(pBtnOcr, dpF(6f), dpF(6f), generalItemBg)
                gameScreenBorderPaint.color = "#818CF8".toColorInt()
                canvas.drawRoundRect(pBtnOcr, dpF(6f), dpF(6f), gameScreenBorderPaint)
                iconBounds.set(pBtnOcr.centerX() - dpF(8f), pBtnOcr.centerY() - dpF(8f), pBtnOcr.centerX() + dpF(8f), pBtnOcr.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.ACTION_OCR, iconBounds, "#818CF8".toColorInt(), dpF(1.5f))

                // Кнопка + Свайп (Оранжевая)
                val pBtnSwipe = RectF(pBtnOcr.right + dpF(4f), btnY, pBtnOcr.right + dpF(4f) + btnW, btnY + btnH)
                generalItemBg.color = "#38230D".toColorInt()
                canvas.drawRoundRect(pBtnSwipe, dpF(6f), dpF(6f), generalItemBg)
                gameScreenBorderPaint.color = "#F59E0B".toColorInt()
                canvas.drawRoundRect(pBtnSwipe, dpF(6f), dpF(6f), gameScreenBorderPaint)
                iconBounds.set(pBtnSwipe.centerX() - dpF(8f), pBtnSwipe.centerY() - dpF(8f), pBtnSwipe.centerX() + dpF(8f), pBtnSwipe.centerY() + dpF(8f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.ACTION_SWIPE, iconBounds, "#F59E0B".toColorInt(), dpF(1.5f))

                // 4. Логика интерактивных стадий сценария (CUJ)
                val currentStage = selectedManualStage ?: when {
                    animProgress < 0.22f -> 0 // 1. Вырезка шаблона и ROI
                    animProgress < 0.40f -> 1 // 2. Настройка шаблона
                    animProgress < 0.58f -> 2 // 3. Настройка OCR
                    animProgress < 0.72f -> 3 // 4. Клики и добавление шагов
                    else -> 4                 // 5. Исполнение макроса
                }

                var targetX = 0f
                var targetY = 0f
                var isScanning = false
                var isTapping = false
                var isSwiping = false
                var stageTagText = ""
                var statusLine1 = ""
                var statusLine2 = ""

                val tplDialogRect = RectF(w * 0.10f, h * 0.22f, w * 0.90f, h * 0.54f)
                val ocrDialogRect = RectF(w * 0.10f, h * 0.22f, w * 0.90f, h * 0.54f)

                when (currentStage) {
                    0 -> {
                        stageTagText = "[ШАГ 1/5 · АВТО-КАДРИРОВАНИЕ И ROI]"
                        val p = if (selectedManualStage != null) (now % 4500) / 4500f else (animProgress / 0.22f)
                        if (p < 0.30f) {
                            targetX = pBtnTpl.centerX()
                            targetY = pBtnTpl.centerY()
                            isTapping = p in 0.18f..0.28f
                            statusLine1 = "Нажатие [+ ШАБЛОН] для выбора объекта."
                            statusLine2 = "Авто-кадрирование создается автоматически!"
                        } else {
                            val drawP = ((p - 0.30f) / 0.70f).coerceIn(0f, 1f)
                            targetX = chestArea.left + drawP * chestArea.width()
                            targetY = chestArea.top + drawP * chestArea.height()
                            isTapping = true
                            statusLine1 = "Область ROI — это ДОПОЛНИТЕЛЬНАЯ опция."
                            statusLine2 = "Она ускоряет поиск шаблона до 0-5 миллисекунд!"
                        }
                    }
                    1 -> {
                        stageTagText = "[ШАГ 2/5 · НАСТРОЙКА ШАБЛОНА]"
                        val p = if (selectedManualStage != null) (now % 4000) / 4000f else ((animProgress - 0.22f) / 0.18f)
                        targetX = tplDialogRect.centerX()
                        targetY = tplDialogRect.bottom - dpF(24f)
                        isTapping = p > 0.65f
                        statusLine1 = "Настройка порога (85%) и маски формы."
                        statusLine2 = "Сохранение Шаблона #1 в сценарий."
                    }
                    2 -> {
                        stageTagText = "[ШАГ 3/5 · НАСТРОЙКА OCR ТЕКСТА]"
                        val p = if (selectedManualStage != null) (now % 4000) / 4000f else ((animProgress - 0.40f) / 0.18f)
                        if (p < 0.35f) {
                            targetX = pBtnOcr.centerX()
                            targetY = pBtnOcr.centerY()
                            isTapping = p in 0.22f..0.32f
                            statusLine1 = "Нажатие [+ OCR] для распознавания текста."
                            statusLine2 = "Поиск кнопок по текстовым ключевым словам."
                        } else {
                            targetX = ocrDialogRect.centerX()
                            targetY = ocrDialogRect.bottom - dpF(24f)
                            isTapping = p > 0.75f
                            statusLine1 = "Ввод искомой строки 'CLAIM 500x'."
                            statusLine2 = "Сохранение шага распознавания текста #2."
                        }
                    }
                    3 -> {
                        stageTagText = "[ШАГ 4/5 · ПРОСТЫЕ КЛИКИ И СВАЙП]"
                        val p = if (selectedManualStage != null) (now % 4500) / 4500f else ((animProgress - 0.58f) / 0.14f)
                        if (p < 0.25f) {
                            targetX = pBtnClick.centerX()
                            targetY = pBtnClick.centerY()
                            isTapping = p in 0.12f..0.22f
                            statusLine1 = "Добавление простых точек клика (#3 и #4)."
                            statusLine2 = "Для быстрой сборки наград списком."
                        } else if (p < 0.50f) {
                            targetX = rewardBtn1.centerX()
                            targetY = rewardBtn1.centerY()
                            isTapping = p in 0.38f..0.48f
                            statusLine1 = "Клик по Кнопке Награда #1."
                            statusLine2 = "Простые клики ставятся мгновенно."
                        } else if (p < 0.75f) {
                            targetX = rewardBtn2.centerX()
                            targetY = rewardBtn2.centerY()
                            isTapping = p in 0.62f..0.72f
                            statusLine1 = "Клик по Кнопке Награда #2."
                            statusLine2 = "Подготовка сценария к исполнению."
                        } else {
                            targetX = pBtnPlay.centerX()
                            targetY = pBtnPlay.centerY()
                            isTapping = p > 0.88f
                            statusLine1 = "Нажатие кнопки [▶ ПУСК]."
                            statusLine2 = "Запуск фонового исполнения макроса!"
                        }
                    }
                    else -> {
                        stageTagText = "[ШАГ 5/5 · АВТОМАТИЧЕСКИЙ МАКРОС]"
                        val p = if (selectedManualStage != null) (now % 6500) / 6500f else ((animProgress - 0.72f) / 0.28f)
                        if (p < 0.20f) {
                            // Фаза 1: Скан проходит по всему экрану, отдельно от указателя
                            isScanning = true
                            isTapping = false
                            targetX = w * 0.82f // Указатель держится в стороне во время сканирования
                            targetY = h * 0.65f
                            statusLine1 = "1. Скан проходит по ВСЕМУ ЭКРАНУ (кроме ROI)."
                            statusLine2 = "Сканер работает отдельно от указателя пальца."
                        } else if (p < 0.35f) {
                            // Фаза 2: Анимация с пояснением, что объект найден
                            isScanning = false
                            isTapping = false
                            targetX = w * 0.82f
                            targetY = h * 0.65f
                            statusLine1 = "[НАЙДЕНО]: Обнаружен объект 'СУНДУК' (98%)."
                            statusLine2 = "Наведение указателя пальца на найденную цель..."
                        } else if (p < 0.50f) {
                            // Фаза 3: Перемещение указателя к цели и клик
                            isScanning = false
                            targetX = arrowTarget.centerX()
                            targetY = arrowTarget.centerY()
                            isTapping = p in 0.42f..0.48f
                            statusLine1 = "3. Перемещение пальца и нажатие сундука."
                            statusLine2 = "Мгновенное исполнение шага #1!"
                        } else if (p < 0.68f) {
                            // Фаза 4: Скан OCR и клик по тексту 'CLAIM 500x'
                            targetX = ocrClaimBtn.centerX()
                            targetY = ocrClaimBtn.centerY()
                            isScanning = p < 0.58f
                            isTapping = p >= 0.58f
                            statusLine1 = "4. OCR скан и клик по тексту 'CLAIM 500x'."
                            statusLine2 = "Автоматическое распознавание символов."
                        } else if (p < 0.84f) {
                            // Фаза 5: Простые клики по списку наград (#1 и #2)
                            if (p < 0.76f) {
                                targetX = rewardBtn1.centerX()
                                targetY = rewardBtn1.centerY()
                                isTapping = p in 0.71f..0.75f
                                statusLine1 = "5. Взять Награду #1 из списка."
                                statusLine2 = "Простой клик по первому пункту."
                            } else {
                                targetX = rewardBtn2.centerX()
                                targetY = rewardBtn2.centerY()
                                isTapping = p in 0.80f..0.83f
                                statusLine1 = "6. Взять Награду #2 из списка."
                                statusLine2 = "Простой клик по второму пункту."
                            }
                        } else {
                            // Фаза 6: Прокрутка (свайп) списка наград
                            val swipeProg = (p - 0.84f) / 0.16f
                            targetX = listArea.centerX()
                            targetY = (listArea.bottom - dpF(15f)) - swipeProg * dpF(55f)
                            isSwiping = true
                            statusLine1 = "7. Прокрутка (свайп) списка наград вверх."
                            statusLine2 = "Демонстрация макроса завершена!"
                        }
                    }
                }

                // 5. Отрисовка интерактивных окон оверлея

                // 5.1 Видоискатель и начерченная рамка ROI (Стадия 0)
                if (currentStage == 0) {
                    val vW = chestArea.width() + dpF(12f)
                    val vH = chestArea.height() + dpF(12f)
                    val vLeft = chestArea.left - dpF(6f)
                    val vTop = chestArea.top - dpF(6f)
                    val vRight = vLeft + vW
                    val vBottom = vTop + vH
                    val cLen = dpF(12f)

                    canvas.drawLine(vLeft, vTop, vLeft + cLen, vTop, captureCrosshairPaint)
                    canvas.drawLine(vLeft, vTop, vLeft, vTop + cLen, captureCrosshairPaint)
                    canvas.drawLine(vRight, vTop, vRight - cLen, vTop, captureCrosshairPaint)
                    canvas.drawLine(vRight, vTop, vRight, vTop + cLen, captureCrosshairPaint)
                    canvas.drawLine(vLeft, vBottom, vLeft + cLen, vBottom, captureCrosshairPaint)
                    canvas.drawLine(vLeft, vBottom, vLeft, vBottom - cLen, captureCrosshairPaint)
                    canvas.drawLine(vRight, vBottom, vRight - cLen, vBottom, captureCrosshairPaint)
                    canvas.drawLine(vRight, vBottom, vRight, vBottom - cLen, captureCrosshairPaint)

                    val roiBox = RectF(chestArea.left - dpF(2f), chestArea.top - dpF(2f), chestArea.right + dpF(2f), chestArea.bottom + dpF(2f))
                    canvas.drawRoundRect(roiBox, dpF(6f), dpF(6f), roiDashedPaint)
                    canvas.drawText("ДОПОЛНИТЕЛЬНАЯ ROI ЗОНА: 240x160 px", roiBox.left + dpF(2f), roiBox.top - dpF(4f), textSubPaint)
                }

                // 5.2 Диалог настройки Шаблона (EditActionDialog)
                if (currentStage == 1) {
                    dialogBorderPaint.color = "#A78BFA".toColorInt()
                    canvas.drawRoundRect(tplDialogRect, dpF(10f), dpF(10f), dialogBgPaint)
                    canvas.drawRoundRect(tplDialogRect, dpF(10f), dpF(10f), dialogBorderPaint)

                    canvas.drawText("НАСТРОЙКА ШАГА #1 (ШАБЛОН)", tplDialogRect.left + dpF(14f), tplDialogRect.top + dpF(22f), textTitlePaint)

                    val thumbCard = RectF(tplDialogRect.left + dpF(14f), tplDialogRect.top + dpF(32f), tplDialogRect.left + dpF(74f), tplDialogRect.top + dpF(92f))
                    generalItemBg.color = "#1E1A33".toColorInt()
                    canvas.drawRoundRect(thumbCard, dpF(6f), dpF(6f), generalItemBg)

                    reusablePath.rewind()
                    reusablePath.moveTo(thumbCard.left + dpF(16f), thumbCard.centerY() - dpF(10f))
                    reusablePath.lineTo(thumbCard.right - dpF(16f), thumbCard.centerY())
                    reusablePath.lineTo(thumbCard.left + dpF(16f), thumbCard.centerY() + dpF(10f))
                    reusablePath.close()
                    generalItemBg.color = "#38BDF8".toColorInt()
                    canvas.drawPath(reusablePath, generalItemBg)

                    canvas.drawText("Детекция: МАСКА ФОРМЫ (ИИ)", thumbCard.right + dpF(10f), thumbCard.top + dpF(18f), textTitlePaint)
                    canvas.drawText("Порог: 85% · Авто-кадрирование: ВКЛ", thumbCard.right + dpF(10f), thumbCard.top + dpF(34f), textSubPaint)
                    canvas.drawText("ROI: Доп. ускорение (0мс)", thumbCard.right + dpF(10f), thumbCard.top + dpF(48f), textSubPaint)

                    val btnSave = RectF(tplDialogRect.left + dpF(14f), tplDialogRect.bottom - dpF(36f), tplDialogRect.right - dpF(14f), tplDialogRect.bottom - dpF(10f))
                    generalItemBg.color = "#059669".toColorInt()
                    canvas.drawRoundRect(btnSave, dpF(6f), dpF(6f), generalItemBg)
                    canvas.drawText("СОХРАНИТЬ ШАГ #1", btnSave.centerX() - dpF(44f), btnSave.centerY() + dpF(4f), textTitlePaint)
                }

                // 5.3 Диалог OCR текста
                if (currentStage == 2) {
                    dialogBorderPaint.color = "#818CF8".toColorInt()
                    canvas.drawRoundRect(ocrDialogRect, dpF(10f), dpF(10f), dialogBgPaint)
                    canvas.drawRoundRect(ocrDialogRect, dpF(10f), dpF(10f), dialogBorderPaint)

                    canvas.drawText("НАСТРОЙКА ШАГА #2 (OCR ТЕКСТ)", ocrDialogRect.left + dpF(14f), ocrDialogRect.top + dpF(22f), textTitlePaint)

                    val etField = RectF(ocrDialogRect.left + dpF(14f), ocrDialogRect.top + dpF(34f), ocrDialogRect.right - dpF(14f), ocrDialogRect.top + dpF(66f))
                    generalItemBg.color = "#161B22".toColorInt()
                    canvas.drawRoundRect(etField, dpF(6f), dpF(6f), generalItemBg)
                    dialogBorderPaint.color = "#30363D".toColorInt()
                    canvas.drawRoundRect(etField, dpF(6f), dpF(6f), dialogBorderPaint)
                    canvas.drawText("Искомый текст: 'CLAIM 500x'", etField.left + dpF(10f), etField.centerY() + dpF(4f), textTitlePaint)

                    val btnOcrSave = RectF(ocrDialogRect.left + dpF(14f), ocrDialogRect.bottom - dpF(36f), ocrDialogRect.right - dpF(14f), ocrDialogRect.bottom - dpF(10f))
                    generalItemBg.color = "#4338CA".toColorInt()
                    canvas.drawRoundRect(btnOcrSave, dpF(6f), dpF(6f), generalItemBg)
                    canvas.drawText("СКАН И СОХРАНИТЬ ШАГ #2", btnOcrSave.centerX() - dpF(54f), btnOcrSave.centerY() + dpF(4f), textTitlePaint)
                }

                // 5.4 Плавающие бейджи созданных шагов (#1, #2, #3, #4)
                if (currentStage >= 1) {
                    val b1X = arrowTarget.left - dpF(4f)
                    val b1Y = arrowTarget.top - dpF(16f)
                    badgeCirclePaint.color = "#A78BFA".toColorInt()
                    canvas.drawCircle(b1X + dpF(8f), b1Y + dpF(8f), dpF(9f), badgeCirclePaint)
                    canvas.drawText("#1", b1X + dpF(8f), b1Y + dpF(11.5f), badgeTextPaint)
                }
                if (currentStage >= 2) {
                    val b2X = ocrClaimBtn.left - dpF(4f)
                    val b2Y = ocrClaimBtn.top - dpF(16f)
                    badgeCirclePaint.color = "#818CF8".toColorInt()
                    canvas.drawCircle(b2X + dpF(8f), b2Y + dpF(8f), dpF(9f), badgeCirclePaint)
                    canvas.drawText("#2", b2X + dpF(8f), b2Y + dpF(11.5f), badgeTextPaint)
                }
                if (currentStage >= 3) {
                    val b3X = rewardBtn1.right - dpF(16f)
                    val b3Y = rewardBtn1.centerY()
                    badgeCirclePaint.color = "#38BDF8".toColorInt()
                    canvas.drawCircle(b3X, b3Y, dpF(8.5f), badgeCirclePaint)
                    canvas.drawText("#3", b3X, b3Y + dpF(3f), badgeTextPaint)

                    val b4X = rewardBtn2.right - dpF(16f)
                    val b4Y = rewardBtn2.centerY()
                    badgeCirclePaint.color = "#F59E0B".toColorInt()
                    canvas.drawCircle(b4X, b4Y, dpF(8.5f), badgeCirclePaint)
                    canvas.drawText("#4", b4X, b4Y + dpF(3f), badgeTextPaint)
                }

                // 5.5 Во время реального исполнения (Стадия 4)
                if (currentStage == 4) {
                    val p = if (selectedManualStage != null) (now % 6500) / 6500f else ((animProgress - 0.72f) / 0.28f)
                    if (p >= 0.20f) {
                        val roiBox = RectF(chestArea.left - dpF(2f), chestArea.top - dpF(2f), chestArea.right + dpF(2f), chestArea.bottom + dpF(2f))
                        canvas.drawRoundRect(roiBox, dpF(6f), dpF(6f), roiDashedPaint)

                        highlightFillPaint.shader = LinearGradient(
                            arrowTarget.left, arrowTarget.top, arrowTarget.right, arrowTarget.bottom,
                            intArrayOf("#5038BDF8".toColorInt(), "#2000F0FF".toColorInt()), null, Shader.TileMode.CLAMP
                        )
                        canvas.drawRoundRect(arrowTarget, dpF(4f), dpF(4f), highlightFillPaint)
                        canvas.drawRoundRect(arrowTarget, dpF(4f), dpF(4f), highlightBoxPaint)

                        canvas.drawRoundRect(ocrClaimBtn, dpF(6f), dpF(6f), ocrBoxPaint)
                    }

                    if (isSwiping) {
                        canvas.drawLine(listArea.centerX(), listArea.bottom - dpF(15f), listArea.centerX(), listArea.top + dpF(15f), swipeTrajectoryPaint)
                    }
                }

                // 6. Классическая узнаваемая указательная рука с вытянутым указательным пальцем (Index Pointer Glove)
                drawClassicIndexPointerGlove(canvas, targetX, targetY, isTapping, isScanning)

                // 7. Лазерный радар сканирования (линейная развертка по всему игровому экрану)
                if (isScanning) {
                    val sweepProgress = (now % 1800) / 1800f
                    val sweepY = gameRect.top + sweepProgress * gameRect.height()
                    val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = dpF(3f)
                        color = "#00F0FF".toColorInt()
                    }
                    canvas.drawLine(gameRect.left, sweepY, gameRect.right, sweepY, scanLinePaint)

                    // Мягкое неоновое свечение под линией развертки
                    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        shader = LinearGradient(
                            0f, sweepY - dpF(14f), 0f, sweepY + dpF(14f),
                            intArrayOf(Color.TRANSPARENT, "#2500F0FF".toColorInt(), Color.TRANSPARENT),
                            null, Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(gameRect.left, sweepY - dpF(14f), gameRect.right, sweepY + dpF(14f), glowPaint)
                }

                // 8. Импульсные волны касания (Pulse Circles)
                if (isTapping) {
                    val pulsePhase = (now % 500) / 500f
                    val r1 = pulsePhase * dpF(36f)
                    val alpha1 = ((1f - pulsePhase) * 255).toInt().coerceIn(0, 255)
                    tapPulsePaint.alpha = alpha1
                    canvas.drawCircle(targetX, targetY, r1, tapPulsePaint)

                    val r2 = (pulsePhase * 0.6f) * dpF(36f)
                    val alpha2 = ((1f - pulsePhase * 0.6f) * 255).toInt().coerceIn(0, 255)
                    tapPulsePaint.alpha = alpha2
                    canvas.drawCircle(targetX, targetY, r2, tapPulsePaint)

                    canvas.drawCircle(targetX, targetY, dpF(4.5f), tapCorePaint)
                }

                // 9. Крупная двухстрочная плашка подсказок внизу экрана (расширена горизонтально и вертикально)
                val bannerH = dpF(82f)
                val bannerY = h - bannerH - dpF(8f)
                reusableRectF.set(w * 0.04f, bannerY, w * 0.96f, bannerY + bannerH)

                generalItemBg.color = "#F80F172A".toColorInt()
                canvas.drawRoundRect(reusableRectF, dpF(10f), dpF(10f), generalItemBg)
                panelBorderPaint.color = "#8B5CF6".toColorInt()
                canvas.drawRoundRect(reusableRectF, dpF(10f), dpF(10f), panelBorderPaint)

                // Смещение текста вровень с левой границей расширенной плашки
                val textStartX = w * 0.04f + dpF(14f)

                // 1. Шаговый тикер
                canvas.drawText(stageTagText, textStartX, bannerY + dpF(20f), stageTagPaint)
                // 2. Строка 1 подсказки (крупный жирный шрифт 13dp)
                canvas.drawText(statusLine1, textStartX, bannerY + dpF(42f), bannerTextPaint)
                // 3. Строка 2 подсказки
                canvas.drawText(statusLine2, textStartX, bannerY + dpF(64f), bannerTextPaint)
            }

            /**
             * Классический узнаваемый кибер-указатель (Index Finger Pointer Glove):
             * Четко вытянутый УКАЗАТЕЛЬНЫЙ ПАЛЕЦ, направленный строго в точку клика (tx, ty),
             * при этом средний, безымянный пальцы и мизинец согнуты в кулак сбоку, а большой палец сложен на ладони.
             */
            /**
             * Простая, высокоэстетичная геометрическая стрелка указателя (Neon Cursor Arrow).
             * Кончик указывает ровно в (tx, ty), имеет золотистую заливку (#FCD34D) и темный контур (#B45309).
             */
            private fun drawClassicIndexPointerGlove(
                canvas: Canvas,
                tx: Float,
                ty: Float,
                isTapping: Boolean,
                isScanning: Boolean
            ) {
                val angleRad = -135f * (PI.toFloat() / 180f)
                val cosA = cos(angleRad.toDouble()).toFloat()
                val sinA = sin(angleRad.toDouble()).toFloat()
                val perpX = -sinA
                val perpY = cosA

                val len = dpF(28f)
                val width = dpF(18f)

                // Основание стрелки
                val baseX = tx - cosA * len
                val baseY = ty - sinA * len

                // Левое крыло стрелки
                val leftX = baseX + perpX * (width * 0.5f)
                val leftY = baseY + perpY * (width * 0.5f)

                // Правое крыло стрелки
                val rightX = baseX - perpX * (width * 0.5f)
                val rightY = baseY - perpY * (width * 0.5f)

                // Срез в центре (хвостовая выемка)
                val recessX = tx - cosA * (len * 0.72f)
                val recessY = ty - sinA * (len * 0.72f)

                // Хвостовой стержень (shaft)
                val tailEndX = tx - cosA * (len * 1.35f)
                val tailEndY = ty - sinA * (len * 1.35f)

                val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = "#FCD34D".toColorInt()
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = dpF(2f)
                    color = "#B45309".toColorInt()
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }

                // Рисуем тело стрелки
                reusablePath.rewind()
                reusablePath.moveTo(tx, ty)
                reusablePath.lineTo(leftX, leftY)
                reusablePath.lineTo(recessX, recessY)
                reusablePath.lineTo(recessX + perpX * dpF(2.5f), recessY + perpY * dpF(2.5f))
                reusablePath.lineTo(tailEndX, tailEndY)
                reusablePath.lineTo(recessX - perpX * dpF(2.5f), recessY - perpY * dpF(2.5f))
                reusablePath.lineTo(recessX, recessY)
                reusablePath.lineTo(rightX, rightY)
                reusablePath.close()

                // Отрисовка тени
                canvas.save()
                canvas.translate(dpF(2f), dpF(2f))
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = "#500F172A".toColorInt()
                }
                canvas.drawPath(reusablePath, shadowPaint)
                canvas.restore()

                canvas.drawPath(reusablePath, arrowPaint)
                canvas.drawPath(reusablePath, borderPaint)

                // Светящаяся неоновая точка касания
                val tipColor = if (isTapping || isScanning) "#38BDF8".toColorInt() else "#10B981".toColorInt()
                tapCorePaint.color = tipColor
                canvas.drawCircle(tx, ty, dpF(4f), tapCorePaint)
            }

            private fun drawSegment(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w1: Float, w2: Float) {
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

                canvas.drawPath(reusablePath, handChassisPaint)
                canvas.drawPath(reusablePath, handBorderPaint)
            }
        }
        root.addView(demoCanvasView, FrameLayout.LayoutParams(-1, -1))

        // --- Верхняя компактная панель управления ---
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
        headerRow.addView(btnClose, LinearLayout.LayoutParams(dp(24), dp(24)))
        topControlsBar.addView(headerRow)

        // Чипы стадий
        val chipScroll = HorizontalScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
        val chipRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val chipTitles = listOf("ПОЛНЫЙ ЦИКЛ", "1. КАДРИРОВАНИЕ & ROI", "2. ДИАЛОГ ШАБЛОНА", "3. ДИАЛОГ OCR", "4. КЛИКИ НАГРАД", "5. ИСПОЛНЕНИЕ")
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

        // Анимационный цикл с комфортной скоростью чтения (0.0012f)
        animRunnable = object : Runnable {
            override fun run() {
                if (isPlaying) {
                    animProgress = (animProgress + 0.0012f * playbackSpeed) % 1.0f
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
