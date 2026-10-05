package com.example.autotap.infrastructure.visualizer

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import java.util.concurrent.atomic.AtomicBoolean

object LaserScanVisualizer {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun showSweepLaser(context: Context, overlayWindowManager: OverlayWindowManager, onFinished: () -> Unit) {
        val dm = context.resources.displayMetrics
        val screenH = dm.heightPixels.toFloat()
        val isCompleted = AtomicBoolean(false)

        val container = FrameLayout(context)
        val laserLine = View(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#00F5D4"))
            }
            elevation = 16f
        }
        container.addView(laserLine, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (3 * dm.density).toInt()).apply {
            gravity = Gravity.TOP or Gravity.START
        })

        val params = overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        overlayWindowManager.addViewSafe(container, params)

        fun completeSweep() {
            if (isCompleted.compareAndSet(false, true)) {
                try {
                    overlayWindowManager.removeViewSafe(container)
                } catch (_: Exception) {}
                onFinished()
            }
        }

        mainHandler.postDelayed({
            completeSweep()
        }, 400L)

        laserLine.animate()
            .translationY(screenH)
            .setDuration(340)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction {
                completeSweep()
            }
            .start()
    }
}
