package com.example.autotap.infrastructure.overlay.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.vision.SmartMaskEngine
import java.util.ArrayDeque
import java.util.Locale

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class MagicWandEditorOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val rawBitmap: Bitmap,
    initialMask: Bitmap,
    private val onApplied: (Bitmap) -> Unit,
    private val onCancelled: () -> Unit
) {

    enum class ToolMode { WAND, ERASER, RESTORE }

    private var rootView: FrameLayout? = null
    private var currentTool = ToolMode.WAND
    private var currentMask = initialMask.copy(Bitmap.Config.ARGB_8888, true)
    private val undoStack = ArrayDeque<Bitmap>()
    private var wandTolerance = 25
    private var brushRadius = 2

    private var zoomFactor = 8f
    private var panX = 0f
    private var panY = 0f

    private var tvToolParamLabel: TextView? = null
    private var sbToolParam: SeekBar? = null

    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    private fun safeReplaceCurrentMask(newMask: Bitmap) {
        if (currentMask != newMask && !currentMask.isRecycled && !undoStack.contains(currentMask)) {
            currentMask.recycle()
        }
        currentMask = newMask
    }

    fun show() {
        val screenW = dm.widthPixels
        val cardW = dp(320).coerceAtMost((screenW * 0.94f).toInt())

        val root = FrameLayout(context).apply {
            setBackgroundColor("#99000000".toColorInt())
        }
        rootView = root

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8161B22".toColorInt(), "#F80D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(16f)
                setStroke(dp(1), "#C084FC".toColorInt())
            }
            val p = dp(10)
            setPadding(p, p, p, p)
            elevation = dpF(24f)
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }
        val tvTitle = TextView(context).apply {
            text = "СТУДИЯ МАСКИ"
            setTextColor("#C084FC".toColorInt())
            textSize = 10f
            typeface = Typeface.MONOSPACE
        }
        header.addView(tvTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val tvZoomStatus = TextView(context).apply {
            text = "8.0x"
            setTextColor("#58A6FF".toColorInt())
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
            setPadding(dp(4), 0, dp(4), 0)
        }

        lateinit var zoomCanvasView: View

        val btnZoomOut = createSmallHeaderBtn("-") {
            if (zoomFactor > 2f) {
                zoomFactor = (zoomFactor - 2f).coerceAtLeast(2f)
                tvZoomStatus.text = String.format(Locale.US, "%.1fx", zoomFactor)
                zoomCanvasView.invalidate()
            }
        }
        header.addView(btnZoomOut)
        header.addView(tvZoomStatus)

        val btnZoomIn = createSmallHeaderBtn("+") {
            if (zoomFactor < 24f) {
                zoomFactor = (zoomFactor + 2f).coerceAtMost(24f)
                tvZoomStatus.text = String.format(Locale.US, "%.1fx", zoomFactor)
                zoomCanvasView.invalidate()
            }
        }
        header.addView(btnZoomIn)

        val btnResetZoom = createSmallHeaderBtn("СБРОС") {
            zoomFactor = 8f
            panX = 0f
            panY = 0f
            tvZoomStatus.text = "8.0x"
            zoomCanvasView.invalidate()
        }
        header.addView(btnResetZoom)
        card.addView(header)

        val zoomView = object : View(context) {
            private val checkerLight = Paint().apply { color = "#281142".toColorInt() }
            private val checkerDark = Paint().apply { color = "#10051C".toColorInt() }
            private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            private val gridLinePaint = Paint().apply {
                color = "#30C084FC".toColorInt()
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            private val srcR = Rect()
            private val dstR = RectF()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val cellSize = dpF(6f)

                var y = 0f
                var row = 0
                while (y < h) {
                    var x = 0f
                    var col = 0
                    while (x < w) {
                        val p = if ((row + col) % 2 == 0) checkerLight else checkerDark
                        canvas.drawRect(x, y, x + cellSize, y + cellSize, p)
                        x += cellSize
                        col++
                    }
                    y += cellSize
                    row++
                }

                srcR.set(0, 0, currentMask.width, currentMask.height)
                val baseScale = minOf((w - dpF(8f)) / currentMask.width, (h - dpF(8f)) / currentMask.height)
                val effectiveScale = baseScale * (zoomFactor / 8f)
                val drawW = currentMask.width * effectiveScale
                val drawH = currentMask.height * effectiveScale

                val dx = (w - drawW) / 2f + panX
                val dy = (h - drawH) / 2f + panY
                dstR.set(dx, dy, dx + drawW, dy + drawH)
                canvas.drawBitmap(currentMask, srcR, dstR, bmpPaint)

                if (effectiveScale >= dpF(4f)) {
                    val pxW = drawW / currentMask.width
                    val pxH = drawH / currentMask.height
                    for (gx in 0..currentMask.width) {
                        val lineX = dx + gx * pxW
                        if (lineX in 0f..w) {
                            canvas.drawLine(lineX, dy.coerceAtLeast(0f), lineX, (dy + drawH).coerceAtMost(h), gridLinePaint)
                        }
                    }
                    for (gy in 0..currentMask.height) {
                        val lineY = dy + gy * pxH
                        if (lineY in 0f..h) {
                            canvas.drawLine(dx.coerceAtLeast(0f), lineY, (dx + drawW).coerceAtMost(w), lineY, gridLinePaint)
                        }
                    }
                }
            }

            fun mapTouchToBitmap(touchX: Float, touchY: Float): Pair<Int, Int>? {
                val baseScale = minOf((width - dpF(8f)) / currentMask.width, (height - dpF(8f)) / currentMask.height)
                val effectiveScale = baseScale * (zoomFactor / 8f)
                val drawW = currentMask.width * effectiveScale
                val drawH = currentMask.height * effectiveScale
                val dx = (width - drawW) / 2f + panX
                val dy = (height - drawH) / 2f + panY

                if (touchX in dx..(dx + drawW) && touchY in dy..(dy + drawH)) {
                    val bx = ((touchX - dx) / effectiveScale).toInt().coerceIn(0, currentMask.width - 1)
                    val by = ((touchY - dy) / effectiveScale).toInt().coerceIn(0, currentMask.height - 1)
                    return Pair(bx, by)
                }
                return null
            }
        }
        zoomCanvasView = zoomView
        card.addView(zoomView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(210)).apply {
            bottomMargin = dp(6)
        })

        fun saveUndo() {
            if (undoStack.size >= 8) {
                val oldest = undoStack.removeFirst()
                if (!oldest.isRecycled && oldest != currentMask) oldest.recycle()
            }
            undoStack.push(currentMask.copy(Bitmap.Config.ARGB_8888, true))
        }

        var lastTouchX = 0f
        var lastTouchY = 0f
        val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomFactor = (zoomFactor * detector.scaleFactor).coerceIn(2f, 24f)
                tvZoomStatus.text = String.format(Locale.US, "%.1fx", zoomFactor)
                zoomView.invalidate()
                return true
            }
        })

        zoomView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)

            if (event.pointerCount >= 2) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        panX += dx
                        panY += dy
                        zoomView.invalidate()
                    }
                }
                lastTouchX = event.x
                lastTouchY = event.y
                return@setOnTouchListener true
            }

            val pt = zoomView.mapTouchToBitmap(event.x, event.y)
            if (pt != null) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                        saveUndo()
                        when (currentTool) {
                            ToolMode.WAND -> {
                                val next = SmartMaskEngine.magicWandEraseAt(currentMask, pt.first, pt.second, wandTolerance)
                                safeReplaceCurrentMask(next)
                                zoomView.invalidate()
                            }
                            ToolMode.ERASER -> {
                                val next = SmartMaskEngine.paintBrushAt(currentMask, null, pt.first, pt.second, brushRadius, true)
                                safeReplaceCurrentMask(next)
                                zoomView.invalidate()
                            }
                            ToolMode.RESTORE -> {
                                val next = SmartMaskEngine.paintBrushAt(currentMask, rawBitmap, pt.first, pt.second, brushRadius, false)
                                safeReplaceCurrentMask(next)
                                zoomView.invalidate()
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        when (currentTool) {
                            ToolMode.ERASER -> {
                                val next = SmartMaskEngine.paintBrushAt(currentMask, null, pt.first, pt.second, brushRadius, true)
                                safeReplaceCurrentMask(next)
                                zoomView.invalidate()
                                true
                            }
                            ToolMode.RESTORE -> {
                                val next = SmartMaskEngine.paintBrushAt(currentMask, rawBitmap, pt.first, pt.second, brushRadius, false)
                                safeReplaceCurrentMask(next)
                                zoomView.invalidate()
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            } else false
        }

        val rowTools = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }

        fun updateSliderForCurrentTool() {
            when (currentTool) {
                ToolMode.WAND -> {
                    tvToolParamLabel?.text = "ЧУВСТВИТЕЛЬНОСТЬ ПАЛОЧКИ: $wandTolerance"
                    sbToolParam?.max = 70
                    sbToolParam?.progress = wandTolerance - 10
                }
                ToolMode.ERASER, ToolMode.RESTORE -> {
                    val name = if (currentTool == ToolMode.ERASER) "ЛАСТИКА" else "КИСТИ"
                    tvToolParamLabel?.text = "РАДИУС $name: ${brushRadius}px"
                    sbToolParam?.max = 14
                    sbToolParam?.progress = brushRadius - 1
                }
            }
        }

        val toolButtons = ArrayList<Button>()
        fun updateToolSelection(activeTool: ToolMode) {
            currentTool = activeTool
            for (b in toolButtons) {
                val mode = b.tag as? ToolMode ?: continue
                val isSel = (mode == activeTool)
                b.setTextColor(if (isSel) Color.BLACK else Color.WHITE)
                b.background = GradientDrawable().apply {
                    setColor(if (isSel) "#00F5D4".toColorInt() else "#21262D".toColorInt())
                    cornerRadius = dpF(4f)
                    if (!isSel) setStroke(dp(1), "#30363D".toColorInt())
                }
            }
            updateSliderForCurrentTool()
        }

        fun createToolBtn(label: String, tool: ToolMode): Button {
            return Button(context).apply {
                tag = tool
                text = label
                textSize = 8f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(3), dp(3), dp(3), dp(3))
                setOnClickListener { updateToolSelection(tool) }
            }
        }

        val btnWand = createToolBtn("ПАЛОЧКА", ToolMode.WAND)
        toolButtons.add(btnWand)
        rowTools.addView(btnWand, LinearLayout.LayoutParams(0, dp(30), 1.1f).apply { marginEnd = dp(2) })

        val btnEraser = createToolBtn("ЛАСТИК", ToolMode.ERASER)
        toolButtons.add(btnEraser)
        rowTools.addView(btnEraser, LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(2) })

        val btnRestore = createToolBtn("КИСТЬ", ToolMode.RESTORE)
        toolButtons.add(btnRestore)
        rowTools.addView(btnRestore, LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(2) })

        val btnAuto = Button(context).apply {
            text = "ЗНАК"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                setColor("#00F5D4".toColorInt())
                cornerRadius = dpF(4f)
            }
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                saveUndo()
                val next = SmartMaskEngine.autoExtractGlyph(rawBitmap)
                safeReplaceCurrentMask(next)
                zoomView.invalidate()
            }
        }
        rowTools.addView(btnAuto, LinearLayout.LayoutParams(0, dp(30), 0.9f).apply { marginEnd = dp(2) })

        val btnInvert = Button(context).apply {
            text = "ИНВЕРСИЯ"
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#FFB703".toColorInt())
            }
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                saveUndo()
                val next = SmartMaskEngine.invertMask(currentMask, rawBitmap)
                safeReplaceCurrentMask(next)
                zoomView.invalidate()
            }
        }
        rowTools.addView(btnInvert, LinearLayout.LayoutParams(dp(62), dp(30)).apply { marginEnd = dp(2) })

        val btnUndo = Button(context).apply {
            text = "ОТМЕНА"
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#C9D1D9".toColorInt())
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                if (undoStack.isNotEmpty()) {
                    val restored = undoStack.pop()
                    if (!currentMask.isRecycled && currentMask != restored) {
                        currentMask.recycle()
                    }
                    currentMask = restored
                    zoomView.invalidate()
                }
            }
        }
        rowTools.addView(btnUndo, LinearLayout.LayoutParams(dp(48), dp(30)))
        card.addView(rowTools)

        val tvSlider = TextView(context).apply {
            text = "ЧУВСТВИТЕЛЬНОСТЬ ПАЛОЧКИ: $wandTolerance"
            setTextColor("#58A6FF".toColorInt())
            textSize = 9f
            typeface = Typeface.MONOSPACE
            includeFontPadding = false
            setPadding(0, dp(2), 0, 0)
        }
        tvToolParamLabel = tvSlider
        card.addView(tvSlider)

        val sbTol = SeekBar(context).apply {
            max = 70
            progress = wandTolerance - 10
            setPadding(dp(4), 0, dp(4), dp(4))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    when (currentTool) {
                        ToolMode.WAND -> {
                            wandTolerance = progress + 10
                            tvSlider.text = "ЧУВСТВИТЕЛЬНОСТЬ ПАЛОЧКИ: $wandTolerance"
                        }
                        ToolMode.ERASER, ToolMode.RESTORE -> {
                            brushRadius = progress + 1
                            val name = if (currentTool == ToolMode.ERASER) "ЛАСТИКА" else "КИСТИ"
                            tvSlider.text = "РАДИУС $name: ${brushRadius}px"
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        sbToolParam = sbTol
        card.addView(sbTol)
        updateToolSelection(ToolMode.WAND)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        val btnApply = Button(context).apply {
            text = "ПРИМЕНИТЬ МАСКУ"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#10B981".toColorInt())
                cornerRadius = dpF(6f)
            }
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                val finalMaskResult = currentMask.copy(Bitmap.Config.ARGB_8888, true)
                dismiss()
                onApplied(finalMaskResult)
            }
        }
        btnRow.addView(btnApply, LinearLayout.LayoutParams(0, dp(36), 1.5f).apply { marginEnd = dp(4) })

        val btnCancel = Button(context).apply {
            text = "ОТМЕНА"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor("#F04438".toColorInt())
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#F04438".toColorInt())
            }
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                dismiss()
                onCancelled()
            }
        }
        btnRow.addView(btnCancel, LinearLayout.LayoutParams(0, dp(36), 1f))
        card.addView(btnRow)

        root.addView(card, FrameLayout.LayoutParams(cardW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val params = overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        overlayWindowManager.addViewSafe(root, params)
    }

    private fun createSmallHeaderBtn(label: String, onClick: () -> Unit): View {
        return Button(context).apply {
            text = label
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(3f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            minHeight = 0
            minimumHeight = 0
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(22)).apply {
                marginStart = dp(2)
            }
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener { onClick() }
        }
    }

    fun dismiss() {
        rootView?.let {
            overlayWindowManager.removeViewSafe(it)
            rootView = null
        }
        while (undoStack.isNotEmpty()) {
            val bmp = undoStack.pop()
            if (!bmp.isRecycled && bmp != currentMask) {
                bmp.recycle()
            }
        }
        if (!currentMask.isRecycled) {
            currentMask.recycle()
        }
    }
}
