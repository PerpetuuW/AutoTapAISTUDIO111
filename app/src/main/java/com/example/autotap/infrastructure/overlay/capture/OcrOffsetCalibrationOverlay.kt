package com.example.autotap.infrastructure.overlay.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
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
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import kotlin.math.roundToInt

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class OcrOffsetCalibrationOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val targetText: String,
    private val targetCenterX: Float,
    private val targetCenterY: Float,
    private val initialOffsetX: Float = 0f,
    private val initialOffsetY: Float = 0f,
    private val onOffsetConfigured: (offsetX: Float, offsetY: Float) -> Unit
) {

    private var rootFrameLayout: View? = null
    private var currentDx = initialOffsetX
    private var currentDy = initialOffsetY
    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    fun show() {
        if (rootFrameLayout != null) return

        val root = FrameLayout(context).apply {
            setBackgroundColor("#55000000".toColorInt())
        }
        rootFrameLayout = root

        var tvOffsetDisplay: TextView? = null

        val overlayCanvasView = object : View(context) {
            private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                color = "#38BDF8".toColorInt()
                pathEffect = DashPathEffect(floatArrayOf(dpF(6f), dpF(4f)), 0f)
            }
            private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                color = "#EC4899".toColorInt()
            }
            private val clickTargetBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#EC4899".toColorInt()
            }
            private val clickTargetStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                color = Color.WHITE
            }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dpF(11f)
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = targetCenterX
                val cy = targetCenterY

                // Прямоугольник распознанного текста
                val textW = dpF(120f)
                val textH = dpF(36f)
                val targetRect = RectF(cx - textW / 2f, cy - textH / 2f, cx + textW / 2f, cy + textH / 2f)
                canvas.drawRoundRect(targetRect, dpF(6f), dpF(6f), targetPaint)
                canvas.drawText("Текст: '$targetText'", cx, cy + dpF(4f), textPaint)

                // Итоговая смещенная точка клика
                val clickX = cx + currentDx
                val clickY = cy + currentDy

                // Линия выноса от центра текста к точке клика
                canvas.drawLine(cx, cy, clickX, clickY, linePaint)

                // Точка клика
                canvas.drawCircle(clickX, clickY, dpF(12f), clickTargetBg)
                canvas.drawCircle(clickX, clickY, dpF(12f), clickTargetStroke)
                canvas.drawCircle(clickX, clickY, dpF(3f), clickTargetStroke)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        currentDx = event.x - targetCenterX
                        currentDy = event.y - targetCenterY
                        val signX = if (currentDx >= 0) "+${currentDx.roundToInt()}" else "${currentDx.roundToInt()}"
                        val signY = if (currentDy >= 0) "+${currentDy.roundToInt()}" else "${currentDy.roundToInt()}"
                        tvOffsetDisplay?.text = "ВЫНОС: ΔX = $signX px, ΔY = $signY px"
                        invalidate()
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
        }
        root.addView(overlayCanvasView, FrameLayout.LayoutParams(-1, -1))

        // Карточка управления выносом внизу экрана
        val cardW = dp(330).coerceAtMost((dm.widthPixels * 0.94f).toInt())
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8161B22".toColorInt(), "#F80D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(16f)
                setStroke(dp(1), "#EC4899".toColorInt())
            }
            elevation = dpF(20f)
            val p = dp(12)
            setPadding(p, p, p, p)
        }

        val tvTitle = TextView(context).apply {
            text = "ТОЧНЫЙ ВЫНОС ОФФСЕТА КЛИКА"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#EC4899".toColorInt())
        }
        card.addView(tvTitle)

        val signXInit = if (currentDx >= 0) "+${currentDx.roundToInt()}" else "${currentDx.roundToInt()}"
        val signYInit = if (currentDy >= 0) "+${currentDy.roundToInt()}" else "${currentDy.roundToInt()}"
        val tvSub = TextView(context).apply {
            text = "ВЫНОС: ΔX = $signXInit px, ΔY = $signYInit px"
            textSize = 8.5f
            typeface = Typeface.MONOSPACE
            includeFontPadding = false
            setTextColor("#38BDF8".toColorInt())
            setPadding(0, dp(2), 0, dp(8))
        }
        tvOffsetDisplay = tvSub
        card.addView(tvSub)

        val quickRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        fun createPresetBtn(label: String, dx: Float, dy: Float): Button {
            return Button(context).apply {
                text = label
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
                setPadding(dp(4), dp(2), dp(4), dp(2))
                setOnClickListener {
                    currentDx = dx
                    currentDy = dy
                    val signX = if (currentDx >= 0) "+${currentDx.roundToInt()}" else "${currentDx.roundToInt()}"
                    val signY = if (currentDy >= 0) "+${currentDy.roundToInt()}" else "${currentDy.roundToInt()}"
                    tvOffsetDisplay?.text = "ВЫНОС: ΔX = $signX px, ΔY = $signY px"
                    overlayCanvasView.invalidate()
                }
            }
        }

        quickRow.addView(createPresetBtn("+50 ВПРАВО", 50f, 0f), LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginEnd = dp(3) })
        quickRow.addView(createPresetBtn("+50 ВНИЗ", 0f, 50f), LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginEnd = dp(3) })
        quickRow.addView(createPresetBtn("-50 ВЛЕВО", -50f, 0f), LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginEnd = dp(3) })
        quickRow.addView(createPresetBtn("ЦЕНТР (0)", 0f, 0f), LinearLayout.LayoutParams(0, dp(26), 1f))
        card.addView(quickRow)

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnSave = Button(context).apply {
            text = "СОХРАНИТЬ ОФФСЕТ"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            minHeight = 0; minimumHeight = 0
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#10B981".toColorInt())
                cornerRadius = dpF(6f)
            }
            setOnClickListener {
                dismiss()
                onOffsetConfigured(currentDx, currentDy)
            }
        }
        actionRow.addView(btnSave, LinearLayout.LayoutParams(0, dp(34), 1.5f).apply { marginEnd = dp(4) })

        val btnCancel = Button(context).apply {
            text = "ОТМЕНА"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            minHeight = 0; minimumHeight = 0
            setTextColor("#F04438".toColorInt())
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#F04438".toColorInt())
            }
            setOnClickListener { dismiss() }
        }
        actionRow.addView(btnCancel, LinearLayout.LayoutParams(0, dp(34), 1f))
        card.addView(actionRow)

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
