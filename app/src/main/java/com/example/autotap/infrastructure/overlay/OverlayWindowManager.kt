package com.example.autotap.infrastructure.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.infrastructure.accessibility.AutoTapAccessibilityService
import java.util.concurrent.ConcurrentHashMap

class OverlayWindowManager(private val context: Context) {

    private val activeViews = ConcurrentHashMap<View, Pair<WindowManager, WindowManager.LayoutParams>>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getActiveWindowManager(requiresKeyboard: Boolean = false): Pair<WindowManager, Int>? {
        val appWm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val hasOverlayPermission = Settings.canDrawOverlays(context)

        if ((requiresKeyboard || hasOverlayPermission) && hasOverlayPermission && appWm != null) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            return Pair(appWm, type)
        }

        val service = AutoTapAccessibilityService.instance
        if (service != null) {
            val serviceWm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (serviceWm != null) {
                return Pair(serviceWm, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            }
        }

        if (hasOverlayPermission && appWm != null) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            return Pair(appWm, type)
        }

        return null
    }

    fun getOverlayType(requiresKeyboard: Boolean = false): Int {
        return getActiveWindowManager(requiresKeyboard)?.second ?: WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
    }

    fun createLayoutParams(
        width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        flags: Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        gravity: Int = Gravity.TOP or Gravity.START
    ): WindowManager.LayoutParams {
        val windowType = getOverlayType(false)
        return WindowManager.LayoutParams(width, height, windowType, flags, PixelFormat.TRANSLUCENT).apply {
            this.gravity = gravity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    @Suppress("DEPRECATION")
    fun createDialogLayoutParams(
        width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        gravity: Int = Gravity.CENTER
    ): WindowManager.LayoutParams {
        // ИНВАРИАНТ 1: Модальные окна/Диалоги строго TYPE_APPLICATION_OVERLAY.
        // Ввод текста и клики в TYPE_ACCESSIBILITY_OVERLAY заблокированы на новых версиях Android.
        val windowType = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        return WindowManager.LayoutParams(width, height, windowType, flags, android.graphics.PixelFormat.TRANSLUCENT).apply {
            this.gravity = gravity
            this.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    fun addViewSafe(view: View?, params: WindowManager.LayoutParams) {
        if (view == null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            executeAddView(view, params)
        } else {
            mainHandler.post { executeAddView(view, params) }
        }
    }

    private fun executeAddView(view: View, params: WindowManager.LayoutParams) {
        try {
            val needsKeyboard = (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0
            val target = getActiveWindowManager(needsKeyboard)
            if (target == null) {
                AppLogger.log(context, "OVERLAY_WM", "Отказ добавления окна: нет разрешения и служба не активна")
                Toast.makeText(context, "Включите службу кликера или разрешите показ поверх окон", Toast.LENGTH_LONG).show()
                return
            }

            val (wm, windowType) = target
            params.type = windowType

            if (view.parent == null && !activeViews.containsKey(view)) {
                wm.addView(view, params)
                activeViews[view] = Pair(wm, params)
            } else {
                executeUpdateView(view, params)
            }
        } catch (e: Exception) {
            AppLogger.logError(context, "OVERLAY_WM", e)
            activeViews.remove(view)
        }
    }

    fun removeViewSafe(view: View?) {
        if (view == null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            executeRemoveView(view)
        } else {
            mainHandler.post { executeRemoveView(view) }
        }
    }

    private fun executeRemoveView(view: View) {
        try {
            val record = activeViews[view]
            if (record != null) {
                record.first.removeView(view)
            } else if (view.parent != null) {
                val fallback = getActiveWindowManager()?.first ?: (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                fallback?.removeView(view)
            }
        } catch (e: Exception) {
            AppLogger.logError(context, "OVERLAY_WM", e)
        } finally {
            activeViews.remove(view)
        }
    }

    fun updateViewSafe(view: View?, params: WindowManager.LayoutParams) {
        if (view == null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            executeUpdateView(view, params)
        } else {
            mainHandler.post { executeUpdateView(view, params) }
        }
    }

    private fun executeUpdateView(view: View, params: WindowManager.LayoutParams) {
        if (view.parent == null) return
        try {
            val record = activeViews[view]
            val wm = record?.first ?: getActiveWindowManager()?.first ?: return
            params.type = record?.second?.type ?: params.type
            wm.updateViewLayout(view, params)
            activeViews[view] = Pair(wm, params)
        } catch (e: Exception) {
            AppLogger.logError(context, "OVERLAY_WM", e)
        }
    }

    fun removeAll() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            executeRemoveAll()
        } else {
            mainHandler.post { executeRemoveAll() }
        }
    }

    private fun executeRemoveAll() {
        activeViews.keys.forEach { executeRemoveView(it) }
        activeViews.clear()
    }
}
