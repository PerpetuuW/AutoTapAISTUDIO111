package com.example.autotap.infrastructure.overlay.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
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
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import kotlin.math.hypot

@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class RoiSelectorOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    initialRect: Rect?,
    private val onRoiConfirmed: (Rect?) -> Unit,
    private val onCancelled: () -> Unit
) {

    private var rootView: FrameLayout? = null
    private val initialProvidedRect = initialRect
    private var roi = Rect()

    private var touchTrackX = -1f
    private var touchTrackY = -1f
    private var isTouching = false

    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    fun show() {
        AppLogger.log(context, "ROI", "Открыт экран выбора области поиска ROI")
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        roi = initialProvidedRect?.let { Rect(it) } ?: Rect(
            screenW / 6,
            screenH / 4,
            screenW * 5 / 6,
            screenH * 3 / 4
        )

        val root = object : FrameLayout(context) {
            private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2.5f)
                color = "#FFB703".toColorInt()
            }
            private val handleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#1F6FEB".toColorInt()
            }
            private val dimPaint = Paint().apply {
                style = Paint.Style.FILL
                color = "#66000000".toColorInt()
            }
            private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.5f)
                color = "#FFB703".toColorInt()
                pathEffect = DashPathEffect(floatArrayOf(dpF(6f), dpF(4f)), 0f)
            }
            private val dimHolePath = Path()
            private val handleBounds = RectF()
            private val preallocatedRoiRectF = RectF()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                dimHolePath.rewind()
                dimHolePath.fillType = Path.FillType.EVEN_ODD
                dimHolePath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
                
                preallocatedRoiRectF.set(roi.left.toFloat(), roi.top.toFloat(), roi.right.toFloat(), roi.bottom.toFloat())
                dimHolePath.addRect(preallocatedRoiRectF, Path.Direction.CW)
                canvas.drawPath(dimHolePath, dimPaint)

                canvas.drawRect(roi, borderPaint)

                if (isTouching && touchTrackX >= 0 && touchTrackY >= 0) {
                    canvas.drawLine(touchTrackX, touchTrackY, roi.centerX().toFloat(), roi.centerY().toFloat(), pointerPaint)
                    canvas.drawCircle(touchTrackX, touchTrackY, dpF(12f), borderPaint)
                }

                val handleSz = dpF(28f)
                val hx = (roi.right.toFloat() + dpF(18f)).coerceIn(dpF(16f), screenW - dpF(16f))
                val hy = (roi.bottom.toFloat() + dpF(18f)).coerceIn(dpF(16f), screenH - dpF(16f))
                canvas.drawCircle(hx, hy, handleSz / 2f, handleBgPaint)
                handleBounds.set(hx - handleSz / 2f + dpF(5f), hy - handleSz / 2f + dpF(5f), hx + handleSz / 2f - dpF(5f), hy + handleSz / 2f - dpF(5f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.RESIZE, handleBounds, Color.WHITE, dpF(2f))
            }
        }
        root.setWillNotDraw(false)
        rootView = root

        val hudLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnTouchListener { _, _ -> true }
        }

        val tvDimensions = TextView(context).apply {
            text = "ROI: ${roi.width()} x ${roi.height()} px"
            setTextColor("#FFB703".toColorInt())
            textSize = 10f
            typeface = Typeface.MONOSPACE
            includeFontPadding = false
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8161B22".toColorInt(), "#F80D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#FFB703".toColorInt())
            }
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
        }
        hudLayout.addView(tvDimensions)

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8161B22".toColorInt(), "#F80D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(16f)
                setStroke(dp(1), "#58A6FF".toColorInt())
            }
            val p = dp(5)
            setPadding(p, p, p, p)
            elevation = dpF(16f)
        }

        val btnOk = createIconButton(VectorIconDrawer.IconType.CHECK, "#10B981", "#38BDF8", dp(36), "OK") {
            dismiss()
            onRoiConfirmed(roi)
        }
        bar.addView(btnOk, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(4) })

        val btnReset = createIconButton(VectorIconDrawer.IconType.SELECT_ALL, "#21262D", "#FFFFFF", dp(36), "RESET") {
            dismiss()
            onRoiConfirmed(null)
        }
        bar.addView(btnReset, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(4) })

        val btnHelp = createIconButton(VectorIconDrawer.IconType.HELP, "#21262D", "#38BDF8", dp(36), "HELP") {
            InteractiveTutorialOverlay(
                context = context,
                overlayWindowManager = overlayWindowManager,
                mode = InteractiveTutorialOverlay.TutorialMode.ROI_SELECTOR,
                hostViewProvider = { rootView }
            ).show()
        }
        bar.addView(btnHelp, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(4) })

        val btnCancel = createIconButton(VectorIconDrawer.IconType.CLOSE, "#21262D", "#F04438", dp(36), "CANCEL") {
            dismiss()
            onCancelled()
        }
        bar.addView(btnCancel, LinearLayout.LayoutParams(dp(36), dp(36)))
        hudLayout.addView(bar)

        root.addView(hudLayout, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        fun updateHudPosition() {
            hudLayout.measure(
                View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
            )
            val hudW = hudLayout.measuredWidth.coerceAtLeast(dp(180))
            val hudH = hudLayout.measuredHeight.coerceAtLeast(dp(70))
            val margin = dp(10)
            val safeTop = dp(30)

            val posX = (roi.centerX() - hudW / 2).coerceIn(margin, screenW - hudW - margin)
            val posY = if (roi.top - hudH - margin >= safeTop) {
                roi.top - hudH - margin
            } else {
                (roi.bottom + dp(15)).coerceAtMost(screenH - hudH - dp(10))
            }

            val hudLp = hudLayout.layoutParams as? FrameLayout.LayoutParams
            hudLp?.leftMargin = posX
            hudLp?.topMargin = posY
            hudLayout.layoutParams = hudLp

            tvDimensions.text = "ROI: ${roi.width()} x ${roi.height()} px"
        }

        updateHudPosition()

        var isResizing = false
        var startTouchX = 0f
        var startTouchY = 0f
        val handleRadiusPx = dpF(36f)
        val minRoiSize = dp(16)

        root.setOnTouchListener { _, event ->
            val tx = event.rawX
            val ty = event.rawY

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startTouchX = tx
                    startTouchY = ty
                    touchTrackX = tx
                    touchTrackY = ty
                    isTouching = true
                    val handleCenterX = (roi.right.toFloat() + dpF(18f)).coerceIn(dpF(16f), screenW - dpF(16f))
                    val handleCenterY = (roi.bottom.toFloat() + dpF(18f)).coerceIn(dpF(16f), screenH - dpF(16f))
                    val distToHandle = hypot((tx - handleCenterX).toDouble(), (ty - handleCenterY).toDouble())
                    isResizing = distToHandle <= handleRadiusPx
                    root.invalidate()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (tx - startTouchX).toInt()
                    val dy = (ty - startTouchY).toInt()

                    touchTrackX = tx
                    touchTrackY = ty

                    if (isResizing) {
                        roi.right = (roi.right + dx).coerceIn(roi.left + minRoiSize, screenW)
                        roi.bottom = (roi.bottom + dy).coerceIn(roi.top + minRoiSize, screenH)
                    } else {
                        roi.offset(dx, dy)
                        if (roi.left < 0) roi.offset(-roi.left, 0)
                        if (roi.top < 0) roi.offset(0, -roi.top)
                        if (roi.right > screenW) roi.offset(screenW - roi.right, 0)
                        if (roi.bottom > screenH) roi.offset(0, screenH - roi.bottom)
                    }

                    startTouchX = tx
                    startTouchY = ty
                    updateHudPosition()
                    root.invalidate()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isResizing = false
                    isTouching = false
                    touchTrackX = -1f
                    touchTrackY = -1f
                    root.invalidate()
                    true
                }
                else -> false
            }
        }

        val params = overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        overlayWindowManager.addViewSafe(root, params)
    }

    fun dismiss() {
        rootView?.let {
            overlayWindowManager.removeViewSafe(it)
            rootView = null
        }
    }

    private fun createIconButton(type: VectorIconDrawer.IconType, bgHex: String, iconHex: String, sizePx: Int, tagStr: String = "", onClick: () -> Unit): View {
        return object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(9f), dpF(9f), width - dpF(9f), height - dpF(9f))
                VectorIconDrawer.drawIcon(canvas, type, bounds, iconHex.toColorInt(), dpF(2.2f))
            }
        }.apply {
            tag = tagStr
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(bgHex.toColorInt(), "#161B22".toColorInt())
            ).apply {
                cornerRadius = dpF(8f)
                setStroke(dp(1), iconHex.toColorInt())
            }
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            elevation = dpF(4f)
            setOnClickListener { onClick() }
        }
    }
}
