package com.example.autotap.infrastructure.overlay.graph

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.MacroAction
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class GraphOverlayView(context: Context) : View(context) {

    private val dm = context.resources.displayMetrics
    private fun dpF(v: Float) = v * dm.density

    private val swipeVectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(2.5f)
        color = Color.parseColor("#58A6FF")
        pathEffect = DashPathEffect(floatArrayOf(dpF(7f), dpF(4f)), 0f)
    }

    private val swipeArrowHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#58A6FF")
    }

    private val matchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(2f)
        color = Color.parseColor("#10B981")
        pathEffect = DashPathEffect(floatArrayOf(dpF(6f), dpF(4f)), 0f)
    }

    private val timeoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444")
        strokeWidth = dpF(2f)
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(dpF(4f), dpF(4f)), 0f)
    }

    // [V90.0] Неоновый рендеринг кривых траектории PATH на экране
    private val pathStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A855F7")
        strokeWidth = dpF(3.5f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pathDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.FILL
    }

    private val arrowFillMatch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#10B981")
    }

    private val arrowFillTimeout = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F97316")
    }

    private val reusableCurvePath = Path()
    private val reusableHeadPath = Path()

    @Volatile
    private var actionsSnapshot: List<MacroAction> = emptyList()

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun submitActions(actions: List<MacroAction>) {
        actionsSnapshot = ArrayList(actions)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val actions = actionsSnapshot
        if (actions.isEmpty()) return

        for (action in actions) {
            if (action.type == ActionType.SWIPE && action.endX != null && action.endY != null) {
                drawDirectArrow(canvas, action.posX, action.posY, action.endX, action.endY, swipeVectorPaint, swipeArrowHeadPaint)
            } else if (action.type == ActionType.PATH && action.pathPoints.size >= 2) {
                // [V90.0] Отрисовка непрерывной кривой пути на экране

                val pts = action.pathPoints
                reusableCurvePath.rewind()
                reusableCurvePath.moveTo(pts[0].x, pts[0].y)
                if (pts.size == 3) {
                    reusableCurvePath.quadTo(pts[1].x, pts[1].y, pts[2].x, pts[2].y)
                } else {
                    for (i in 1 until pts.size) {
                        reusableCurvePath.lineTo(pts[i].x, pts[i].y)
                    }
                }
                canvas.drawPath(reusableCurvePath, pathStrokePaint)
                canvas.drawCircle(pts[0].x, pts[0].y, dpF(5f), pathDotPaint)
                canvas.drawCircle(pts.last().x, pts.last().y, dpF(6f), swipeArrowHeadPaint)
            }
        }

        if (actions.size < 2) return
        for (action in actions) {
            val x1 = action.posX
            val y1 = action.posY

            val matchTargetId = action.jumpToStepOnMatch
            if (matchTargetId != null && matchTargetId in 1..actions.size && matchTargetId != action.id) {
                val target = actions[matchTargetId - 1]
                drawCurvedArrow(canvas, x1, y1, target.posX, target.posY, matchPaint, arrowFillMatch, dpF(20f))
            }

            val timeoutTargetId = action.jumpToStepOnTimeout
            if (timeoutTargetId != null && timeoutTargetId in 1..actions.size && timeoutTargetId != action.id) {
                val target = actions[timeoutTargetId - 1]
                drawCurvedArrow(canvas, x1, y1, target.posX, target.posY, timeoutPaint, arrowFillTimeout, -dpF(20f))
            }
        }
    }

    private fun drawDirectArrow(
        canvas: Canvas,
        x1: Float, y1: Float, x2: Float, y2: Float,
        linePaint: Paint, arrowPaint: Paint
    ) {
        val dx = x2 - x1
        val dy = y2 - y1
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist < dpF(4f)) return

        canvas.drawLine(x1, y1, x2, y2, linePaint)

        val angle = atan2(dy.toDouble(), dx.toDouble())
        val arrowHeadLen = dpF(10f)
        val arrowAngle = Math.PI / 6.0

        val p1X = (x2 - arrowHeadLen * cos(angle - arrowAngle)).toFloat()
        val p1Y = (y2 - arrowHeadLen * sin(angle - arrowAngle)).toFloat()
        val p2X = (x2 - arrowHeadLen * cos(angle + arrowAngle)).toFloat()
        val p2Y = (y2 - arrowHeadLen * sin(angle + arrowAngle)).toFloat()

        reusableHeadPath.rewind()
        reusableHeadPath.moveTo(x2, y2)
        reusableHeadPath.lineTo(p1X, p1Y)
        reusableHeadPath.lineTo(p2X, p2Y)
        reusableHeadPath.close()
        canvas.drawPath(reusableHeadPath, arrowPaint)
    }

    private fun drawCurvedArrow(
        canvas: Canvas,
        x1: Float, y1: Float, x2: Float, y2: Float,
        linePaint: Paint, arrowPaint: Paint,
        curvature: Float
    ) {
        val dx = x2 - x1
        val dy = y2 - y1
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist < dpF(5f)) return

        val normX = -dy / dist
        val normY = dx / dist
        val midX = (x1 + x2) / 2f + normX * curvature
        val midY = (y1 + y2) / 2f + normY * curvature

        reusableCurvePath.rewind()
        reusableCurvePath.moveTo(x1, y1)
        reusableCurvePath.quadTo(midX, midY, x2, y2)
        canvas.drawPath(reusableCurvePath, linePaint)

        val angle = atan2((y2 - midY).toDouble(), (x2 - midX).toDouble())
        val arrowHeadLen = dpF(9f)
        val arrowAngle = Math.PI / 6.0

        val p1X = (x2 - arrowHeadLen * cos(angle - arrowAngle)).toFloat()
        val p1Y = (y2 - arrowHeadLen * sin(angle - arrowAngle)).toFloat()
        val p2X = (x2 - arrowHeadLen * cos(angle + arrowAngle)).toFloat()
        val p2Y = (y2 - arrowHeadLen * sin(angle + arrowAngle)).toFloat()

        reusableHeadPath.rewind()
        reusableHeadPath.moveTo(x2, y2)
        reusableHeadPath.lineTo(p1X, p1Y)
        reusableHeadPath.lineTo(p2X, p2Y)
        reusableHeadPath.close()
        canvas.drawPath(reusableHeadPath, arrowPaint)
    }
}
