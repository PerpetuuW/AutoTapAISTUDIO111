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
 * Использует узнаваемую аккуратную указательную кисть (Cyber Pointing Hand) без загромождающих стрел.
 * Выделенная контрастная плашка подсказок с крупным шрифтом (13.5dp).
 * Включает демонстрацию простых кликов по списку наград, прокрутки и пояснения про ROI.
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
                textSize = dpF(13.5f)
                typeface = Typeface.DEFAULT_BOLD
            }
            private val stageTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#38BDF8".toColorInt()
                textSize = dpF(11.5f)
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

            // Paints for Cybernetic Pointing Hand
            private val handChassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            private val handBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.6f)
                color = "#38BDF8".toColorInt()
            }
            private val fingerJointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#CBD5E1".toColorInt()
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
                val gameRect = RectF(w * 0.04f, h * 0.14f, w * 0.96f, h * 0.74f)
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
                canvas.drawText("⚔️ QUEST REWARDS · ИГРОВАЯ СЦЕНА", gameHeader.left + dpF(14f), gameHeader.centerY() + dpF(4f), textTitlePaint)

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
                val panelRect = RectF(w * 0.06f, h * 0.76f, w * 0.94f, h * 0.85f)
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
                var statusText = ""

                val tplDialogRect = RectF(w * 0.10f, h * 0.25f, w * 0.90f, h * 0.58f)
                val ocrDialogRect = RectF(w * 0.10f, h * 0.25f, w * 0.90f, h * 0.58f)

                when (currentStage) {
                    0 -> {
                        stageTagText = "[ШАГ 1/5 · АВТО-КАДРИРОВАНИЕ И ROI]"
                        val p = if (selectedManualStage != null) (now % 3500) / 3500f else (animProgress / 0.22f)
                        if (p < 0.30f) {
                            targetX = pBtnTpl.centerX()
                            targetY = pBtnTpl.centerY()
                            isTapping = p in 0.18f..0.28f
                            statusText = "Нажатие [+ ШАБЛОН]. Авто-кадрирование создается сразу!"
                        } else {
                            val drawP = ((p - 0.30f) / 0.70f).coerceIn(0f, 1f)
                            targetX = chestArea.left + drawP * chestArea.width()
                            targetY = chestArea.top + drawP * chestArea.height()
                            isTapping = true
                            statusText = "ROI служит для ДОПОЛНИТЕЛЬНОЙ точной настройки и ускорения поиска!"
                        }
                    }
                    1 -> {
                        stageTagText = "[ШАГ 2/5 · НАСТРОЙКА ШАБЛОНА]"
                        val p = if (selectedManualStage != null) (now % 3000) / 3000f else ((animProgress - 0.22f) / 0.18f)
                        targetX = tplDialogRect.centerX()
                        targetY = tplDialogRect.bottom - dpF(24f)
                        isTapping = p > 0.65f
                        statusText = "Выбор порога (85%), маски формы и сохранение шага #1"
                    }
                    2 -> {
                        stageTagText = "[ШАГ 3/5 · НАСТРОЙКА OCR ТЕКСТА]"
                        val p = if (selectedManualStage != null) (now % 3000) / 3000f else ((animProgress - 0.40f) / 0.18f)
                        if (p < 0.35f) {
                            targetX = pBtnOcr.centerX()
                            targetY = pBtnOcr.centerY()
                            isTapping = p in 0.22f..0.32f
                            statusText = "Нажатие [+ OCR] для поиска текстовых кнопок"
                        } else {
                            targetX = ocrDialogRect.centerX()
                            targetY = ocrDialogRect.bottom - dpF(24f)
                            isTapping = p > 0.75f
                            statusText = "Ввод текста 'CLAIM 500x' и сохранение шага #2"
                        }
                    }
                    3 -> {
                        stageTagText = "[ШАГ 4/5 · ПРОСТЫЕ КЛИКИ И СВАЙП]"
                        val p = if (selectedManualStage != null) (now % 3500) / 3500f else ((animProgress - 0.58f) / 0.14f)
                        if (p < 0.25f) {
                            targetX = pBtnClick.centerX()
                            targetY = pBtnClick.centerY()
                            isTapping = p in 0.12f..0.22f
                            statusText = "Добавление точки клика для сбора Награды #1 (#3)"
                        } else if (p < 0.50f) {
                            targetX = rewardBtn1.centerX()
                            targetY = rewardBtn1.centerY()
                            isTapping = p in 0.38f..0.48f
                            statusText = "Клик по Кнопке Награда #1"
                        } else if (p < 0.75f) {
                            targetX = rewardBtn2.centerX()
                            targetY = rewardBtn2.centerY()
                            isTapping = p in 0.62f..0.72f
                            statusText = "Клик по Кнопке Награда #2 (#4)"
                        } else {
                            targetX = pBtnPlay.centerX()
                            targetY = pBtnPlay.centerY()
                            isTapping = p > 0.88f
                            statusText = "Нажатие [▶ ПУСК] для запуска макроса!"
                        }
                    }
                    else -> {
                        stageTagText = "[ШАГ 5/5 · АВТОМАТИЧЕСКИЙ МАКРОС]"
                        val p = if (selectedManualStage != null) (now % 4500) / 4500f else ((animProgress - 0.72f) / 0.28f)
                        if (p < 0.25f) {
                            targetX = arrowTarget.centerX()
                            targetY = arrowTarget.centerY()
                            isScanning = p < 0.12f
                            isTapping = p >= 0.12f
                            statusText = "ИИ-скан внутри ROI (98%) -> Клик по стрелке сундука"
                        } else if (p < 0.50f) {
                            targetX = ocrClaimBtn.centerX()
                            targetY = ocrClaimBtn.centerY()
                            isScanning = p < 0.38f
                            isTapping = p >= 0.38f
                            statusText = "OCR детекция 'CLAIM 500x' (100%) -> Нажатие кнопки"
                        } else if (p < 0.75f) {
                            targetX = rewardBtn1.centerX()
                            targetY = rewardBtn1.centerY()
                            isTapping = p in 0.62f..0.72f
                            statusText = "Простой клик по списку Награда #1"
                        } else {
                            val swipeProg = (p - 0.75f) / 0.25f
                            targetX = listArea.centerX()
                            targetY = (listArea.bottom - dpF(15f)) - swipeProg * dpF(55f)
                            isSwiping = true
                            statusText = "Плавный свайп списка вверх. Демонстрация завершена!"
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

                    canvas.drawText("⚙️ НАСТРОЙКА ШАГА #1 (ШАБЛОН)", tplDialogRect.left + dpF(14f), tplDialogRect.top + dpF(22f), textTitlePaint)

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

                    canvas.drawText("🔤 НАСТРОЙКА ШАГА #2 (OCR ТЕКСТ)", ocrDialogRect.left + dpF(14f), ocrDialogRect.top + dpF(22f), textTitlePaint)

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
                    val roiBox = RectF(chestArea.left - dpF(2f), chestArea.top - dpF(2f), chestArea.right + dpF(2f), chestArea.bottom + dpF(2f))
                    canvas.drawRoundRect(roiBox, dpF(6f), dpF(6f), roiDashedPaint)

                    highlightFillPaint.shader = LinearGradient(
                        arrowTarget.left, arrowTarget.top, arrowTarget.right, arrowTarget.bottom,
                        intArrayOf("#5038BDF8".toColorInt(), "#2000F0FF".toColorInt()), null, Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(arrowTarget, dpF(4f), dpF(4f), highlightFillPaint)
                    canvas.drawRoundRect(arrowTarget, dpF(4f), dpF(4f), highlightBoxPaint)

                    canvas.drawRoundRect(ocrClaimBtn, dpF(6f), dpF(6f), ocrBoxPaint)

                    if (isSwiping) {
                        canvas.drawLine(listArea.centerX(), listArea.bottom - dpF(15f), listArea.centerX(), listArea.top + dpF(15f), swipeTrajectoryPaint)
                    }
                }

                // 6. Аккуратная узнаваемая указательная кибер-кисть (Pointing Cybernetic Hand)
                drawPointingCyberGlove(canvas, targetX, targetY, isTapping, isScanning)

                // 7. Лазерный радар сканирования
                if (isScanning) {
                    laserScanPaint.shader = RadialGradient(
                        targetX, targetY, dpF(75f),
                        intArrayOf("#5000F0FF".toColorInt(), "#1000F0FF".toColorInt(), Color.TRANSPARENT),
                        floatArrayOf(0f, 0.6f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(targetX, targetY, dpF(70f), laserScanPaint)

                    val scanAngle = (now % 1000) / 1000f * (2 * PI.toFloat())
                    val scanLineX = targetX + cos(scanAngle.toDouble()).toFloat() * dpF(60f)
                    val scanLineY = targetY + sin(scanAngle.toDouble()).toFloat() * dpF(60f)
                    canvas.drawLine(targetX, targetY, scanLineX, scanLineY, laserLinePaint)
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

                // 9. Высококонтрастная плашка подсказок внизу экрана
                val bannerH = dpF(46f)
                val bannerY = h - bannerH - dpF(12f)
                reusableRectF.set(dpF(10f), bannerY, w - dpF(10f), bannerY + bannerH)

                generalItemBg.color = "#F50F172A".toColorInt()
                canvas.drawRoundRect(reusableRectF, dpF(10f), dpF(10f), generalItemBg)
                panelBorderPaint.color = "#8B5CF6".toColorInt()
                canvas.drawRoundRect(reusableRectF, dpF(10f), dpF(10f), panelBorderPaint)

                // Текстовая плашка фазы
                canvas.drawText(stageTagText, dpF(18f), bannerY + dpF(17f), stageTagPaint)
                // Основная подсказка крупным жирным шрифтом 13.5dp
                canvas.drawText(statusText, dpF(18f), bannerY + dpF(36f), bannerTextPaint)
            }

            /**
             * Узнаваемая аккуратная указательная перчатка/кисть с ровной фалангой
             */
            private fun drawPointingCyberGlove(
                canvas: Canvas,
                tx: Float,
                ty: Float,
                isTapping: Boolean,
                isScanning: Boolean
            ) {
                val handAngle = -PI.toFloat() / 3.8f // Легкий узнаваемый наклон кисти (35 градусов)
                val handLen = dpF(42f)
                val palmX = tx - cos(handAngle.toDouble()).toFloat() * handLen
                val palmY = ty - sin(handAngle.toDouble()).toFloat() * handLen

                val perp = handAngle + (PI / 2f).toFloat()
                val palmW = dpF(22f)

                // Корпус ладони
                reusablePath.rewind()
                reusablePath.moveTo(palmX + cos(perp.toDouble()).toFloat() * (palmW / 2f), palmY + sin(perp.toDouble()).toFloat() * (palmW / 2f))
                reusablePath.lineTo(palmX - cos(perp.toDouble()).toFloat() * (palmW / 2f), palmY - sin(perp.toDouble()).toFloat() * (palmW / 2f))
                reusablePath.lineTo(
                    palmX + cos(handAngle.toDouble()).toFloat() * dpF(18f) - cos(perp.toDouble()).toFloat() * (palmW * 0.4f),
                    palmY + sin(handAngle.toDouble()).toFloat() * dpF(18f) - sin(perp.toDouble()).toFloat() * (palmW * 0.4f)
                )
                reusablePath.lineTo(
                    palmX + cos(handAngle.toDouble()).toFloat() * dpF(18f) + cos(perp.toDouble()).toFloat() * (palmW * 0.4f),
                    palmY + sin(handAngle.toDouble()).toFloat() * dpF(18f) + sin(perp.toDouble()).toFloat() * (palmW * 0.4f)
                )
                reusablePath.close()

                handChassisPaint.shader = LinearGradient(
                    palmX, palmY, tx, ty,
                    intArrayOf("#334155".toColorInt(), "#0F172A".toColorInt(), "#1E293B".toColorInt()), null, Shader.TileMode.CLAMP
                )
                canvas.drawPath(reusablePath, handChassisPaint)
                canvas.drawPath(reusablePath, handBorderPaint)

                // Суставы согнутых пальцев кулака
                val k1X = palmX + cos(handAngle.toDouble()).toFloat() * dpF(10f) - cos(perp.toDouble()).toFloat() * dpF(7f)
                val k1Y = palmY + sin(handAngle.toDouble()).toFloat() * dpF(10f) - sin(perp.toDouble()).toFloat() * dpF(7f)
                canvas.drawCircle(k1X, k1Y, dpF(4f), fingerJointPaint)

                val k2X = palmX + cos(handAngle.toDouble()).toFloat() * dpF(6f) - cos(perp.toDouble()).toFloat() * dpF(10f)
                val k2Y = palmY + sin(handAngle.toDouble()).toFloat() * dpF(6f) - sin(perp.toDouble()).toFloat() * dpF(10f)
                canvas.drawCircle(k2X, k2Y, dpF(3.5f), fingerJointPaint)

                // Указательный палец
                val fMidX = (palmX + tx) / 2f
                val fMidY = (palmY + ty) / 2f

                // Фаланга 1
                drawSegment(canvas, palmX, palmY, fMidX, fMidY, dpF(7f), dpF(5.5f))
                canvas.drawCircle(fMidX, fMidY, dpF(4f), fingerJointPaint)

                // Фаланга 2 (указывающая точно в кончик касания)
                drawSegment(canvas, fMidX, fMidY, tx, ty, dpF(5.5f), dpF(4f))

                // Светящийся наконечник стилуса
                val tipColor = if (isTapping || isScanning) "#00F0FF".toColorInt() else "#10B981".toColorInt()
                tapCorePaint.color = tipColor
                canvas.drawCircle(tx, ty, dpF(6f), tapCorePaint)
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
            text = "🤖 ДЕМО AUTOTAP"
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
            text = "✕"
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

        val chipTitles = listOf("⚡ ПОЛНЫЙ ЦИКЛ", "1. КАДРИРОВАНИЕ & ROI", "2. ДИАЛОГ ШАБЛОНА", "3. ДИАЛОГ OCR", "4. КЛИКИ НАГРАД", "5. ИСПОЛНЕНИЕ")
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
