package com.example.autotap.infrastructure.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.View

class ClickPulseView(context: Context) : View(context) {

    var onFinishedCallback: (() -> Unit)? = null

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#38BDF8")
    }

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#38BDF8")
    }

    private var startTime = -1L
    private val durationMs = 380f
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
            if (isAttachedToWindow) {
                onFinishedCallback?.invoke()
            }
        }
        finishRunnable = task
        mainHandler.postDelayed(task, durationMs.toLong())
    }

    override fun onDetachedFromWindow() {
        finishRunnable?.let { mainHandler.removeCallbacks(it) }
        finishRunnable = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (startTime == -1L) startTime = System.currentTimeMillis()
        val elapsed = System.currentTimeMillis() - startTime
        val progress = (elapsed / durationMs).coerceIn(0f, 1f)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = ((width / 2f) - 8f).coerceAtLeast(10f)
        val interpolatedProgress = 1f - (1f - progress) * (1f - progress)

        val ringRadius = maxRadius * interpolatedProgress
        ringPaint.alpha = ((1f - interpolatedProgress) * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)

        if (interpolatedProgress < 0.65f) {
            val coreProgress = interpolatedProgress / 0.65f
            val coreRadius = (maxRadius * 0.28f) * (1f - coreProgress)
            corePaint.alpha = ((1f - coreProgress) * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, coreRadius, corePaint)
        }

        val chLen = 14f * (1f - interpolatedProgress)
        val chDist = ringRadius + 4f
        crosshairPaint.alpha = ((1f - interpolatedProgress) * 220).toInt().coerceIn(0, 255)
        if (chDist < width / 2f) {
            canvas.drawLine(cx, cy - chDist, cx, cy - chDist - chLen, crosshairPaint)
            canvas.drawLine(cx, cy + chDist, cx, cy + chDist + chLen, crosshairPaint)
            canvas.drawLine(cx - chDist, cy, cx - chDist - chLen, cy, crosshairPaint)
            canvas.drawLine(cx + chDist, cy, cx + chDist + chLen, cy, crosshairPaint)
        }

        if (progress < 1f) postInvalidateOnAnimation()
    }

    fun clearPulses() {
        post {
            try {
                visibility = android.view.View.GONE
                invalidate()
            } catch (_: Exception) {}
        }
    }
}
