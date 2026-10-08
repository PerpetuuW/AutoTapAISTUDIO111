package com.example.autotap.infrastructure.visualizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.autotap.domain.model.MatchCandidate
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import kotlin.math.max
import kotlin.math.min

object TargetHighlightVisualizer {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeHighlightViews = java.util.Collections.synchronizedList(mutableListOf<java.lang.ref.WeakReference<View>>())
    private val activeHighlightRects = java.util.Collections.synchronizedList(mutableListOf<Rect>())

    /**
     * Глобальный флаг включения визуального дебага (настраивается в глобальных настройках).
     * Если выключен — рамки, скоринг и оверлеи шаблонов не отображаются.
     */
    @Volatile
    var isVisualDebugEnabled: Boolean = true

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("autotap_prefs", Context.MODE_PRIVATE)
        isVisualDebugEnabled = prefs.getBoolean("PREF_VISUAL_DEBUG", true)
    }

    fun setVisualDebugEnabled(context: Context, enabled: Boolean) {
        isVisualDebugEnabled = enabled
        val prefs = context.getSharedPreferences("autotap_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("PREF_VISUAL_DEBUG", enabled).apply()
    }

    /**
     * Возвращает список текущих активных рамок подсветки на экране.
     */
    fun getActiveHighlightBounds(): List<Rect> {
        synchronized(activeHighlightRects) {
            return ArrayList(activeHighlightRects)
        }
    }

    /**
     * Проверяет, является ли обнаруженный OCR текст или прямоугольник элементом визуализации совпадения.
     * Защищает от ложных срабатываний OCR на бейджи процентов уверенности (например '100%', '85%', 'ΔE'),
     * отладочные HUD-баннеры и графические рамки подсветки.
     */
    fun isMatchVisualization(rect: Rect, text: String): Boolean {
        val clean = text.trim()
        val upper = clean.uppercase(java.util.Locale.ROOT)
        if (clean.matches(Regex("^[0-9]{1,3}%$")) ||
            clean.matches(Regex("""^[0-9]{1,3}\s*ΔE$""")) ||
            clean.matches(Regex("""^[0-9]{1,4}\s*(ms|мс)$""")) ||
            clean == "%" || clean == "ΔE" ||
            upper.contains("НАЙДЕНО") || upper.contains("ОТКЛОНЕНО") ||
            upper.contains("ОТЛАДКА") || upper.contains("ПОРОГ") ||
            upper.contains("ЦЕЛЬ НАЙДЕНА") || upper.contains("НЕ НАЙДЕНО") ||
            upper.contains("MATCH") || upper.contains("REJECT") ||
            upper.contains("DEBUG") || upper.contains("ВХОЖДЕНИЕ") ||
            upper.contains("ЦЕНТР КЛИКА") || upper.contains("ОБУЧЕНИЕ ИИ") ||
            upper.contains("СЖАТИЕ") || upper.contains("МАСШТАБ") || upper.contains("DOWNSCALE")
        ) {
            return true
        }
        synchronized(activeHighlightRects) {
            for (r in activeHighlightRects) {
                // Если область текста полностью или частично внутри или пересекает активную рамку подсветки
                val expanded = Rect(r.left - 24, r.top - 24, r.right + 24, r.bottom + 24)
                if (Rect.intersects(expanded, rect)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Скрывает рамки и бейджи на время снятия скриншота, чтобы они не попадали в кадр для OCR и CV.
     */
    fun setTemporarilyTransparent(transparent: Boolean) {
        val action = Runnable {
            synchronized(activeHighlightViews) {
                for (ref in activeHighlightViews) {
                    ref.get()?.alpha = if (transparent) 0f else 1f
                }
            }
            trainingViewRef?.get()?.alpha = if (transparent) 0f else 1f
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            if (transparent) {
                val latch = java.util.concurrent.CountDownLatch(1)
                mainHandler.post {
                    action.run()
                    latch.countDown()
                }
                try {
                    latch.await(35, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {}
            } else {
                mainHandler.post(action)
            }
        }
    }

    fun hideAllHighlights(overlayWindowManager: OverlayWindowManager) {
        synchronized(activeHighlightViews) {
            val iterator = activeHighlightViews.iterator()
            while (iterator.hasNext()) {
                val ref = iterator.next()
                val v = ref.get()
                if (v != null) {
                    overlayWindowManager.removeViewSafe(v)
                }
                iterator.remove()
            }
        }
        synchronized(activeHighlightRects) {
            activeHighlightRects.clear()
        }
        hideTrainingHighlight(overlayWindowManager)
    }

    fun hasActiveHighlights(): Boolean {
        synchronized(activeHighlightViews) {
            val iterator = activeHighlightViews.iterator()
            while (iterator.hasNext()) {
                val ref = iterator.next()
                if (ref.get() == null) {
                    iterator.remove()
                }
            }
            return activeHighlightViews.isNotEmpty()
        }
    }

    /**
     * Премиальный визуализатор обнаружения цели без лишнего текстового шума.
     * Отображает:
     * 1) Элегантную градиентную рамку и полупрозрачную подсветку зоны
     * 2) Если передан bitmap шаблона — накладывает полупрозрачный шаблон с градиентным свечением поверх экрана
     * 3) Компактный Pill-бейдж только с процентом уверенности "%"
     */
    fun showConfidenceHighlight(
        context: Context,
        overlayWindowManager: OverlayWindowManager,
        rect: Rect,
        moduleTag: String = "OCR",
        scorePercent: Int = 100,
        detailText: String = "",
        durationMs: Long = 1800L,
        templateBitmap: Bitmap? = null
    ) {
        if (!isVisualDebugEnabled) return

        hideAllHighlights(overlayWindowManager)
        val dm = context.resources.displayMetrics
        val density = dm.density
        fun dp(v: Float): Int = (v * density).toInt()

        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val edgePadding = dp(8f)

        // Безопасные координаты рамки внутри экрана (защита от вылета за границы и инверсии)
        val safeLeft = min(rect.left, rect.right).coerceIn(0, max(0, screenW - dp(16f)))
        val safeTop = min(rect.top, rect.bottom).coerceIn(0, max(0, screenH - dp(16f)))
        val safeRight = max(rect.left, rect.right).coerceIn(safeLeft + dp(16f), screenW)
        val safeBottom = max(rect.top, rect.bottom).coerceIn(safeTop + dp(16f), screenH)
        val boxW = safeRight - safeLeft
        val boxH = safeBottom - safeTop

        // Soft inverted neon color palette with ultra-low alpha fill to prevent CV edge detection
        val (primaryColorHex, gradStartHex, gradEndHex) = when {
            scorePercent >= 85 -> Triple("#34D399", "#2034D399", "#0D38BDF8")
            scorePercent >= 70 -> Triple("#FBBF24", "#20FBBF24", "#0DFCD34D")
            else -> Triple("#F87171", "#20F87171", "#0DF87171")
        }

        val primaryColor = primaryColorHex.toColorInt()
        val gradStart = gradStartHex.toColorInt()
        val gradEnd = gradEndHex.toColorInt()

        val rootContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        // 1. Контейнер целевой области с инвертированной пунктирной рамкой и сглаженными овалами/пиллами (защита от срабатывания Canny/Sobel)
        val boxContainer = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(gradStart, gradEnd)
            ).apply {
                setStroke(dp(1.8f), primaryColor, dp(10f).toFloat(), dp(8f).toFloat())
                cornerRadius = kotlin.math.min(24f * density, kotlin.math.min(boxW, boxH) / 2.1f)
            }
            alpha = 0f
            scaleX = 1.05f
            scaleY = 1.05f
        }

        val boxParams = FrameLayout.LayoutParams(boxW, boxH).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = safeLeft
            topMargin = safeTop
        }
        rootContainer.addView(boxContainer, boxParams)

        // 2. Минималистичный бейдж уверенности: строго процент "$scorePercent%" без лишних надписей
        val badgeLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f), dp(3.5f), dp(8f), dp(3.5f))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#EE0F172A".toColorInt(), "#EE1E293B".toColorInt())
            ).apply {
                setStroke(dp(1.2f), primaryColor)
                cornerRadius = dp(12f).toFloat()
            }
            elevation = dp(6f).toFloat()
        }

        val tvScore = TextView(context).apply {
            text = "$scorePercent%"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryColor)
            isSingleLine = true
            maxLines = 1
        }
        badgeLayout.addView(tvScore)

        // Пре-измерение бейджа для точного позиционирования без сжатия в углах экрана
        badgeLayout.measure(
            View.MeasureSpec.makeMeasureSpec(screenW - 2 * edgePadding, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
        )
        val badgeW = badgeLayout.measuredWidth
        val badgeH = badgeLayout.measuredHeight

        // Горизонтальное позиционирование
        val badgeLeft = if (safeLeft + badgeW <= screenW - edgePadding) {
            safeLeft.coerceAtLeast(edgePadding)
        } else {
            (screenW - badgeW - edgePadding).coerceAtLeast(edgePadding)
        }

        // Вертикальное позиционирование (если цель у верхнего края - бейдж снизу, иначе сверху)
        val badgeTop = if (safeTop >= badgeH + dp(5f)) {
            safeTop - badgeH - dp(3f)
        } else if (safeBottom + badgeH + dp(5f) <= screenH) {
            safeBottom + dp(3f)
        } else {
            (safeTop + dp(3f)).coerceIn(edgePadding, screenH - badgeH - edgePadding)
        }

        val badgeParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = badgeLeft
            topMargin = badgeTop
        }
        rootContainer.addView(badgeLayout, badgeParams)

        val windowParams = overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val highlightRect = Rect(safeLeft, safeTop, safeRight, safeBottom)
        synchronized(activeHighlightRects) {
            activeHighlightRects.add(highlightRect)
        }

        overlayWindowManager.addViewSafe(rootContainer, windowParams)
        activeHighlightViews.add(java.lang.ref.WeakReference(rootContainer))

        boxContainer.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(160)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                mainHandler.postDelayed({
                    if (rootContainer.isAttachedToWindow) {
                        rootContainer.animate().alpha(0f).setDuration(220).withEndAction {
                            if (rootContainer.isAttachedToWindow) {
                                overlayWindowManager.removeViewSafe(rootContainer)
                                synchronized(activeHighlightRects) {
                                    activeHighlightRects.remove(highlightRect)
                                }
                            }
                        }.start()
                    }
                }, durationMs)
            }
            .start()
    }

    /**
     * Мульти-подсветка нескольких найденных шаблонов со скорингом.
     */
    fun showMultiTemplateHighlights(
        context: Context,
        overlayWindowManager: OverlayWindowManager,
        candidates: List<MatchCandidate>,
        requiredThreshold: Int = 85,
        durationMs: Long = 2400L,
        templateBitmaps: Map<String, Bitmap> = emptyMap()
    ) {
        if (!isVisualDebugEnabled || candidates.isEmpty()) return
        val dm = context.resources.displayMetrics
        val density = dm.density
        fun dp(v: Float): Int = (v * density).toInt()

        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val edgePadding = dp(8f)

        val rootContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        val addedRects = mutableListOf<Rect>()

        candidates.take(6).forEachIndexed { idx, cand ->
            val scorePct = (cand.score * 100).toInt().coerceIn(0, 100)
            val isBest = (idx == 0)
            val (primaryColorHex, gradStartHex, gradEndHex) = when {
                scorePct >= requiredThreshold -> if (isBest) Triple("#34D399", "#2034D399", "#0D38BDF8") else Triple("#38BDF8", "#2038BDF8", "#0D0284C7")
                scorePct >= (requiredThreshold - 10) -> Triple("#FBBF24", "#20FBBF24", "#0DFCD34D")
                else -> Triple("#F87171", "#20F87171", "#0DF87171")
            }
            val primaryColor = primaryColorHex.toColorInt()
            val gradStart = gradStartHex.toColorInt()
            val gradEnd = gradEndHex.toColorInt()

            val safeLeft = min(cand.rectLeft, cand.rectRight).coerceIn(0, max(0, screenW - dp(16f)))
            val safeTop = min(cand.rectTop, cand.rectBottom).coerceIn(0, max(0, screenH - dp(16f)))
            val safeRight = max(cand.rectLeft, cand.rectRight).coerceIn(safeLeft + dp(16f), screenW)
            val safeBottom = max(cand.rectTop, cand.rectBottom).coerceIn(safeTop + dp(16f), screenH)
            val boxW = safeRight - safeLeft
            val boxH = safeBottom - safeTop

            val boxContainer = FrameLayout(context).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(gradStart, gradEnd)
                ).apply {
                    setStroke(dp(if (isBest) 1.8f else 1.2f), primaryColor, dp(10f).toFloat(), dp(8f).toFloat())
                    cornerRadius = kotlin.math.min(24f * density, kotlin.math.min(boxW, boxH) / 2.1f)
                }
            }

            val boxParams = FrameLayout.LayoutParams(boxW, boxH).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = safeLeft
                topMargin = safeTop
            }
            rootContainer.addView(boxContainer, boxParams)
            addedRects.add(Rect(safeLeft, safeTop, safeRight, safeBottom))

            val badge = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6f), dp(2.5f), dp(6f), dp(2.5f))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf("#EE0F172A".toColorInt(), "#EE1E293B".toColorInt())
                ).apply {
                    setStroke(dp(1.1f), primaryColor)
                    cornerRadius = dp(10f).toFloat()
                }
                elevation = dp(4f).toFloat()
            }
            val tv = TextView(context).apply {
                text = "$scorePct%"
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(primaryColor)
                isSingleLine = true
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            badge.addView(tv)

            badge.measure(
                View.MeasureSpec.makeMeasureSpec(screenW - 2 * edgePadding, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
            )
            val bW = badge.measuredWidth
            val bH = badge.measuredHeight

            val badgeLeft = if (safeLeft + bW <= screenW - edgePadding) {
                safeLeft.coerceAtLeast(edgePadding)
            } else {
                (screenW - bW - edgePadding).coerceAtLeast(edgePadding)
            }

            val badgeTop = if (safeTop >= bH + dp(5f)) {
                safeTop - bH - dp(3f)
            } else if (safeBottom + bH + dp(5f) <= screenH) {
                safeBottom + dp(3f)
            } else {
                (safeTop + dp(3f)).coerceIn(edgePadding, screenH - bH - edgePadding)
            }

            val badgeParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = badgeLeft
                topMargin = badgeTop
            }
            rootContainer.addView(badge, badgeParams)
        }

        val windowParams = overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        synchronized(activeHighlightRects) {
            activeHighlightRects.addAll(addedRects)
        }

        overlayWindowManager.addViewSafe(rootContainer, windowParams)
        activeHighlightViews.add(java.lang.ref.WeakReference(rootContainer))

        rootContainer.alpha = 0f
        rootContainer.animate().alpha(1f).setDuration(160).withEndAction {
            mainHandler.postDelayed({
                if (rootContainer.isAttachedToWindow) {
                    rootContainer.animate().alpha(0f).setDuration(220).withEndAction {
                        if (rootContainer.isAttachedToWindow) {
                            overlayWindowManager.removeViewSafe(rootContainer)
                            synchronized(activeHighlightRects) {
                                activeHighlightRects.removeAll(addedRects)
                            }
                        }
                    }.start()
                }
            }, durationMs)
        }.start()
    }

    /**
     * Интерактивная отладочная визуализация процесса поиска для понимания проблем и ложных срабатываний:
     * 1) Подсветка зоны поиска (ROI)
     * 2) Зеленая рамка для принятого кандидата (MATCH)
     * 3) Янтарные/красные рамки для отклонённых кандидатов с указанием причины (REJECT)
     * 4) Информационный HUD вверху экрана с методом, порогом и временем
     */
    fun showSearchDebugVisualization(
        context: Context,
        overlayWindowManager: OverlayWindowManager,
        roi: Rect? = null,
        bestCandidate: MatchCandidate? = null,
        rejectedCandidates: List<Pair<MatchCandidate, String>> = emptyList(),
        thresholdPct: Int = 80,
        methodName: String = "ГИБРИД + УМНЫЙ ЦВЕТ",
        elapsedMs: Long = 0L,
        durationMs: Long = 1800L
    ) {
        if (!isVisualDebugEnabled) return
        hideAllHighlights(overlayWindowManager)

        val dm = context.resources.displayMetrics
        val density = dm.density
        fun dp(v: Float): Int = (v * density).toInt()

        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        val rootContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        val addedRects = mutableListOf<Rect>()

        // 1. Зона поиска ROI (если задана)
        if (roi != null) {
            val safeLeft = roi.left.coerceIn(0, max(0, screenW - dp(16f)))
            val safeTop = roi.top.coerceIn(0, max(0, screenH - dp(16f)))
            val safeRight = roi.right.coerceIn(safeLeft + dp(16f), screenW)
            val safeBottom = roi.bottom.coerceIn(safeTop + dp(16f), screenH)
            val roiW = safeRight - safeLeft
            val roiH = safeBottom - safeTop

            val roiBox = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setStroke(dp(1.2f), "#38BDF8".toColorInt(), dp(6f).toFloat(), dp(4f).toFloat())
                    setColor("#0838BDF8".toColorInt())
                    cornerRadius = dp(6f).toFloat()
                }
            }
            rootContainer.addView(roiBox, FrameLayout.LayoutParams(roiW, roiH).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = safeLeft
                topMargin = safeTop
            })
            addedRects.add(Rect(safeLeft, safeTop, safeRight, safeBottom))
        }

        // 2. Отклонённые кандидаты (красные / янтарные пунктирные рамки)
        rejectedCandidates.take(3).forEach { (cand, reason) ->
            val safeLeft = cand.rectLeft.coerceIn(0, max(0, screenW - dp(16f)))
            val safeTop = cand.rectTop.coerceIn(0, max(0, screenH - dp(16f)))
            val safeRight = cand.rectRight.coerceIn(safeLeft + dp(16f), screenW)
            val safeBottom = cand.rectBottom.coerceIn(safeTop + dp(16f), screenH)
            val boxW = safeRight - safeLeft
            val boxH = safeBottom - safeTop

            val rejectBox = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setStroke(dp(1.2f), "#F43F5E".toColorInt(), dp(4f).toFloat(), dp(4f).toFloat())
                    setColor("#10F43F5E".toColorInt())
                    cornerRadius = dp(6f).toFloat()
                }
            }
            rootContainer.addView(rejectBox, FrameLayout.LayoutParams(boxW, boxH).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = safeLeft
                topMargin = safeTop
            })

            val rejectBadge = TextView(context).apply {
                val scorePct = (cand.score * 100).toInt()
                text = "ОТКЛОНЕНО $scorePct%: $reason"
                textSize = 8f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor("#FCA5A5".toColorInt())
                background = GradientDrawable().apply {
                    setColor("#EE18181B".toColorInt())
                    setStroke(dp(1f), "#F43F5E".toColorInt())
                    cornerRadius = dp(4f).toFloat()
                }
                setPadding(dp(4f), dp(2f), dp(4f), dp(2f))
            }
            rootContainer.addView(rejectBadge, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = safeLeft
                topMargin = (safeTop - dp(18f)).coerceAtLeast(0)
            })
            addedRects.add(Rect(safeLeft, safeTop, safeRight, safeBottom))
        }

        // 3. Лучший принятый кандидат (зеленая рамка)
        if (bestCandidate != null) {
            val safeLeft = bestCandidate.rectLeft.coerceIn(0, max(0, screenW - dp(16f)))
            val safeTop = bestCandidate.rectTop.coerceIn(0, max(0, screenH - dp(16f)))
            val safeRight = bestCandidate.rectRight.coerceIn(safeLeft + dp(16f), screenW)
            val safeBottom = bestCandidate.rectBottom.coerceIn(safeTop + dp(16f), screenH)
            val boxW = safeRight - safeLeft
            val boxH = safeBottom - safeTop

            val bestBox = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setStroke(dp(2f), "#10B981".toColorInt())
                    setColor("#2010B981".toColorInt())
                    cornerRadius = dp(8f).toFloat()
                }
            }
            rootContainer.addView(bestBox, FrameLayout.LayoutParams(boxW, boxH).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = safeLeft
                topMargin = safeTop
            })

            val matchBadge = TextView(context).apply {
                val scorePct = (bestCandidate.score * 100).toInt()
                text = "НАЙДЕНО $scorePct% (ПОРОГ $thresholdPct%)"
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor("#6EE7B7".toColorInt())
                background = GradientDrawable().apply {
                    setColor("#EE064E3B".toColorInt())
                    setStroke(dp(1f), "#10B981".toColorInt())
                    cornerRadius = dp(4f).toFloat()
                }
                setPadding(dp(6f), dp(2.5f), dp(6f), dp(2.5f))
            }
            rootContainer.addView(matchBadge, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = safeLeft
                topMargin = (safeTop - dp(20f)).coerceAtLeast(0)
            })
            addedRects.add(Rect(safeLeft, safeTop, safeRight, safeBottom))
        }

        // 4. Верхний информационный HUD баннер
        val hudLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor("#EE0F172A".toColorInt())
                setStroke(dp(1f), "#38BDF8".toColorInt())
                cornerRadius = dp(14f).toFloat()
            }
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            elevation = dp(8f).toFloat()
        }
        val tvHud = TextView(context).apply {
            val status = if (bestCandidate != null) "ЦЕЛЬ НАЙДЕНА" else "НЕ НАЙДЕНО"
            text = "ОТЛАДКА: $methodName | $status (${elapsedMs}мс)"
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setTextColor(if (bestCandidate != null) "#10B981".toColorInt() else "#F59E0B".toColorInt())
        }
        hudLayout.addView(tvHud)
        rootContainer.addView(hudLayout, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(24f)
        })

        synchronized(activeHighlightRects) {
            activeHighlightRects.addAll(addedRects)
        }

        val windowParams = overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        overlayWindowManager.addViewSafe(rootContainer, windowParams)
        activeHighlightViews.add(java.lang.ref.WeakReference(rootContainer))

        rootContainer.alpha = 0f
        rootContainer.animate().alpha(1f).setDuration(120).withEndAction {
            mainHandler.postDelayed({
                if (rootContainer.isAttachedToWindow) {
                    rootContainer.animate().alpha(0f).setDuration(180).withEndAction {
                        if (rootContainer.isAttachedToWindow) {
                            overlayWindowManager.removeViewSafe(rootContainer)
                            synchronized(activeHighlightRects) {
                                activeHighlightRects.removeAll(addedRects)
                            }
                        }
                    }.start()
                }
            }, durationMs)
        }.start()
    }

    fun showSpringHighlight(context: Context, overlayWindowManager: OverlayWindowManager, rect: Rect, durationMs: Long = 1400L, templateBitmap: Bitmap? = null) {
        showConfidenceHighlight(
            context = context,
            overlayWindowManager = overlayWindowManager,
            rect = rect,
            scorePercent = 100,
            durationMs = durationMs,
            templateBitmap = templateBitmap
        )
    }

    private var trainingViewRef: java.lang.ref.WeakReference<View>? = null

    fun showTrainingHighlight(context: Context, overlayWindowManager: OverlayWindowManager, rect: Rect) {
        hideTrainingHighlight(overlayWindowManager)
        val dm = context.resources.displayMetrics
        val density = dm.density
        fun dp(v: Float): Int = (v * density).toInt()

        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        val safeLeft = min(rect.left, rect.right).coerceIn(0, max(0, screenW - dp(16f)))
        val safeTop = min(rect.top, rect.bottom).coerceIn(0, max(0, screenH - dp(16f)))
        val safeRight = max(rect.left, rect.right).coerceIn(safeLeft + dp(16f), screenW)
        val safeBottom = max(rect.top, rect.bottom).coerceIn(safeTop + dp(16f), screenH)
        val boxW = safeRight - safeLeft
        val boxH = safeBottom - safeTop

        val v = View(context).apply {
            background = GradientDrawable().apply {
                setStroke(dp(2.5f), Color.parseColor("#FFB703"))
                setColor(Color.parseColor("#40FFB703"))
                cornerRadius = 6f * density
            }
        }
        trainingViewRef = java.lang.ref.WeakReference(v)
        val params = overlayWindowManager.createLayoutParams(
            width = boxW, height = boxH,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = safeLeft
            y = safeTop
        }
        overlayWindowManager.addViewSafe(v, params)
    }

    fun hideTrainingHighlight(overlayWindowManager: OverlayWindowManager) {
        trainingViewRef?.get()?.let { overlayWindowManager.removeViewSafe(it) }
        trainingViewRef = null
    }
}
