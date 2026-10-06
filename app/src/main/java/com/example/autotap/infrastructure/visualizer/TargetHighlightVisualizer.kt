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

        // Цветовая дифференциация: >=85% Изумрудный/Неоновый, 70-84% Янтарный, <70% Коралловый
        val (primaryColorHex, gradStartHex, gradEndHex) = when {
            scorePercent >= 85 -> Triple("#10B981", "#4D10B981", "#1538BDF8")
            scorePercent >= 70 -> Triple("#F59E0B", "#4DF59E0B", "#15FCD34D")
            else -> Triple("#EF4444", "#4DEF4444", "#15F87171")
        }

        val primaryColor = primaryColorHex.toColorInt()
        val gradStart = gradStartHex.toColorInt()
        val gradEnd = gradEndHex.toColorInt()

        val rootContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        // 1. Контейнер целевой области с градиентной подсветкой и неоновой рамкой
        val boxContainer = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(gradStart, gradEnd)
            ).apply {
                setStroke(dp(2.2f), primaryColor)
                cornerRadius = 8f * density
            }
            alpha = 0f
            scaleX = 1.05f
            scaleY = 1.05f
        }

        // 1.1 Если передан шаблон — накладываем его с мягким альфа-блендингом для моментального визуального сравнения
        if (templateBitmap != null && !templateBitmap.isRecycled) {
            val ivTemplate = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageBitmap(templateBitmap)
                alpha = 0.78f
            }
            boxContainer.addView(ivTemplate, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
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

        candidates.take(6).forEachIndexed { idx, cand ->
            val scorePct = (cand.score * 100).toInt().coerceIn(0, 100)
            val isBest = (idx == 0)
            val (primaryColorHex, gradStartHex, gradEndHex) = when {
                scorePct >= requiredThreshold -> if (isBest) Triple("#10B981", "#4D10B981", "#1538BDF8") else Triple("#38BDF8", "#4038BDF8", "#150284C7")
                scorePct >= (requiredThreshold - 10) -> Triple("#F59E0B", "#4DF59E0B", "#15FCD34D")
                else -> Triple("#EF4444", "#4DEF4444", "#15F87171")
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
                    setStroke(dp(if (isBest) 2.2f else 1.6f), primaryColor)
                    cornerRadius = 8f * density
                }
            }

            val tplBmp = templateBitmaps[cand.templatePath]
            if (tplBmp != null && !tplBmp.isRecycled) {
                val iv = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_XY
                    setImageBitmap(tplBmp)
                    alpha = 0.72f
                }
                boxContainer.addView(iv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }

            val boxParams = FrameLayout.LayoutParams(boxW, boxH).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = safeLeft
                topMargin = safeTop
            }
            rootContainer.addView(boxContainer, boxParams)

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

        overlayWindowManager.addViewSafe(rootContainer, windowParams)
        activeHighlightViews.add(java.lang.ref.WeakReference(rootContainer))

        rootContainer.alpha = 0f
        rootContainer.animate().alpha(1f).setDuration(160).withEndAction {
            mainHandler.postDelayed({
                if (rootContainer.isAttachedToWindow) {
                    rootContainer.animate().alpha(0f).setDuration(220).withEndAction {
                        if (rootContainer.isAttachedToWindow) {
                            overlayWindowManager.removeViewSafe(rootContainer)
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
