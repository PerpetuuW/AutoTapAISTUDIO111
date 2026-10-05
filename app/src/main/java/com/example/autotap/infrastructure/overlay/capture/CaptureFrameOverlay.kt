package com.example.autotap.infrastructure.overlay.capture


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.util.DisplayMetrics
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
import androidx.core.graphics.toColorInt
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import kotlin.math.hypot

@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class CaptureFrameOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val overrideScreenshot: android.graphics.Bitmap? = null,
    private val onCaptureTriggered: (cropX: Int, cropY: Int, cropW: Int, cropH: Int, isCircle: Boolean) -> Unit,
    private val onCancelled: () -> Unit
) {

    private var rootView: FrameLayout? = null
    private var isCircle = true
    private var cropRect = Rect()
    var designatedRoi: Rect? = null
    var onRoiDesignated: ((Rect) -> Unit)? = null
    private var touchTrackX = -1f
    private var touchTrackY = -1f
    private var isTouching = false

    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density


    fun show() {
        AppLogger.log(context, "CAPTURE", "Открыт экран точного позиционирования шаблона")
        // [V23.0] Физические аппаратные размеры экрана без скрытия навигационной панели
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val realMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(realMetrics)
        val screenW = if (realMetrics.widthPixels > 0) realMetrics.widthPixels else dm.widthPixels
        val screenH = if (realMetrics.heightPixels > 0) realMetrics.heightPixels else dm.heightPixels

        val cx = screenW / 2
        val cy = screenH / 2
        val initialHalfW = 20
        val initialHalfH = 20
        cropRect.set(cx - initialHalfW, cy - initialHalfH, cx + initialHalfW, cy + initialHalfH)

        val root = object : FrameLayout(context) {
            init {
                if (overrideScreenshot != null) {
                    this.background = android.graphics.drawable.BitmapDrawable(context.resources, overrideScreenshot)
                }
            }

            private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2.5f)
                color = "#38BDF8".toColorInt()
            }
            private val handleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = "#2563EB".toColorInt()
            }
            private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.5f)
                color = "#38BDF8".toColorInt()
            }
            private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.5f)
                color = "#38BDF8".toColorInt()
                pathEffect = DashPathEffect(floatArrayOf(dpF(4f), dpF(3f)), 0f)
            }
            private val dimPaint = Paint().apply {
                style = Paint.Style.FILL
                color = "#77000000".toColorInt()
            }
            private val laserPointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(1.5f)
                color = "#58A6FF".toColorInt()
                pathEffect = DashPathEffect(floatArrayOf(dpF(6f), dpF(4f)), 0f)
            }
            private val touchFeedbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                color = "#38BDF8".toColorInt()
            }

            private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#F00B0F17".toColorInt()
                style = Paint.Style.FILL
            }
            private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#30363D".toColorInt()
                style = Paint.Style.STROKE
                strokeWidth = dpF(1f)
            }

            private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#38BDF8".toColorInt()
                textSize = dpF(9.5f)
                typeface = Typeface.MONOSPACE
                textAlign = Paint.Align.CENTER
            }

            private val dimHolePath = Path()
            private val handleBounds = RectF()
            private val preallocatedCropRectF = RectF()
            private val badgeRectF = RectF()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)

                preallocatedCropRectF.set(cropRect.left.toFloat(), cropRect.top.toFloat(), cropRect.right.toFloat(), cropRect.bottom.toFloat())

                dimHolePath.rewind()
                dimHolePath.fillType = Path.FillType.EVEN_ODD
                dimHolePath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)

                val cX = cropRect.centerX().toFloat()
                val cY = cropRect.centerY().toFloat()

                if (isCircle) {
                    dimHolePath.addOval(preallocatedCropRectF, Path.Direction.CW)
                } else {
                    dimHolePath.addRect(preallocatedCropRectF, Path.Direction.CW)
                }
                canvas.drawPath(dimHolePath, dimPaint)

                if (isCircle) {
                    canvas.drawOval(preallocatedCropRectF, borderPaint)
                } else {
                    canvas.drawRect(preallocatedCropRectF, borderPaint)
                }

                val dimText = "${cropRect.width()} x ${cropRect.height()} px"
                val textW = badgeTextPaint.measureText(dimText)
                val badgeW = textW + dpF(14f)
                val badgeH = dpF(18f)
                val badgeCenterX = cX.coerceIn(badgeW / 2f + dpF(8f), width - badgeW / 2f - dpF(8f))

                val badgeTop = if (cropRect.top.toFloat() >= badgeH + dpF(10f)) {
                    cropRect.top.toFloat() - badgeH - dpF(6f)
                } else {
                    cropRect.bottom.toFloat() + dpF(8f)
                }

                badgeRectF.set(badgeCenterX - badgeW / 2f, badgeTop, badgeCenterX + badgeW / 2f, badgeTop + badgeH)
                canvas.drawRoundRect(badgeRectF, dpF(5f), dpF(5f), badgeBgPaint)
                canvas.drawRoundRect(badgeRectF, dpF(5f), dpF(5f), badgeBorderPaint)
                canvas.drawText(dimText, badgeCenterX, badgeTop + badgeH / 2f + dpF(3.5f), badgeTextPaint)

                if (isTouching && touchTrackX >= 0 && touchTrackY >= 0) {
                    canvas.drawLine(touchTrackX, touchTrackY, cX, cY, laserPointerPaint)
                    canvas.drawCircle(touchTrackX, touchTrackY, dpF(14f), touchFeedbackPaint)
                }

                val handleSz = dpF(28f)
                val rawHx = cropRect.right.toFloat() + dpF(18f)
                val rawHy = cropRect.bottom.toFloat() + dpF(18f)

                val hx = rawHx.coerceIn(dpF(16f), screenW - dpF(16f))
                val hy = rawHy.coerceIn(dpF(16f), screenH - dpF(16f))

                canvas.drawLine(cropRect.right.toFloat(), cropRect.bottom.toFloat(), hx, hy, connectorPaint)
                canvas.drawCircle(hx, hy, handleSz / 2f, handleBgPaint)
                canvas.drawCircle(hx, hy, handleSz / 2f, handleStrokePaint)

                handleBounds.set(hx - handleSz / 2f + dpF(5f), hy - handleSz / 2f + dpF(5f), hx + handleSz / 2f - dpF(5f), hy + handleSz / 2f - dpF(5f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.RESIZE, handleBounds, Color.WHITE, dpF(2f))
            }
        }
        root.setWillNotDraw(false)
        rootView = root

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8161B22".toColorInt(), "#F80D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(14f)
                setStroke(dp(1), "#58A6FF".toColorInt())
            }
            val p = dp(5)
            setPadding(p, p, p, p)
            elevation = dpF(16f)
            setOnTouchListener { _, _ -> true }
        }

        val btnSnap = createIconButton(VectorIconDrawer.IconType.CAPTURE, "#162032", "#38BDF8", dp(38), "SNAP") {
            root.visibility = View.GONE
            val capturedCrop = Rect(cropRect)
            val isCirc = isCircle
            // Компенсация системных отступов (Статус-бар / Navigation Bar)
            val location = IntArray(2)
            root.getLocationOnScreen(location)
            val absoluteX = capturedCrop.left + location[0]
            val absoluteY = capturedCrop.top + location[1]
            dismiss()
            onCaptureTriggered(absoluteX, absoluteY, capturedCrop.width(), capturedCrop.height(), isCirc)
        }.apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(android.graphics.Color.parseColor("#162032"), android.graphics.Color.parseColor("#0F172A"))
            ).apply {
                cornerRadius = dpF(8f)
                setStroke(dp(1), android.graphics.Color.parseColor("#38BDF8"))
            }
        }
        controls.addView(btnSnap, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(4) })

        // Векторная пиктограмма рамки видоискателя ROI [ ] без центральной точки фокуса
        var isRoiActive = false
        val btnRoi = object : View(context) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val strokeCol = if (isRoiActive) Color.parseColor("#10B981") else Color.parseColor("#38BDF8")
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = strokeCol
                    strokeWidth = dpF(2f)
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                val pad = dpF(9f)
                val corner = dpF(6f)
                val w = width.toFloat()
                val h = height.toFloat()

                // 4 угловые рамки видоискателя без точки в центре
                canvas.drawLine(pad, pad + corner, pad, pad, p)
                canvas.drawLine(pad, pad, pad + corner, pad, p)
                canvas.drawLine(w - pad - corner, pad, w - pad, pad, p)
                canvas.drawLine(w - pad, pad, w - pad, pad + corner, p)
                canvas.drawLine(pad, h - pad - corner, pad, h - pad, p)
                canvas.drawLine(pad, h - pad, pad + corner, h - pad, p)
                canvas.drawLine(w - pad - corner, h - pad, w - pad, h - pad, p)
                canvas.drawLine(w - pad, h - pad - corner, w - pad, h - pad, p)
            }
        }.apply {
            tag = "ROI"
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#21262D"), Color.parseColor("#161B22"))
            ).apply {
                cornerRadius = dpF(8f)
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(4) }
            elevation = dpF(4f)
            setOnClickListener {
                val location = IntArray(2)
                root.getLocationOnScreen(location)
                val absX = cropRect.left + location[0]
                val absY = cropRect.top + location[1]
                val currentFrameRoi = Rect(absX, absY, absX + cropRect.width(), absY + cropRect.height())

                root.visibility = View.GONE
                RoiSelectorOverlay(
                    context = context,
                    overlayWindowManager = overlayWindowManager,
                    initialRect = designatedRoi ?: currentFrameRoi,
                    onRoiConfirmed = { confirmedRoi ->
                        root.visibility = View.VISIBLE
                        designatedRoi = confirmedRoi
                        isRoiActive = confirmedRoi != null
                        if (isRoiActive) {
                            background = GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM,
                                intArrayOf(Color.parseColor("#064E3B"), Color.parseColor("#065F46"))
                            ).apply {
                                cornerRadius = dpF(8f)
                                setStroke(dp(1), Color.parseColor("#10B981"))
                            }
                            android.widget.Toast.makeText(context, "Область поиска (ROI) сохранена: ${confirmedRoi!!.width()}x${confirmedRoi.height()} px", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            background = GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM,
                                intArrayOf(Color.parseColor("#21262D"), Color.parseColor("#161B22"))
                            ).apply {
                                cornerRadius = dpF(8f)
                                setStroke(dp(1), Color.parseColor("#38BDF8"))
                            }
                        }
                        invalidate()
                    },
                    onCancelled = {
                        root.visibility = View.VISIBLE
                    }
                ).show()
            }
            setOnLongClickListener {
                val location = IntArray(2)
                root.getLocationOnScreen(location)
                val absX = cropRect.left + location[0]
                val absY = cropRect.top + location[1]
                designatedRoi = Rect(absX, absY, absX + cropRect.width(), absY + cropRect.height())
                isRoiActive = true
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.parseColor("#064E3B"), Color.parseColor("#065F46"))
                ).apply {
                    cornerRadius = dpF(8f)
                    setStroke(dp(1), Color.parseColor("#10B981"))
                }
                invalidate()
                android.widget.Toast.makeText(context, "Рамка зафиксирована как ROI: ${cropRect.width()}x${cropRect.height()} px", android.widget.Toast.LENGTH_SHORT).show()
                true
            }
        }
        controls.addView(btnRoi)

        val btnToggleShape = object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(8f), dpF(8f), width - dpF(8f), height - dpF(8f))
                val type = if (isCircle) VectorIconDrawer.IconType.SHAPE_CIRCLE else VectorIconDrawer.IconType.SHAPE_RECT
                VectorIconDrawer.drawIcon(canvas, type, bounds, "#FFB703".toColorInt(), dpF(2f))
            }
        }.apply {
            tag = "SHAPE"
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#21262D".toColorInt(), "#161B22".toColorInt())
            ).apply {
                cornerRadius = dpF(8f)
                setStroke(dp(1), "#FFB703".toColorInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(4) }
            elevation = dpF(4f)
            setOnClickListener {
                isCircle = !isCircle
                invalidate()
                root.invalidate()
            }
        }
        controls.addView(btnToggleShape)


        val btnHelp = createIconButton(VectorIconDrawer.IconType.HELP, "#21262D", "#38BDF8", dp(38), "HELP") {
            InteractiveTutorialOverlay(
                context = context,
                overlayWindowManager = overlayWindowManager,
                mode = InteractiveTutorialOverlay.TutorialMode.CAPTURE,
                hostViewProvider = { rootView }
            ).show()
        }
        controls.addView(btnHelp, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(4) })

        val btnCancel = createIconButton(VectorIconDrawer.IconType.CLOSE, "#21262D", "#F04438", dp(38), "CANCEL") {
            dismiss()
            onCancelled()
        }
        controls.addView(btnCancel, LinearLayout.LayoutParams(dp(38), dp(38)))

        root.addView(controls, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        fun updateFloatingControlsPosition() {
            val ctrlW = dp(184)
            val ctrlH = dp(48)
            val margin = dp(10)
            val safeTop = dp(35)
            val safeBottom = dp(35)



            val posX = (cropRect.centerX() - ctrlW / 2).coerceIn(margin, (screenW - ctrlW - margin).coerceAtLeast(margin))
            // [V31.2] Безопасная дистанция 48dp: исключает наложение панели кнопок на ручку ресайза (которая на +18dp)
            val handleClearance = dp(48)
            val posY = if (cropRect.bottom + handleClearance + ctrlH <= screenH) {
                cropRect.bottom + handleClearance
            } else if (cropRect.top - ctrlH - dp(18) >= 0) {
                cropRect.top - ctrlH - dp(18)
            } else {
                margin
            }

            controls.translationX = posX.toFloat()
            controls.translationY = posY.toFloat()
        }

        updateFloatingControlsPosition()

        var isResizing = false
        var startTouchX = 0f
        var startTouchY = 0f
        val handleRadiusPx = dpF(36f)
        val minCropSize = 16

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

                    val handleCenterX = (cropRect.right.toFloat() + dpF(18f)).coerceIn(dpF(16f), screenW - dpF(16f))
                    val handleCenterY = (cropRect.bottom.toFloat() + dpF(18f)).coerceIn(dpF(16f), screenH - dpF(16f))
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
                        cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minCropSize, screenW)
                        cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropSize, screenH)
                    } else {
                        cropRect.offset(dx, dy)
                        if (cropRect.left < 0) cropRect.offset(-cropRect.left, 0)
                        if (cropRect.top < 0) cropRect.offset(0, -cropRect.top)
                        if (cropRect.right > screenW) cropRect.offset(screenW - cropRect.right, 0)
                        if (cropRect.bottom > screenH) cropRect.offset(0, screenH - cropRect.bottom)
                    }

                    startTouchX = tx
                    startTouchY = ty
                    updateFloatingControlsPosition()
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
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        overlayWindowManager.addViewSafe(root, params)
    }

    fun dismiss() {
        rootView?.let {
            overlayWindowManager.removeViewSafe(it)
            rootView = null
        }
    }

    private fun safeParseColor(hex: String, defaultColor: Int): Int {
        if (hex.isBlank()) return defaultColor
        return try {
            val clean = if (hex.startsWith("#")) hex else "#$hex"
            android.graphics.Color.parseColor(clean)
        } catch (_: Exception) {
            defaultColor
        }
    }

    private fun createIconButton(type: VectorIconDrawer.IconType, bgHex: String, iconHex: String, sizePx: Int, tagStr: String = "", onClick: () -> Unit): View {
        val parsedIconColor = safeParseColor(iconHex, android.graphics.Color.WHITE)
        val parsedBgColor = safeParseColor(bgHex, android.graphics.Color.parseColor("#162032"))
        val parsedStrokeColor = safeParseColor(iconHex, android.graphics.Color.parseColor("#38BDF8"))
        return object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(8f), dpF(8f), width - dpF(8f), height - dpF(8f))
                VectorIconDrawer.drawIcon(canvas, type, bounds, parsedIconColor, dpF(2f))
            }
        }.apply {
            tag = tagStr
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(parsedBgColor, android.graphics.Color.parseColor("#0F172A"))
            ).apply {
                cornerRadius = dpF(8f)
                setStroke(dp(1), parsedStrokeColor)
            }
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            elevation = dpF(4f)
            setOnClickListener { onClick() }
        }
    }
}
