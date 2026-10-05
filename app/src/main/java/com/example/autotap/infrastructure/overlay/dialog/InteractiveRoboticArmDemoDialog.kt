package com.example.autotap.infrastructure.overlay.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class InteractiveRoboticArmDemoDialog(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager
) {

    private var rootFrameLayout: View? = null
    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    private var isPlaying = true
    private var isCleanRecordingMode = false
    private var animProgress = 0f
    private val handler = Handler(Looper.getMainLooper())
    private var animRunnable: Runnable? = null

    fun show() {
        if (rootFrameLayout != null) return

        val root = FrameLayout(context).apply {
            setBackgroundColor("#F80B0F19".toColorInt())
        }
        rootFrameLayout = root

        val controlsBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor("#1E293B".toColorInt())
                cornerRadius = dpF(12f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            elevation = dpF(16f)
        }

        val demoCanvasView = object : View(context) {
            private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#1E293B".toColorInt()
                strokeWidth = dpF(1f)
            }
            private val targetBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                color = "#10B981".toColorInt()
            }
            private val roiBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(2f)
                pathEffect = DashPathEffect(floatArrayOf(dpF(6f), dpF(4f)), 0f)
                color = "#F59E0B".toColorInt()
            }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = dpF(12f)
                typeface = Typeface.DEFAULT_BOLD
            }
            private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#38BDF8".toColorInt()
                textSize = dpF(10f)
                typeface = Typeface.DEFAULT_BOLD
            }
            private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#94A3B8".toColorInt()
                strokeWidth = dpF(8f)
                strokeCap = Paint.Cap.ROUND
            }
            private val armJointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#38BDF8".toColorInt()
                style = Paint.Style.FILL
            }
            private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#EC4899".toColorInt()
                strokeWidth = dpF(2.5f)
            }
            private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(3f)
                color = "#10B981".toColorInt()
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()

                // 1. Draw Grid Background
                val step = dpF(40f)
                var x = 0f
                while (x < w) {
                    canvas.drawLine(x, 0f, x, h, gridPaint)
                    x += step
                }
                var y = 0f
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, gridPaint)
                    y += step
                }

                // 2. Draw Target Elements
                // Target A: Text Target "ВХОД В ИГРУ"
                val rectA = RectF(w * 0.15f, h * 0.22f, w * 0.48f, h * 0.29f)
                canvas.drawRoundRect(rectA, dpF(8f), dpF(8f), targetBoxPaint)
                canvas.drawText("[OCR] 'ВХОД В ИГРУ'", rectA.left + dpF(12f), rectA.centerY() + dpF(4f), textPaint)

                // Target B: Image Target + ROI
                val rectB = RectF(w * 0.55f, h * 0.42f, w * 0.88f, h * 0.52f)
                canvas.drawRoundRect(rectB, dpF(8f), dpF(8f), roiBoxPaint)
                canvas.drawText("ИИ ПОИСК: 99.4%", rectB.left + dpF(12f), rectB.centerY() + dpF(4f), labelPaint)

                // Target C: Steps Flow 1 -> 2 -> 3
                val stepY = h * 0.72f
                val s1 = RectF(w * 0.12f, stepY, w * 0.32f, stepY + dpF(36f))
                val s2 = RectF(w * 0.42f, stepY, w * 0.62f, stepY + dpF(36f))
                val s3 = RectF(w * 0.72f, stepY, w * 0.92f, stepY + dpF(36f))

                canvas.drawRoundRect(s1, dpF(6f), dpF(6f), targetBoxPaint)
                canvas.drawText("ШАГ 1", s1.centerX() - dpF(20f), s1.centerY() + dpF(4f), labelPaint)

                canvas.drawRoundRect(s2, dpF(6f), dpF(6f), targetBoxPaint)
                canvas.drawText("ШАГ 2", s2.centerX() - dpF(20f), s2.centerY() + dpF(4f), labelPaint)

                canvas.drawRoundRect(s3, dpF(6f), dpF(6f), targetBoxPaint)
                canvas.drawText("ШАГ 3", s3.centerX() - dpF(20f), s3.centerY() + dpF(4f), labelPaint)

                // 3. Robotic Arm Kinematics Motion Calculation
                val armBaseX = w * 0.05f
                val armBaseY = h * 0.08f

                // Target Waypoints based on animProgress (0.0 to 1.0)
                val targetX: Float
                val targetY: Float
                val isTapping: Boolean
                val stageText: String

                when {
                    animProgress < 0.28f -> {
                        val t = (animProgress / 0.28f)
                        targetX = rectA.centerX() * t + armBaseX * (1 - t)
                        targetY = rectA.centerY() * t + armBaseY * (1 - t)
                        isTapping = t > 0.85f
                        stageText = "1. ПОИСК И НАЖАТИЕ ПО ТЕКСТУ (OCR)"
                    }
                    animProgress < 0.60f -> {
                        val t = ((animProgress - 0.28f) / 0.32f)
                        targetX = rectB.centerX() * t + rectA.centerX() * (1 - t)
                        targetY = rectB.centerY() * t + rectA.centerY() * (1 - t)
                        isTapping = t > 0.85f
                        stageText = "2. ВЫСОКОТОЧНЫЙ ИИ-ПОИСК И ЗОНА ROI"
                    }
                    else -> {
                        val t = ((animProgress - 0.60f) / 0.40f)
                        if (t < 0.33f) {
                            targetX = s1.centerX()
                            targetY = s1.centerY()
                        } else if (t < 0.66f) {
                            targetX = s2.centerX()
                            targetY = s2.centerY()
                        } else {
                            targetX = s3.centerX()
                            targetY = s3.centerY()
                        }
                        isTapping = (t % 0.33f) > 0.25f
                        stageText = "3. АВТОМАТИЧЕСКАЯ ЦЕПОЧКА ШАГОВ (1 ➔ 2 ➔ 3)"
                    }
                }

                // Render Robotic Arm (2-Segment Manipulator Arm)
                val joint1X = (armBaseX + targetX) / 2f + dpF(40f)
                val joint1Y = (armBaseY + targetY) / 2f - dpF(60f)

                // Arm Segment 1
                canvas.drawLine(armBaseX, armBaseY, joint1X, joint1Y, armPaint)
                // Arm Segment 2
                canvas.drawLine(joint1X, joint1Y, targetX, targetY, armPaint)

                // Joints
                canvas.drawCircle(armBaseX, armBaseY, dpF(12f), armJointPaint)
                canvas.drawCircle(joint1X, joint1Y, dpF(9f), armJointPaint)

                // Robotic End Effector / Laser Finger Tip
                canvas.drawCircle(targetX, targetY, dpF(8f), laserPaint)

                // Click Ripple Animation
                if (isTapping) {
                    val rippleR = (System.currentTimeMillis() % 600) / 600f * dpF(35f)
                    ripplePaint.alpha = ((1f - (rippleR / dpF(35f))) * 255).toInt().coerceIn(0, 255)
                    canvas.drawCircle(targetX, targetY, rippleR, ripplePaint)
                }

                // Stage Info Banner
                if (!isCleanRecordingMode) {
                    canvas.drawText(stageText, dpF(16f), h - dpF(24f), textPaint)
                }
            }
        }
        root.addView(demoCanvasView, FrameLayout.LayoutParams(-1, -1))

        // Header Title
        val tvTitleHeader = TextView(context).apply {
            text = "ДЕМОНСТРАЦИЯ АВТОМАТИЗАЦИИ РОБОРУКИ"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#38BDF8".toColorInt())
        }
        controlsBar.addView(tvTitleHeader, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnPlayPause = Button(context).apply {
            text = "ПАУЗА"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            minHeight = 0; minimumHeight = 0
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#0EA5E9".toColorInt())
                cornerRadius = dpF(6f)
            }
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                isPlaying = !isPlaying
                text = if (isPlaying) "ПАУЗА" else "ПУСК"
            }
        }
        controlsBar.addView(btnPlayPause, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)).apply {
            marginEnd = dp(6)
        })

        val btnRecMode = Button(context).apply {
            text = "ЗАПИСЬ (15с)"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            minHeight = 0; minimumHeight = 0
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#8B5CF6".toColorInt())
                cornerRadius = dpF(6f)
            }
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                isCleanRecordingMode = true
                controlsBar.visibility = View.GONE
                handler.postDelayed({
                    isCleanRecordingMode = false
                    controlsBar.visibility = View.VISIBLE
                }, 15000L)
            }
        }
        controlsBar.addView(btnRecMode, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)).apply {
            marginEnd = dp(6)
        })

        val btnClose = Button(context).apply {
            text = "X"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setTextColor("#94A3B8".toColorInt())
            background = null
            setOnClickListener { dismiss() }
        }
        controlsBar.addView(btnClose, LinearLayout.LayoutParams(dp(28), dp(28)))

        val barLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            setMargins(dp(12), dp(24), dp(12), 0)
        }
        root.addView(controlsBar, barLp)

        // Start Animation Loop
        animRunnable = object : Runnable {
            override fun run() {
                if (isPlaying) {
                    animProgress = (animProgress + 0.008f) % 1.0f
                    demoCanvasView.invalidate()
                }
                handler.postDelayed(this, 30L)
            }
        }
        handler.post(animRunnable!!)

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
        animRunnable?.let { handler.removeCallbacks(it) }
        rootFrameLayout?.let {
            overlayWindowManager.removeViewSafe(it)
            rootFrameLayout = null
        }
    }
}
