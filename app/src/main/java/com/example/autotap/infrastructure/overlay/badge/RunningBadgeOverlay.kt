package com.example.autotap.infrastructure.overlay.badge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.ExecutionState
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import com.example.autotap.infrastructure.projection.MediaProjectionService
import java.util.Locale
import kotlin.math.abs

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class RunningBadgeOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager
) {

    private var badgeView: View? = null
    private var tvStepHeader: TextView? = null
    private var tvActionStatus: TextView? = null
    private var actionProgressBar: ProgressBar? = null
    private var fillProgressDrawable: GradientDrawable? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentStepStartTime = 0L
    private var currentStepDurationMs = 1000L
    private var currentActionTitle = ""
    private var isSearchingMode = false
    private var isInfiniteMode = false

    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    private val contentWidthDp = 78

    fun isShowing(): Boolean = badgeView != null

    fun setTemporarilyTransparent(transparent: Boolean) {
        // [G-06.1] Полная блокировка паразитного стробоскопа при активном поиске и захвате
        if (transparent && (MediaProjectionService.isStreaming || isSearchingMode)) return
        mainHandler.post {
            badgeView?.alpha = if (transparent) 0f else 1f
        }
    }

    private val tickerRunnable = object : Runnable {
        override fun run() {
            if (badgeView != null) {
                val elapsed = System.currentTimeMillis() - currentStepStartTime
                if (isSearchingMode) {
                    if (isInfiniteMode) {
                        tvActionStatus?.text = "$currentActionTitle (inf)"
                        actionProgressBar?.progress = 50
                    } else {
                        val remainingMs = (currentStepDurationMs - elapsed).coerceAtLeast(0L)
                        val ratio = (elapsed.toFloat() / currentStepDurationMs.toFloat()).coerceIn(0f, 1f)
                        val remainingSec = String.format(Locale.US, "%.1fс", remainingMs / 1000f)
                        tvActionStatus?.text = "$currentActionTitle ($remainingSec)"
                        actionProgressBar?.progress = (ratio * 100).toInt()
                    }
                } else {
                    val ratio = (elapsed.toFloat() / currentStepDurationMs.toFloat()).coerceIn(0f, 1f)
                    actionProgressBar?.progress = (ratio * 100).toInt()
                }
                mainHandler.postDelayed(this, 40L)
            }
        }
    }

    private fun vibrate(durationMs: Long) {
        try {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }

    fun forceVisible() {
        mainHandler.post {
            badgeView?.visibility = View.VISIBLE
            badgeView?.alpha = 1f
        }
    }

    fun show(onLockClick: () -> Unit, onStopClick: () -> Unit) {
        if (badgeView != null) {
            forceVisible()
            return
        }

        val contentW = dp(contentWidthDp)
        val padH = dp(6)
        val totalCardW = contentW + padH * 2

        val wmParams = overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.START
        ).apply {
            x = (dm.widthPixels - totalCardW - dp(10)).coerceAtLeast(dp(6))
            y = dp(110)
        }

        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8161B22".toColorInt(), "#F80D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(18f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            setPadding(padH, padH, padH, padH)
            elevation = dpF(12f)
        }

        val tvHeader = TextView(context).apply {
            text = "ШАГ 1/1"
            setTextColor("#58A6FF".toColorInt())
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(contentW, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(2)
            }
        }
        tvStepHeader = tvHeader
        rootCard.addView(tvHeader)

        val infoContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(contentW, dp(20)).apply {
                bottomMargin = dp(4)
            }
        }

        val tvStatus = TextView(context).apply {
            text = "Запуск..."
            setTextColor("#34D399".toColorInt())
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
            includeFontPadding = false
            maxLines = 1
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(contentW, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        tvActionStatus = tvStatus
        infoContainer.addView(tvStatus)

        val bgProgress = GradientDrawable().apply {
            setColor("#21262D".toColorInt())
            cornerRadius = dpF(2f)
        }

        // [V25.0] Объемный градиент для шкалы прогресса бейджа
        val fillProgress = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf("#34D399".toColorInt(), "#38BDF8".toColorInt())
        ).apply {
            cornerRadius = dpF(2f)
        }
        fillProgressDrawable = fillProgress

        val clipFill = ClipDrawable(fillProgress, Gravity.START, ClipDrawable.HORIZONTAL)
        val progressDrawable = LayerDrawable(arrayOf(bgProgress, clipFill)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }

        val pBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            val barHeight = (3.5f * dm.density).toInt().coerceAtLeast(1)
            layoutParams = LinearLayout.LayoutParams(contentW, barHeight)
            max = 100
            progress = 0
            this.progressDrawable = progressDrawable
        }
        actionProgressBar = pBar
        infoContainer.addView(pBar)
        rootCard.addView(infoContainer)

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(contentW, dp(36))
        }

        val btnLock = object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(8.5f), dpF(8.5f), width - dpF(8.5f), height - dpF(8.5f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.LOCK, bounds, Color.WHITE, dpF(2f))
            }
        }.apply {
            outlineProvider = ViewOutlineProvider.BACKGROUND
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#1F6FEB".toColorInt(), "#161B22".toColorInt())
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), "#58A6FF".toColorInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                vibrate(30L)
                onLockClick()
            }
        }
        buttonsRow.addView(btnLock)

        val btnStop = object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(8.5f), dpF(8.5f), width - dpF(8.5f), height - dpF(8.5f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.STOP, bounds, Color.WHITE, dpF(2f))
            }
        }.apply {
            outlineProvider = ViewOutlineProvider.BACKGROUND
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F04438".toColorInt(), "#B42318".toColorInt())
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), "#FDA29B".toColorInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener {
                vibrate(45L)
                onStopClick()
            }
        }
        buttonsRow.addView(btnStop)
        rootCard.addView(buttonsRow)

        rootCard.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0
            private var initY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var isMoved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = wmParams.x
                        initY = wmParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        isMoved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = abs(event.rawX - touchX)
                        val dy = abs(event.rawY - touchY)
                        if (dx > dp(5) || dy > dp(5)) {
                            isMoved = true
                            val maxPosX = (dm.widthPixels - totalCardW - dp(6)).coerceAtLeast(0)
                            wmParams.x = (initX + (event.rawX - touchX).toInt()).coerceIn(dp(6), maxPosX)
                            wmParams.y = (initY + (event.rawY - touchY).toInt()).coerceIn(dp(20), dm.heightPixels - dp(80))
                            overlayWindowManager.updateViewSafe(rootCard, wmParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        return true
                    }
                }
                return false
            }
        })

        badgeView = rootCard
        overlayWindowManager.addViewSafe(rootCard, wmParams)
        mainHandler.post(tickerRunnable)
    }

    fun updateState(state: ExecutionState.Running) {
        mainHandler.post {
            badgeView?.alpha = 1f
            val fpsTag = if (MediaProjectionService.isStreaming) "60 FPS" else "МАКРОС"
            val repeatStr = if (state.totalRepeats > 1) " (${state.currentRepeat}/${state.totalRepeats})" else ""
            val stepPrefix = if (state.activeNodeTitle.isNotEmpty()) "ГРАФ" else "ШАГ"
            tvStepHeader?.text = "$fpsTag • $stepPrefix ${state.currentStepIndex}/${state.totalSteps}$repeatStr"

            currentStepStartTime = if (state.searchStartTime > 0L) state.searchStartTime else System.currentTimeMillis()
            isSearchingMode = state.isSearching
            isInfiniteMode = state.isInfiniteSearch

            if (state.isSearching) {
                currentStepDurationMs = state.searchTimeoutMs
                currentActionTitle = state.searchTitle.substringBefore(" (")
                tvActionStatus?.setTextColor("#FFB703".toColorInt())
                fillProgressDrawable?.setColor("#FFB703".toColorInt())
            } else {
                currentStepDurationMs = if (state.searchTimeoutMs > 0L) state.searchTimeoutMs else 1000L
                currentActionTitle = when {
                    state.activeNodeTitle.isNotEmpty() -> "* ${state.activeNodeTitle}"
                    state.stepDescription.isNotEmpty() -> state.stepDescription
                    else -> when (state.stepType) {
                        ActionType.CLICK -> "Клик #${state.currentStepIndex}"
                        ActionType.LONG_PRESS -> "Удержание #${state.currentStepIndex}"
                        ActionType.SWIPE -> "Свайп #${state.currentStepIndex}"
                        ActionType.PATH -> "Траектория #${state.currentStepIndex}"
                        ActionType.PINCH -> "Пинч #${state.currentStepIndex}"
                        ActionType.SUBROUTINE -> "Сценарий"
                        ActionType.RETURN -> "Возврат"
                        ActionType.GLOBAL_BACK -> "Назад #${state.currentStepIndex}"
                        ActionType.GLOBAL_HOME -> "Домой #${state.currentStepIndex}"
                        ActionType.DELAY -> "Пауза #${state.currentStepIndex}"
                        ActionType.TRIGGER, ActionType.OCR, ActionType.COLOR_CHECK -> "Фаза #${state.currentStepIndex}"
                        }
                        }
                        tvActionStatus?.text = currentActionTitle
                        tvActionStatus?.setTextColor("#38BDF8".toColorInt())
                        fillProgressDrawable?.setColor("#6366F1".toColorInt())
            }
        }
    }

    fun dismiss() {
        mainHandler.removeCallbacks(tickerRunnable)
        badgeView?.let {
            overlayWindowManager.removeViewSafe(it)
            badgeView = null
            tvStepHeader = null
            tvActionStatus = null
            actionProgressBar = null
        }
    }
}
