package com.example.autotap.infrastructure.overlay.graph


import android.text.TextUtils
import android.text.TextPaint
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.graph.TriggerBehavior

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.toColorInt
import com.example.autotap.domain.model.graph.GraphMacroScenario
import com.example.autotap.domain.model.graph.ScenarioEdge
import com.example.autotap.domain.model.graph.ScenarioNode
import java.util.UUID
import kotlin.math.hypot

@SuppressLint("ClickableViewAccessibility")
class GraphCanvasView(context: Context) : View(context) {
    // =========================================================================
    // MASTER PROTOCOL v64.0: ERGONOMIC GRAPH ENGINE (BEZIER, PORTS & FIT)
    // =========================================================================

    private val bezierPath = android.graphics.Path()
    private val cardClipPath = android.graphics.Path()
    private val edgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    private fun safeEllipsize(text: String?, paint: android.graphics.Paint, maxWidth: Float): String {
        if (text.isNullOrEmpty()) return ""
        val safeW = if (maxWidth > 10f) maxWidth else 10f
        return android.text.TextUtils.ellipsize(
            text,
            if (paint is android.text.TextPaint) paint else android.text.TextPaint(paint),
            safeW,
            android.text.TextUtils.TruncateAt.END
        ).toString()
    }

    /**
     * Draws a smooth horizontal cubic Bezier curve between two nodes.
     */
    fun drawSmoothBezierEdge(
        canvas: android.graphics.Canvas,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        colorHex: Int = 0xFFA78BFA.toInt(),
        strokeWidthDp: Float = 2.5f
    ) {
        val density = resources.displayMetrics.density
        val dx = kotlin.math.abs(endX - startX) * 0.5f
        val curvature = dx.coerceAtLeast(32f * density)

        bezierPath.reset()
        bezierPath.moveTo(startX, startY)
        bezierPath.cubicTo(
            startX + curvature, startY,
            endX - curvature, endY,
            endX, endY
        )

        edgePaint.color = colorHex
        edgePaint.strokeWidth = strokeWidthDp * density
        canvas.drawPath(bezierPath, edgePaint)
    }

    /**
     * Magnetic Snap: Detects if dragging coordinates are within snap radius (28dp) of a port.
     */
    fun getMagneticSnapPoint(targetX: Float, targetY: Float, portX: Float, portY: Float, snapRadiusDp: Float = 28f): android.graphics.PointF? {
        val density = resources.displayMetrics.density
        val radiusPx = snapRadiusDp * density
        val distSq = (targetX - portX) * (targetX - portX) + (targetY - portY) * (targetY - portY)
        return if (distSq <= radiusPx * radiusPx) {
            android.graphics.PointF(portX, portY)
        } else null
    }

    /**
     * Snap to 16dp grid for clean alignment during node drag.
     */
    fun snapCoordinateToGrid(coord: Float, gridSizeDp: Float = 16f): Float {
        val gridPx = gridSizeDp * resources.displayMetrics.density
        return kotlin.math.round(coord / gridPx) * gridPx
    }




    var scenario: GraphMacroScenario? = null
        private set


        var onScenarioModified: (() -> Unit)? = null
        var onNodeSelected: ((ScenarioNode) -> Unit)? = null
        var onTemplateThumbnailClicked: ((ScenarioNode, Int) -> Unit)? = null
        var onWireToEmptySpace: ((fromNodeId: String, fromPortId: String, worldX: Float, worldY: Float) -> Unit)? = null

    fun resetViewToFit() {
        val sc = scenario ?: return
        if (sc.nodes.isEmpty()) {
            panX = 40f
            panY = 100f
            scaleFactor = 1.0f
        } else {
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
            sc.nodes.values.forEach {
                if (it.canvasX < minX) minX = it.canvasX
                if (it.canvasY < minY) minY = it.canvasY
                val r = it.canvasX + nodeWidth
                val b = it.canvasY + calculateHeight(it)
                if (r > maxX) maxX = r
                if (b > maxY) maxY = b
            }
            // [V70.0] Холст полностью изолирован: отступы оптимизированы под чистый видовой экран
            val marginH = dpF(24f)
            val topClearance = dpF(24f)
            val w = (width - marginH * 2).coerceAtLeast(100f)
            val h = (height - topClearance * 2).coerceAtLeast(100f)
            val graphW = (maxX - minX).coerceAtLeast(100f)
            val graphH = (maxY - minY).coerceAtLeast(100f)
            scaleFactor = (minOf(w / graphW, h / graphH)).coerceIn(0.35f, 1.2f)
            panX = (width - graphW * scaleFactor) / 2f - minX * scaleFactor
            panY = topClearance + ((h - graphH * scaleFactor) / 2f).coerceAtLeast(0f) - minY * scaleFactor
        }
        updateMatrix()
        invalidate()
    }

