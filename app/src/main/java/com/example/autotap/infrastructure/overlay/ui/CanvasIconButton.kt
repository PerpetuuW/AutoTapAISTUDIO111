package com.example.autotap.infrastructure.overlay.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * Высокопроизводительный компонент кнопки на Canvas для WindowManager Overlays.
 * Реализует Material 3 Cyber-Glassmorphism с верхним световым фасетом (Ambient Top Rim),
 * нулевыми аллокациями в цикле рендеринга и тактильной анимацией нажатия.
 */
class CanvasIconButton(context: Context) : View(context) {

    var iconType: VectorIconDrawer.IconType = VectorIconDrawer.IconType.SETTINGS
        set(value) {
            field = value
            invalidate()
        }

    var iconColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var bgColorStart: Int = Color.parseColor("#1A202C")
        set(value) {
            field = value
            updateShader()
            invalidate()
        }

    var bgColorEnd: Int = Color.parseColor("#10141D")
        set(value) {
            field = value
            updateShader()
            invalidate()
        }

    var strokeColor: Int = Color.parseColor("#2D3748")
        set(value) {
            field = value
            strokePaint.color = value
            invalidate()
        }

    var strokeWidthPx: Float = 2f
        set(value) {
            field = value
            strokePaint.strokeWidth = value
            invalidate()
        }

    var cornerRadiusPx: Float = 16f
        set(value) {
            field = value
            invalidate()
        }

    var iconPaddingPx: Float = 16f
        set(value) {
            field = value
            invalidate()
        }

    var isCircleShape: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#2D3748")
    }

    private val ambientTopRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.parseColor("#33FFFFFF")
    }

    private val boundsF = RectF()
    private val topRimBoundsF = RectF()
    private val iconBoundsF = RectF()
    private var isPressedState = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
    }

    private fun updateShader() {
        if (width > 0 && height > 0) {
            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                bgColorStart, bgColorEnd, Shader.TileMode.CLAMP
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || !isClickable) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressedState = true
                animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).start()
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isPressedState) {
                    isPressedState = false
                    animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    invalidate()
                    playSoundEffect(android.view.SoundEffectConstants.CLICK)
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isPressedState) {
                    isPressedState = false
                    animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val halfStroke = strokeWidthPx * 0.5f
        boundsF.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)

        if (isCircleShape) {
            val cx = w * 0.5f
            val cy = h * 0.5f
            val r = (minOf(w, h) * 0.5f) - halfStroke
            canvas.drawCircle(cx, cy, r, bgPaint)
            if (strokeWidthPx > 0f) {
                canvas.drawCircle(cx, cy, r, strokePaint)
                canvas.drawCircle(cx, cy - 0.5f, r - 0.5f, ambientTopRimPaint)
            }
        } else {
            canvas.drawRoundRect(boundsF, cornerRadiusPx, cornerRadiusPx, bgPaint)
            if (strokeWidthPx > 0f) {
                canvas.drawRoundRect(boundsF, cornerRadiusPx, cornerRadiusPx, strokePaint)
                topRimBoundsF.set(boundsF.left + 1f, boundsF.top + 0.8f, boundsF.right - 1f, boundsF.top + 3.5f)
                canvas.drawRoundRect(topRimBoundsF, cornerRadiusPx, cornerRadiusPx, ambientTopRimPaint)
            }
        }

        iconBoundsF.set(iconPaddingPx, iconPaddingPx, w - iconPaddingPx, h - iconPaddingPx)
        if (iconBoundsF.width() > 0f && iconBoundsF.height() > 0f) {
            try {
                VectorIconDrawer.drawIcon(canvas, iconType, iconBoundsF, iconColor, strokeWidthPx)
            } catch (_: Throwable) {}
        }
    }
}
