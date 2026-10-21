package com.example.autotap.infrastructure.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.View

class ClickPulseView(context: Context) : View(context) {

    var onFinishedCallback: (() -> Unit)? = null

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
        color = Color.parseColor("#38BDF8")
    }

    private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#A78BFA")
    }

    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#38BDF8")
    }

    private var startTime = -1L
    private val durationMs = 420f
    private val mainHandler = Handler(Looper.getMainLooper())
    private var finishRunnable: Runnable? = null
    var isMacroRunning: Boolean = false

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

    private val glowColors = IntArray(3)
    private val glowStops = floatArrayOf(0f, 0.55f, 1f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (startTime == -1L) startTime = System.currentTimeMillis()
        val elapsed = System.currentTimeMillis() - startTime
        val progress = (elapsed / durationMs).coerceIn(0f, 1f)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = ((width / 2f) - 6f).coerceAtLeast(10f)

        // Ease-out cubic curve
        val t = 1f - progress
        val easeOut = 1f - (t * t * t)

        val currentRadius = maxRadius * easeOut
        val alphaMultiplier = (1f - progress).coerceIn(0f, 1f)

        // 1. Неоновое градиентное свечение (Radial Glow)
        if (currentRadius > 4f) {
            glowColors[0] = Color.argb((140 * alphaMultiplier).toInt(), 56, 189, 248)
            glowColors[1] = Color.argb((70 * alphaMultiplier).toInt(), 167, 139, 250)
            glowColors[2] = Color.TRANSPARENT
            glowPaint.shader = RadialGradient(
                cx, cy, currentRadius,
                glowColors,
                glowStops,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, currentRadius, glowPaint)
        }

        // 2. Внешнее расширяющееся кольцо
        outerRingPaint.alpha = (255 * alphaMultiplier).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, currentRadius, outerRingPaint)

        // 3. Внутреннее контрастное кольцо (со смещением фазы)
        val innerRadius = currentRadius * 0.65f
        if (innerRadius > 2f) {
            innerRingPaint.alpha = (200 * alphaMultiplier).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, innerRadius, innerRingPaint)
        }

        // 4. Изящные направляющие лучи
        val rayLen = 12f * alphaMultiplier
        val rayDist = currentRadius + 3f
        rayPaint.alpha = (180 * alphaMultiplier).toInt().coerceIn(0, 255)
        if (rayDist + rayLen < width / 2f) {
            canvas.drawLine(cx, cy - rayDist, cx, cy - rayDist - rayLen, rayPaint)
            canvas.drawLine(cx, cy + rayDist, cx, cy + rayDist + rayLen, rayPaint)
            canvas.drawLine(cx - rayDist, cy, cx - rayDist - rayLen, cy, rayPaint)
            canvas.drawLine(cx + rayDist, cy, cx + rayDist + rayLen, cy, rayPaint)
        }

        val frameStepMs = if (isMacroRunning) 33L else 16L
        if (progress < 1f) postInvalidateDelayed(frameStepMs)
    }

    fun clearPulses() {
        post {
            try {
                visibility = GONE
                invalidate()
            } catch (_: Exception) {}
        }
    }
}