    fun autoAlignNodes() {
        val sc = scenario ?: return
        if (sc.nodes.isEmpty()) return

        val entryId = sc.entryNodeId ?: sc.nodes.keys.firstOrNull() ?: return
        val visited = mutableSetOf<String>()
        val levels = mutableMapOf<String, Int>()

        // Поиск в ширину для расчета колонок от корня
        val queue = java.util.ArrayDeque<Pair<String, Int>>()
        queue.add(Pair(entryId, 0))
        visited.add(entryId)
        levels[entryId] = 0

        while (queue.isNotEmpty()) {
            val (currentId, level) = queue.poll()
            val outEdges = sc.edges.filter { it.fromNodeId == currentId }
            for (edge in outEdges) {
                if (edge.toNodeId !in visited && edge.toNodeId in sc.nodes) {
                    visited.add(edge.toNodeId)
                    levels[edge.toNodeId] = level + 1
                    queue.add(Pair(edge.toNodeId, level + 1))
                }
            }
        }

        var maxLevel = (levels.values.maxOrNull() ?: 0) + 1
        for (nodeId in sc.nodes.keys) {
            if (nodeId !in levels) {
                levels[nodeId] = maxLevel++
            }
        }

        val levelGroups = mutableMapOf<Int, MutableList<String>>()
        for ((nodeId, level) in levels) {
            levelGroups.getOrPut(level) { mutableListOf() }.add(nodeId)
        }

        val colSpacing = dpF(340f)
        val rowSpacing = dpF(220f)
        val startX = dpF(80f)
        val startY = dpF(80f)

        val updatedNodes = HashMap(sc.nodes)
        for ((level, nodesInLevel) in levelGroups) {
            val colX = startX + level * colSpacing
            for ((rowIdx, nodeId) in nodesInLevel.withIndex()) {
                val node = updatedNodes[nodeId] ?: continue
                val rowY = startY + rowIdx * rowSpacing
                updatedNodes[nodeId] = node.copy(canvasX = colX, canvasY = rowY)
            }
        }

        scenario = sc.copy(nodes = updatedNodes)
        onScenarioModified?.invoke()
        resetViewToFit()
    }

    private val dm = context.resources.displayMetrics
    private fun dpF(v: Float) = v * dm.density

    private val canvasMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private var scaleFactor = 1.0f
    private var panX = 40f
    private var panY = 100f

    // [V50.0] АБСОЛЮТНАЯ НАУЧНАЯ ЭРГОНОМИКА (Unreal Engine Blueprint Style)
    private val nodeWidth get() = dpF(280f)
    private val headerHeight get() = dpF(44f)
    private val portRadius get() = dpF(9f)
    private val portRowHeight get() = dpF(36f)
    private val triggerRowHeight get() = dpF(112f)

