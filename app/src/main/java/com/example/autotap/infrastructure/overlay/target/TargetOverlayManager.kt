package com.example.autotap.infrastructure.overlay.target

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.domain.gateway.IOverlayGateway
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.MatchCandidate
import com.example.autotap.domain.model.Point2D
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.capture.CalibrationOverlay
import com.example.autotap.infrastructure.overlay.capture.CaptureFrameOverlay
import com.example.autotap.infrastructure.overlay.graph.GraphOverlayView
import com.example.autotap.infrastructure.overlay.ring.TargetQuickRingOverlay
import com.example.autotap.infrastructure.storage.TemplateRepositoryImpl
import com.example.autotap.infrastructure.vision.PixelBufferPool
import com.example.autotap.infrastructure.vision.TemplateMatchingEngine
import com.example.autotap.infrastructure.visualizer.LaserScanVisualizer
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.hypot

@SuppressLint("ClickableViewAccessibility")
class TargetOverlayManager(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val quickRingOverlay: TargetQuickRingOverlay,
    private val graphOverlayView: GraphOverlayView
) : IOverlayGateway {

    private val actionsList = CopyOnWriteArrayList<MacroAction>()

    private val targetViews = HashMap<Int, TargetOverlayView>()
    private val endTargetViews = HashMap<Int, TargetOverlayView>()
    private val waypointViews = HashMap<Int, MutableList<View>>()
    private val templateRepository by lazy { TemplateRepositoryImpl(context) }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val dm = context.resources.displayMetrics
    private fun dp(v: Float): Int = (v * dm.density).toInt()

    private var isGraphAttached = false

    var isNumbersHidden: Boolean = false
    var onActionEditRequested: ((MacroAction) -> Unit)? = null
    var onActionTestRequested: ((MacroAction) -> Unit)? = null
    var onActionAddTemplateRequested: ((MacroAction) -> Unit)? = null
    var onActionCalibrateRequested: ((MacroAction) -> Unit)? = null
    var onActionsChanged: ((List<MacroAction>) -> Unit)? = null

    fun attachOverlaysIfNeeded() {
        mainHandler.post {
            var needsSync = false
            if (!isGraphAttached || graphOverlayView.parent == null) {
                overlayWindowManager.removeViewSafe(graphOverlayView)
                val params = overlayWindowManager.createLayoutParams(
                    width = WindowManager.LayoutParams.MATCH_PARENT,
                    height = WindowManager.LayoutParams.MATCH_PARENT,
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                )
                overlayWindowManager.addViewSafe(graphOverlayView, params)
                isGraphAttached = true
                needsSync = true
            }

            if (targetViews.values.any { it.parent == null } || endTargetViews.values.any { it.parent == null }) {
                needsSync = true
            }

            if (needsSync) {
                syncAllViews()
            }
        }
    }

    private fun ensureGraphOverlayAttached() {
        attachOverlaysIfNeeded()
    }

    fun getActions(): List<MacroAction> = actionsList.toList()

    fun loadActions(actions: List<MacroAction>) {
        clearAll()
        ensureGraphOverlayAttached()
        actions.forEach { act ->
            actionsList.add(act)
            spawnTargetView(act)
            if (act.type == ActionType.SWIPE || act.type == ActionType.PATH) {
                val endX = act.endX ?: (act.posX + dp(80f))
                val endY = act.endY ?: (act.posY + dp(80f))
                spawnEndTargetView(act, endX, endY)
            }
            if (act.type == ActionType.PATH) {
                spawnWaypointViews(act)
            }
        }
        setOverlaysVisible(true)
        updateGraph()
        AppLogger.log(context, "TARGET_MGR", "Загружено ${actionsList.size} действий")
    }

    private fun getRealScreenDimensions(): Pair<Float, Float> {
        val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager
        val realMetrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(realMetrics)
        val w = if (realMetrics.widthPixels > 0) realMetrics.widthPixels.toFloat() else dm.widthPixels.toFloat()
        val h = if (realMetrics.heightPixels > 0) realMetrics.heightPixels.toFloat() else dm.heightPixels.toFloat()
        return Pair(w, h)
    }

    fun addAction(action: MacroAction) {
        ensureGraphOverlayAttached()
        val nextId = actionsList.size + 1
        val (screenW, screenH) = getRealScreenDimensions()

        val stagger = ((actionsList.size % 6) * dp(20f)).toFloat()
        val validPosX = if (action.posX <= 0f && action.posY <= 0f) (screenW / 2f + stagger) else action.posX.coerceIn(0f, screenW)
        val validPosY = if (action.posX <= 0f && action.posY <= 0f) (screenH / 2f + stagger) else action.posY.coerceIn(0f, screenH)

        val isMovementType = action.type == ActionType.SWIPE || action.type == ActionType.PATH
        val endX = action.endX ?: (validPosX + dp(80f))
        val endY = action.endY ?: (validPosY + dp(80f))
        val defaultPts = if (action.type == ActionType.PATH && action.pathPoints.isEmpty()) {
            listOf(Point2D(validPosX, validPosY), Point2D(endX, endY))
        } else action.pathPoints
        val newAction = action.copy(
            id = nextId, posX = validPosX, posY = validPosY,
            endX = if (isMovementType) endX else action.endX,
            endY = if (isMovementType) endY else action.endY,
            pathPoints = defaultPts
        )
        actionsList.add(newAction)
        spawnTargetView(newAction)
        if (isMovementType) {
            // [Dual-Overlay Path Architecture] Создание Оверлея 2 (Конечная мишень вектора)
            spawnEndTargetView(newAction, endX, endY)
        }
        updateGraph()
    }

    fun addActionAt(
        posX: Float, posY: Float, type: ActionType = ActionType.CLICK, delayMs: Long = 200L,
        templateIndex: Int = -1, templatePath: String = "", similarityPercent: Int = 88
    ) {
        ensureGraphOverlayAttached()
        val nextId = actionsList.size + 1
        val (screenW, screenH) = getRealScreenDimensions()

        val stagger = ((actionsList.size % 6) * dp(20f)).toFloat()
        val safeX = if (posX <= 10f || posX >= screenW - 10f) (screenW / 2f + stagger) else posX
        val safeY = if (posY <= 10f || posY >= screenH - 10f) (screenH / 2f + stagger) else posY

        val isMovementType = type == ActionType.SWIPE || type == ActionType.PATH
        val endX = (safeX + dp(100f)).coerceIn(0f, screenW)
        val endY = (safeY + dp(100f)).coerceIn(0f, screenH)
        val defaultPts = if (type == ActionType.PATH) {
            val midX = (safeX + endX) / 2f + dp(25f)
            val midY = (safeY + endY) / 2f - dp(25f)
            listOf(Point2D(safeX, safeY), Point2D(midX, midY), Point2D(endX, endY))
        } else emptyList()

        val newAction = MacroAction(
            id = nextId, type = type, posX = safeX, posY = safeY,
            endX = if (isMovementType) endX else null,
            endY = if (isMovementType) endY else null,
            pathPoints = defaultPts,
            delayMs = delayMs,
            selectedTemplateIndex = templateIndex,
            multiTemplateIndices = if (templateIndex != -1) listOf(templateIndex) else emptyList(),
            templatePath = templatePath, similarityPercent = similarityPercent
        )
        actionsList.add(newAction)
        spawnTargetView(newAction)
        if (isMovementType) spawnEndTargetView(newAction, endX, endY)
        if (type == ActionType.PATH) spawnWaypointViews(newAction)
        updateGraph()
        }

    fun updateAction(updated: MacroAction) {
        val idx = actionsList.indexOfFirst { it.id == updated.id }
        if (idx != -1) {
            actionsList[idx] = updated
            val sz = dp(if (updated.type == ActionType.TRIGGER) 52f else 38f)
            val halfSz = sz / 2f
            val safePad = dp(4f)
            val totalW = sz + safePad * 2
            val totalH = sz + safePad * 2

            targetViews[updated.id]?.let { tv ->
                tv.bindAction(updated, isNumbersHidden)
                val lp = tv.layoutParams as? WindowManager.LayoutParams
                if (lp != null) {
                    lp.width = totalW
                    lp.height = totalH
                    lp.x = (updated.posX - halfSz - safePad).toInt()
                    lp.y = (updated.posY - halfSz - safePad).toInt()
                    overlayWindowManager.updateViewSafe(tv, lp)
                }
            }

            endTargetViews[updated.id]?.let { etv ->
                etv.bindAction(updated, isNumbersHidden)
                val lp = etv.layoutParams as? WindowManager.LayoutParams
                if (lp != null && updated.endX != null && updated.endY != null) {
                    val endSz = dp(38f)
                    val endHalfSz = endSz / 2f
                    lp.x = (updated.endX - endHalfSz - safePad).toInt()
                    lp.y = (updated.endY - endHalfSz - safePad).toInt()
                    overlayWindowManager.updateViewSafe(etv, lp)
                }
            }

            if (updated.type == ActionType.PATH) {
                spawnWaypointViews(updated)
            }

            val isMovementType = updated.type == ActionType.SWIPE || updated.type == ActionType.PATH
            if (isMovementType && !endTargetViews.containsKey(updated.id)) {
                val endX = updated.endX ?: (updated.posX + dp(80f))
                val endY = updated.endY ?: (updated.posY + dp(80f))
                val pts = if (updated.type == ActionType.PATH && updated.pathPoints.isEmpty()) {
                    listOf(Point2D(updated.posX, updated.posY), Point2D(endX, endY))
                } else updated.pathPoints
                val finalUpdated = updated.copy(endX = endX, endY = endY, pathPoints = pts)

                actionsList[idx] = finalUpdated
                spawnEndTargetView(finalUpdated, endX, endY)
                spawnWaypointViews(finalUpdated)
                } else if (!isMovementType && endTargetViews.containsKey(updated.id)) {
                endTargetViews[updated.id]?.let { overlayWindowManager.removeViewSafe(it) }
                endTargetViews.remove(updated.id)
            }
            updateGraph()
        }
    }

    fun removeAction(actionId: Int) {
        val idx = actionsList.indexOfFirst { it.id == actionId }
        if (idx == -1) return

        targetViews[actionId]?.let { overlayWindowManager.removeViewSafe(it) }
        targetViews.remove(actionId)


        endTargetViews[actionId]?.let { overlayWindowManager.removeViewSafe(it) }
        endTargetViews.remove(actionId)

        waypointViews[actionId]?.forEach { overlayWindowManager.removeViewSafe(it) }
        waypointViews.remove(actionId)

        actionsList.removeAt(idx)

        for (i in actionsList.indices) {
            val old = actionsList[i]
            val newId = i + 1
            val remappedMatch = when {
                old.jumpToStepOnMatch == actionId -> null
                old.jumpToStepOnMatch != null && old.jumpToStepOnMatch > actionId -> old.jumpToStepOnMatch - 1
                else -> old.jumpToStepOnMatch
            }
            val remappedTimeout = when {
                old.jumpToStepOnTimeout == actionId -> null
                old.jumpToStepOnTimeout != null && old.jumpToStepOnTimeout > actionId -> old.jumpToStepOnTimeout - 1
                else -> old.jumpToStepOnTimeout
            }
            actionsList[i] = old.copy(
                id = newId,
                jumpToStepOnMatch = remappedMatch,
                jumpToStepOnTimeout = remappedTimeout
            )
        }

        syncAllViews()
        updateGraph()
    }

    fun clearAll() {
        quickRingOverlay.dismiss()
        targetViews.values.forEach { overlayWindowManager.removeViewSafe(it) }
        targetViews.clear()
        endTargetViews.values.forEach { overlayWindowManager.removeViewSafe(it) }
        endTargetViews.clear()
        actionsList.clear()
        if (isGraphAttached) {
            overlayWindowManager.removeViewSafe(graphOverlayView)
            isGraphAttached = false
        }
        updateGraph()
    }

    override fun setTargetsTouchable(touchable: Boolean) {
        mainHandler.post {
            targetViews.values.forEach { v ->
                val lp = v.layoutParams as? WindowManager.LayoutParams ?: return@forEach
                if (touchable) lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                else lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                overlayWindowManager.updateViewSafe(v, lp)
            }

            endTargetViews.values.forEach { v ->
                val lp = v.layoutParams as? WindowManager.LayoutParams ?: return@forEach
                if (touchable) lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                else lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                overlayWindowManager.updateViewSafe(v, lp)
            }
            waypointViews.values.flatten().forEach { v ->
                val lp = v.layoutParams as? WindowManager.LayoutParams ?: return@forEach
                if (touchable) lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                else lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                overlayWindowManager.updateViewSafe(v, lp)
            }
        }
    }

    override fun setOverlaysVisible(visible: Boolean) {
        mainHandler.post {
                        val vis = if (visible) View.VISIBLE else View.GONE
            targetViews.values.forEach {
                it.visibility = vis
                it.tvNumber.visibility = if (isNumbersHidden) View.INVISIBLE else View.VISIBLE
                it.tvCornerBadge.visibility = if (isNumbersHidden) View.GONE else View.VISIBLE
            }

            endTargetViews.values.forEach {
                it.visibility = vis
                it.tvNumber.visibility = if (isNumbersHidden) View.INVISIBLE else View.VISIBLE
                it.tvCornerBadge.visibility = if (isNumbersHidden) View.GONE else View.VISIBLE
            }
            waypointViews.values.flatten().forEach { it.visibility = vis }
            graphOverlayView.visibility = if (visible && !isNumbersHidden) View.VISIBLE else View.GONE
        }
    }

    override fun requestRedraw() {
        mainHandler.post {
            updateGraph()
            targetViews.values.forEach { it.invalidate() }
            endTargetViews.values.forEach { it.invalidate() }
        }
    }

    override fun hideAll() {
        setOverlaysVisible(false)
        quickRingOverlay.dismiss()
    }

    fun startCaptureForStep(targetAction: MacroAction) {
        setOverlaysVisible(false)

        val service = com.example.autotap.infrastructure.accessibility.AutoTapAccessibilityService.instance
        if (service == null) {
            setOverlaysVisible(true)
            return
        }

        CaptureFrameOverlay(
            context = context,
            overlayWindowManager = overlayWindowManager,
            onCaptureTriggered = { cropX, cropY, cropW, cropH, isCircle ->
                mainHandler.postDelayed({
                    val screenshot = service.captureScreenshotSync(2000L)
                    if (screenshot != null) {
                        val safeW = cropW.coerceIn(8, screenshot.width)
                        val safeH = cropH.coerceIn(8, screenshot.height)
                        val safeX = cropX.coerceIn(0, screenshot.width - safeW)
                        val safeY = cropY.coerceIn(0, screenshot.height - safeH)
                        val cropped = Bitmap.createBitmap(screenshot, safeX, safeY, safeW, safeH)

                        val initialAnchorCandidate = MatchCandidate(safeX + safeW / 2, safeY + safeH / 2, safeX, safeY, safeX + safeW, safeY + safeH, 1.0f)

                        LaserScanVisualizer.showSweepLaser(context, overlayWindowManager) {
                            CalibrationOverlay(
                                context = context,
                                overlayWindowManager = overlayWindowManager,
                                screenshot = screenshot,
                                rawTemplateBitmap = cropped,
                                initialCandidates = listOf(initialAnchorCandidate),
                                existingAction = targetAction,
                                existingTemplatePath = null, // COPY-ON-WRITE: Force new file!
                                allActions = getActions(),
                                targetStepId = targetAction.id,
                                anchorCropX = safeX,
                                anchorCropY = safeY,
                                onFinished = { updatedAction, path ->
                                    setOverlaysVisible(true)
                                    TemplateMatchingEngine.lastMatchedPositions[path] = Pair(updatedAction.posX.toInt(), updatedAction.posY.toInt())
                                    updateAction(updatedAction)
                                },
                                onCancelled = {
                                    setOverlaysVisible(true)
                                }
                            ).show()
                        }
                    } else {
                        setOverlaysVisible(true)
                    }
                }, 80L)
            },
            onCancelled = {
                setOverlaysVisible(true)
            }
        ).show()
    }

    private fun spawnTargetView(action: MacroAction) {

        // [V34.1] Чистая центрированная геометрия без паразитного верхнего поля badgeExtra
        val circleSz = dp(if (action.type == ActionType.TRIGGER) 52f else 38f)
        val halfSz = circleSz / 2f
        val safePad = dp(4f)
        val totalW = circleSz + safePad * 2
        val totalH = circleSz + safePad * 2

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
        val realMetrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(realMetrics)
        val screenW = if (realMetrics.widthPixels > 0) realMetrics.widthPixels else dm.widthPixels
        val screenH = if (realMetrics.heightPixels > 0) realMetrics.heightPixels else dm.heightPixels

        val minSafeX = (-halfSz - safePad).toInt()
        val maxSafeX = (screenW - halfSz + safePad).toInt()
        val minSafeY = (-halfSz - safePad).toInt()
        val maxSafeY = (screenH - halfSz + safePad).toInt()

        val safePosX = if (action.posX <= 0f && action.posY <= 0f) screenW.toFloat() / 2f else action.posX.coerceIn(0f, screenW.toFloat())
        val safePosY = if (action.posX <= 0f && action.posY <= 0f) screenH.toFloat() / 2f else action.posY.coerceIn(0f, screenH.toFloat())

        val targetX = (safePosX - halfSz - safePad).toInt().coerceIn(minSafeX, maxSafeX)
        val targetY = (safePosY - halfSz - safePad).toInt().coerceIn(minSafeY, maxSafeY)

        val targetView = TargetOverlayView(context, isEndTarget = false) { templateRepository.getTemplate(it) }.apply {
            bindAction(action, isNumbersHidden)
            tag = action.id
        }

        val lp = overlayWindowManager.createLayoutParams(
            width = totalW, height = totalH, gravity = Gravity.TOP or Gravity.START,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply {
            x = targetX
            y = targetY
        }

        targetView.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0; private var initY = 0
            private var touchX = 0f; private var touchY = 0f
            private var isMoved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val currentId = (v.tag as? Int) ?: action.id
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = lp.x; initY = lp.y; touchX = event.rawX; touchY = event.rawY; isMoved = false; return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = abs(event.rawX - touchX)
                        val dy = abs(event.rawY - touchY)
                        val touchSlop = dp(12f)
                        if (dx > touchSlop || dy > touchSlop) {
                            isMoved = true
                            lp.x = (initX + (event.rawX - touchX).toInt()).coerceIn(minSafeX, maxSafeX)
                            lp.y = (initY + (event.rawY - touchY).toInt()).coerceIn(minSafeY, maxSafeY)
                            overlayWindowManager.updateViewSafe(targetView, lp)


                            val idx = actionsList.indexOfFirst { it.id == currentId }
                            if (idx != -1) {
                                val oldAct = actionsList[idx]
                                val newStartX = lp.x.toFloat() + halfSz + safePad
                                val newStartY = lp.y.toFloat() + halfSz + safePad
                                val deltaX = newStartX - oldAct.posX
                                val deltaY = newStartY - oldAct.posY

                                val isMovement = oldAct.type == ActionType.PATH || oldAct.type == ActionType.SWIPE
                                val newEndX = if (isMovement && oldAct.endX != null) oldAct.endX + deltaX else oldAct.endX
                                val newEndY = if (isMovement && oldAct.endY != null) oldAct.endY + deltaY else oldAct.endY
                                val newPts = if (oldAct.type == ActionType.PATH && oldAct.pathPoints.isNotEmpty()) {
                                    oldAct.pathPoints.map { Point2D(it.x + deltaX, it.y + deltaY, it.timeOffsetMs) }
                                } else oldAct.pathPoints

                                val updatedAction = oldAct.copy(
                                    posX = newStartX, posY = newStartY,
                                    endX = newEndX, endY = newEndY,
                                    pathPoints = newPts
                                )
                                actionsList[idx] = updatedAction


                                endTargetViews[currentId]?.let { etv ->
                                    val elp = etv.layoutParams as WindowManager.LayoutParams
                                    elp.x = ((newEndX ?: 0f) - halfSz - safePad).toInt()
                                    elp.y = ((newEndY ?: 0f) - halfSz - safePad).toInt()
                                    overlayWindowManager.updateViewSafe(etv, elp)
                                }
                                waypointViews[currentId]?.forEachIndexed { i, wpView ->
                                    val pIdx = i + 1
                                    if (pIdx < newPts.size) {
                                        val wlp = wpView.layoutParams as WindowManager.LayoutParams
                                        wlp.x = (newPts[pIdx].x - dp(14f)).toInt()
                                        wlp.y = (newPts[pIdx].y - dp(14f)).toInt()
                                        overlayWindowManager.updateViewSafe(wpView, wlp)
                                    }
                                }
                                updateGraph()
                            }
                            // [V33.8] Эластичное слежение быстрого меню за мишенью при перетаскивании
                            if (quickRingOverlay.isShowingFor(currentId)) {
                                quickRingOverlay.updatePosition(
                                    centerX = lp.x + halfSz,
                                    centerY = lp.y.toFloat() + halfSz
                                )
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isMoved) {
                            val currentAct = actionsList.firstOrNull { it.id == currentId } ?: action
                            if (quickRingOverlay.isShowingFor(currentAct.id)) {
                                quickRingOverlay.dismiss()
                            } else {
                                quickRingOverlay.show(
                                    action = currentAct,
                                    centerX = lp.x + halfSz,
                                    centerY = lp.y.toFloat() + halfSz,
                                    onEdit = { onActionEditRequested?.invoke(it) },
                                    onAddTemplate = { onActionAddTemplateRequested?.invoke(it) },
                                    onCalibrate = { onActionCalibrateRequested?.invoke(it) },
                                    onClone = { addAction(it.copy(posX = it.posX + 30f, posY = it.posY + 30f)) },
                                    onDelete = { removeAction(it.id) }
                                )
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        targetViews[action.id] = targetView
        overlayWindowManager.addViewSafe(targetView, lp)
    }

    private fun spawnEndTargetView(action: MacroAction, endX: Float, endY: Float) {
        val sz = dp(38f)
        val halfSz = sz / 2f
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        val minSafeX = (-halfSz).toInt()
        val maxSafeX = (screenW - halfSz).toInt()
        val minSafeY = (-halfSz).toInt()
        val maxSafeY = (screenH - halfSz).toInt()

        val safeEndX = if (endX <= 10f || endX >= screenW.toFloat() - 10f) (action.posX + dp(80f)).coerceIn(0f, screenW.toFloat()) else endX.coerceIn(0f, screenW.toFloat())
        val safeEndY = if (endY <= 10f || endY >= screenH.toFloat() - 10f) (action.posY + dp(80f)).coerceIn(0f, screenH.toFloat()) else endY.coerceIn(0f, screenH.toFloat())

        val endView = TargetOverlayView(context, isEndTarget = true).apply {
            bindAction(action, isNumbersHidden)
            tag = action.id
        }

        val lp = overlayWindowManager.createLayoutParams(
            width = sz, height = sz, gravity = Gravity.TOP or Gravity.START,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply {
            x = (safeEndX - halfSz).toInt().coerceIn(minSafeX, maxSafeX)
            y = (safeEndY - halfSz).toInt().coerceIn(minSafeY, maxSafeY)
        }

        endView.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0; private var initY = 0
            private var touchX = 0f; private var touchY = 0f
            private var isMoved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val currentId = (v.tag as? Int) ?: action.id
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = lp.x; initY = lp.y; touchX = event.rawX; touchY = event.rawY; isMoved = false; return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = abs(event.rawX - touchX)
                        val dy = abs(event.rawY - touchY)
                        val touchSlop = dp(12f)
                        if (dx > touchSlop || dy > touchSlop) {
                            isMoved = true
                            lp.x = (initX + (event.rawX - touchX).toInt()).coerceIn(minSafeX, maxSafeX)
                            lp.y = (initY + (event.rawY - touchY).toInt()).coerceIn(minSafeY, maxSafeY)
                            overlayWindowManager.updateViewSafe(endView, lp)

                            val idx = actionsList.indexOfFirst { it.id == currentId }
                            if (idx != -1) {
                                val currentAct = actionsList[idx]
                                val newEndX = lp.x + halfSz
                                val newEndY = lp.y + halfSz
                                val pts = if (currentAct.type == ActionType.PATH && currentAct.pathPoints.size >= 2) {
                                    val mPts = currentAct.pathPoints.toMutableList()
                                    mPts[mPts.size - 1] = Point2D(newEndX, newEndY, mPts.last().timeOffsetMs)
                                    mPts
                                } else currentAct.pathPoints
                                val updatedAction = currentAct.copy(endX = newEndX, endY = newEndY, pathPoints = pts)
                                actionsList[idx] = updatedAction
                                updateGraph()
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> return true
                }
                return false
            }
        })


        endTargetViews[action.id] = endView
        overlayWindowManager.addViewSafe(endView, lp)
        }

        private fun spawnWaypointViews(action: MacroAction) {
        waypointViews[action.id]?.forEach { overlayWindowManager.removeViewSafe(it) }
        waypointViews.remove(action.id)
        if (action.type != ActionType.PATH || action.pathPoints.size <= 2) return

        val sz = dp(28f)
        val halfSz = sz / 2f
        val list = mutableListOf<View>()

        for (pIdx in 1 until action.pathPoints.size - 1) {
            val pt = action.pathPoints[pIdx]
            val lp = overlayWindowManager.createLayoutParams(sz, sz).apply {
                x = (pt.x - halfSz).toInt()
                y = (pt.y - halfSz).toInt()
            }


            val wpView = android.widget.FrameLayout(context).apply {
                tag = action.id
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor("#F59E0B"))
                    setStroke(dp(1.5f), android.graphics.Color.WHITE)
                }
                val tv = android.widget.TextView(context).apply {
                    text = (pIdx + 1).toString()
                    textSize = 9.5f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(android.graphics.Color.BLACK)
                    gravity = android.view.Gravity.CENTER
                }
                addView(tv, android.widget.FrameLayout.LayoutParams(-1, -1))

                setOnTouchListener(object : View.OnTouchListener {
                    var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f
                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                initX = lp.x; initY = lp.y; touchX = event.rawX; touchY = event.rawY
                                return true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val dx = Math.abs(event.rawX - touchX)
                                val dy = Math.abs(event.rawY - touchY)
                                lp.x = initX + (event.rawX - touchX).toInt()
                                lp.y = initY + (event.rawY - touchY).toInt()
                                overlayWindowManager.updateViewSafe(v, lp)

                                val aIdx = actionsList.indexOfFirst { it.id == action.id }
                                if (aIdx != -1) {
                                    val cur = actionsList[aIdx]
                                    if (pIdx < cur.pathPoints.size) {
                                        val mPts = cur.pathPoints.toMutableList()
                                        mPts[pIdx] = com.example.autotap.domain.model.Point2D(lp.x + halfSz, lp.y + halfSz, 0L)
                                        actionsList[aIdx] = cur.copy(pathPoints = mPts)
                                        updateGraph()
                                    }
                                }
                                return true
                            }
                            MotionEvent.ACTION_UP -> {
                                updateGraph()
                                val holdTime = System.currentTimeMillis() - touchX.toLong()
                                return true
                            }
                            }
                            return false
                            }
                            })

                            setOnLongClickListener {
                            val aIdx = actionsList.indexOfFirst { it.id == action.id }
                            if (aIdx != -1) {
                            val cur = actionsList[aIdx]
                            if (cur.pathPoints.size > 2 && pIdx < cur.pathPoints.size) {
                            val mPts = cur.pathPoints.toMutableList()
                            mPts.removeAt(pIdx)
                            val updated = cur.copy(pathPoints = mPts)
                            actionsList[aIdx] = updated
                            spawnWaypointViews(updated)
                            updateGraph()
                            android.widget.Toast.makeText(context, "Точка пути удалена", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            }
                            true
                            }
            }
            list.add(wpView)
            overlayWindowManager.addViewSafe(wpView, lp)
        }
        waypointViews[action.id] = list
        }

    private fun syncAllViews() {
        val currentActions = actionsList.toList()
        targetViews.values.forEach { overlayWindowManager.removeViewSafe(it) }
        targetViews.clear()
        endTargetViews.values.forEach { overlayWindowManager.removeViewSafe(it) }
        endTargetViews.clear()

        currentActions.forEach { act ->
            spawnTargetView(act)
            if (act.type == ActionType.SWIPE || act.type == ActionType.PATH) {
                spawnEndTargetView(act, act.endX ?: (act.posX + dp(80f)), act.endY ?: (act.posY + dp(80f)))
            }
            if (act.type == ActionType.PATH) {
                spawnWaypointViews(act)
            }
        }
    }

    private fun updateGraph() {
        val list = actionsList.toList()
        mainHandler.post {
            graphOverlayView.submitActions(list)
            onActionsChanged?.invoke(list)
        }
    }
}
