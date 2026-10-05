package com.example.autotap.infrastructure.visualizer

import android.content.Context
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
        hideTrainingHighlight(overlayWindowManager)
    }

    /**
     * Полноценный оверлей с отображением рамки и HUD-бейджа уверенности (Confidence Score).
     * Адаптивная верстка с защитой от сжатия текста в вертикальные колонки в углах экрана.
     *
     * @param moduleTag Метка модуля: "OCR" или "ШАБЛОН"
     * @param scorePercent Процент уверенности (0..100%)
     * @param detailText Дополнительная информация (распознанный текст или статус совпадения)
     */
    fun showConfidenceHighlight(
        context: Context,
        overlayWindowManager: OverlayWindowManager,
        rect: Rect,
        moduleTag: String = "OCR",
        scorePercent: Int = 100,
        detailText: String = "",
        durationMs: Long = 1800L
    ) {
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

        // Цветовая дифференциация: >=85% Зеленый (Отлично), 70-84% Янтарный (Внимание), <70% Красный (Плохо)
        val (primaryColorHex, bgColorHex) = when {
            scorePercent >= 85 -> Pair("#10B981", "#2510B981")
            scorePercent >= 70 -> Pair("#F59E0B", "#25F59E0B")
            else -> Pair("#EF4444", "#25EF4444")
        }

        val primaryColor = primaryColorHex.toColorInt()
        val bgColor = bgColorHex.toColorInt()

        val rootContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        // 1. Рамка выделения цели с подсветкой
        val boxView = View(context).apply {
            background = GradientDrawable().apply {
                setStroke(dp(2.5f), primaryColor)
                setColor(bgColor)
                cornerRadius = 6f * density
            }
            alpha = 0f
            scaleX = 1.04f
            scaleY = 1.04f
        }
        val boxParams = FrameLayout.LayoutParams(boxW, boxH).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = safeLeft
            topMargin = safeTop
        }
        rootContainer.addView(boxView, boxParams)

        // 2. HUD-бейдж уверенности (Pill Badge) с защитой от вертикального переноса
        val isOcrModule = (moduleTag == "OCR")
        val badgeLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
            background = GradientDrawable().apply {
                setColor("#0F172A".toColorInt())
                setStroke(dp(1.5f), primaryColor)
                cornerRadius = dp(14f).toFloat()
            }
            elevation = dp(6f).toFloat()
        }

        val tvTag = TextView(context).apply {
            text = "[$moduleTag]"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor("#94A3B8".toColorInt())
            setPadding(0, 0, dp(4f), 0)
            isSingleLine = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val tvScore = TextView(context).apply {
            text = "$scorePercent%"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryColor)
            setPadding(0, 0, dp(4f), 0)
            isSingleLine = true
            maxLines = 1
        }
        val tvDetail = TextView(context).apply {
            // Для OCR не выводим сырой текст в оверлей во избежание самораспознавания в зацикленных сценариях
            val textToShow = if (!isOcrModule && detailText.isNotBlank()) "· ${detailText.take(20)}" else ""
            text = textToShow
            textSize = 9.5f
            setTextColor(Color.WHITE)
            isSingleLine = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            visibility = if (textToShow.isNotBlank()) View.VISIBLE else View.GONE
        }

        badgeLayout.addView(tvTag)
        badgeLayout.addView(tvScore)
        if (!isOcrModule && detailText.isNotBlank()) {
            badgeLayout.addView(tvDetail)
        }

        // Пре-измерение бейджа для точного позиционирования без сжатия в углах экрана
        badgeLayout.measure(
            View.MeasureSpec.makeMeasureSpec(screenW - 2 * edgePadding, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
        )
        val badgeW = badgeLayout.measuredWidth
        val badgeH = badgeLayout.measuredHeight

        // Горизонтальное позиционирование (защита от вылета за правый край и сплющивания)
        val badgeLeft = if (safeLeft + badgeW <= screenW - edgePadding) {
            safeLeft.coerceAtLeast(edgePadding)
        } else {
            (screenW - badgeW - edgePadding).coerceAtLeast(edgePadding)
        }

        // Вертикальное позиционирование (если цель у верхнего края - бейдж снизу, иначе сверху)
        val badgeTop = if (safeTop >= badgeH + dp(6f)) {
            safeTop - badgeH - dp(4f)
        } else if (safeBottom + badgeH + dp(6f) <= screenH) {
            safeBottom + dp(4f)
        } else {
            (safeTop + dp(4f)).coerceIn(edgePadding, screenH - badgeH - edgePadding)
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

        overlayWindowManager.addViewSafe(rootContainer, windowParams)
        activeHighlightViews.add(java.lang.ref.WeakReference(rootContainer))

        boxView.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(160)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                mainHandler.postDelayed({
                    if (rootContainer.isAttachedToWindow) {
                        rootContainer.animate().alpha(0f).setDuration(220).withEndAction {
                            overlayWindowManager.removeViewSafe(rootContainer)
                        }.start()
                    } else {
                        overlayWindowManager.removeViewSafe(rootContainer)
                    }
                }, durationMs)
            }
            .start()
    }

    /**
     * Мульти-подсветка нескольких найденных шаблонов со скорингом и ранжированием (#1, #2, #3).
     * Адаптивная верстка бейджей с проверкой границ углов экрана.
     */
    fun showMultiTemplateHighlights(
        context: Context,
        overlayWindowManager: OverlayWindowManager,
        candidates: List<MatchCandidate>,
        requiredThreshold: Int = 85,
        durationMs: Long = 2400L
    ) {
        if (candidates.isEmpty()) return
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

        candidates.take(6).forEachIndexed { idx, cand ->
            val scorePct = (cand.score * 100).toInt().coerceIn(0, 100)
            val isBest = (idx == 0)
            val (primaryColorHex, bgColorHex) = when {
                scorePct >= requiredThreshold -> if (isBest) Pair("#10B981", "#2510B981") else Pair("#38BDF8", "#2038BDF8")
                scorePct >= (requiredThreshold - 10) -> Pair("#F59E0B", "#25F59E0B")
                else -> Pair("#EF4444", "#25EF4444")
            }
            val primaryColor = primaryColorHex.toColorInt()
            val bgColor = bgColorHex.toColorInt()

            val safeLeft = min(cand.rectLeft, cand.rectRight).coerceIn(0, max(0, screenW - dp(16f)))
            val safeTop = min(cand.rectTop, cand.rectBottom).coerceIn(0, max(0, screenH - dp(16f)))
            val safeRight = max(cand.rectLeft, cand.rectRight).coerceIn(safeLeft + dp(16f), screenW)
            val safeBottom = max(cand.rectTop, cand.rectBottom).coerceIn(safeTop + dp(16f), screenH)
            val boxW = safeRight - safeLeft
            val boxH = safeBottom - safeTop

            val boxView = View(context).apply {
                background = GradientDrawable().apply {
                    setStroke(dp(if (isBest) 2.5f else 1.8f), primaryColor)
                    setColor(bgColor)
                    cornerRadius = 6f * density
                }
            }
            val boxParams = FrameLayout.LayoutParams(boxW, boxH).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = safeLeft
                topMargin = safeTop
            }
            rootContainer.addView(boxView, boxParams)

            val badge = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6f), dp(3f), dp(6f), dp(3f))
                background = GradientDrawable().apply {
                    setColor("#0F172A".toColorInt())
                    setStroke(dp(1.2f), primaryColor)
                    cornerRadius = dp(12f).toFloat()
                }
                elevation = dp(4f).toFloat()
            }
            val tv = TextView(context).apply {
                text = if (isBest) "[ШАБЛОН #1] $scorePct%" else "#${idx + 1}: $scorePct%"
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

            val badgeTop = if (safeTop >= bH + dp(6f)) {
                safeTop - bH - dp(4f)
            } else if (safeBottom + bH + dp(6f) <= screenH) {
                safeBottom + dp(4f)
            } else {
                (safeTop + dp(4f)).coerceIn(edgePadding, screenH - bH - edgePadding)
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

        overlayWindowManager.addViewSafe(rootContainer, windowParams)
        activeHighlightViews.add(java.lang.ref.WeakReference(rootContainer))

        rootContainer.alpha = 0f
        rootContainer.animate().alpha(1f).setDuration(160).withEndAction {
            mainHandler.postDelayed({
                if (rootContainer.isAttachedToWindow) {
                    rootContainer.animate().alpha(0f).setDuration(220).withEndAction {
                        overlayWindowManager.removeViewSafe(rootContainer)
                    }.start()
                } else {
                    overlayWindowManager.removeViewSafe(rootContainer)
                }
            }, durationMs)
        }.start()
    }

    fun showSpringHighlight(context: Context, overlayWindowManager: OverlayWindowManager, rect: Rect, durationMs: Long = 1400L) {
        showConfidenceHighlight(
            context = context,
            overlayWindowManager = overlayWindowManager,
            rect = rect,
            moduleTag = "TARGET",
            scorePercent = 100,
            detailText = "",
            durationMs = durationMs
        )
    }

    // [V28.1] Использование слабой ссылки WeakReference для предотвращения Static Field Context Leak
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
