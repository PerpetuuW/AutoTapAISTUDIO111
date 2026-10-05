package com.example.autotap.infrastructure.overlay.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import java.util.Locale

@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class EyedropperOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val screenshot: Bitmap,
    private val onColorPicked: (String) -> Unit,
    private val onCancelled: () -> Unit
) {

    private var overlayView: FrameLayout? = null
    private var loupeX = 0f
    private var loupeY = 0f
    private var selectedHex = "#00F5D4"
    private var colorPreviewSwatch: View? = null

    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    fun show() {
        val root = object : FrameLayout(context) {
            private val gridPaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = "#44FFFFFF".toColorInt()
            }
            private val centerCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2.5f)
                color = "#00F5D4".toColorInt()
            }
            private val pixelPaint = Paint()
            private val cellRect = RectF()
            private val centerRect = RectF()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (screenshot.isRecycled || (loupeX <= 0f && loupeY <= 0f)) return

                val loupeSize = dpF(140f)
                val targetPixelX = loupeX.toInt().coerceIn(0, screenshot.width - 1)
                val targetPixelY = loupeY.toInt().coerceIn(0, screenshot.height - 1)

                val lx = (loupeX - loupeSize / 2f).coerceIn(dpF(10f), width - loupeSize - dpF(10f))
                val ly = if (loupeY >= loupeSize + dpF(70f)) {
                    loupeY - loupeSize - dpF(30f)
                } else {
                    loupeY + dpF(35f)
                }.coerceIn(dpF(10f), height - loupeSize - dpF(10f))

                val gridDim = 9
                val cellSize = loupeSize / gridDim.toFloat()

                for (gy in 0 until gridDim) {
                    for (gx in 0 until gridDim) {
                        val px = (targetPixelX + (gx - gridDim / 2)).coerceIn(0, screenshot.width - 1)
                        val py = (targetPixelY + (gy - gridDim / 2)).coerceIn(0, screenshot.height - 1)
                        if (!screenshot.isRecycled) {
                            val color = screenshot.getPixel(px, py)
                            pixelPaint.color = color
                            cellRect.set(lx + gx * cellSize, ly + gy * cellSize, lx + (gx + 1) * cellSize, ly + (gy + 1) * cellSize)
                            canvas.drawRect(cellRect, pixelPaint)
                            canvas.drawRect(cellRect, gridPaint)
                        }
                    }
                }

                val centerIdx = gridDim / 2
                centerRect.set(lx + centerIdx * cellSize, ly + centerIdx * cellSize, lx + (centerIdx + 1) * cellSize, ly + (centerIdx + 1) * cellSize)
                canvas.drawRect(centerRect, centerCellPaint)
            }
        }
        root.setWillNotDraw(false)
        root.setBackgroundColor("#44000000".toColorInt())
        overlayView = root

        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8161B22".toColorInt(), "#F80D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(14f)
                setStroke(dp(1), "#58A6FF".toColorInt())
            }
            val p = dp(6)
            setPadding(p * 2, p, p, p)
            elevation = dpF(16f)
        }

        val swatch = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(selectedHex.toColorInt())
                setStroke(dp(1), Color.WHITE)
            }
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                marginEnd = dp(6)
            }
        }
        colorPreviewSwatch = swatch
        topBar.addView(swatch)

        val tvHint = TextView(context).apply {
            text = "ПИПЕТКА: Коснитесь пикселя"
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            includeFontPadding = false
        }
        topBar.addView(tvHint, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnClose = createIconButton(VectorIconDrawer.IconType.CLOSE, "#21262D", "#F04438", dp(32)) {
            dismiss()
            onCancelled()
        }
        topBar.addView(btnClose)

        val topBarParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(40)
        }
        root.addView(topBar, topBarParams)

        root.setOnTouchListener { v, event ->
            if (screenshot.isRecycled) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    loupeX = event.rawX
                    loupeY = event.rawY
                    val px = event.rawX.toInt().coerceIn(0, screenshot.width - 1)
                    val py = event.rawY.toInt().coerceIn(0, screenshot.height - 1)
                    val color = screenshot.getPixel(px, py)
                    selectedHex = String.format(Locale.US, "#%06X", 0xFFFFFF and color)
                    tvHint.text = "ЦВЕТ: $selectedHex ($px, $py)"
                    colorPreviewSwatch?.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                        setStroke(dp(1), Color.WHITE)
                    }
                    v.invalidate()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val finalHex = selectedHex
                    dismiss()
                    onColorPicked(finalHex)
                    true
                }
                else -> false
            }
        }

        val params = overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        overlayWindowManager.addViewSafe(root, params)
    }

    fun dismiss() {
        overlayView?.let {
            overlayWindowManager.removeViewSafe(it)
            overlayView = null
            colorPreviewSwatch = null
        }
        if (!screenshot.isRecycled) screenshot.recycle()
    }

    private fun createIconButton(type: VectorIconDrawer.IconType, bgHex: String, iconHex: String, sizePx: Int, onClick: () -> Unit): View {
        return object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(6f), dpF(6f), width - dpF(6f), height - dpF(6f))
                VectorIconDrawer.drawIcon(canvas, type, bounds, iconHex.toColorInt(), dpF(2f))
            }
        }.apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(bgHex.toColorInt(), "#161B22".toColorInt())
            ).apply {
                cornerRadius = dpF(6f)
                setStroke(dp(1), iconHex.toColorInt())
            }
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            setOnClickListener { onClick() }
        }
    }
}