    // [V60.0] Научная двухуровневая сетка (Major + Minor)
    private val paintMajorGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1838BDF8.toInt(); strokeWidth = dpF(1.2f) }
    private val paintMinorGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x10FFFFFF.toInt(); strokeWidth = dpF(2f); strokeCap = Paint.Cap.ROUND }
    private val paintShadowAmbient = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x30000000.toInt(); style = Paint.Style.FILL }
    private val paintShadowKey = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x50000000.toInt(); style = Paint.Style.FILL }
    private val curveShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dpF(5f); strokeCap = Paint.Cap.ROUND; color = 0x80050811.toInt() }

    private var draggedNodeId: String? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isDraggingNode = false
    private var isPanningCanvas = false
    private var lastPanRawX = 0f
    private var lastPanRawY = 0f

    private var isWiring = false
    private var isWiringReverse = false
    private var wiringToNodeId: String? = null
    private var wiringFromNodeId: String? = null
    private var wiringFromPortId: String? = null
    private var wiringStartX = 0f
    private var wiringStartY = 0f
    private var wiringCurrentX = 0f
    private var wiringCurrentY = 0f

    // [V60.0] Профессиональная палитра Material 3 Cyber-Nodes
    private val paintNodeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#F20B0F17".toColorInt(); style = Paint.Style.FILL }
    private val paintNodeHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dpF(1.2f) }
    private val paintTextTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#FFFFFF".toColorInt(); textSize = dpF(13f); typeface = Typeface.DEFAULT_BOLD }
    private val paintTextSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#94A3B8".toColorInt(); textSize = dpF(11f); typeface = Typeface.DEFAULT_BOLD }

    // [V60.0] Семантическая типизация шапок узлов (Unreal Engine Blueprint Typology)
    data class NodeTheme(val headerStart: Int, val headerEnd: Int, val border: Int)

    private fun getNodeTheme(node: ScenarioNode): NodeTheme {
        val isStartNode = node.id == "node_start" || node.title == "СТАРТ" || (scenario?.entryNodeId == node.id && node.standardPorts.contains("out_default"))
        return when {
            isStartNode -> {
                // Изумрудный неон: Постоянная точка старта сценария (Root Node)
                NodeTheme("#065F46".toColorInt(), "#022C22".toColorInt(), "#10B981".toColorInt())
            }
            node.triggers.isNotEmpty() -> {
                // Изумрудно-бирюзовый: Компьютерное зрение / AI Детекция (Event/Decision)
                NodeTheme("#064E3B".toColorInt(), "#022C22".toColorInt(), "#059669".toColorInt())
            }
            node.standardPorts.contains("out_loop_body") || node.standardPorts.contains("out_loop_done") -> {
                // Пурпурный: Управление потоком и циклы (Flow Control)
                NodeTheme("#581C87".toColorInt(), "#3B0764".toColorInt(), "#9333EA".toColorInt())
            }
            node.entryActions.any { it.type == ActionType.GLOBAL_BACK || it.type == ActionType.GLOBAL_HOME } -> {
                // Кораллово-рубиновый: Системные прерывания
                NodeTheme("#881337".toColorInt(), "#4C0519".toColorInt(), "#E11D48".toColorInt())
            }
            node.entryActions.isNotEmpty() -> {
                // Сапфировый синий: Физические действия (Click/Swipe/Gesture)
                NodeTheme("#1E3A8A".toColorInt(), "#172554".toColorInt(), "#2563EB".toColorInt())
            }
            else -> {
                // Нейтральный титановый сланцевый: Общие фазы
                NodeTheme("#1E293B".toColorInt(), "#0F172A".toColorInt(), "#475569".toColorInt())
            }
        }
    }

    // [Мост для туториала] - передает экранные координаты отрисованных элементов
    fun getTutorialRect(tag: String): android.graphics.Rect? {
        val sc = scenario ?: return null
        val firstNode = sc.nodes.values.firstOrNull() ?: return null
        val rectF = RectF()
        when (tag) {
            "NODE_HEADER" -> rectF.set(firstNode.canvasX, firstNode.canvasY, firstNode.canvasX + nodeWidth, firstNode.canvasY + headerHeight)
            "NODE_PORT" -> {
                val p = getPortPos(firstNode, "in_entry", false)
                rectF.set(p.x - dpF(20f), p.y - dpF(20f), p.x + dpF(20f), p.y + dpF(20f))
            }
            "NODE_PREVIEW" -> {
                if (firstNode.triggers.isNotEmpty()) {
                    val py = firstNode.canvasY + headerHeight + dpF(16f)
                    rectF.set(firstNode.canvasX + dpF(16f), py, firstNode.canvasX + dpF(16f) + dpF(96f), py + dpF(96f))
                } else return null
            }
            else -> return null
        }
        val pts = floatArrayOf(rectF.left, rectF.top, rectF.right, rectF.bottom)
        canvasMatrix.mapPoints(pts)
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return android.graphics.Rect((loc[0] + pts[0]).toInt(), (loc[1] + pts[1]).toInt(), (loc[0] + pts[2]).toInt(), (loc[1] + pts[3]).toInt())
    }
    private val paintEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#38BDF8".toColorInt(); style = Paint.Style.STROKE; strokeWidth = dpF(2.5f); strokeCap = Paint.Cap.ROUND }

    private val paintActiveWiring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#38BDF8".toColorInt(); style = Paint.Style.STROKE; strokeWidth = dpF(3f); strokeCap = Paint.Cap.ROUND }
    private val paintPortIn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#38BDF8".toColorInt(); style = Paint.Style.FILL }
    private val paintPortOut = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#10B981".toColorInt(); style = Paint.Style.FILL }

    // [V28.1] Предвыделенные структуры Zero-Allocation для устранения аллокаций в onDraw
    private val wiringP1 = PointF()
    private val wiringP2 = PointF()
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val previewRect = RectF()
    private val cachedNodeRect = RectF()
    private val cachedHeadRect = RectF()
    private val cachedTargetFitRect = RectF()

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prevScale = scaleFactor
            scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(0.35f, 2.5f)
            val factor = scaleFactor / prevScale
            panX = detector.focusX - (detector.focusX - panX) * factor
            panY = detector.focusY - (detector.focusY - panY) * factor
            updateMatrix()
            invalidate()
            return true
        }
    })

    init { updateMatrix() }

    fun setGraphScenario(newScenario: GraphMacroScenario) {
        scenario = newScenario
        invalidate()
    }

    private fun updateMatrix() {
        canvasMatrix.reset()
        canvasMatrix.postScale(scaleFactor, scaleFactor)
        canvasMatrix.postTranslate(panX, panY)
        canvasMatrix.invert(inverseMatrix)
    }

        override fun onDraw(canvas: Canvas) {
        val sc = scenario ?: return
        try {
            // [V60.0] Архитектурная двухуровневая сетка холста (Major + Minor LOD)
            val w = width.toFloat()
            val h = height.toFloat()
            val minorStep = dpF(24f) * scaleFactor
            val majorStep = minorStep * 5f

            // Точечная суб-сетка (Minor Grid) с плавным LOD-затуханием при отдалении
            if (scaleFactor > 0.45f) {
                val alphaRatio = ((scaleFactor - 0.45f) / 0.55f).coerceIn(0f, 1f)
                paintMinorGrid.alpha = (alphaRatio * 32).toInt()
                val minX = panX % minorStep
                val minY = panY % minorStep
                var x = minX
                while (x < w) {
                    var y = minY
                    while (y < h) {
                        canvas.drawPoint(x, y, paintMinorGrid)
                        y += minorStep
                    }
                    x += minorStep
                }
            }

            // Магистральная сетка кластеризации (Major Grid)
            val majX = panX % majorStep
            val majY = panY % majorStep
            var mx = majX
            while (mx < w) { canvas.drawLine(mx, 0f, mx, h, paintMajorGrid); mx += majorStep }
            var my = majY
            while (my < h) { canvas.drawLine(0f, my, w, my, paintMajorGrid); my += majorStep }

            canvas.save()
            canvas.concat(canvasMatrix)

        sc.edges.forEach { edge ->
            val from = sc.nodes[edge.fromNodeId] ?: return@forEach
            val to = sc.nodes[edge.toNodeId] ?: return@forEach
            val p1 = getPortPos(from, edge.fromPortId, true)
            val p2 = getPortPos(to, edge.toPortId, false)
            val edgeColor = when {
                edge.fromPortId == "out_timeout" -> 0xFFF59E0B.toInt() // Оранжевый таймаут
                edge.fromPortId == "out_loop_body" -> 0xFF10B981.toInt() // Зеленая итерация
                edge.fromPortId == "out_loop_done" -> 0xFFC084FC.toInt() // Фиолетовый выход
                edge.fromPortId == "out_default" -> 0xFF8B5CF6.toInt() // Сиреневый далее
                else -> {
                    val trig = from.triggers.firstOrNull { it.targetPortId == edge.fromPortId }
                    if (trig?.behavior == com.example.autotap.domain.model.graph.TriggerBehavior.WAIT_DISAPPEAR) {
                        0xFF06B6D4.toInt() // Бирюзовый исчез
                    } else {
                        0xFF10B981.toInt() // Зеленый найдено
                    }
                }
            }
            paintEdge.color = edgeColor
            drawCurve(canvas, p1, p2, paintEdge)
        }


                if (isWiring) {
                    wiringP1.set(wiringStartX, wiringStartY)
                    wiringP2.set(wiringCurrentX, wiringCurrentY)
                    drawCurve(canvas, wiringP1, wiringP2, paintActiveWiring)
                    // [V12.5] Направляющий маркер потока на конце связи (Visual Target Arrow)
                    canvas.drawCircle(wiringCurrentX, wiringCurrentY, dpF(7f), paintActiveWiring)
                    paintPortIn.color = Color.WHITE
                    canvas.drawCircle(wiringCurrentX, wiringCurrentY, dpF(3.5f), paintPortIn)
                    paintPortIn.color = "#C084FC".toColorInt()
                }

                sc.nodes.values.forEach { node ->
                    val h = calculateHeight(node)
                    val effectiveWidth = nodeWidth
                    val r = dpF(16f)
                    val theme = getNodeTheme(node)

                    // Двухпроходная тень глубины (Ambient + Key Light)
                    cachedNodeRect.set(node.canvasX, node.canvasY + dpF(4f), node.canvasX + effectiveWidth, node.canvasY + h + dpF(4f))
                    canvas.drawRoundRect(cachedNodeRect, r, r, paintShadowAmbient)
                    cachedNodeRect.set(node.canvasX + dpF(6f), node.canvasY + dpF(10f), node.canvasX + effectiveWidth + dpF(6f), node.canvasY + h + dpF(10f))
                    canvas.drawRoundRect(cachedNodeRect, r, r, paintShadowKey)

                    // Корпус узла (Ultra-Dark Cyber Glass)
                    cachedNodeRect.set(node.canvasX, node.canvasY, node.canvasX + effectiveWidth, node.canvasY + h)
                    canvas.drawRoundRect(cachedNodeRect, r, r, paintNodeBg)

                    // Семантическая шапка с клипом скругления
                    paintNodeHeader.color = theme.headerStart
                    canvas.save()
                    canvas.clipRect(node.canvasX, node.canvasY, node.canvasX + effectiveWidth, node.canvasY + headerHeight)
                    canvas.drawRoundRect(cachedNodeRect, r, r, paintNodeHeader)
                    canvas.restore()

                    // Неоновый микро-разделитель шапки
                    paintBorder.color = theme.border
                    canvas.drawLine(node.canvasX, node.canvasY + headerHeight, node.canvasX + effectiveWidth, node.canvasY + headerHeight, paintBorder)

                    // [V100.0] Типографика заголовка с визуализацией точки входа СТАРТ ▶
                    val isEntryNode = sc.entryNodeId == node.id
                    val maxTitleWidth = nodeWidth - dpF(if (isEntryNode) 96f else 48f)
                    val finalTitle = safeEllipsize(node.title, paintTextTitle, maxTitleWidth)
                    val textY = node.canvasY + headerHeight / 2f - (paintTextTitle.fontMetrics.ascent + paintTextTitle.fontMetrics.descent) / 2f
                    canvas.drawText(finalTitle, node.canvasX + dpF(24f), textY, paintTextTitle)

                    if (isEntryNode) {
                        val badgeW = dpF(58f)
                        val badgeH = dpF(20f)
                        val bX = node.canvasX + effectiveWidth - badgeW - dpF(10f)
                        val bY = node.canvasY + (headerHeight - badgeH) / 2f
                        previewRect.set(bX, bY, bX + badgeW, bY + badgeH)
                        paintActiveWiring.color = 0xFF10B981.toInt()
                        canvas.drawRoundRect(previewRect, dpF(4f), dpF(4f), paintActiveWiring)
                        paintPortIn.color = Color.BLACK
                        paintPortIn.textSize = dpF(9f)
                        paintPortIn.typeface = Typeface.DEFAULT_BOLD
                        canvas.drawText("СТАРТ ▶", bX + dpF(7f), bY + dpF(14f), paintPortIn)
                    }

                    // Внешний контур узла с акцентным оттенком
                    canvas.drawRoundRect(cachedNodeRect, r, r, paintBorder)

                    // Входной порт Exec Flow (Тактильный сокет со световым ободом)
                    val inY = node.canvasY + headerHeight / 2f
                    paintPortIn.color = theme.border
                    canvas.drawCircle(node.canvasX, inY, portRadius + dpF(1.5f), paintBorder)
                    canvas.drawCircle(node.canvasX, inY, portRadius, paintPortIn)
                    canvas.drawCircle(node.canvasX, inY, portRadius * 0.45f, paintNodeBg)

                    var py = node.canvasY + headerHeight + dpF(16f)
                    node.triggers.forEach { trig ->
                        // [V50.0] Архитектурное квадратное превью шаблона (96x96dp)
                        val thumbSize = dpF(96f)
                        val thumbX = node.canvasX + dpF(16f)
                        val thumbY = py

                        previewRect.set(thumbX, thumbY, thumbX + thumbSize, thumbY + thumbSize)
                        canvas.drawRoundRect(previewRect, dpF(10f), dpF(10f), paintNodeHeader)

                        var drawnBitmap = false
                        if (trig.templatePath.isNotEmpty()) {
                            try {
                                val bmp = android.graphics.BitmapFactory.decodeFile(trig.templatePath)
                                if (bmp != null) {
                                    val bw = bmp.width.toFloat()
                                    val bh = bmp.height.toFloat()
                                    val scale = minOf(thumbSize / bw, thumbSize / bh)
                                    val rw = bw * scale
                                    val rh = bh * scale
                                    val rx = previewRect.centerX() - rw / 2f
                                    val ry = previewRect.centerY() - rh / 2f
                                    val targetFitRect = cachedTargetFitRect
                                    targetFitRect.set(rx, ry, rx + rw, ry + rh)
                                    canvas.drawBitmap(bmp, null, targetFitRect, previewPaint)
                                    bmp.recycle()
                                    drawnBitmap = true
                                }
                            } catch (_: Exception) {}
                        }
                        if (!drawnBitmap) {
                            paintTextSub.color = "#38BDF8".toColorInt()
                            canvas.drawText("+ ШАБЛОН", thumbX + dpF(14f), thumbY + thumbSize * 0.58f, paintTextSub)
                            paintTextSub.color = "#94A3B8".toColorInt()
                        }

                        val behaviorBadge = when (trig.behavior) {
                            com.example.autotap.domain.model.graph.TriggerBehavior.WAIT_DISAPPEAR -> "ИСЧЕЗ"
                            com.example.autotap.domain.model.graph.TriggerBehavior.CHECK_ONCE -> "ЧЕК"
                            com.example.autotap.domain.model.graph.TriggerBehavior.LOOP_WHILE -> "ЦИКЛ"
                            com.example.autotap.domain.model.graph.TriggerBehavior.WAIT_APPEAR -> "${trig.similarityThreshold}%"
                        }

                        val textStartX = thumbX + thumbSize + dpF(16f)
                        // [V95.0] Исправлена формула ширины текста: относительный расчет без вычитания мирового canvasX
                        val maxTrigWidth = (nodeWidth - dpF(32f) - thumbSize - dpF(28f)).coerceAtLeast(dpF(60f))
                        val isMulti = trig.multiTemplatePaths.size > 1
                        val displayName = if (isMulti) "${trig.name} [x${trig.multiTemplatePaths.size}]" else trig.name
                        val finalTrigName = safeEllipsize(displayName, paintTextTitle, maxTrigWidth)

                        paintTextTitle.textSize = dpF(12f)
                        canvas.drawText(finalTrigName, textStartX, thumbY + dpF(28f), paintTextTitle)
                        paintTextTitle.textSize = dpF(14f)

                        paintActiveWiring.color = "#10B981".toColorInt()
                        canvas.drawText(behaviorBadge, textStartX, thumbY + dpF(54f), paintActiveWiring)
                        paintActiveWiring.color = "#38BDF8".toColorInt()

                        // Выходной порт
                        val isDisappear = trig.behavior == com.example.autotap.domain.model.graph.TriggerBehavior.WAIT_DISAPPEAR
                        paintPortOut.color = if (isDisappear) 0xFF06B6D4.toInt() else 0xFF10B981.toInt()
                        val portY = thumbY + thumbSize * 0.5f
                        canvas.drawCircle(node.canvasX + effectiveWidth, portY, portRadius, paintPortOut)
                        canvas.drawCircle(node.canvasX + effectiveWidth, portY, portRadius * 0.4f, paintNodeBg)
                        py += triggerRowHeight
                    }
                    node.standardPorts.forEach { portName ->
                        val localizedPort = when (portName) {
                            "out_timeout" -> "ТАЙМАУТ"
                            "out_default" -> "ДАЛЕЕ"
                            "out_loop_body" -> "ИТЕРАЦИЯ"
                            "out_loop_done" -> "ЗАВЕРШЕНО"
                            else -> portName
                        }
                        val portColor = when (portName) {
                            "out_timeout" -> 0xFFF59E0B.toInt()
                            "out_loop_body" -> 0xFF10B981.toInt()
                            "out_loop_done" -> 0xFFC084FC.toInt()
                            else -> 0xFF8B5CF6.toInt()
                        }
                        paintTextSub.color = portColor
                        val finalPortText = safeEllipsize(localizedPort, paintTextSub, nodeWidth - dpF(40f))

                        // [V50.0] Идеальное выравнивание текста порта по правому краю
                        val tw = paintTextSub.measureText(finalPortText)
                        canvas.drawText(finalPortText, node.canvasX + effectiveWidth - dpF(24f) - tw, py, paintTextSub)
                        paintTextSub.color = "#9E95B8".toColorInt()

                        paintPortOut.color = portColor
                        canvas.drawCircle(node.canvasX + effectiveWidth, py - dpF(4f), portRadius, paintPortOut)
                        canvas.drawCircle(node.canvasX + effectiveWidth, py - dpF(4f), portRadius * 0.4f, paintNodeBg)
                        py += portRowHeight
                    }
        }
                canvas.restore()
        } catch (e: Throwable) {
            com.example.autotap.core.logger.AppLogger.logError(context, "CANVAS_DRAW_CRASH", e)
        }
    }

        private fun calculateHeight(node: ScenarioNode): Float {
            return headerHeight + (node.triggers.size * triggerRowHeight) + (node.standardPorts.size * portRowHeight) + dpF(16f)
        }

        // [V50.0] Zero-Allocation getPortPos: синхронизировано с новой эргономикой Blueprint
        private fun getPortPos(node: ScenarioNode, portId: String, isOut: Boolean, out: PointF = PointF()): PointF {
            if (!isOut) {
                out.set(node.canvasX, node.canvasY + headerHeight / 2f)
                return out
            }
            val idxTrig = node.triggers.indexOfFirst { it.targetPortId == portId }
            val py = if (idxTrig != -1) {
                // Превью 96x96, отступ 16, половина 48
                node.canvasY + headerHeight + dpF(16f) + (idxTrig * triggerRowHeight) + dpF(48f)
            } else {
                val idxStd = node.standardPorts.indexOf(portId).coerceAtLeast(0)
                node.canvasY + headerHeight + dpF(16f) + (node.triggers.size * triggerRowHeight) + (idxStd * portRowHeight) - dpF(4f)
            }
            out.set(node.canvasX + nodeWidth, py)
            return out
        }

        private fun drawCurve(canvas: Canvas, p1: PointF, p2: PointF, paint: Paint) {
        val path = Path()
        path.moveTo(p1.x, p1.y)
        val dx = kotlin.math.abs(p2.x - p1.x) * 0.5f
        val curvature = dx.coerceAtLeast(dpF(36f))
        path.cubicTo(p1.x + curvature, p1.y, p2.x - curvature, p2.y, p2.x, p2.y)

        // Pass 1: Контрастная подложка кабеля (эффект объема над сеткой)
        canvas.drawPath(path, curveShadowPaint)
        // Pass 2: Неоновая сигнальная жила
        canvas.drawPath(path, paint)
        }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        val pts = floatArrayOf(event.x, event.y)
        inverseMatrix.mapPoints(pts)
        val cx = pts[0]
        val cy = pts[1]

        val sc = scenario ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = cx
                touchDownY = cy
                isDraggingNode = false

                for (node in sc.nodes.values) {
                    var py = node.canvasY + headerHeight + dpF(22f)
                    val allOutPorts = node.triggers.map { it.targetPortId } + node.standardPorts
                    for (portId in allOutPorts) {
                        val portPos = getPortPos(node, portId, true)
                        if (hypot((cx - portPos.x).toDouble(), (cy - portPos.y).toDouble()) <= dpF(24f)) {
                            isWiring = true
                            isWiringReverse = false
                            wiringFromNodeId = node.id
                            wiringFromPortId = portId
                            wiringStartX = portPos.x
                            wiringStartY = portPos.y
                            wiringCurrentX = cx
                            wiringCurrentY = cy
                            invalidate()
                            return true
                        }
                        py += portRowHeight
                    }

                    // [V100.0] Поддержка протягивания от входа к выходу (Reverse Wiring)
                    val inPortPos = getPortPos(node, "in_entry", false)
                    if (hypot((cx - inPortPos.x).toDouble(), (cy - inPortPos.y).toDouble()) <= dpF(24f)) {
                        isWiring = true
                        isWiringReverse = true
                        wiringToNodeId = node.id
                        wiringStartX = inPortPos.x
                        wiringStartY = inPortPos.y
                        wiringCurrentX = cx
                        wiringCurrentY = cy
                        invalidate()
                        return true
                    }
                }

                val touchedNode = sc.nodes.values.reversed().firstOrNull {
                    cx in it.canvasX..(it.canvasX + nodeWidth) && cy in it.canvasY..(it.canvasY + calculateHeight(it))
                }
                if (touchedNode != null) {
                    draggedNodeId = touchedNode.id
                    return true
                }

                // [V65.0] 1-пальцевое свободное перемещение холста при касании пустой зоны
                if (!isWiring) {
                    isPanningCanvas = true
                    lastPanRawX = event.x
                    lastPanRawY = event.y
                    return true
                }
                }
                MotionEvent.ACTION_MOVE -> {
                if (isWiring) {
                    wiringCurrentX = cx
                    wiringCurrentY = cy
                    invalidate()
                    return true
                }
                draggedNodeId?.let { id ->
                    val dist = hypot((cx - touchDownX).toDouble(), (cy - touchDownY).toDouble())
                    if (dist > dpF(4f)) {
                        isDraggingNode = true
                    }
                    if (isDraggingNode) {
                        val nodes = sc.nodes.toMutableMap()
                        val n = nodes[id] ?: return@let
                        nodes[id] = n.copy(canvasX = cx - nodeWidth / 2, canvasY = cy - headerHeight / 2)
                        scenario = sc.copy(nodes = nodes)
                        invalidate()
                    }
                    return true
                }
                if (isPanningCanvas && !scaleDetector.isInProgress) {
                    val dx = event.x - lastPanRawX
                    val dy = event.y - lastPanRawY
                    panX += dx
                    panY += dy
                    lastPanRawX = event.x
                    lastPanRawY = event.y
                    updateMatrix()
                    invalidate()
                    return true
                }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanningCanvas = false
                                if (isWiring) {
                                if (isWiringReverse) {
                                val toNode = wiringToNodeId
                                if (toNode != null) {
                                val candidate = sc.nodes.values.firstOrNull { node ->
                                if (node.id == toNode) return@firstOrNull false
                                val allOutPorts = node.triggers.map { it.targetPortId } + node.standardPorts
                                allOutPorts.any { portId ->
                                    val pos = getPortPos(node, portId, true)
                                    hypot((cx - pos.x).toDouble(), (cy - pos.y).toDouble()) <= dpF(36f)
                                }
                                }
                                if (candidate != null) {
                                val allOutPorts = candidate.triggers.map { it.targetPortId } + candidate.standardPorts
                                val matchedPort = allOutPorts.minByOrNull { portId ->
                                    val pos = getPortPos(candidate, portId, true)
                                    hypot((cx - pos.x).toDouble(), (cy - pos.y).toDouble())
                                } ?: "out_default"

                                val newEdge = ScenarioEdge(
                                    id = "edge_${UUID.randomUUID().toString().take(6)}",
                                    fromNodeId = candidate.id,
                                    fromPortId = matchedPort,
                                    toNodeId = toNode,
                                    toPortId = "in_entry"
                                )
                                val mutableEdges = sc.edges.toMutableList()
                                mutableEdges.removeAll { it.fromNodeId == candidate.id && it.fromPortId == matchedPort }
                                mutableEdges.add(newEdge)
                                scenario = sc.copy(edges = mutableEdges)
                                onScenarioModified?.invoke()
                                }
                                }
                                isWiringReverse = false
                                wiringToNodeId = null
                                } else {
                                val fromNode = wiringFromNodeId
                                val fromPort = wiringFromPortId
                                if (fromNode != null && fromPort != null) {
                                val targetNode = sc.nodes.values.firstOrNull { node ->
                                if (node.id == fromNode) return@firstOrNull false
                                val distToPort = hypot((cx - node.canvasX).toDouble(), (cy - (node.canvasY + headerHeight)).toDouble())
                                val inHeaderZone = cx in (node.canvasX - dpF(24f))..(node.canvasX + nodeWidth * 0.45f) &&
                                                   cy in (node.canvasY - dpF(12f))..(node.canvasY + headerHeight + dpF(16f))
                                distToPort <= dpF(48f) || inHeaderZone
                                }

                                if (targetNode != null) {
                                val newEdge = ScenarioEdge(
                                    id = "edge_${UUID.randomUUID().toString().take(6)}",
                                    fromNodeId = fromNode,
                                    fromPortId = fromPort,
                                    toNodeId = targetNode.id,
                                    toPortId = "in_entry"
                                )
                                val mutableEdges = sc.edges.toMutableList()
                                mutableEdges.removeAll { it.fromNodeId == fromNode && it.fromPortId == fromPort }
                                mutableEdges.add(newEdge)
                                scenario = sc.copy(edges = mutableEdges)
                                onScenarioModified?.invoke()
                                } else {
                                onWireToEmptySpace?.invoke(fromNode, fromPort, cx, cy)
                                }
                                }
                                }
                                isWiring = false
                                wiringFromNodeId = null
                                wiringFromPortId = null
                                invalidate()
                                return true
                                }
                draggedNodeId?.let { id ->
                    if (!isDraggingNode) {
                        val n = sc.nodes[id]
                        if (n != null) {
                            // [V50.0] Синхронизированный хитбокс по габаритам превью (96x96dp)
                            var clickedTrigIdx = -1
                            var checkPy = n.canvasY + headerHeight + dpF(16f)
                            for (tIdx in n.triggers.indices) {
                                val thumbSize = dpF(96f)
                                val tX = n.canvasX + dpF(16f)
                                val tY = checkPy
                                if (cx in tX..(tX + thumbSize) && cy in tY..(tY + thumbSize)) {
                                    clickedTrigIdx = tIdx
                                    break
                                }
                                checkPy += triggerRowHeight
                            }
                            if (clickedTrigIdx != -1) {
                                onTemplateThumbnailClicked?.invoke(n, clickedTrigIdx)
                            } else {
                                onNodeSelected?.invoke(n)
                            }
                        }
                    } else {
                        onScenarioModified?.invoke()
                    }
                    draggedNodeId = null
                    isDraggingNode = false
                    return true
                }
            }
        }
        return true
    }
}
