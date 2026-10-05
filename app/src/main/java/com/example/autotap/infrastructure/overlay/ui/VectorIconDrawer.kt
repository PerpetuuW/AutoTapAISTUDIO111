package com.example.autotap.infrastructure.overlay.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object VectorIconDrawer {

    enum class IconType {
        PLAY, PAUSE, STOP, PLUS, RECORD, CAPTURE, SCRIPTS, SETTINGS, JOYSTICK,
        LOCK, VISIBILITY, CLEAR, DRAG_HANDLE, CLOSE, WAND, SHAPE_RECT,
        SHAPE_CIRCLE, HEATMAP, STEP_NEXT, STEP_PREV, CHECK, DELETE, TEST,
        CALIBRATE, LOGS, COPY, SHARE, RESIZE, HELP, NUDGE_UP, NUDGE_DOWN,
        NUDGE_LEFT, NUDGE_RIGHT, SELECT_ALL, DESELECT_ALL, INSET_SHAVE,
        PANEL_COLLAPSE, PANEL_EXPAND, IMAGE, CONTOUR, GRAPH_TREE,
        ACTION_CLICK, ACTION_LONG_PRESS, ACTION_SWIPE, ACTION_PATH, ACTION_PINCH,
        ACTION_TRIGGER, ACTION_OCR, ACTION_COLOR, ACTION_SUBROUTINE,
        UNDO, TEMPLATE_ADD
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    fun drawIcon(canvas: Canvas, type: IconType, bounds: RectF, color: Int, strokeWidthPx: Float = 4f) {
        synchronized(this) {
            val cx = bounds.centerX()
            val cy = bounds.centerY()
            val w = bounds.width()
            val h = bounds.height()
            val r = minOf(w, h) / 2f

            fillPaint.color = color
            strokePaint.color = color
            strokePaint.strokeWidth = strokeWidthPx

            when (type) {
                IconType.UNDO -> {
                    path.rewind()
                    val p = r * 0.6f
                    // U-образная дуга возврата
                    path.moveTo(cx + p, cy + p * 0.5f)
                    path.cubicTo(cx + p, cy - p * 1.2f, cx - p, cy - p * 1.2f, cx - p, cy + p * 0.2f)
                    canvas.drawPath(path, strokePaint)

                    // Стрелка на конце дуги
                    path.rewind()
                    path.moveTo(cx - p * 1.5f, cy - p * 0.3f)
                    path.lineTo(cx - p, cy + p * 0.2f)
                    path.lineTo(cx - p * 0.5f, cy - p * 0.3f)
                    canvas.drawPath(path, strokePaint)
                }
                IconType.PLAY -> {
                    path.rewind()
                    val p = r * 0.72f
                    path.moveTo(cx - p * 0.55f, cy - p)
                    path.lineTo(cx + p * 0.9f, cy)
                    path.lineTo(cx - p * 0.55f, cy + p)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                IconType.PAUSE -> {
                    val bw = r * 0.34f
                    val bh = r * 1.25f
                    val gap = r * 0.28f
                    canvas.drawRoundRect(RectF(cx - gap - bw, cy - bh / 2f, cx - gap, cy + bh / 2f), 4f, 4f, fillPaint)
                    canvas.drawRoundRect(RectF(cx + gap, cy - bh / 2f, cx + gap + bw, cy + bh / 2f), 4f, 4f, fillPaint)
                }
                IconType.STOP -> {
                    canvas.drawRoundRect(RectF(cx - r * 0.58f, cy - r * 0.58f, cx + r * 0.58f, cy + r * 0.58f), 6f, 6f, fillPaint)
                }
                IconType.PLUS -> {
                    canvas.drawLine(cx - r * 0.75f, cy, cx + r * 0.75f, cy, strokePaint)
                    canvas.drawLine(cx, cy - r * 0.75f, cx, cy + r * 0.75f, strokePaint)
                }
                IconType.RECORD -> {
                    canvas.drawCircle(cx, cy, r * 0.55f, fillPaint)
                    canvas.drawCircle(cx, cy, r * 0.88f, strokePaint)
                }
                IconType.CAPTURE -> {
                    // [V35.1] Прецизионный видоискатель кинокамеры
                    val s = r * 0.74f
                    val c = s * 0.40f
                    // Угловые скобки
                    path.rewind()
                    path.moveTo(cx - s + c, cy - s); path.lineTo(cx - s, cy - s); path.lineTo(cx - s, cy - s + c)
                    path.moveTo(cx + s - c, cy - s); path.lineTo(cx + s, cy - s); path.lineTo(cx + s, cy - s + c)
                    path.moveTo(cx - s + c, cy + s); path.lineTo(cx - s, cy + s); path.lineTo(cx - s, cy + s - c)
                    path.moveTo(cx + s - c, cy + s); path.lineTo(cx + s, cy + s); path.lineTo(cx + s, cy + s - c)
                    canvas.drawPath(path, strokePaint)
                    // Центральный оптический сенсор
                    canvas.drawCircle(cx, cy, r * 0.22f, strokePaint)
                    canvas.drawCircle(cx, cy, strokeWidthPx * 0.8f, fillPaint)
                }
                IconType.SCRIPTS -> {
                    // [V38.0] Каскадный стек сценариев со значком исполнения ▶ (Stacked Macro Cards)
                    val wP = r * 0.54f
                    val hP = r * 0.72f
                    val off = r * 0.18f

                    path.rewind()
                    path.moveTo(cx - wP + off, cy - hP - off)
                    path.lineTo(cx + wP + off, cy - hP - off)
                    path.lineTo(cx + wP + off, cy + hP - off)
                    canvas.drawPath(path, strokePaint)

                    canvas.drawRoundRect(RectF(cx - wP - off * 0.5f, cy - hP + off * 0.5f, cx + wP - off * 0.5f, cy + hP + off * 0.5f), 4f, 4f, strokePaint)

                    path.rewind()
                    val pCx = cx - off * 0.5f
                    val pCy = cy + off * 0.5f
                    val triR = r * 0.28f
                    path.moveTo(pCx - triR * 0.6f, pCy - triR * 0.8f)
                    path.lineTo(pCx + triR * 0.9f, pCy)
                    path.lineTo(pCx - triR * 0.6f, pCy + triR * 0.8f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                IconType.SETTINGS -> {
                    // [V33.8] Настоящая промышленная 6-зубчатая шестеренка (Mechanical Gear)
                    canvas.drawCircle(cx, cy, r * 0.22f, strokePaint)
                    canvas.drawCircle(cx, cy, r * 0.54f, strokePaint)
                    val savedWidth = strokePaint.strokeWidth
                    val savedCap = strokePaint.strokeCap
                    strokePaint.strokeWidth = strokeWidthPx * 2.2f
                    strokePaint.strokeCap = Paint.Cap.BUTT
                    for (i in 0 until 6) {
                        val angle = i * (Math.PI / 3.0)
                        val cosA = kotlin.math.cos(angle).toFloat()
                        val sinA = kotlin.math.sin(angle).toFloat()
                        canvas.drawLine(
                            cx + (r * 0.48f) * cosA,
                            cy + (r * 0.48f) * sinA,
                            cx + (r * 0.82f) * cosA,
                            cy + (r * 0.82f) * sinA,
                            strokePaint
                        )
                    }
                    strokePaint.strokeWidth = savedWidth
                    strokePaint.strokeCap = savedCap
                }
                IconType.TEMPLATE_ADD -> {
                    // [V33.8] Рамка шаблона со знаком '+' в правом нижнем углу для мультипоиска
                    val b = r * 0.75f
                    val pad = r * 0.15f
                    // Контур рамки видоискателя
                    path.rewind()
                    path.moveTo(cx - b, cy - b + pad)
                    path.lineTo(cx - b, cy - b)
                    path.lineTo(cx - b + pad, cy - b)

                    path.moveTo(cx + b - pad, cy - b)
                    path.lineTo(cx + b, cy - b)
                    path.lineTo(cx + b, cy - b + pad)

                    path.moveTo(cx - b, cy + b - pad)
                    path.lineTo(cx - b, cy + b)
                    path.lineTo(cx - b + pad, cy + b)
                    canvas.drawPath(path, strokePaint)

                    // Центральная миниатюра
                    canvas.drawRect(cx - b * 0.55f, cy - b * 0.55f, cx + b * 0.15f, cy + b * 0.15f, strokePaint)

                    // Яркий знак '+' в правом нижнем углу
                    val plusCx = cx + b * 0.55f
                    val plusCy = cy + b * 0.55f
                    val plusSz = r * 0.38f
                    canvas.drawLine(plusCx - plusSz, plusCy, plusCx + plusSz, plusCy, strokePaint)
                    canvas.drawLine(plusCx, plusCy - plusSz, plusCx, plusCy + plusSz, strokePaint)
                }
                IconType.JOYSTICK -> {
                    val p = r * 0.8f
                    val b = p * 0.35f
                    path.rewind()
                    path.moveTo(cx - b, cy - p)
                    path.lineTo(cx + b, cy - p)
                    path.lineTo(cx + b, cy - b)
                    path.lineTo(cx + p, cy - b)
                    path.lineTo(cx + p, cy + b)
                    path.lineTo(cx + b, cy + b)
                    path.lineTo(cx + b, cy + p)
                    path.lineTo(cx - b, cy + p)
                    path.lineTo(cx - b, cy + b)
                    path.lineTo(cx - p, cy + b)
                    path.lineTo(cx - p, cy - b)
                    path.lineTo(cx - b, cy - b)
                    path.close()
                    canvas.drawPath(path, strokePaint)
                    canvas.drawCircle(cx, cy, strokeWidthPx * 0.75f, fillPaint)
                }
                IconType.LOCK -> {
                    // [V35.1] Прецизионный замок безопасности (контурный, без глухих пятен)
                    val bw = r * 0.60f
                    val bh = r * 0.48f
                    canvas.drawRoundRect(RectF(cx - bw, cy - bh * 0.05f, cx + bw, cy + bh * 1.15f), 6f, 6f, strokePaint)
                    canvas.drawArc(RectF(cx - bw * 0.60f, cy - bh * 0.95f, cx + bw * 0.60f, cy + bh * 0.15f), 180f, 180f, false, strokePaint)
                    canvas.drawCircle(cx, cy + bh * 0.45f, strokeWidthPx * 1.1f, fillPaint)
                }
                IconType.VISIBILITY -> {
                    // [V35.1] Элегантный глаз SF Symbols с четким зрачком
                    path.rewind()
                    path.moveTo(cx - r * 0.82f, cy)
                    path.cubicTo(cx - r * 0.40f, cy - r * 0.62f, cx + r * 0.40f, cy - r * 0.62f, cx + r * 0.82f, cy)
                    path.cubicTo(cx + r * 0.40f, cy + r * 0.62f, cx - r * 0.40f, cy + r * 0.62f, cx - r * 0.82f, cy)
                    path.close()
                    canvas.drawPath(path, strokePaint)
                    canvas.drawCircle(cx, cy, r * 0.28f, fillPaint)
                }
                IconType.CLEAR, IconType.DELETE -> {
                    // [V35.1] Студийная корзина с парящей крышкой
                    val p = r * 0.66f
                    // Крышка
                    canvas.drawLine(cx - p * 0.85f, cy - p * 0.45f, cx + p * 0.85f, cy - p * 0.45f, strokePaint)
                    canvas.drawArc(RectF(cx - p * 0.30f, cy - p * 0.80f, cx + p * 0.30f, cy - p * 0.35f), 180f, 180f, false, strokePaint)
                    // Корпус
                    path.rewind()
                    path.moveTo(cx - p * 0.65f, cy - p * 0.35f)
                    path.lineTo(cx - p * 0.50f, cy + p * 0.85f)
                    path.lineTo(cx + p * 0.50f, cy + p * 0.85f)
                    path.lineTo(cx + p * 0.65f, cy - p * 0.35f)
                    canvas.drawPath(path, strokePaint)
                    // Вертикальные ребра
                    canvas.drawLine(cx - p * 0.22f, cy - p * 0.15f, cx - p * 0.18f, cy + p * 0.65f, strokePaint)
                    canvas.drawLine(cx + p * 0.22f, cy - p * 0.15f, cx + p * 0.18f, cy + p * 0.65f, strokePaint)
                }
                IconType.DRAG_HANDLE -> {
                    // [V35.1] Четкая 6-точечная матрица захвата Apple/Figma Studio
                    val pX = r * 0.38f
                    val pY = r * 0.52f
                    val dotR = strokeWidthPx * 1.05f
                    for (dx in listOf(-pX, pX)) {
                        for (dy in listOf(-pY, 0f, pY)) {
                            canvas.drawCircle(cx + dx, cy + dy, dotR, fillPaint)
                        }
                    }
                }
                IconType.CLOSE -> {
                    val p = r * 0.60f
                    canvas.drawLine(cx - p, cy - p, cx + p, cy + p, strokePaint)
                    canvas.drawLine(cx + p, cy - p, cx - p, cy + p, strokePaint)
                }
                IconType.WAND -> {
                    val p = r * 0.75f
                    canvas.drawLine(cx - p * 0.9f, cy + p * 0.9f, cx + p * 0.5f, cy - p * 0.5f, strokePaint)
                    canvas.drawCircle(cx + p * 0.7f, cy - p * 0.7f, 3.5f, fillPaint)
                    canvas.drawCircle(cx - p * 0.2f, cy - p * 0.6f, 2.5f, fillPaint)
                    canvas.drawCircle(cx + p * 0.6f, cy + p * 0.1f, 2.5f, fillPaint)
                }
                IconType.SHAPE_RECT -> {
                    canvas.drawRoundRect(RectF(cx - r * 0.75f, cy - r * 0.7f, cx + r * 0.75f, cy + r * 0.7f), 4f, 4f, strokePaint)
                }
                IconType.SHAPE_CIRCLE -> {
                    canvas.drawCircle(cx, cy, r * 0.75f, strokePaint)
                }
                IconType.HEATMAP -> {
                    canvas.drawCircle(cx - r * 0.32f, cy, r * 0.42f, strokePaint)
                    canvas.drawCircle(cx + r * 0.32f, cy, r * 0.42f, strokePaint)
                }
                IconType.STEP_NEXT -> {
                    val p = r * 0.55f
                    path.rewind()
                    path.moveTo(cx - p * 0.5f, cy - p)
                    path.lineTo(cx + p * 0.5f, cy)
                    path.lineTo(cx - p * 0.5f, cy + p)
                    canvas.drawPath(path, strokePaint)
                }
                IconType.STEP_PREV -> {
                    val p = r * 0.55f
                    path.rewind()
                    path.moveTo(cx + p * 0.5f, cy - p)
                    path.lineTo(cx - p * 0.5f, cy)
                    path.lineTo(cx + p * 0.5f, cy + p)
                    canvas.drawPath(path, strokePaint)
                }
                IconType.CHECK -> {
                    val p = r * 0.68f
                    path.rewind()
                    path.moveTo(cx - p * 0.8f, cy)
                    path.lineTo(cx - p * 0.2f, cy + p * 0.7f)
                    path.lineTo(cx + p * 0.9f, cy - p * 0.7f)
                    canvas.drawPath(path, strokePaint)
                }
                IconType.TEST -> {
                    val p = r * 0.65f
                    path.rewind()
                    path.moveTo(cx - p * 0.5f, cy - p)
                    path.lineTo(cx + p, cy)
                    path.lineTo(cx - p * 0.5f, cy + p)
                    path.close()
                    canvas.drawPath(path, strokePaint)
                }
                IconType.CALIBRATE -> {
                    val p = r * 0.75f
                    canvas.drawCircle(cx, cy, p * 0.9f, strokePaint)
                    canvas.drawCircle(cx, cy, p * 0.35f, strokePaint)
                    canvas.drawLine(cx - p * 1.15f, cy, cx + p * 1.15f, cy, strokePaint)
                    canvas.drawLine(cx, cy - p * 1.15f, cx, cy + p * 1.15f, strokePaint)
                }
                IconType.LOGS -> {
                    // [V38.0] Системная консоль отладки и аудита (Dev Terminal Window) с шеллом '> _'
                    val wP = r * 0.82f
                    val hP = r * 0.62f
                    val corner = 4.5f

                    canvas.drawRoundRect(RectF(cx - wP, cy - hP, cx + wP, cy + hP), corner, corner, strokePaint)
                    canvas.drawLine(cx - wP, cy - hP * 0.45f, cx + wP, cy - hP * 0.45f, strokePaint)

                    val dotR = strokeWidthPx * 0.65f
                    canvas.drawCircle(cx - wP + r * 0.22f, cy - hP * 0.72f, dotR, fillPaint)
                    canvas.drawCircle(cx - wP + r * 0.42f, cy - hP * 0.72f, dotR, fillPaint)
                    canvas.drawCircle(cx - wP + r * 0.62f, cy - hP * 0.72f, dotR, fillPaint)

                    path.rewind()
                    val shY = cy + hP * 0.22f
                    val shX = cx - wP * 0.55f
                    val shSz = r * 0.22f
                    path.moveTo(shX, shY - shSz)
                    path.lineTo(shX + shSz * 0.9f, shY)
                    path.lineTo(shX, shY + shSz)
                    canvas.drawPath(path, strokePaint)

                    val curX = shX + shSz * 1.35f
                    canvas.drawLine(curX, shY + shSz, curX + shSz * 1.1f, shY + shSz, strokePaint)
                }
                IconType.COPY -> {
                    val p = r * 0.68f
                    canvas.drawRoundRect(RectF(cx - p * 0.8f, cy - p * 0.8f, cx + p * 0.35f, cy + p * 0.35f), 4f, 4f, strokePaint)
                    canvas.drawRoundRect(RectF(cx - p * 0.35f, cy - p * 0.35f, cx + p * 0.8f, cy + p * 0.8f), 4f, 4f, strokePaint)
                }
                IconType.SHARE -> {
                    val p = r * 0.72f
                    canvas.drawCircle(cx - p * 0.6f, cy, 3.5f, fillPaint)
                    canvas.drawCircle(cx + p * 0.6f, cy - p * 0.6f, 3.5f, fillPaint)
                    canvas.drawCircle(cx + p * 0.6f, cy + p * 0.6f, 3.5f, fillPaint)
                    canvas.drawLine(cx - p * 0.6f, cy, cx + p * 0.6f, cy - p * 0.6f, strokePaint)
                    canvas.drawLine(cx - p * 0.6f, cy, cx + p * 0.6f, cy + p * 0.6f, strokePaint)
                }
                IconType.RESIZE -> {
                    val p = r * 0.75f
                    canvas.drawLine(cx - p, cy + p, cx + p, cy - p, strokePaint)
                    canvas.drawLine(cx + p * 0.4f, cy - p, cx + p, cy - p, strokePaint)
                    canvas.drawLine(cx + p, cy - p * 0.4f, cx + p, cy - p, strokePaint)
                    canvas.drawLine(cx - p * 0.4f, cy + p, cx - p, cy + p, strokePaint)
                    canvas.drawLine(cx - p, cy + p * 0.4f, cx - p, cy + p, strokePaint)
                }
                IconType.HELP -> {
                    path.rewind()
                    path.moveTo(cx - r * 0.4f, cy - r * 0.45f)
                    path.quadTo(cx, cy - r * 0.85f, cx + r * 0.4f, cy - r * 0.45f)
                    path.quadTo(cx + r * 0.4f, cy - r * 0.1f, cx, cy + (0.15f * r))
                    canvas.drawPath(path, strokePaint)
                    canvas.drawCircle(cx, cy + r * 0.65f, strokeWidthPx * 0.8f, fillPaint)
                }
                IconType.NUDGE_UP -> {
                    path.rewind()
                    path.moveTo(cx - r * 0.55f, cy + r * 0.3f)
                    path.lineTo(cx, cy - r * 0.45f)
                    path.lineTo(cx + r * 0.55f, cy + r * 0.3f)
                    canvas.drawPath(path, strokePaint)
                }
                IconType.NUDGE_DOWN -> {
                    path.rewind()
                    path.moveTo(cx - r * 0.55f, cy - r * 0.3f)
                    path.lineTo(cx, cy + r * 0.45f)
                    path.lineTo(cx + r * 0.55f, cy - r * 0.3f)
                    canvas.drawPath(path, strokePaint)
                }
                IconType.NUDGE_LEFT -> {
                    path.rewind()
                    path.moveTo(cx + r * 0.3f, cy - r * 0.55f)
                    path.lineTo(cx - r * 0.45f, cy)
                    path.lineTo(cx + r * 0.3f, cy + r * 0.55f)
                    canvas.drawPath(path, strokePaint)
                }
                IconType.NUDGE_RIGHT -> {
                    path.rewind()
                    path.moveTo(cx - r * 0.3f, cy - r * 0.55f)
                    path.lineTo(cx + r * 0.45f, cy)
                    path.lineTo(cx - r * 0.3f, cy + r * 0.55f)
                    canvas.drawPath(path, strokePaint)
                }
                IconType.SELECT_ALL -> {
                    canvas.drawRoundRect(RectF(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f), 4f, 4f, strokePaint)
                    canvas.drawCircle(cx, cy, 3.5f, fillPaint)
                }
                IconType.DESELECT_ALL -> {
                    canvas.drawRoundRect(RectF(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f), 4f, 4f, strokePaint)
                    canvas.drawLine(cx - r * 0.35f, cy - r * 0.35f, cx + r * 0.35f, cy + r * 0.35f, strokePaint)
                }
                IconType.INSET_SHAVE -> {
                    val p = r * 0.75f
                    canvas.drawRect(cx - p, cy - p, cx + p, cy + p, strokePaint)
                    canvas.drawRect(cx - p * 0.45f, cy - p * 0.45f, cx + p * 0.45f, cy + p * 0.45f, strokePaint)
                }
                IconType.PANEL_COLLAPSE -> {
                    val p = r * 0.55f
                    path.rewind()
                    path.moveTo(cx - p, cy - p * 0.3f)
                    path.lineTo(cx, cy + p * 0.4f)
                    path.lineTo(cx + p, cy - p * 0.3f)
                    canvas.drawPath(path, strokePaint)
                }
                IconType.PANEL_EXPAND -> {
                    val p = r * 0.55f
                    path.rewind()
                    path.moveTo(cx - p, cy + p * 0.3f)
                    path.lineTo(cx, cy - p * 0.4f)
                    path.lineTo(cx + p, cy + p * 0.3f)
                    canvas.drawPath(path, strokePaint)
                }
                IconType.IMAGE -> {
                    val p = r * 0.75f
                    canvas.drawRoundRect(RectF(cx - p, cy - p * 0.75f, cx + p, cy + p * 0.75f), 4f, 4f, strokePaint)
                    canvas.drawCircle(cx - p * 0.4f, cy - p * 0.25f, 2.5f, fillPaint)
                    path.rewind()
                    path.moveTo(cx - p * 0.7f, cy + p * 0.45f)
                    path.lineTo(cx - p * 0.1f, cy - p * 0.1f)
                    path.lineTo(cx + p * 0.3f, cy + p * 0.25f)
                    path.lineTo(cx + p * 0.75f, cy - p * 0.2f)
                    path.lineTo(cx + p * 0.75f, cy + p * 0.55f)
                    path.lineTo(cx - p * 0.7f, cy + p * 0.55f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                IconType.CONTOUR -> {
                    val p = r * 0.75f
                    path.rewind()
                    path.moveTo(cx - p * 0.7f, cy)
                    path.quadTo(cx, cy - p * 0.8f, cx + p * 0.7f, cy)
                    path.quadTo(cx, cy + p * 0.8f, cx - p * 0.7f, cy)
                    canvas.drawPath(path, strokePaint)
                    canvas.drawCircle(cx, cy, 3f, fillPaint)
                }
                IconType.GRAPH_TREE -> {
                    val p = r * 0.75f
                    val topX = cx
                    val topY = cy - p * 0.6f
                    val b1X = cx - p * 0.65f
                    val b1Y = cy + p * 0.6f
                    val b2X = cx + p * 0.65f
                    val b2Y = cy + p * 0.6f
                    canvas.drawLine(topX, topY, b1X, b1Y, strokePaint)
                    canvas.drawLine(topX, topY, b2X, b2Y, strokePaint)
                    canvas.drawCircle(topX, topY, strokeWidthPx * 1.5f, fillPaint)
                    canvas.drawCircle(b1X, b1Y, strokeWidthPx * 1.2f, fillPaint)
                    canvas.drawCircle(b2X, b2Y, strokeWidthPx * 1.2f, fillPaint)
                }
                IconType.ACTION_CLICK -> {
                    canvas.drawCircle(cx, cy, r * 0.75f, strokePaint)
                    canvas.drawCircle(cx, cy, r * 0.32f, fillPaint)
                }
                IconType.ACTION_LONG_PRESS -> {
                    canvas.drawCircle(cx, cy, r * 0.78f, strokePaint)
                    canvas.drawCircle(cx, cy, r * 0.28f, fillPaint)
                    canvas.drawLine(cx, cy - r * 0.55f, cx, cy, strokePaint)
                    canvas.drawLine(cx, cy, cx + r * 0.35f, cy, strokePaint)
                }
                IconType.ACTION_SWIPE -> {
                    path.rewind()
                    path.moveTo(cx - r * 0.55f, cy + r * 0.55f)
                    path.lineTo(cx + r * 0.55f, cy - r * 0.55f)
                    canvas.drawPath(path, strokePaint)
                    path.rewind()
                    path.moveTo(cx + r * 0.15f, cy - r * 0.55f)
                    path.lineTo(cx + r * 0.55f, cy - r * 0.55f)
                    path.lineTo(cx + r * 0.55f, cy - r * 0.15f)
                    canvas.drawPath(path, strokePaint)
                }
                IconType.ACTION_PATH -> {
                    path.rewind()
                    path.moveTo(cx - r * 0.65f, cy + r * 0.45f)
                    path.cubicTo(cx - r * 0.2f, cy - r * 0.8f, cx + r * 0.2f, cy + r * 0.8f, cx + r * 0.65f, cy - r * 0.45f)
                    canvas.drawPath(path, strokePaint)
                    canvas.drawCircle(cx - r * 0.65f, cy + r * 0.45f, strokeWidthPx * 1.3f, fillPaint)
                    canvas.drawCircle(cx + r * 0.65f, cy - r * 0.45f, strokeWidthPx * 1.3f, fillPaint)
                }
                IconType.ACTION_PINCH -> {
                    canvas.drawLine(cx - r * 0.65f, cy - r * 0.65f, cx - r * 0.18f, cy - r * 0.18f, strokePaint)
                    canvas.drawLine(cx + r * 0.65f, cy + r * 0.65f, cx + r * 0.18f, cy + r * 0.18f, strokePaint)
                    canvas.drawCircle(cx - r * 0.65f, cy - r * 0.65f, strokeWidthPx * 1.2f, fillPaint)
                    canvas.drawCircle(cx + r * 0.65f, cy + r * 0.65f, strokeWidthPx * 1.2f, fillPaint)
                }
                IconType.ACTION_TRIGGER -> {
                    val s = r * 0.72f
                    val c = s * 0.45f
                    canvas.drawLine(cx - s, cy - s, cx - s + c, cy - s, strokePaint)
                    canvas.drawLine(cx - s, cy - s, cx - s, cy - s + c, strokePaint)
                    canvas.drawLine(cx + s, cy - s, cx + s - c, cy - s, strokePaint)
                    canvas.drawLine(cx + s, cy - s, cx + s, cy - s + c, strokePaint)
                    canvas.drawLine(cx - s, cy + s, cx - s + c, cy + s, strokePaint)
                    canvas.drawLine(cx - s, cy + s, cx - s, cy + s - c, strokePaint)
                    canvas.drawLine(cx + s, cy + s, cx + s - c, cy + s, strokePaint)
                    canvas.drawLine(cx + s, cy + s, cx + s, cy + s - c, strokePaint)
                    canvas.drawCircle(cx, cy, r * 0.25f, fillPaint)
                }
                IconType.ACTION_OCR -> {
                    val bw = r * 0.75f
                    val bh = r * 0.88f
                    canvas.drawRoundRect(RectF(cx - bw, cy - bh, cx + bw, cy + bh), 4f, 4f, strokePaint)
                    canvas.drawLine(cx - bw * 0.55f, cy - bh * 0.45f, cx + bw * 0.55f, cy - bh * 0.45f, strokePaint)
                    canvas.drawLine(cx, cy - bh * 0.45f, cx, cy + bh * 0.55f, strokePaint)
                }
                IconType.ACTION_COLOR -> {
                    canvas.drawCircle(cx, cy, r * 0.75f, strokePaint)
                    val p = r * 0.45f
                    canvas.drawRoundRect(RectF(cx - p, cy - p, cx + p, cy + p), 6f, 6f, fillPaint)
                }
                IconType.ACTION_SUBROUTINE -> {
                    canvas.drawLine(cx - r * 0.5f, cy, cx + r * 0.5f, cy, strokePaint)
                    canvas.drawLine(cx + r * 0.5f, cy, cx + r * 0.2f, cy - r * 0.35f, strokePaint)
                    canvas.drawLine(cx + r * 0.5f, cy, cx + r * 0.2f, cy + r * 0.35f, strokePaint)
                    canvas.drawCircle(cx - r * 0.5f, cy, strokeWidthPx * 1.5f, fillPaint)
                }
            }
        }
    }
}
