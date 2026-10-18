package com.example.autotap.infrastructure.overlay.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.autotap.domain.model.OcrMatchResult
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import kotlin.math.hypot

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class OcrCandidatePickerDialog(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val candidates: List<OcrMatchResult>,
    private val onRescanRequested: (() -> Unit)? = null,
    private val onDismiss: (() -> Unit)? = null,
    private val onCandidateSelected: (index: Int, selectedMatch: OcrMatchResult) -> Unit
) {
    private var isDismissed = false
    private var rootFrameLayout: View? = null
    private var selectedIndex: Int = 0

    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    fun show() {
        if (rootFrameLayout != null || candidates.isEmpty()) return

        val root = FrameLayout(context).apply {
            setBackgroundColor("#55000000".toColorInt())
        }
        rootFrameLayout = root

        var cardRef: LinearLayout? = null
        var floatingBadgeRef: LinearLayout? = null
        var highlightViewRef: View? = null
        val itemRowsList = mutableListOf<LinearLayout>()

        // Свернутый плавающий бейдж
        val floatingBadge = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor("#1E293B".toColorInt())
                cornerRadius = dpF(20f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            elevation = dpF(24f)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            visibility = View.GONE
        }
        floatingBadgeRef = floatingBadge

        val tvBadgeIcon = TextView(context).apply {
            text = "ТАБЛИЦА СОВПАДЕНИЙ (${candidates.size})"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#38BDF8".toColorInt())
        }
        floatingBadge.addView(tvBadgeIcon)

        // Карточка таблицы
        val cardW = dp(350).coerceAtMost((dm.widthPixels * 0.95f).toInt())
        val maxListH = (dm.heightPixels * 0.44f).toInt().coerceAtLeast(dp(170))

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8161B22".toColorInt(), "#F80D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(16f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            elevation = dpF(20f)
            val p = dp(10)
            setPadding(p, p, p, p)
        }
        cardRef = card

        // Интерактивный оверлей поверх экрана для прямого тапа по рамкам
        val highlightView = object : View(context) {
            private val boxPaintNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                color = "#38BDF8".toColorInt()
            }
            private val boxPaintSelected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(3f)
                color = "#10B981".toColorInt()
            }
            private val boxFillNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#1A38BDF8".toColorInt()
            }
            private val boxFillSelected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#4010B981".toColorInt()
            }
            private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#0F172A".toColorInt()
            }
            private val badgeBorderNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.5f)
                color = "#38BDF8".toColorInt()
            }
            private val badgeBorderSelected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                color = "#10B981".toColorInt()
            }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dpF(10f)
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            private val badgeRect = RectF()
            private val boxRectF = RectF()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                candidates.forEachIndexed { idx, match ->
                    val isSel = idx == selectedIndex
                    val l = match.rectLeft.toFloat()
                    val t = match.rectTop.toFloat()
                    val r = match.rectRight.toFloat()
                    val b = match.rectBottom.toFloat()
                    boxRectF.set(l, t, r, b)

                    canvas.drawRoundRect(boxRectF, dpF(4f), dpF(4f), if (isSel) boxFillSelected else boxFillNormal)
                    canvas.drawRoundRect(boxRectF, dpF(4f), dpF(4f), if (isSel) boxPaintSelected else boxPaintNormal)

                    val badgeText = "#${idx + 1}"
                    val bw = dpF(26f)
                    val bh = dpF(18f)
                    val bx = l.coerceIn(dpF(4f), width - bw - dpF(4f))
                    val by = (t - bh - dpF(2f)).coerceIn(dpF(4f), height - bh - dpF(4f))
                    badgeRect.set(bx, by, bx + bw, by + bh)

                    canvas.drawRoundRect(badgeRect, dpF(4f), dpF(4f), badgeBgPaint)
                    canvas.drawRoundRect(badgeRect, dpF(4f), dpF(4f), if (isSel) badgeBorderSelected else badgeBorderNormal)
                    canvas.drawText(badgeText, badgeRect.centerX(), badgeRect.centerY() + dpF(3.5f), textPaint)
                }
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val touchX = event.x
                    val touchY = event.y

                    var bestIdx = -1
                    var minDistance = Float.MAX_VALUE

                    candidates.forEachIndexed { idx, match ->
                        val l = match.rectLeft.toFloat() - dp(14)
                        val t = match.rectTop.toFloat() - dp(14)
                        val r = match.rectRight.toFloat() + dp(14)
                        val b = match.rectBottom.toFloat() + dp(14)
                        val hitBox = RectF(l, t, r, b)
                        if (hitBox.contains(touchX, touchY)) {
                            bestIdx = idx
                            minDistance = 0f
                        } else {
                            val cX = match.clickX.toFloat()
                            val cY = match.clickY.toFloat()
                            val dist = hypot((touchX - cX).toDouble(), (touchY - cY).toDouble()).toFloat()
                            if (dist < minDistance && dist < dpF(90f)) {
                                minDistance = dist
                                bestIdx = idx
                            }
                        }
                    }

                    if (bestIdx != -1) {
                        if (bestIdx == selectedIndex) {
                            // Повторный тап по той же рамке — мгновенное подтверждение выбора
                            onCandidateSelected(bestIdx, candidates[bestIdx])
                            dismiss()
                        } else {
                            applySelection(bestIdx)
                        }
                        return true
                    }
                }
                return false
            }
        }
        highlightViewRef = highlightView
        root.addView(highlightView, FrameLayout.LayoutParams(-1, -1))

        // Заголовок карточки таблицы
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }

        val tvTitle = TextView(context).apply {
            text = "ТАБЛИЦА СОВПАДЕНИЙ (${candidates.size})"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#38BDF8".toColorInt())
        }
        headerRow.addView(tvTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnHide = Button(context).apply {
            text = "СВЕРНУТЬ"
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            minHeight = 0; minimumHeight = 0
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#1E293B".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            setPadding(dp(6), dp(3), dp(6), dp(3))
            setOnClickListener {
                card.visibility = View.GONE
                floatingBadge.visibility = View.VISIBLE
            }
        }
        headerRow.addView(btnHide, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(26)).apply {
            marginEnd = dp(6)
        })

        floatingBadge.setOnClickListener {
            floatingBadge.visibility = View.GONE
            card.visibility = View.VISIBLE
        }

        if (onRescanRequested != null) {
            val btnRescan = Button(context).apply {
                text = "ПЕРЕСКАН"
                textSize = 7.5f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                gravity = Gravity.CENTER
                minHeight = 0; minimumHeight = 0
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor("#1E293B".toColorInt())
                    cornerRadius = dpF(4f)
                    setStroke(dp(1), "#38BDF8".toColorInt())
                }
                setPadding(dp(6), dp(3), dp(6), dp(3))
                setOnClickListener {
                    dismiss()
                    onRescanRequested.invoke()
                }
            }
            headerRow.addView(btnRescan, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(26)).apply {
                marginEnd = dp(6)
            })
        }

        val btnClose = Button(context).apply {
            text = "X"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setTextColor("#94A3B8".toColorInt())
            background = null
            setOnClickListener { dismiss() }
        }
        headerRow.addView(btnClose, LinearLayout.LayoutParams(dp(28), dp(28)))

        // Перетаскивание карточки таблицы
        var dX = 0f
        var dY = 0f
        headerRow.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = card.translationX - event.rawX
                    dY = card.translationY - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    card.translationX = event.rawX + dX
                    card.translationY = event.rawY + dY
                    true
                }
                else -> false
            }
        }
        card.addView(headerRow)

        // Поясняющая подсказка
        val tvHint = TextView(context).apply {
            text = "Тапните рамку на экране или выберите строку в таблице ниже:"
            textSize = 8f
            includeFontPadding = false
            setTextColor("#94A3B8".toColorInt())
            setPadding(dp(4), 0, dp(4), dp(6))
        }
        card.addView(tvHint)

        // Таблица вариантов
        val tableContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun updateTableHighlight() {
            itemRowsList.forEachIndexed { idx, row ->
                val isSel = idx == selectedIndex
                row.background = GradientDrawable().apply {
                    setColor(if (isSel) "#1E3A5F".toColorInt() else "#161B22".toColorInt())
                    cornerRadius = dpF(8f)
                    setStroke(dp(1), (if (isSel) "#10B981" else "#21262D").toColorInt())
                }
            }
            highlightViewRef?.invalidate()
        }

        fun applySelectionInternal(idx: Int) {
            selectedIndex = idx
            updateTableHighlight()
        }

        candidates.forEachIndexed { idx, match ->
            val itemRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = dp(8)
                setPadding(p, p, p, p)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6)
                }
            }
            itemRowsList.add(itemRow)

            val tvBadge = TextView(context).apply {
                text = "#${idx + 1}"
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setTextColor("#38BDF8".toColorInt())
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor("#1E293B".toColorInt())
                    cornerRadius = dpF(4f)
                    setStroke(dp(1), "#38BDF8".toColorInt())
                }
                setPadding(dp(6), dp(3), dp(6), dp(3))
            }
            itemRow.addView(tvBadge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(8)
            })

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            val tvMatched = TextView(context).apply {
                text = "'${match.matchedText}'"
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setTextColor(Color.WHITE)
            }
            val tvCoords = TextView(context).apply {
                text = "Координаты клика: (${match.clickX}, ${match.clickY}) px"
                textSize = 7.5f
                includeFontPadding = false
                setTextColor("#94A3B8".toColorInt())
            }
            textCol.addView(tvMatched)
            textCol.addView(tvCoords)
            itemRow.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            fun chooseCandidate() {
                onCandidateSelected(idx, match)
                dismiss()
            }

            itemRow.setOnClickListener {
                applySelectionInternal(idx)
            }

            val btnPick = Button(context).apply {
                text = "ВЫБРАТЬ"
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                gravity = Gravity.CENTER
                minHeight = 0; minimumHeight = 0
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor("#10B981".toColorInt())
                    cornerRadius = dpF(6f)
                }
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener {
                    chooseCandidate()
                }
            }
            itemRow.addView(btnPick, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)))
            tableContainer.addView(itemRow)
        }

        updateTableHighlight()

        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(tableContainer)
        }

        val scrollContainer = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val limitedSpec = MeasureSpec.makeMeasureSpec(maxListH, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, limitedSpec)
            }
        }.apply {
            addView(scrollView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        card.addView(scrollContainer)

        // Нижняя панель подтверждения активного выбора
        val bottomActionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        val btnConfirm = Button(context).apply {
            text = "ПОДТВЕРДИТЬ ВЫБОР"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#10B981".toColorInt())
                cornerRadius = dpF(8f)
            }
            setOnClickListener {
                if (selectedIndex in candidates.indices) {
                    onCandidateSelected(selectedIndex, candidates[selectedIndex])
                    dismiss()
                }
            }
        }
        bottomActionsRow.addView(btnConfirm, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)))
        card.addView(bottomActionsRow)

        val cardLp = FrameLayout.LayoutParams(cardW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(24)
        }
        root.addView(card, cardLp)

        val badgeLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            bottomMargin = dp(24)
            leftMargin = dp(20)
        }
        root.addView(floatingBadge, badgeLp)

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

    private fun applySelection(index: Int) {
        if (index in candidates.indices) {
            selectedIndex = index
            rootFrameLayout?.invalidate()
        }
    }

    fun dismiss() {
        if (isDismissed) return
        isDismissed = true
        rootFrameLayout?.let {
            overlayWindowManager.removeViewSafe(it)
            rootFrameLayout = null
        }
        onDismiss?.invoke()
    }
}
