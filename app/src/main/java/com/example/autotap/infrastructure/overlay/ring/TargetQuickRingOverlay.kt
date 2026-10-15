package com.example.autotap.infrastructure.overlay.ring

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.graphics.toColorInt
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class TargetQuickRingOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager
) {

    private var activeContainerView: View? = null
    var activeActionId: Int? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

        fun updatePosition(centerX: Float, centerY: Float) {
        val root = activeContainerView ?: return
        val orbitRadius = dpF(64f)
        val btnSize = dp(38)
        val containerSize = ((orbitRadius + btnSize) * 2).toInt()
        val screenW = dm.widthPixels.toFloat()
        val screenH = dm.heightPixels.toFloat()

        val winX = (centerX - containerSize / 2f).coerceIn(dpF(4f), screenW - containerSize - dpF(4f)).toInt()
        val winY = (centerY - containerSize / 2f).coerceIn(dpF(25f), screenH - containerSize - dpF(25f)).toInt()

        val lp = root.layoutParams as? WindowManager.LayoutParams ?: return
        lp.x = winX
        lp.y = winY
        overlayWindowManager.updateViewSafe(root, lp)
    }

    fun isShowingFor(actionId: Int): Boolean {
        return activeContainerView != null && activeActionId == actionId
    }

    fun show(
        action: MacroAction,
        centerX: Float,
        centerY: Float,
        onEdit: (MacroAction) -> Unit,
        onAddTemplate: (MacroAction) -> Unit,
        onSwapRequested: ((MacroAction) -> Unit)? = null,
        onCalibrate: (MacroAction) -> Unit,
        onClone: (MacroAction) -> Unit,
        onDelete: (MacroAction) -> Unit,
        onScripts: ((MacroAction) -> Unit)? = null
    ) {
        dismiss()

        activeActionId = action.id
        val orbitRadius = dpF(64f)
        val btnSize = dp(38)
        val containerSize = ((orbitRadius + btnSize) * 2).toInt()
        val screenW = dm.widthPixels.toFloat()
        val screenH = dm.heightPixels.toFloat()

        val root = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        activeContainerView = root

        val buttonsConfig = mutableListOf(
            Triple(VectorIconDrawer.IconType.SETTINGS, Pair("#1F6FEB", "#38BDF8")) { dismiss(); onEdit(action) },
            Triple(VectorIconDrawer.IconType.TEMPLATE_ADD, Pair("#1E293B", "#34D399")) { dismiss(); onAddTemplate(action) },
            Triple(VectorIconDrawer.IconType.CALIBRATE, Pair("#1E293B", "#A78BFA")) { dismiss(); onCalibrate(action) },
            Triple(VectorIconDrawer.IconType.SAVE, Pair("#1E293B", "#10B981")) { 
                dismiss()
                val logDialog = com.example.autotap.infrastructure.overlay.dialog.DebugLogViewerDialog(context)
                logDialog.show()
            },
            Triple(VectorIconDrawer.IconType.PLUS, Pair("#21262D", "#58A6FF")) { dismiss(); onClone(action) },
            Triple(VectorIconDrawer.IconType.DELETE, Pair("#2A0E0C", "#F04438")) { dismiss(); onDelete(action) }
        )
        if (onScripts != null) {
            buttonsConfig.add(Triple(VectorIconDrawer.IconType.SCRIPTS, Pair("#0D2E3A", "#38BDF8")) { dismiss(); onScripts(action) })
        }

        val count = buttonsConfig.size
        val angleStep = 360.0 / count
        val baseAnglesDeg = (0 until count).map { i -> -90.0 + (i * angleStep) }

        for (i in buttonsConfig.indices) {
            val (iconType, colors, onClick) = buttonsConfig[i]
            val angleRad = Math.toRadians(baseAnglesDeg[i])

            val finalOffsetX = orbitRadius * cos(angleRad).toFloat()
            val finalOffsetY = orbitRadius * sin(angleRad).toFloat()

            val btn = createRadialButton(iconType, colors.first, colors.second) {
                onClick()
            }

            val lp = FrameLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.CENTER
            }
            btn.translationX = 0f
            btn.translationY = 0f
            root.addView(btn, lp)

            btn.alpha = 0f
            btn.scaleX = 0.3f
            btn.scaleY = 0.3f
            btn.animate()
                .translationX(finalOffsetX)
                .translationY(finalOffsetY)
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(220)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.3f))
                .start()
        }

        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val centerOffset = containerSize / 2f
                val distFromCenter = hypot((event.x - centerOffset).toDouble(), (event.y - centerOffset).toDouble()).toFloat()
                // Расширенный допуск: закрытие происходит только при явном клике мимо всех кнопок
                if (distFromCenter < dpF(16f) || distFromCenter > orbitRadius + dpF(38f)) {
                    dismiss()
                    return@setOnTouchListener true
                }
            }
            false
        }

        val winX = (centerX - containerSize / 2f).coerceIn(dpF(4f), screenW - containerSize - dpF(4f)).toInt()
        val winY = (centerY - containerSize / 2f).coerceIn(dpF(25f), screenH - containerSize - dpF(25f)).toInt()

        val params = overlayWindowManager.createLayoutParams(
            width = containerSize,
            height = containerSize,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            gravity = Gravity.TOP or Gravity.START
        ).apply {
            x = winX
            y = winY
        }

        overlayWindowManager.addViewSafe(root, params)

        val dismissTask = Runnable {
            if (activeContainerView == root) {
                dismiss()
            }
        }
        autoDismissRunnable = dismissTask
        mainHandler.postDelayed(dismissTask, 5000L)
    }

    fun dismiss() {
        autoDismissRunnable?.let {
            mainHandler.removeCallbacks(it)
            autoDismissRunnable = null
        }
        activeActionId = null
        activeContainerView?.let {
            overlayWindowManager.removeViewSafe(it)
            activeContainerView = null
        }
    }

    private fun createRadialButton(type: VectorIconDrawer.IconType, bgHex: String, iconHex: String, onClick: () -> Unit): View {
        return object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(8f), dpF(8f), width - dpF(8f), height - dpF(8f))
                VectorIconDrawer.drawIcon(canvas, type, bounds, iconHex.toColorInt(), dpF(2f))
            }
        }.apply {
            outlineProvider = ViewOutlineProvider.BACKGROUND
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(bgHex.toColorInt(), "#161B22".toColorInt())
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), iconHex.toColorInt())
            }
            elevation = dpF(8f)
            setOnClickListener { onClick() }
        }
    }
}
