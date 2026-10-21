package com.example.autotap.infrastructure.overlay.badge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.toColorInt
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.autotap.domain.model.ExecutionState
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.ui.CanvasIconButton
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import kotlin.math.hypot

/**
 * Высокопроизводительный нативный Kotlin Canvas оверлей бейджа исполнения (60 FPS / 16.6ms).
 * Мгновенная реакция кнопки СТОП и энергоэффективный прогресс-бар на Canvas.
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class RunningBadgeOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val onLockClick: () -> Unit,
    private val onStopClick: () -> Unit
) {
    private var rootView: View? = null
    private var tvStepHeader: TextView? = null
    private var tvActionTitle: TextView? = null
    private var progressBarView: View? = null

    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    val contentWidthDp = 78
    private val cardW = dp(contentWidthDp)
    private var posX = (dm.widthPixels - cardW - dp(10)).coerceAtLeast(dp(6))
    private var posY = dp(110)

    private var currentProgressPercent = 0f
    private var isSearching = false

    fun show() {
        if (rootView != null) return

        val root = object : LinearLayout(context) {
            private var startRawX = 0f
            private var startRawY = 0f
            private var initLpX = 0
            private var initLpY = 0
            private var isDragging = false
            private val touchSlop = dpF(4f)

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawX = ev.rawX
                        startRawY = ev.rawY
                        initLpX = posX
                        initLpY = posY
                        isDragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - startRawX
                        val dy = ev.rawY - startRawY
                        if (hypot(dx, dy) > touchSlop) {
                            isDragging = true
                            return true
                        }
                    }
                }
                return super.onInterceptTouchEvent(ev)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                val lp = layoutParams as? WindowManager.LayoutParams ?: return super.onTouchEvent(event)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawX = event.rawX
                        startRawY = event.rawY
                        initLpX = posX
                        initLpY = posY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startRawX
                        val dy = event.rawY - startRawY
                        if (!isDragging && hypot(dx, dy) > touchSlop) {
                            isDragging = true
                        }
                        if (isDragging) {
                            val maxX = (dm.widthPixels - cardW).coerceAtLeast(dp(6))
                            posX = (initLpX + dx.toInt()).coerceIn(dp(6), maxX)
                            posY = (initLpY + dy.toInt()).coerceIn(dp(20), dm.heightPixels - dp(80))
                            lp.x = posX
                            lp.y = posY
                            overlayWindowManager.updateViewSafe(this, lp)
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            isDragging = false
                            return true
                        }
                    }
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8161B22".toColorInt(), "#F80D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(18f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            val p = dp(5)
            setPadding(p, p, p, p)
            elevation = dpF(16f)
            layoutParams = LinearLayout.LayoutParams(cardW, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        rootView = root

        tvStepHeader = TextView(context).apply {
            text = "ШАГ 1/1"
            setTextColor(Color.parseColor("#58A6FF"))
            textSize = 9.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            isSelected = true
            marqueeRepeatLimit = -1
        }
        root.addView(tvStepHeader)

        tvActionTitle = TextView(context).apply {
            text = "Запуск"
            setTextColor(Color.parseColor("#34D399"))
            textSize = 9f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            isSelected = true
            marqueeRepeatLimit = -1
        }
        root.addView(tvActionTitle)

        progressBarView = object : View(context) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#21262D")
                style = Paint.Style.FILL
            }
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            private val rectF = RectF()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val r = h / 2f

                rectF.set(0f, 0f, w, h)
                canvas.drawRoundRect(rectF, r, r, bgPaint)

                val fillW = (w * (currentProgressPercent / 100f)).coerceIn(0f, w)
                if (fillW > 0f) {
                    fillPaint.shader = LinearGradient(
                        0f, 0f, fillW, 0f,
                        Color.parseColor(if (isSearching) "#FFFFB703" else "#FF34D399"),
                        Color.parseColor(if (isSearching) "#FFFB8500" else "#FF38BDF8"),
                        Shader.TileMode.CLAMP
                    )
                    rectF.set(0f, 0f, fillW, h)
                    canvas.drawRoundRect(rectF, r, r, fillPaint)
                }
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).apply {
                val m = dp(3)
                setMargins(0, m, 0, m)
            }
        }
        root.addView(progressBarView)

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val btnLock = CanvasIconButton(context).apply {
            iconType = VectorIconDrawer.IconType.LOCK
            iconColor = Color.WHITE
            bgColorStart = Color.parseColor("#1F6FEB")
            bgColorEnd = Color.parseColor("#161B22")
            strokeColor = Color.parseColor("#58A6FF")
            strokeWidthPx = dpF(1.2f)
            cornerRadiusPx = dpF(15f)
            iconPaddingPx = dpF(6f)
            val s = dp(30)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(8) }
            setOnClickListener { onLockClick() }
        }
        buttonsRow.addView(btnLock)

        val btnStop = CanvasIconButton(context).apply {
            iconType = VectorIconDrawer.IconType.STOP
            iconColor = Color.WHITE
            bgColorStart = Color.parseColor("#F04438")
            bgColorEnd = Color.parseColor("#B42318")
            strokeColor = Color.parseColor("#FDA29B")
            strokeWidthPx = dpF(1.2f)
            cornerRadiusPx = dpF(15f)
            iconPaddingPx = dpF(6f)
            val s = dp(30)
            layoutParams = LinearLayout.LayoutParams(s, s)
            setOnClickListener { onStopClick() }
        }
        buttonsRow.addView(btnStop)

        root.addView(buttonsRow)

        val params = overlayWindowManager.createLayoutParams(
            width = cardW,
            height = WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            this.x = posX
            this.y = posY
        }
        overlayWindowManager.addViewSafe(root, params)
    }

    fun hide() {
        rootView?.let {
            overlayWindowManager.removeViewSafe(it)
            rootView = null
        }
    }

    fun dismiss() = hide()

    fun isShowing(): Boolean = rootView != null && rootView?.visibility == View.VISIBLE

    fun setTemporarilyTransparent(transparent: Boolean) {
        rootView?.alpha = if (transparent) 0f else 1f
    }

    fun forceVisible() {
        rootView?.alpha = 1f
    }

    fun updateState(state: ExecutionState.Running) {
        if (rootView == null) show()

        val repeatStr = if (state.totalRepeats > 1) " (${state.currentRepeat}/${state.totalRepeats})" else ""
        tvStepHeader?.text = "ШАГ ${state.currentStepIndex}/${state.totalSteps}$repeatStr"

        val title = if (state.isSearching) {
            val rawTitle = state.searchTitle.removeSuffix(".png").removeSuffix(".json").removeSuffix(".jpg").removePrefix("mask_").trim()
            if (rawTitle.isNotBlank()) rawTitle else "Поиск..."
        } else {
            "Выполнение"
        }
        tvActionTitle?.text = title
        tvActionTitle?.setTextColor(Color.parseColor(if (state.isSearching) "#FFB703" else "#34D399"))

        isSearching = state.isSearching
        val elapsed = System.currentTimeMillis() - state.searchStartTime
        currentProgressPercent = if (state.isSearching) {
            if (state.isInfiniteSearch) 50f else ((elapsed.toFloat() / state.searchTimeoutMs.toFloat()).coerceIn(0f, 1f) * 100f)
        } else 100f

        progressBarView?.invalidate()
    }
}
