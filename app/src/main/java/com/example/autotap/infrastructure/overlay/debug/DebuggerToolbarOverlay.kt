package com.example.autotap.infrastructure.overlay.debug

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import kotlin.math.abs

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class DebuggerToolbarOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager
) {

    private var toolbarView: View? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    fun show(onStepNext: () -> Unit, onStop: () -> Unit) {
        if (toolbarView != null) return

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8161B22".toColorInt(), "#F80D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(14f)
                setStroke(dp(1), "#A78BFA".toColorInt())
            }
            val p = dp(5)
            setPadding(p * 2, p, p * 2, p)
            elevation = dpF(16f)
        }

        val tvInfo = TextView(context).apply {
            text = "DBG: ШАГ"
            setTextColor("#A78BFA".toColorInt())
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setPadding(0, 0, dp(6), 0)
        }
        container.addView(tvInfo)

        val btnStep = Button(context).apply {
            text = "► 1 ШАГ"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#8B5CF6".toColorInt())
                cornerRadius = dpF(6f)
            }
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { onStepNext() }
        }
        container.addView(btnStep, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30)).apply { marginEnd = dp(4) })

        val btnStop = Button(context).apply {
            text = "■ СТОП"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#F43F5E".toColorInt())
                cornerRadius = dpF(6f)
            }
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { onStop() }
        }
        container.addView(btnStop, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30)))

        val params = overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.START
        ).apply {
            x = (dm.widthPixels / 2 - dp(100)).coerceAtLeast(dp(10))
            y = dp(80)
        }
        windowParams = params

        container.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0
            private var initY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val p = windowParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = p.x
                        initY = p.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = abs(event.rawX - touchX)
                        val dy = abs(event.rawY - touchY)
                        if (dx > dp(5) || dy > dp(5)) {
                            p.x = (initX + (event.rawX - touchX).toInt()).coerceIn(dp(6), dm.widthPixels - v.width - dp(6))
                            p.y = (initY + (event.rawY - touchY).toInt()).coerceIn(dp(20), dm.heightPixels - dp(60))
                            overlayWindowManager.updateViewSafe(v, p)
                            return true
                        }
                    }
                }
                return false
            }
        })

        toolbarView = container
        overlayWindowManager.addViewSafe(container, params)
    }

    fun dismiss() {
        toolbarView?.let {
            overlayWindowManager.removeViewSafe(it)
            toolbarView = null
            windowParams = null
        }
    }
}
