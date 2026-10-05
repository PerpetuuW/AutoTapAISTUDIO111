package com.example.autotap.infrastructure.overlay.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
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

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class OcrCandidatePickerDialog(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val candidates: List<OcrMatchResult>,
    private val onRescanRequested: (() -> Unit)? = null,
    private val onCandidateSelected: (index: Int, selectedMatch: OcrMatchResult) -> Unit
) {

    private var rootFrameLayout: View? = null
    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    fun show() {
        if (rootFrameLayout != null || candidates.isEmpty()) return

        val root = FrameLayout(context).apply {
            setBackgroundColor("#66000000".toColorInt())
        }
        rootFrameLayout = root

        // Canvas для рисования подсвеченных рамок и номеров вариантов на экране
        val highlightView = object : View(context) {
            private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2.5f)
                color = "#38BDF8".toColorInt()
            }
            private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#0F172A".toColorInt()
            }
            private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.5f)
                color = "#38BDF8".toColorInt()
            }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dpF(10f)
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            private val badgeRect = RectF()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                candidates.forEachIndexed { idx, match ->
                    val r = Rect(match.rectLeft, match.rectTop, match.rectRight, match.rectBottom)
                    canvas.drawRect(r, boxPaint)

                    val badgeText = "#${idx + 1}"
                    val bw = dpF(24f)
                    val bh = dpF(18f)
                    val bx = (r.left.toFloat()).coerceIn(dpF(4f), width - bw - dpF(4f))
                    val by = (r.top - bh - dpF(2f)).coerceIn(dpF(4f), height - bh - dpF(4f))

                    badgeRect.set(bx, by, bx + bw, by + bh)
                    canvas.drawRoundRect(badgeRect, dpF(4f), dpF(4f), badgeBgPaint)
                    canvas.drawRoundRect(badgeRect, dpF(4f), dpF(4f), badgeBorderPaint)
                    canvas.drawText(badgeText, badgeRect.centerX(), badgeRect.centerY() + dpF(3.5f), textPaint)
                }
            }
        }
        root.addView(highlightView, FrameLayout.LayoutParams(-1, -1))

        val cardW = dp(340).coerceAtMost((dm.widthPixels * 0.94f).toInt())
        val maxListH = (dm.heightPixels * 0.45f).toInt().coerceAtLeast(dp(160))

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
            val p = dp(12)
            setPadding(p, p, p, p)
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        val tvTitle = TextView(context).apply {
            text = "ВАРИАНТЫ ТЕКСТА (${candidates.size})"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#38BDF8".toColorInt())
        }
        headerRow.addView(tvTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (onRescanRequested != null) {
            val btnRescan = Button(context).apply {
                text = "🔄 ПЕРЕСКАНИРОВАТЬ"
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
        headerRow.addView(btnClose, LinearLayout.LayoutParams(dp(28), dp(28)))
        card.addView(headerRow)

        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        candidates.forEachIndexed { idx, match ->
            val itemRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor("#161B22".toColorInt())
                    cornerRadius = dpF(8f)
                    setStroke(dp(1), "#21262D".toColorInt())
                }
                val p = dp(8)
                setPadding(p, p, p, p)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(6)
                }
            }

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
                text = "Центр клика: (${match.clickX}, ${match.clickY}) px"
                textSize = 7.5f
                includeFontPadding = false
                setTextColor("#94A3B8".toColorInt())
            }
            textCol.addView(tvMatched)
            textCol.addView(tvCoords)
            itemRow.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

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
                    dismiss()
                    onCandidateSelected(idx, match)
                }
            }
            itemRow.addView(btnPick, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)))
            listLayout.addView(itemRow)
        }

        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(listLayout)
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

        val cardLp = FrameLayout.LayoutParams(cardW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(24)
        }
        root.addView(card, cardLp)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowManager.getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        overlayWindowManager.addViewSafe(root, params)
    }

    fun dismiss() {
        rootFrameLayout?.let {
            overlayWindowManager.removeViewSafe(it)
            rootFrameLayout = null
        }
    }
}
