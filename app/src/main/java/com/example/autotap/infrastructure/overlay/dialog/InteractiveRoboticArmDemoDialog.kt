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
 * Показывает реальную работу с плавающими меню и оверлеями AutoTap:
 * 1. Главный плавающий пульт AutoTap (ControlPanel).
 * 2. Видоискатель захвата (CaptureFrameOverlay) и интерактивное вычерчивание рамки ROI пальцем/стилусом.
 * 3. Реальный диалог настройки шага (EditActionDialog) с калибровкой формы, маской и порогом 85%.
 * 4. Настройка текстового поиска OCR с распознаванием кнопки 'CLAIM 500x'.
 * 5. Размещение нумерованных бейджей шагов (#1, #2, #3, #4) прямо на экране.
 * 6. Нажатие кнопки ПУСК на пульте и автоматическое исполнение макроса (ИИ-сканирование внутри ROI, наложение градиентного шаблона, тапы и свайп).
 *
 * Кисть манипулятора детально проработана: анатомический кибер-кулак с вытянутым указательным пальцем/стилусом,
 * фалангами, шарнирами, неоновыми световодами и реакторным свечением.
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
    private var playbackSpeed = 0.5f // Базовая комфортная скорость 0.5x
    private var animProgress = 0f
    private var selectedManualStage: Int? = null // null = полный цикл, 0..4 = выбор конкретной стадии

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
            private val captureCrosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2.2f)
                color = "#38BDF8".toColorInt()
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
            private val badgeCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dpF(9f)
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            // --- Paints for Ultra-Detailed Cybernetic Hand & Arm ---
            private val armChassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            private val armBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.6f)
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
            private val fingerChassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            private val fingerJointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#CBD5E1".toColorInt()
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
            private val laserScanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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
            private val generalItemBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

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
                val gameHeader = RectF(gameRect.left, gameRect.top, gameRect.right, gameRect.top + dpF(32f))
                generalItemBg.shader = null
                generalItemBg.color = "#1E293B".toColorInt()
                canvas.drawRoundRect(gameHeader, dpF(12f), dpF(12f), generalItemBg)
                canvas.drawText("⚔️ QUEST REWARDS · ИГРОВАЯ СЦЕНА", gameHeader.left + dpF(14f), gameHeader.centerY() + dpF(4f), textTitlePaint)

                // Игровые элементы на экране:
                // Элемент 1: Сундук и полупрозрачная стрелка ▶ (Цель Шаблона)
                val chestArea = RectF(gameRect.left + dpF(14f), gameHeader.bottom + dpF(14f), gameRect.left + dpF(130f), gameHeader.bottom + dpF(100f))
                generalItemBg.color = "#1A2234".toColorInt()
                canvas.drawRoundRect(chestArea, dpF(8f), dpF(8f), generalItemBg)
                canvas.drawText("СУНДУК x1", chestArea.left + dpF(8f), chestArea.top + dpF(16f), textSubPaint)

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

                // Элемент 2: Кнопка "CLAIM 500x" (Цель OCR)
                val ocrClaimBtn = RectF(gameRect.right - dpF(140f), gameHeader.bottom + dpF(24f), gameRect.right - dpF(14f), gameHeader.bottom + dpF(68f))
                generalItemBg.color = "#064E3B".toColorInt()
                canvas.drawRoundRect(ocrClaimBtn, dpF(6f), dpF(6f), generalItemBg)
                gameScreenBorderPaint.color = "#10B981".toColorInt()
                canvas.drawRoundRect(ocrClaimBtn, dpF(6f), dpF(6f), gameScreenBorderPaint)
                canvas.drawText("CLAIM 500x", ocrClaimBtn.left + dpF(20f), ocrClaimBtn.centerY() + dpF(4f), textTitlePaint)

                // Элемент 3: Список наград (Цель свайпа)
                val listArea = RectF(gameRect.left + dpF(14f), chestArea.bottom + dpF(14f), gameRect.right - dpF(14f), gameRect.bottom - dpF(14f))
                generalItemBg.color = "#131C2E".toColorInt()
                canvas.drawRoundRect(listArea, dpF(8f), dpF(8f), generalItemBg)
                canvas.drawText("СПИСОК НАГРАД (ПРОКРУТКА СВАЙПОМ)", listArea.left + dpF(10f), listArea.top + dpF(16f), textSubPaint)

                for (i in 0..2) {
                    val itemY = listArea.top + dpF(24f) + i * dpF(26f)
                    val itm = RectF(listArea.left + dpF(8f), itemY, listArea.right - dpF(8f), itemY + dpF(22f))
                    generalItemBg.color = "#1E293B".toColorInt()
                    canvas.drawRoundRect(itm, dpF(4f), dpF(4f), generalItemBg)
                    canvas.drawText("Награда #0${i + 1} — Золото +${(i + 1) * 250}", itm.left + dpF(10f), itm.centerY() + dpF(3.5f), textSubPaint)
                }

                // 3. Плавающий реальный пульт AutoTap (ControlPanel внизу экрана)
                val panelRect = RectF(w * 0.06f, h * 0.79f, w * 0.94f, h * 0.88f)
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

                // Кнопка + Клик (Синяя)
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
                val armBaseX = w * 0.92f
                val armBaseY = h * 0.08f

                val currentStage = selectedManualStage ?: when {
                    animProgress < 0.22f -> 0 // 1. Видоискатель и вычерчивание ROI пальцем
                    animProgress < 0.40f -> 1 // 2. Всплывающий диалог настройки Шаблона (#1)
                    animProgress < 0.58f -> 2 // 3. Всплывающий диалог OCR текста (#2)
                    animProgress < 0.72f -> 3 // 4. Добавление шага клика (#3) и свайпа (#4) + Нажатие ПУСК
                    else -> 4                 // 5. Автоматическое исполнение макроса по шагам
                }

                var targetX = 0f
                var targetY = 0f
                var isScanning = false
                var isTapping = false
                var isSwiping = false
                var statusText = ""
                var pointingAngle = 0f

                // Позиции модальных карточек и элементов
                val tplDialogRect = RectF(w * 0.12f, h * 0.28f, w * 0.88f, h * 0.58f)
                val ocrDialogRect = RectF(w * 0.12f, h * 0.28f, w * 0.88f, h * 0.58f)

                when (currentStage) {
                    0 -> {
                        // Роборука нажимает +ШАБЛОН, появляется видоискатель, и палец чертит рамку ROI
                        val p = if (selectedManualStage != null) (now % 3500) / 3500f else (animProgress / 0.22f)
                        if (p < 0.30f) {
                            targetX = pBtnTpl.centerX()
                            targetY = pBtnTpl.centerY()
                            isTapping = p in 0.18f..0.28f
                            statusText = "1.1 ПУЛЬТ: Нажатие [+ ШАБЛОН] для открытия видоискателя"
                        } else {
                            // Прорисовка рамки ROI вокруг сундука
                            val drawP = ((p - 0.30f) / 0.70f).coerceIn(0f, 1f)
                            targetX = chestArea.left + drawP * chestArea.width()
                            targetY = chestArea.top + drawP * chestArea.height()
                            isTapping = true
                            statusText = "1.2 ВИДОИСКАТЕЛЬ: Черчение области поиска ROI вокруг стрелки"
                        }
                    }
                    1 -> {
                        // Всплывает реальный диалог настройки шага EditActionDialog, рука нажимает [СОХРАНИТЬ ШАГ]
                        val p = if (selectedManualStage != null) (now % 3000) / 3000f else ((animProgress - 0.22f) / 0.18f)
                        val btnSaveX = tplDialogRect.centerX()
                        val btnSaveY = tplDialogRect.bottom - dpF(24f)
                        targetX = btnSaveX
                        targetY = btnSaveY
                        isTapping = p > 0.65f
                        statusText = "2. ДИАЛОГ ШАБЛОНА: Выбор маски формы (85%), ROI и сохранение шага #1"
                    }
                    2 -> {
                        // Роборука жмет +OCR на пульте, открывается диалог OCR, рука жмет [СКАНИРОВАТЬ]
                        val p = if (selectedManualStage != null) (now % 3000) / 3000f else ((animProgress - 0.40f) / 0.18f)
                        if (p < 0.35f) {
                            targetX = pBtnOcr.centerX()
                            targetY = pBtnOcr.centerY()
                            isTapping = p in 0.22f..0.32f
                            statusText = "3.1 ПУЛЬТ: Нажатие [+ OCR] для добавления поиска текста"
                        } else {
                            val btnOcrScanX = ocrDialogRect.centerX()
                            val btnOcrScanY = ocrDialogRect.bottom - dpF(24f)
                            targetX = btnOcrScanX
                            targetY = btnOcrScanY
                            isTapping = p > 0.75f
                            statusText = "3.2 ДИАЛОГ OCR: Ввод строки 'CLAIM 500x' и сохранение шага #2"
                        }
                    }
                    3 -> {
                        // Добавление клика (#3), свайпа (#4) и нажатие зеленой кнопки [▶ ПУСК]
                        val p = if (selectedManualStage != null) (now % 3000) / 3000f else ((animProgress - 0.58f) / 0.14f)
                        if (p < 0.33f) {
                            targetX = pBtnClick.centerX()
                            targetY = pBtnClick.centerY()
                            isTapping = p in 0.18f..0.28f
                            statusText = "4.1 ПУЛЬТ: Добавление простого клика (#3)"
                        } else if (p < 0.66f) {
                            targetX = pBtnSwipe.centerX()
                            targetY = pBtnSwipe.centerY()
                            isTapping = p in 0.48f..0.58f
                            statusText = "4.2 ПУЛЬТ: Добавление кинематического свайпа (#4)"
                        } else {
                            targetX = pBtnPlay.centerX()
                            targetY = pBtnPlay.centerY()
                            isTapping = p > 0.85f
                            statusText = "4.3 ЗАПУСК: Нажатие [▶ ПУСК] на пульте управления"
                        }
                    }
                    else -> {
                        // Реальное исполнение макроса: ИИ скан внутри ROI -> клик по стрелке -> клик по OCR -> свайп
                        val p = if (selectedManualStage != null) (now % 4000) / 4000f else ((animProgress - 0.72f) / 0.28f)
                        if (p < 0.35f) {
                            // Тап по стрелке
                            targetX = arrowTarget.centerX()
                            targetY = arrowTarget.centerY()
                            isScanning = p < 0.18f
                            isTapping = p >= 0.18f
                            statusText = "5.1 ШАГ #1: Мгновенный скан ROI, маска формы (98%) и клик по стрелке"
                        } else if (p < 0.70f) {
                            // Клик по кнопке OCR
                            targetX = ocrClaimBtn.centerX()
                            targetY = ocrClaimBtn.centerY()
                            isScanning = p < 0.50f
                            isTapping = p >= 0.50f
                            statusText = "5.2 ШАГ #2: Обнаружение OCR бокса 'CLAIM 500x' (100%) и клик"
                        } else {
                            // Свайп по списку
                            val swipeProg = (p - 0.70f) / 0.30f
                            targetX = listArea.centerX()
                            targetY = (listArea.bottom - dpF(20f)) - swipeProg * dpF(50f)
                            isSwiping = true
                            statusText = "5.3 ШАГИ #3-4: Тап и плавный свайп списка вверх. Макрос завершен!"
                        }
                    }
                }

                // --- 5. Отрисовка интерактивных окон оверлея ---

                // 5.1 Видоискатель и начерченная рамка ROI (Стадия 0 или исполнение)
                if (currentStage == 0) {
                    // Уголки видоискателя вокруг сундука
                    val vW = chestArea.width() + dpF(12f)
                    val vH = chestArea.height() + dpF(12f)
                    val vLeft = chestArea.left - dpF(6f)
                    val vTop = chestArea.top - dpF(6f)
                    val vRight = vLeft + vW
                    val vBottom = vTop + vH
                    val cLen = dpF(12f)

                    // 4 угловые скобки видоискателя
                    canvas.drawLine(vLeft, vTop, vLeft + cLen, vTop, captureCrosshairPaint)
                    canvas.drawLine(vLeft, vTop, vLeft, vTop + cLen, captureCrosshairPaint)
                    canvas.drawLine(vRight, vTop, vRight - cLen, vTop, captureCrosshairPaint)
                    canvas.drawLine(vRight, vTop, vRight, vTop + cLen, captureCrosshairPaint)
                    canvas.drawLine(vLeft, vBottom, vLeft + cLen, vBottom, captureCrosshairPaint)
                    canvas.drawLine(vLeft, vBottom, vLeft, vBottom - cLen, captureCrosshairPaint)
                    canvas.drawLine(vRight, vBottom, vRight - cLen, vBottom, captureCrosshairPaint)
                    canvas.drawLine(vRight, vBottom, vRight, vBottom - cLen, captureCrosshairPaint)

                    // Начерченная пальцем золотая пунктирная рамка ROI
                    val roiBox = RectF(chestArea.left - dpF(2f), chestArea.top - dpF(2f), chestArea.right + dpF(2f), chestArea.bottom + dpF(2f))
                    canvas.drawRoundRect(roiBox, dpF(6f), dpF(6f), roiDashedPaint)
                    canvas.drawText("ROI: 240x160 px", roiBox.left + dpF(4f), roiBox.top - dpF(4f), textSubPaint)
                }

                // 5.2 Реальный диалог настройки шага (EditActionDialog)
                if (currentStage == 1) {
                    dialogBorderPaint.color = "#A78BFA".toColorInt()
                    canvas.drawRoundRect(tplDialogRect, dpF(10f), dpF(10f), dialogBgPaint)
                    canvas.drawRoundRect(tplDialogRect, dpF(10f), dpF(10f), dialogBorderPaint)

                    // Заголовок диалога
                    canvas.drawText("⚙️ НАСТРОЙКА ШАГА #1 (ШАБЛОН)", tplDialogRect.left + dpF(14f), tplDialogRect.top + dpF(20f), textTitlePaint)

                    // Карточка шаблона
                    val thumbCard = RectF(tplDialogRect.left + dpF(14f), tplDialogRect.top + dpF(30f), tplDialogRect.left + dpF(74f), tplDialogRect.top + dpF(90f))
                    generalItemBg.color = "#1E1A33".toColorInt()
                    canvas.drawRoundRect(thumbCard, dpF(6f), dpF(6f), generalItemBg)
                    // Стрелка внутри карточки
                    reusablePath.rewind()
                    reusablePath.moveTo(thumbCard.left + dpF(16f), thumbCard.centerY() - dpF(10f))
                    reusablePath.lineTo(thumbCard.right - dpF(16f), thumbCard.centerY())
                    reusablePath.lineTo(thumbCard.left + dpF(16f), thumbCard.centerY() + dpF(10f))
                    reusablePath.close()
                    generalItemBg.color = "#38BDF8".toColorInt()
                    canvas.drawPath(reusablePath, generalItemBg)

                    // Параметры калибровки
                    canvas.drawText("Детекция: МАСКА ФОРМЫ (ИИ)", thumbCard.right + dpF(10f), thumbCard.top + dpF(18f), textTitlePaint)
                    canvas.drawText("Порог: 85% · Шумоподавление: ВКЛ", thumbCard.right + dpF(10f), thumbCard.top + dpF(34f), textSubPaint)
                    canvas.drawText("Область поиска: ROI ЗАДАНА (0мс)", thumbCard.right + dpF(10f), thumbCard.top + dpF(48f), textSubPaint)

                    // Большая зеленая кнопка [СОХРАНИТЬ ШАГ]
                    val btnSave = RectF(tplDialogRect.left + dpF(14f), tplDialogRect.bottom - dpF(36f), tplDialogRect.right - dpF(14f), tplDialogRect.bottom - dpF(10f))
                    generalItemBg.color = "#059669".toColorInt()
                    canvas.drawRoundRect(btnSave, dpF(6f), dpF(6f), generalItemBg)
                    canvas.drawText("СОХРАНИТЬ ШАГ #1", btnSave.centerX() - dpF(44f), btnSave.centerY() + dpF(4f), textTitlePaint)
                }

                // 5.3 Реальный диалог OCR (EditActionDialog OCR)
                if (currentStage == 2) {
                    dialogBorderPaint.color = "#818CF8".toColorInt()
                    canvas.drawRoundRect(ocrDialogRect, dpF(10f), dpF(10f), dialogBgPaint)
                    canvas.drawRoundRect(ocrDialogRect, dpF(10f), dpF(10f), dialogBorderPaint)

                    canvas.drawText("🔤 НАСТРОЙКА ШАГА #2 (OCR ТЕКСТ)", ocrDialogRect.left + dpF(14f), ocrDialogRect.top + dpF(20f), textTitlePaint)

                    // Поле ввода текста
                    val etField = RectF(ocrDialogRect.left + dpF(14f), ocrDialogRect.top + dpF(34f), ocrDialogRect.right - dpF(14f), ocrDialogRect.top + dpF(66f))
                    generalItemBg.color = "#161B22".toColorInt()
                    canvas.drawRoundRect(etField, dpF(6f), dpF(6f), generalItemBg)
                    dialogBorderPaint.color = "#30363D".toColorInt()
                    canvas.drawRoundRect(etField, dpF(6f), dpF(6f), dialogBorderPaint)
                    canvas.drawText("Искомый текст: 'CLAIM 500x'", etField.left + dpF(10f), etField.centerY() + dpF(4f), textTitlePaint)

                    // Кнопка [СКАНИРОВАТЬ И СОХРАНИТЬ]
                    val btnOcrSave = RectF(ocrDialogRect.left + dpF(14f), ocrDialogRect.bottom - dpF(36f), ocrDialogRect.right - dpF(14f), ocrDialogRect.bottom - dpF(10f))
                    generalItemBg.color = "#4338CA".toColorInt()
                    canvas.drawRoundRect(btnOcrSave, dpF(6f), dpF(6f), generalItemBg)
                    canvas.drawText("СКАН И СОХРАНИТЬ ШАГ #2", btnOcrSave.centerX() - dpF(54f), btnOcrSave.centerY() + dpF(4f), textTitlePaint)
                }

                // 5.4 Плавающие бейджи созданных шагов на экране (#1, #2, #3, #4)
                if (currentStage >= 1) {
                    // Бейдж #1 (Шаблон над стрелкой)
                    val b1X = arrowTarget.left - dpF(4f)
                    val b1Y = arrowTarget.top - dpF(16f)
                    badgeCirclePaint.color = "#A78BFA".toColorInt()
                    canvas.drawCircle(b1X + dpF(8f), b1Y + dpF(8f), dpF(9f), badgeCirclePaint)
                    canvas.drawText("#1", b1X + dpF(8f), b1Y + dpF(11.5f), badgeTextPaint)
                }
                if (currentStage >= 2) {
                    // Бейдж #2 (OCR над кнопкой CLAIM)
                    val b2X = ocrClaimBtn.left - dpF(4f)
                    val b2Y = ocrClaimBtn.top - dpF(16f)
                    badgeCirclePaint.color = "#818CF8".toColorInt()
                    canvas.drawCircle(b2X + dpF(8f), b2Y + dpF(8f), dpF(9f), badgeCirclePaint)
                    canvas.drawText("#2", b2X + dpF(8f), b2Y + dpF(11.5f), badgeTextPaint)
                }
                if (currentStage >= 3) {
                    // Бейдж #3 (Клик) и #4 (Свайп)
                    val b3X = chestArea.centerX()
                    val b3Y = chestArea.centerY()
                    badgeCirclePaint.color = "#38BDF8".toColorInt()
                    canvas.drawCircle(b3X, b3Y, dpF(8f), badgeCirclePaint)
                    canvas.drawText("#3", b3X, b3Y + dpF(3f), badgeTextPaint)

                    val b4X = listArea.centerX()
                    val b4Y = listArea.bottom - dpF(16f)
                    badgeCirclePaint.color = "#F59E0B".toColorInt()
                    canvas.drawCircle(b4X, b4Y, dpF(8f), badgeCirclePaint)
                    canvas.drawText("#4", b4X, b4Y + dpF(3f), badgeTextPaint)
                }

                // 5.5 Во время реального исполнения (Стадия 4): подсветка, скан и оверлеи
                if (currentStage == 4) {
                    // Рамка ROI вокруг сундука
                    val roiBox = RectF(chestArea.left - dpF(2f), chestArea.top - dpF(2f), chestArea.right + dpF(2f), chestArea.bottom + dpF(2f))
                    canvas.drawRoundRect(roiBox, dpF(6f), dpF(6f), roiDashedPaint)

                    // Градиентное наложение шаблона на стрелку
                    highlightFillPaint.shader = LinearGradient(
                        arrowTarget.left, arrowTarget.top, arrowTarget.right, arrowTarget.bottom,
                        intArrayOf("#5038BDF8".toColorInt(), "#2000F0FF".toColorInt()), null, Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(arrowTarget, dpF(4f), dpF(4f), highlightFillPaint)
                    canvas.drawRoundRect(arrowTarget, dpF(4f), dpF(4f), highlightBoxPaint)

                    // OCR бокс вокруг кнопки CLAIM
                    canvas.drawRoundRect(ocrClaimBtn, dpF(6f), dpF(6f), ocrBoxPaint)

                    if (isSwiping) {
                        canvas.drawLine(listArea.centerX(), listArea.bottom - dpF(20f), listArea.centerX(), listArea.top + dpF(20f), swipeTrajectoryPaint)
                    }
                }

                // 6. Отрисовка высокоточной роботизированной руки с анатомической кистью (КАК В ИКОНКЕ)
                val dist = sqrt(((targetX - armBaseX) * (targetX - armBaseX) + (targetY - armBaseY) * (targetY - armBaseY)).toDouble()).toFloat()
                val midX = (armBaseX + targetX) / 2f
                val midY = (armBaseY + targetY) / 2f
                val angle = atan2((targetY - armBaseY).toDouble(), (targetX - armBaseX).toDouble()).toFloat()
                val perpAngle = angle + (PI / 2f).toFloat()
                val bendOffset = dpF(45f).coerceAtMost(dist * 0.28f)
                val elbowX = midX + cos(perpAngle.toDouble()).toFloat() * bendOffset
                val elbowY = midY + sin(perpAngle.toDouble()).toFloat() * bendOffset

                val wristX = targetX + cos((angle + PI).toDouble()).toFloat() * dpF(28f)
                val wristY = targetY + sin((angle + PI).toDouble()).toFloat() * dpF(28f)

                // 6.1 Гидравлический поршень
                val pMidX = (armBaseX + elbowX) / 2f - dpF(8f)
                val pMidY = (armBaseY + elbowY) / 2f - dpF(8f)
                canvas.drawLine(armBaseX, armBaseY, pMidX, pMidY, pistonCylinderPaint)
                canvas.drawLine(pMidX, pMidY, elbowX, elbowY, pistonRodPaint)

                // 6.2 Плечевой сегмент с титановым металлическим градиентом
                armChassisPaint.shader = LinearGradient(
                    armBaseX, armBaseY, elbowX, elbowY,
                    intArrayOf("#475569".toColorInt(), "#0F172A".toColorInt(), "#1E293B".toColorInt()),
                    null, Shader.TileMode.CLAMP
                )
                drawArmSegment(canvas, armBaseX, armBaseY, elbowX, elbowY, dpF(19f), dpF(14f))

                neonConduitPaint.color = if (isScanning) "#EC4899".toColorInt() else "#00F0FF".toColorInt()
                canvas.drawLine(armBaseX, armBaseY, elbowX, elbowY, neonGlowPaint)
                canvas.drawLine(armBaseX, armBaseY, elbowX, elbowY, neonConduitPaint)

                // 6.3 Предплечье
                armChassisPaint.shader = LinearGradient(
                    elbowX, elbowY, wristX, wristY,
                    intArrayOf("#1E293B".toColorInt(), "#0B132B".toColorInt(), "#334155".toColorInt()),
                    null, Shader.TileMode.CLAMP
                )
                drawArmSegment(canvas, elbowX, elbowY, wristX, wristY, dpF(14f), dpF(10f))
                canvas.drawLine(elbowX, elbowY, wristX, wristY, neonGlowPaint)
                canvas.drawLine(elbowX, elbowY, wristX, wristY, neonConduitPaint)

                // 6.4 Серво-шарниры
                // База
                canvas.drawCircle(armBaseX, armBaseY, dpF(18f), jointBasePaint)
                canvas.drawCircle(armBaseX, armBaseY, dpF(18f), jointBevelPaint)
                canvas.drawCircle(armBaseX, armBaseY, dpF(7f), jointCorePaint)

                // Локоть
                canvas.drawCircle(elbowX, elbowY, dpF(14f), jointBasePaint)
                canvas.drawCircle(elbowX, elbowY, dpF(14f), jointBevelPaint)
                canvas.drawCircle(elbowX, elbowY, dpF(5f), jointCorePaint)

                // Запястье
                canvas.drawCircle(wristX, wristY, dpF(11f), jointBasePaint)
                canvas.drawCircle(wristX, wristY, dpF(11f), jointBevelPaint)

                // 6.5 Анатомическая кисть роборуки в режиме указания (Pointing Cybernetic Hand)
                pointingAngle = atan2((targetY - wristY).toDouble(), (targetX - wristX).toDouble()).toFloat()
                drawDetailedPointingHand(canvas, wristX, wristY, targetX, targetY, pointingAngle, isTapping, isScanning)

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

                // 8. Импульсные кольца и тактильная волна на клик
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

                // 9. Нижняя информационная плашка
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

            /**
             * Высокодетализированная кибернетическая кисть руки:
             * ладонь с карбоновой текстурой, закругленный кулак из 3 согнутых пальцев,
             * и анатомически вытянутый указательный палец/стилус с суставами и неоновым ядром.
             */
            private fun drawDetailedPointingHand(
                canvas: Canvas,
                wx: Float,
                wy: Float,
                tx: Float,
                ty: Float,
                angle: Float,
                isTapping: Boolean,
                isScanning: Boolean
            ) {
                val perp = angle + (PI / 2f).toFloat()

                // 1. Корпус кисти (Ладонь)
                val palmW = dpF(16f)
                val palmL = dpF(14f)
                val palmCenterX = wx + cos(angle.toDouble()).toFloat() * (palmL / 2f)
                val palmCenterY = wy + sin(angle.toDouble()).toFloat() * (palmL / 2f)

                reusablePath.rewind()
                reusablePath.moveTo(wx + cos(perp.toDouble()).toFloat() * (palmW / 2f), wy + sin(perp.toDouble()).toFloat() * (palmW / 2f))
                reusablePath.lineTo(wx - cos(perp.toDouble()).toFloat() * (palmW / 2f), wy - sin(perp.toDouble()).toFloat() * (palmW / 2f))
                reusablePath.lineTo(
                    wx + cos(angle.toDouble()).toFloat() * palmL - cos(perp.toDouble()).toFloat() * (palmW * 0.4f),
                    wy + sin(angle.toDouble()).toFloat() * palmL - sin(perp.toDouble()).toFloat() * (palmW * 0.4f)
                )
                reusablePath.lineTo(
                    wx + cos(angle.toDouble()).toFloat() * palmL + cos(perp.toDouble()).toFloat() * (palmW * 0.4f),
                    wy + sin(angle.toDouble()).toFloat() * palmL + sin(perp.toDouble()).toFloat() * (palmW * 0.4f)
                )
                reusablePath.close()

                fingerChassisPaint.shader = LinearGradient(
                    wx, wy, palmCenterX, palmCenterY,
                    intArrayOf("#334155".toColorInt(), "#0F172A".toColorInt(), "#1E293B".toColorInt()), null, Shader.TileMode.CLAMP
                )
                canvas.drawPath(reusablePath, fingerChassisPaint)
                canvas.drawPath(reusablePath, armBorderPaint)

                // 2. Согнутые пальцы кулака (боковые фаланги)
                val knuckle1X = wx + cos(angle.toDouble()).toFloat() * dpF(10f) - cos(perp.toDouble()).toFloat() * dpF(6f)
                val knuckle1Y = wy + sin(angle.toDouble()).toFloat() * dpF(10f) - sin(perp.toDouble()).toFloat() * dpF(6f)
                canvas.drawCircle(knuckle1X, knuckle1Y, dpF(3.5f), fingerJointPaint)

                val knuckle2X = wx + cos(angle.toDouble()).toFloat() * dpF(7f) - cos(perp.toDouble()).toFloat() * dpF(8f)
                val knuckle2Y = wy + sin(angle.toDouble()).toFloat() * dpF(7f) - sin(perp.toDouble()).toFloat() * dpF(8f)
                canvas.drawCircle(knuckle2X, knuckle2Y, dpF(3.2f), fingerJointPaint)

                // 3. Вытянутый указательный палец (3 сегмента с суставами)
                val fBaseX = wx + cos(angle.toDouble()).toFloat() * palmL + cos(perp.toDouble()).toFloat() * dpF(3f)
                val fBaseY = wy + sin(angle.toDouble()).toFloat() * palmL + sin(perp.toDouble()).toFloat() * dpF(3f)

                // Сустав фаланги 1
                val fMidX = (fBaseX + tx) / 2f
                val fMidY = (fBaseY + ty) / 2f

                // Фаланга 1 (Проксимальная)
                drawArmSegment(canvas, fBaseX, fBaseY, fMidX, fMidY, dpF(6.5f), dpF(5f))
                canvas.drawCircle(fMidX, fMidY, dpF(3.5f), fingerJointPaint)

                // Фаланга 2 (Дистальная - указывает прямо в точку клика)
                drawArmSegment(canvas, fMidX, fMidY, tx, ty, dpF(5f), dpF(3.5f))

                // Светящийся наконечник стилуса
                val tipColor = if (isTapping || isScanning) "#00F0FF".toColorInt() else "#10B981".toColorInt()
                jointCorePaint.color = tipColor
                canvas.drawCircle(tx, ty, dpF(5.5f), jointCorePaint)
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

        // Чипы стадий
        val chipScroll = HorizontalScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
        val chipRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val chipTitles = listOf("⚡ ПОЛНЫЙ ЦИКЛ", "1. ВИДОИСКАТЕЛЬ ROI", "2. ДИАЛОГ ШАБЛОНА", "3. ДИАЛОГ OCR", "4. ДОБАВЛЕНИЕ ШАГОВ", "5. ИСПОЛНЕНИЕ")
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
