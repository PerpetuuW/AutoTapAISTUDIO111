package com.example.autotap.infrastructure.visualizer

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.autotap.core.logger.AppLogger
import java.util.concurrent.CopyOnWriteArraySet

class GestureVisualizerManager(private val context: Context) {
    @Volatile
    var isSuppressed: Boolean = false


    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeVisualizers = CopyOnWriteArraySet<View>()

    @Suppress("DEPRECATION")
    private fun getOverlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    fun showClickVisualizer(x: Float, y: Float) {
        mainHandler.post {
            try {
                val sizePx = (84 * context.resources.displayMetrics.density).toInt()
                val pulseView = ClickPulseView(context)
                val params = WindowManager.LayoutParams(
                    sizePx, sizePx, getOverlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    this.x = (x - sizePx / 2f).toInt()
                    this.y = (y - sizePx / 2f).toInt()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                pulseView.onFinishedCallback = {
                    removeVisualizerSafe(pulseView)
                }
                activeVisualizers.add(pulseView)
                windowManager.addView(pulseView, params)
            } catch (e: Exception) {
                AppLogger.logError(context, "VISUALIZER", e)
            }
        }
    }

    fun showSwipeVisualizer(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long) {
        mainHandler.post {
            try {
                val trailView = SwipeTrailView(context).apply {
                    this.startX = startX
                    this.startY = startY
                    this.endX = endX
                    this.endY = endY
                    this.durationMs = durationMs
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    getOverlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                trailView.onFinishedCallback = {
                    removeVisualizerSafe(trailView)
                }
                activeVisualizers.add(trailView)
                windowManager.addView(trailView, params)
            } catch (e: Exception) {
                AppLogger.logError(context, "VISUALIZER", e)
            }
        }
    }

    private fun removeVisualizerSafe(view: View) {
        try {
            if (activeVisualizers.remove(view) && view.isAttachedToWindow) {
                windowManager.removeView(view)
            }
        } catch (_: Exception) {}
    }

    fun removeAll() {
        mainHandler.post {
            for (view in activeVisualizers) {
                try {
                    if (view.isAttachedToWindow) {
                        windowManager.removeView(view)
                    }
                } catch (_: Exception) {}
            }
            activeVisualizers.clear()
        }
    }
}
