package com.example.autotap.infrastructure.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.View

class SwipeTrailView(context: Context) : View(context) {

    var startX = 0f
    var startY = 0f
    var endX = 0f
    var endY = 0f
    var durationMs = 300L
    var onFinishedCallback: (() -> Unit)? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#58A6FF")
    }

    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38BDF8")
    }

    private var startTime = -1L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var finishRunnable: Runnable? = null

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTime = System.currentTimeMillis()
        invalidate()
        val task = Runnable {
            if (isAttachedToWindow) onFinishedCallback?.invoke()
        }
        finishRunnable = task
        mainHandler.postDelayed(task, durationMs.coerceAtLeast(10L) + 180L)
    }

    override fun onDetachedFromWindow() {
        finishRunnable?.let { mainHandler.removeCallbacks(it) }
        finishRunnable = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (startTime == -1L) startTime = System.currentTimeMillis()
        val safeDuration = durationMs.coerceAtLeast(10L).toFloat()
        val elapsed = (System.currentTimeMillis() - startTime).toFloat()
        val progress = (elapsed / safeDuration).coerceIn(0f, 1f)

        val curX = startX + (endX - startX) * progress
        val curY = startY + (endY - startY) * progress
        linePaint.alpha = ((1f - progress * 0.4f) * 255).toInt().coerceIn(0, 255)
        canvas.drawLine(startX, startY, curX, curY, linePaint)
        canvas.drawCircle(curX, curY, 14f, headPaint)
        if (progress < 1f) postInvalidateOnAnimation()
    }
}
