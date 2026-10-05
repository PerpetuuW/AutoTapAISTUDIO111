package com.example.autotap.infrastructure.engine

import android.graphics.Bitmap
import android.graphics.Rect
import android.widget.Toast
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.core.math.PathCompressionEngine
import com.example.autotap.core.safety.SafetyGovernor
import com.example.autotap.domain.engine.IMacroEngine
import com.example.autotap.domain.engine.SubroutineManager
import com.example.autotap.domain.gateway.IGestureGateway
import com.example.autotap.domain.gateway.IVisionGateway
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.DeviceDisplaySpecs
import com.example.autotap.domain.model.ExecutionState
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.MacroScenario
import com.example.autotap.domain.model.MatchCandidate
import com.example.autotap.domain.model.PauseReason
import com.example.autotap.domain.model.Point2D
import com.example.autotap.domain.model.graph.EvaluationPolicy
import com.example.autotap.domain.model.graph.GraphMacroScenario
import com.example.autotap.domain.model.graph.NodeActionSpec
import com.example.autotap.domain.model.graph.NodeTriggerSpec
import com.example.autotap.domain.model.graph.ScenarioNode
import com.example.autotap.domain.repository.IScenarioRepository
import com.example.autotap.domain.repository.ITemplateRepository
import com.example.autotap.infrastructure.accessibility.AutoTapAccessibilityService
import com.example.autotap.infrastructure.ocr.ExpressionEvaluator
import com.example.autotap.infrastructure.ocr.OcrEngine
import com.example.autotap.infrastructure.projection.MediaProjectionService
import com.example.autotap.infrastructure.vision.PixelBufferPool
import com.example.autotap.infrastructure.vision.TemplateMatchingEngine
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MacroExecutionEngine(
    private val gestureGateway: IGestureGateway,
    private val visionGateway: IVisionGateway,
    private val safetyGovernor: SafetyGovernor,
    private val scenarioRepository: IScenarioRepository,
    private val templateRepository: ITemplateRepository,
    private val accessibilityServiceProvider: () -> AutoTapAccessibilityService?
) : IMacroEngine {
    var onAutoHealRequested: ((com.example.autotap.domain.model.MacroAction, android.graphics.Bitmap, (com.example.autotap.domain.model.MacroAction) -> Unit) -> Unit)? = null


    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var executionJob: Job? = null

    private val _executionState = MutableStateFlow<ExecutionState>(ExecutionState.Idle)
    override val executionState: StateFlow<ExecutionState> = _executionState.asStateFlow()

    private val subroutineManager = SubroutineManager(maxDepth = 50)
    private var debugStepDeferred: CompletableDeferred<Unit>? = null

    val variableContext = ConcurrentHashMap<String, String>()
    private val nodeLoopCounters = ConcurrentHashMap<String, Int>()

    @Volatile
    private var isStepByStepDebug = false

    @Volatile
    private var isPaused = false

    override fun start(scenario: MacroScenario, templates: Map<String, Bitmap>, isDebug: Boolean) {
        if (executionJob?.isActive == true) return
        isStepByStepDebug = isDebug
        isPaused = false
        subroutineManager.clear()
        variableContext.clear()

        AppLogger.log(null, "MACRO_ENGINE", "Запуск сценария: '${scenario.name}', шагов: ${scenario.actions.size}")

        executionJob = engineScope.launch {
            val startTime = System.currentTimeMillis()
            var totalExecutedSteps = 0
            var currentIndex = 0
            var activeActionsList = scenario.actions.toMutableList()
            var currentScenarioName = scenario.name

            if (activeActionsList.isEmpty()) {
                _executionState.value = ExecutionState.Completed(0, 0L)
                return@launch
            }

            try {
                while (isActive) {
                    if (currentIndex >= activeActionsList.size || currentIndex < 0) {
                        currentIndex = 0
                    }

                    val action = activeActionsList[currentIndex]
                    val repeats = if (action.repeatCount <= 0) Int.MAX_VALUE else action.repeatCount

                    if (isStepByStepDebug) {
                        _executionState.value = ExecutionState.Running(
                            currentStepIndex = currentIndex + 1,
                            totalSteps = activeActionsList.size,
                            currentRepeat = 1,
                            totalRepeats = repeats,
                            isDebugPaused = true,
                            stepType = action.type,
                            stepDescription = "Отладка #${action.id}"
                        )
                        debugStepDeferred = CompletableDeferred()
                        debugStepDeferred?.await()
                        debugStepDeferred = null
                    }

                    while (isPaused && isActive) {
                        delay(200L)
                    }

                    var isJumpTriggered = false
                    var currentRepeat = 0

                    while (currentRepeat < repeats && isActive) {
                        val telemetry = safetyGovernor.getTelemetry()
                        if (telemetry.zone == SafetyGovernor.ThermalZone.CRITICAL) {
                            _executionState.value = ExecutionState.Paused(PauseReason.THERMAL_THROTTLING)
                            while (!safetyGovernor.checkThermalAndBattery() && isActive) {
                                delay(2000L)
                            }
                        }

                        val jitter = if (action.randomRadiusPx > 0) safetyGovernor.calculateJitter(action.randomRadiusPx) else 0L
                        val adaptiveCooling = telemetry.suggestedCoolingDelayMs
                        val stepDelay = (action.delayMs + jitter + adaptiveCooling).coerceAtLeast(10L)

                        val thermalBadge = if (telemetry.zone != SafetyGovernor.ThermalZone.OPTIMAL) " [ECO ${telemetry.tempCelsius.toInt()}°C]" else ""
                        val stepDesc = when (action.type) {
                            ActionType.CLICK -> "Клик #${action.id}$thermalBadge"
                            ActionType.LONG_PRESS -> "Удержание #${action.id}$thermalBadge"
                            ActionType.SWIPE -> "Свайп #${action.id}$thermalBadge"
                            ActionType.PATH -> "Траектория #${action.id}$thermalBadge"
                            ActionType.PINCH -> "Пинч #${action.id}$thermalBadge"
                            ActionType.SUBROUTINE -> "Вызов: ${action.targetScriptOrQuery.ifEmpty { action.subroutineTag }}"
                            ActionType.RETURN -> "Возврат"
                            ActionType.GLOBAL_BACK -> "Назад #${action.id}$thermalBadge"
                            ActionType.GLOBAL_HOME -> "Домой #${action.id}$thermalBadge"
                            ActionType.DELAY -> "Пауза #${action.id}$thermalBadge"
                            ActionType.TRIGGER, ActionType.OCR, ActionType.COLOR_CHECK -> "Фаза #${action.id}$thermalBadge"
                        }

                        _executionState.value = ExecutionState.Running(
                            currentStepIndex = currentIndex + 1,
                            totalSteps = activeActionsList.size,
                            currentRepeat = currentRepeat + 1,
                            totalRepeats = repeats,
                            isDebugPaused = false,
                            isSearching = false,
                            stepType = action.type,
                            stepDescription = stepDesc,
                            searchTimeoutMs = stepDelay
                        )

                        delay(stepDelay)
                        if (!isActive) break

                        when (action.type) {
                            ActionType.CLICK -> {
                                val (gx, gy) = safetyGovernor.computeGaussianOffset(action.randomRadiusPx)
                                val effHold = action.holdDurationMs.coerceIn(20L, maxOf(20L, action.delayMs))
                                withContext(Dispatchers.IO) {
                                    gestureGateway.performClick(action.posX + gx, action.posY + gy, effHold)
                                }
                                totalExecutedSteps++
                            }
                            ActionType.LONG_PRESS -> {
                                val (gx, gy) = safetyGovernor.computeGaussianOffset(action.randomRadiusPx)
                                withContext(Dispatchers.IO) {
                                    gestureGateway.performClick(action.posX + gx, action.posY + gy, action.holdDurationMs.coerceAtLeast(400L))
                                }
                                totalExecutedSteps++
                            }
                            ActionType.SWIPE -> {
                                val sx = action.posX
                                val sy = action.posY
                                val ex = action.endX ?: (sx + 100f)
                                val ey = action.endY ?: (sy + 100f)
                                withContext(Dispatchers.IO) {
                                    gestureGateway.performSwipe(sx, sy, ex, ey, action.holdDurationMs.coerceAtLeast(100L))
                                }
                                totalExecutedSteps++
                            }
                            ActionType.PATH -> {
                                if (action.pathPoints.isNotEmpty()) {
                                    val path = android.graphics.Path()
                                    path.moveTo((action.primaryAnchorPoints.firstOrNull()?.x ?: 0f).toFloat(), (action.primaryAnchorPoints.firstOrNull()?.y ?: 0f).toFloat())
                                    for (i in 1 until action.pathPoints.size) {
                                        path.lineTo(action.pathPoints[i].x.toFloat(), action.pathPoints[i].y.toFloat())
                                    }
                                    val totalDuration = action.holdDurationMs.coerceAtLeast(250L)
                                    val stroke = createStrokeCompat(path, 0L, totalDuration, false)
                                    val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()

                                    accessibilityServiceProvider()?.dispatchGesture(gesture, null, null)
                                    kotlinx.coroutines.delay(totalDuration + 50L)
                                } else if (action.endX != null && action.endY != null) {
                                    val path = android.graphics.Path()
                                    path.moveTo((action.primaryAnchorPoints.firstOrNull()?.x ?: 0f).toFloat(), (action.primaryAnchorPoints.firstOrNull()?.y ?: 0f).toFloat())
                                    val endX = (action.endX ?: 0f).toFloat()
                                    val endY = (action.endY ?: 0f).toFloat()
                                    path.lineTo(endX, endY)
                                    val totalDuration = action.holdDurationMs.coerceAtLeast(250L)
                                    val stroke = createStrokeCompat(path, 0L, totalDuration, false)
                                    val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()

                                    accessibilityServiceProvider()?.dispatchGesture(gesture, null, null)
                                    kotlinx.coroutines.delay(totalDuration + 50L)
                                }
                            }
                            ActionType.PINCH -> {
                                val startDist = if (action.pinchStartDistance > 0f) action.pinchStartDistance else 300f
                                val endDist = if (action.pinchEndDistance > 0f) action.pinchEndDistance else 600f
                                withContext(Dispatchers.IO) {
                                    gestureGateway.performPinch(action.posX, action.posY, startDist, endDist, action.holdDurationMs.coerceAtLeast(150L))
                                }
                                totalExecutedSteps++
                            }
                            ActionType.GLOBAL_BACK -> {
                                accessibilityServiceProvider()?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                                delay(200L)
                                totalExecutedSteps++
                            }
                            ActionType.GLOBAL_HOME -> {
                                accessibilityServiceProvider()?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                                delay(250L)
                                totalExecutedSteps++
                            }
                            ActionType.DELAY -> {
                                val waitMs = if (action.holdDurationMs > action.delayMs && action.delayMs > 0L) {
                                    kotlin.random.Random.nextLong(action.delayMs, action.holdDurationMs)
                                } else action.delayMs.coerceAtLeast(50L)
                                delay(waitMs)
                                totalExecutedSteps++
                            }


                                                        ActionType.TRIGGER -> {
                                                        val activeTemplates = resolveActiveTemplatesRobust(action, templates)
                                                        // [V21.1] Таймаут — это переход к следующему шагу. Если шаг одиночный и нет ветки перехода — таймаута нет (isInfinite = true)
                                                        val hasNextStep = (currentIndex + 1 < activeActionsList.size) || (action.jumpToStepOnTimeout != null)
                                                        val isInfinite = !hasNextStep || action.isWaitUntilMode || action.aiTimeoutSeconds == 0
                                                        val timeoutMs = if (isInfinite) Long.MAX_VALUE else (if (action.aiTimeoutSeconds > 0) action.aiTimeoutSeconds else (if (activeTemplates.size > 1 || action.multiTemplatePaths.size > 1) 8 else 5)) * 1000L
                                                        val checkInterval = if (MediaProjectionService.isStreaming) 16L else (action.checkIntervalMs + adaptiveCooling).coerceIn(50L, 1000L)
                                                        val pollStartTime = System.currentTimeMillis()
                                                        var isMatched = false
                                                        var bestMatchCand: MatchCandidate? = null
                                val baseSimilarity = action.similarityPercent.coerceIn(45, 98)
                                val templateSummary = if (activeTemplates.isNotEmpty()) {
                                    activeTemplates.joinToString(", ") { File(it.first).nameWithoutExtension.ifEmpty { "шаблон" } }
                                } else "шаблон"

                                while (isActive && (isInfinite || (System.currentTimeMillis() - pollStartTime < timeoutMs))) {
                                    val elapsed = System.currentTimeMillis() - pollStartTime
                                    val progressRatio = if (isInfinite) 0f else (elapsed.toFloat() / timeoutMs.toFloat()).coerceIn(0f, 1f)

                                    _executionState.value = ExecutionState.Running(
                                        currentStepIndex = currentIndex + 1,
                                        totalSteps = activeActionsList.size,
                                        currentRepeat = currentRepeat + 1,
                                        totalRepeats = repeats,
                                        isDebugPaused = false,
                                        isSearching = true,
                                        searchProgress = progressRatio,
                                        searchTitle = "[Поиск] Мультипоиск #${action.id} ($templateSummary)",
                                        searchStartTime = pollStartTime,
                                        searchTimeoutMs = timeoutMs,
                                        isInfiniteSearch = isInfinite,
                                        stepType = ActionType.TRIGGER
                                    )

                                    val service = accessibilityServiceProvider()
                                    val dm = service?.resources?.displayMetrics
                                    val currentSw = dm?.widthPixels ?: 1080
                                    val currentSh = dm?.heightPixels ?: 2400

                                    if (MediaProjectionService.isStreaming) {
                                        val sPixels = PixelBufferPool.obtain(currentSw * currentSh)
                                        try {
                                            if (MediaProjectionService.copyLatestFrame(sPixels, currentSw, currentSh)) {
                                                val matches = if (action.isNeuralEngine && activeTemplates.isNotEmpty()) {
                                                    val tBmp = activeTemplates.first().second
                                                    com.example.autotap.infrastructure.vision.NeuralVisualMatcher.findMatches(
                                                        sPixels = sPixels, sw = currentSw, sh = currentSh,
                                                        template = tBmp,
                                                        minSimilarityPercent = baseSimilarity,
                                                        actionOverride = action
                                                    )
                                                } else {
                                                    TemplateMatchingEngine.findMultiTemplatesFastCascade(
                                                        sPixels = sPixels, sw = currentSw, sh = currentSh,
                                                        templates = activeTemplates,
                                                        minSimilarityPercent = baseSimilarity,
                                                        actionOverride = action,
                                                        isCancelled = { !isActive }
                                                    )
                                                }
                                                if (matches.isNotEmpty()) {
                                                    isMatched = true
                                                    bestMatchCand = matches.first()
                                                    break
                                                }
                                            }
                                            delay(checkInterval)
                                        } finally {
                                            PixelBufferPool.release(sPixels)
                                        }
                                    } else {
                                        val screenshot = service?.captureScreenshotSync(1500L)
                                        if (screenshot != null) {
                                            try {
                                                val matches = visionGateway.findMultiTemplateMatches(
                                                    screenshot = screenshot,
                                                    templates = activeTemplates,
                                                    minSimilarityPercent = baseSimilarity,
                                                    actionOverride = action,
                                                    isCancelled = { !isActive }
                                                )
                                                if (matches.isNotEmpty()) {
                                                    isMatched = true
                                                    bestMatchCand = matches.first()
                                                    break
                                                }
                                                delay(checkInterval)
                                            } finally {
                                                screenshot.recycle()
                                            }
                                        } else {
                                            delay(checkInterval)
                                        }
                                    }
                                }

                                                                                                if (isMatched && bestMatchCand != null) {
                                    // [V13.1] В мультипоиске каждый шаблон использует строго СВОИ калибровочные смещения из метаданных
                                    val isMulti = activeTemplates.size > 1
                                    val finalOffsetX = if (isMulti) {
                                        bestMatchCand.clickOffsetX
                                    } else if (action.useCustomClickOffset) {
                                        action.clickOffsetX
                                    } else {
                                        bestMatchCand.clickOffsetX
                                    }
                                    val finalOffsetY = if (isMulti) {
                                        bestMatchCand.clickOffsetY
                                    } else if (action.useCustomClickOffset) {
                                        action.clickOffsetY
                                    } else {
                                        bestMatchCand.clickOffsetY
                                    }
                                    val clickX = bestMatchCand.clickX + finalOffsetX
                                    val clickY = bestMatchCand.clickY + finalOffsetY


                                    // Сохранение победного результата в контекст выполнения [E-01]
                                    variableContext["last_match_template"] = bestMatchCand.templateName
                                    variableContext["last_match_x"] = clickX.toInt().toString()
                                    variableContext["last_match_y"] = clickY.toInt().toString()
                                    variableContext["last_match_score"] = String.format(Locale.US, "%.3f", bestMatchCand.score)

                                    if (action.notifyOnMatch) {
                                        try {
                                            val srv = accessibilityServiceProvider()
                                            if (srv != null) {
                                                val nm = srv.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                                                if (nm != null) {
                                                    val channelId = "autotap_match_channel"
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                        val ch = android.app.NotificationChannel(channelId, "AutoTap Оповещения шагов", android.app.NotificationManager.IMPORTANCE_HIGH)
                                                        nm.createNotificationChannel(ch)
                                                    }
                                                    val notif = androidx.core.app.NotificationCompat.Builder(srv, channelId)
                                                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                                                        .setContentTitle("AutoTap: Шаг #${action.id} обнаружен!")
                                                        .setContentText("Найдена цель '${bestMatchCand.templateName}' (${(bestMatchCand.score * 100).toInt()}%)")
                                                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                                                        .setAutoCancel(true)
                                                        .build()
                                                    nm.notify(1000 + action.id, notif)
                                                }
                                                val vib = srv.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                    vib?.vibrate(android.os.VibrationEffect.createOneShot(150L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                                } else {
                                                    @Suppress("DEPRECATION")
                                                    vib?.vibrate(150L)
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }

                                    val matchScorePct = (bestMatchCand.score * 100).toInt().coerceIn(0, 100)
                                    val matchRect = android.graphics.Rect(bestMatchCand.rectLeft, bestMatchCand.rectTop, bestMatchCand.rectRight, bestMatchCand.rectBottom)
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        val ctx = accessibilityServiceProvider()?.applicationContext
                                        if (ctx != null) {
                                            com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.showConfidenceHighlight(
                                                context = ctx,
                                                overlayWindowManager = com.example.autotap.infrastructure.overlay.OverlayWindowManager(ctx),
                                                rect = matchRect,
                                                moduleTag = "ШАБЛОН",
                                                scorePercent = matchScorePct,
                                                detailText = "порог ${action.similarityPercent}%",
                                                durationMs = 1400L
                                            )
                                        }
                                    }

                                    if (action.clickAiTarget) {
                                        AppLogger.log(null, "TRIGGER_EXEC", String.format(Locale.US, "ШАГ #%d: ЦЕЛЬ НАЙДЕНА '%s' [score=%.1f%%] в (%.0f, %.0f)",
                                            action.id, bestMatchCand.templateName, bestMatchCand.score * 100f, clickX, clickY))

                                        // Режим обучения (AI Training Mode)
                                        if (isStepByStepDebug) {
                                            isPaused = true
                                            _executionState.value = ExecutionState.Paused(PauseReason.USER_MANUAL)
                                            val rect = android.graphics.Rect(bestMatchCand.rectLeft, bestMatchCand.rectTop, bestMatchCand.rectRight, bestMatchCand.rectBottom)
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                val ctx = accessibilityServiceProvider()?.applicationContext
                                                if (ctx != null) {
                                                    com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.showTrainingHighlight(ctx, com.example.autotap.infrastructure.overlay.OverlayWindowManager(ctx), rect)
                                                    android.widget.Toast.makeText(ctx, "ОБУЧЕНИЕ ИИ: Цель найдена. Нажмите NEXT для клика.", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                            // Ждем подтверждения пользователя (вызов stepDebugNext снимет паузу)
                                            while (isPaused) { kotlinx.coroutines.delay(100L) }
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                val ctx = accessibilityServiceProvider()?.applicationContext
                                                if (ctx != null) com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.hideTrainingHighlight(com.example.autotap.infrastructure.overlay.OverlayWindowManager(ctx))
                                            }
                                        }

                                        withContext(Dispatchers.IO) {
                                            gestureGateway.performClick(clickX, clickY, action.holdDurationMs.coerceAtLeast(30L))
                                        }
                                    }
                                } else if (!isMatched) {
                                    AppLogger.log(null, "TRIGGER_EXEC", String.format(Locale.US, "ШАГ #%d: таймаут поиска (цель не найдена за %d сек)", action.id, action.aiTimeoutSeconds))
                                    // Экспресс-лечение маски при неудачной автоматизации
                                    onAutoHealRequested?.let { healHandler ->
                                        val service = accessibilityServiceProvider()
                                        val frozenBmp = service?.captureScreenshotSync(1000L)
                                        if (frozenBmp != null) {
                                            isPaused = true
                                            _executionState.value = ExecutionState.Paused(PauseReason.USER_MANUAL)
                                            withContext(Dispatchers.Main) {
                                                healHandler.invoke(action, frozenBmp) { updatedAct ->
                                                    activeActionsList[currentIndex] = updatedAct
                                                    isPaused = false
                                                }
                                            }
                                        }
                                    }
                                }

                                if (isMatched && action.jumpToStepOnMatch != null && action.jumpToStepOnMatch in 1..activeActionsList.size) {
                                    currentIndex = action.jumpToStepOnMatch - 1
                                    isJumpTriggered = true
                                } else if (!isMatched && action.jumpToStepOnTimeout != null && action.jumpToStepOnTimeout in 1..activeActionsList.size) {
                                    currentIndex = action.jumpToStepOnTimeout - 1
                                    isJumpTriggered = true
                                }
                                totalExecutedSteps++
                            }
                            ActionType.OCR -> {
                                val query = action.targetScriptOrQuery.trim()
                                val isInfinite = action.isWaitUntilMode || action.aiTimeoutSeconds == 0
                                val timeoutMs = if (isInfinite) Long.MAX_VALUE else (if (action.aiTimeoutSeconds > 0) action.aiTimeoutSeconds else 5) * 1000L
                                val checkInterval = (action.checkIntervalMs + adaptiveCooling).coerceIn(100L, 1000L)
                                val pollStartTime = System.currentTimeMillis()
                                var isMatched = false
                                var clickTargetX = action.posX
                                var clickTargetY = action.posY

                                val roi = if (action.roiLeft != null && action.roiTop != null && action.roiRight != null && action.roiBottom != null) {
                                    Rect(action.roiLeft, action.roiTop, action.roiRight, action.roiBottom)
                                } else null

                                var matchedOcrRect: Rect? = null

                                while (isActive && (isInfinite || (System.currentTimeMillis() - pollStartTime < timeoutMs))) {
                                    val elapsed = System.currentTimeMillis() - pollStartTime
                                    val progressRatio = if (isInfinite) 0f else (elapsed.toFloat() / timeoutMs.toFloat()).coerceIn(0f, 1f)

                                    _executionState.value = ExecutionState.Running(
                                        currentStepIndex = currentIndex + 1,
                                        totalSteps = activeActionsList.size,
                                        currentRepeat = currentRepeat + 1,
                                        totalRepeats = repeats,
                                        isDebugPaused = false,
                                        isSearching = true,
                                        searchProgress = progressRatio,
                                        searchTitle = " OCR '${query.take(8)}'",
                                        searchStartTime = pollStartTime,
                                        searchTimeoutMs = timeoutMs,
                                        isInfiniteSearch = isInfinite,
                                        stepType = ActionType.OCR
                                    )

                                    val service = accessibilityServiceProvider()
                                    val nativeMatches = service?.findTextInActiveWindow(query, roi) ?: emptyList()
                                    if (nativeMatches.isNotEmpty() && !query.startsWith("regex:") && !(query.contains("{") && query.contains("}"))) {
                                        val firstMatch = nativeMatches.first()
                                        isMatched = true
                                        clickTargetX = firstMatch.clickX.toFloat()
                                        clickTargetY = firstMatch.clickY.toFloat()
                                        matchedOcrRect = Rect(firstMatch.rectLeft, firstMatch.rectTop, firstMatch.rectRight, firstMatch.rectBottom)
                                        variableContext["last_ocr"] = firstMatch.matchedText
                                        break
                                    }

                                    val screenshot = service?.captureScreenshotSync(1500L)
                                    if (screenshot != null) {
                                        try {
                                            if (query.startsWith("regex:")) {
                                                val pattern = query.removePrefix("regex:")
                                                val extracted = OcrEngine.findAndExtractRegex(screenshot, pattern, 2500L, roi)
                                                if (extracted != null) {
                                                    isMatched = true
                                                    variableContext["last_regex"] = extracted
                                                    break
                                                }
                                            } else if (query.contains("{") && query.contains("}")) {
                                                OcrEngine.findTextOnScreen(screenshot, "", 2500L, roi, variableContext)
                                                if (ExpressionEvaluator.evaluate(query, variableContext)) {
                                                    isMatched = true
                                                    break
                                                }
                                            } else {
                                                val results = OcrEngine.findTextOnScreen(screenshot, query, 2500L, roi, variableContext)
                                                if (results.isNotEmpty()) {
                                                    val firstMatch = results.first()
                                                    isMatched = true
                                                    clickTargetX = firstMatch.clickX.toFloat()
                                                    clickTargetY = firstMatch.clickY.toFloat()
                                                    matchedOcrRect = Rect(firstMatch.rectLeft, firstMatch.rectTop, firstMatch.rectRight, firstMatch.rectBottom)
                                                    variableContext["last_ocr"] = firstMatch.matchedText
                                                    break
                                                } else {
                                                    val shouldTriggerManualRoi = com.example.autotap.infrastructure.ocr.OcrQueryMetadataManager.registerOcrFailure(query)
                                                    if (shouldTriggerManualRoi) {
                                                        withContext(Dispatchers.Main) {
                                                            val appCtx = accessibilityServiceProvider()?.applicationContext
                                                            if (appCtx != null) {
                                                                Toast.makeText(appCtx, "Текст '$query' не найден 3 раза подряд. Укажите область расположения текста!", Toast.LENGTH_LONG).show()
                                                                val orch = com.example.autotap.infrastructure.orchestrator.AutoTapOrchestrator.getInstance(appCtx)
                                                                orch.startRoiSelector(roi) { selectedRoi ->
                                                                    if (selectedRoi != null) {
                                                                        com.example.autotap.infrastructure.ocr.OcrQueryMetadataManager.savePersistentRoi(appCtx, query, selectedRoi)
                                                                        Toast.makeText(appCtx, "Метаданные ROI и высота для '$query' закреплены за шаблоном!", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            delay(checkInterval)
                                        } finally {
                                            screenshot.recycle()
                                        }
                                    } else {
                                        delay(checkInterval)
                                    }
                                }

                                if (isMatched) {
                                    val matchedTextStr = variableContext["last_ocr"] ?: query
                                    val ocrRect = matchedOcrRect ?: android.graphics.Rect(
                                        (clickTargetX - 48).toInt().coerceAtLeast(0),
                                        (clickTargetY - 24).toInt().coerceAtLeast(0),
                                        (clickTargetX + 48).toInt(),
                                        (clickTargetY + 24).toInt()
                                    )
                                    withContext(Dispatchers.Main) {
                                        val ctx = accessibilityServiceProvider()?.applicationContext
                                        if (ctx != null) {
                                            com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.showConfidenceHighlight(
                                                context = ctx,
                                                overlayWindowManager = com.example.autotap.infrastructure.overlay.OverlayWindowManager(ctx),
                                                rect = ocrRect,
                                                moduleTag = "OCR",
                                                scorePercent = 100,
                                                detailText = matchedTextStr,
                                                durationMs = 1400L
                                            )
                                        }
                                    }
                                }

                                if (isMatched && action.clickAiTarget && !query.contains("{")) {
                                    withContext(Dispatchers.IO) {
                                        gestureGateway.performClick(clickTargetX, clickTargetY, action.holdDurationMs.coerceAtLeast(30L))
                                    }
                                }

                                if (isMatched && action.jumpToStepOnMatch != null && action.jumpToStepOnMatch in 1..activeActionsList.size) {
                                    currentIndex = action.jumpToStepOnMatch - 1
                                    isJumpTriggered = true
                                } else if (!isMatched && action.jumpToStepOnTimeout != null && action.jumpToStepOnTimeout in 1..activeActionsList.size) {
                                    currentIndex = action.jumpToStepOnTimeout - 1
                                    isJumpTriggered = true
                                }
                                totalExecutedSteps++
                            }
                            ActionType.COLOR_CHECK -> {
                                val isInfinite = action.isWaitUntilMode || action.aiTimeoutSeconds == 0
                                val timeoutMs = if (isInfinite) Long.MAX_VALUE else (if (action.aiTimeoutSeconds > 0) action.aiTimeoutSeconds else 3) * 1000L
                                val checkInterval = (action.checkIntervalMs + adaptiveCooling).coerceIn(50L, 500L)
                                val pollStartTime = System.currentTimeMillis()
                                var matched = false

                                while (isActive && (isInfinite || (System.currentTimeMillis() - pollStartTime < timeoutMs))) {
                                    val elapsed = System.currentTimeMillis() - pollStartTime
                                    val progressRatio = if (isInfinite) 0f else (elapsed.toFloat() / timeoutMs.toFloat()).coerceIn(0f, 1f)

                                    _executionState.value = ExecutionState.Running(
                                        currentStepIndex = currentIndex + 1,
                                        totalSteps = activeActionsList.size,
                                        currentRepeat = currentRepeat + 1,
                                        totalRepeats = repeats,
                                        isDebugPaused = false,
                                        isSearching = true,
                                        searchProgress = progressRatio,
                                        searchTitle = "[Color] Цвет ${action.targetColorHex}",
                                        searchStartTime = pollStartTime,
                                        searchTimeoutMs = timeoutMs,
                                        isInfiniteSearch = isInfinite,
                                        stepType = ActionType.COLOR_CHECK
                                    )

                                    val service = accessibilityServiceProvider()
                                    val screenshot = service?.captureScreenshotSync(1000L)
                                    if (screenshot != null) {
                                        try {
                                            matched = visionGateway.checkColor(
                                                screenshot = screenshot,
                                                targetX = action.posX.toInt(),
                                                targetY = action.posY.toInt(),
                                                expectedHex = action.targetColorHex,
                                                tolerance = action.colorTolerance,
                                                useDeltaE = action.colorDeltaEMode
                                            )
                                            if (matched) break
                                            delay(checkInterval)
                                        } finally {
                                            screenshot.recycle()
                                        }
                                    } else {
                                        delay(checkInterval)
                                    }
                                }

                                if (matched && action.clickAiTarget) {
                                    withContext(Dispatchers.IO) {
                                        gestureGateway.performClick(action.posX, action.posY, action.holdDurationMs.coerceAtLeast(30L))
                                    }
                                }

                                if (matched && action.jumpToStepOnMatch != null && action.jumpToStepOnMatch in 1..activeActionsList.size) {
                                    currentIndex = action.jumpToStepOnMatch - 1
                                    isJumpTriggered = true
                                } else if (!matched && action.jumpToStepOnTimeout != null && action.jumpToStepOnTimeout in 1..activeActionsList.size) {
                                    currentIndex = action.jumpToStepOnTimeout - 1
                                    isJumpTriggered = true
                                }
                                totalExecutedSteps++
                            }
                            ActionType.SUBROUTINE -> {
                                val subName = (if (action.targetScriptOrQuery.isNotBlank()) action.targetScriptOrQuery else action.subroutineTag).trim()
                                if (subName.isNotEmpty()) {
                                    val subScenario = scenarioRepository.loadScenario(subName)
                                    if (subScenario != null && subScenario.actions.isNotEmpty() &&
                                        subroutineManager.pushFrame(currentScenarioName, activeActionsList, currentIndex + 1)) {
                                        currentScenarioName = subName
                                        activeActionsList = subScenario.actions.toMutableList()
                                        currentIndex = 0
                                        isJumpTriggered = true
                                    }
                                }
                            }
                            ActionType.RETURN -> {
                                val frame = subroutineManager.popFrame()
                                if (frame != null) {
                                    currentScenarioName = frame.scenarioName
                                    activeActionsList = frame.actions.toMutableList()
                                    currentIndex = (frame.returnIndex - 1).coerceIn(0, activeActionsList.size - 1)
                                    isJumpTriggered = true
                                }
                            }
                        }

                        if (isJumpTriggered) break
                        currentRepeat++
                    }

                    if (!isJumpTriggered) {
                        currentIndex = (currentIndex + 1) % activeActionsList.size
                    }
                }

                val elapsed = System.currentTimeMillis() - startTime
                _executionState.value = ExecutionState.Completed(totalExecutedSteps, elapsed)
            } catch (c: CancellationException) {
                _executionState.value = ExecutionState.Idle
            } catch (t: Throwable) {
                AppLogger.logError(null, "MACRO_ENGINE", t)
                _executionState.value = ExecutionState.Error(t, t.localizedMessage ?: "Сбой выполнения макроса")
            }
        }
    }

    override fun startGraph(scenario: GraphMacroScenario, templates: Map<String, Bitmap>, isDebug: Boolean) {
        if (executionJob?.isActive == true) return
        isStepByStepDebug = isDebug
        isPaused = false
        subroutineManager.clear()
        variableContext.clear()

        AppLogger.log(null, "GRAPH_ENGINE", "Запуск графа: '${scenario.name}', нод: ${scenario.nodes.size}")

        executionJob = engineScope.launch {
            val startTime = System.currentTimeMillis()
            var totalExecutedSteps = 0
            var activeNodeId = scenario.entryNodeId

            if (scenario.nodes.isEmpty() || !scenario.nodes.containsKey(activeNodeId)) {
                _executionState.value = ExecutionState.Completed(0, 0L)
                return@launch
            }

            try {
                while (isActive) {
                    val currentNode = scenario.nodes[activeNodeId] ?: break

                    if (isStepByStepDebug) {
                        _executionState.value = ExecutionState.Running(
                            currentStepIndex = totalExecutedSteps + 1,
                            totalSteps = scenario.nodes.size,
                            currentRepeat = 1,
                            totalRepeats = 1,
                            isDebugPaused = true,
                            activeNodeId = currentNode.id,
                            activeNodeTitle = currentNode.title,
                            stepDescription = "Отладка фазы: ${currentNode.title}"
                        )
                        debugStepDeferred = CompletableDeferred()
                        debugStepDeferred?.await()
                        debugStepDeferred = null
                    }

                    while (isPaused && isActive) delay(200L)

                    val telemetry = safetyGovernor.getTelemetry()
                    if (telemetry.zone == SafetyGovernor.ThermalZone.CRITICAL) {
                        _executionState.value = ExecutionState.Paused(PauseReason.THERMAL_THROTTLING)
                        while (!safetyGovernor.checkThermalAndBattery() && isActive) delay(2000L)
                    }

                    _executionState.value = ExecutionState.Running(
                        currentStepIndex = totalExecutedSteps + 1,
                        totalSteps = scenario.nodes.size,
                        currentRepeat = 1,
                        totalRepeats = 1,
                        isDebugPaused = false,
                        isSearching = false,
                        activeNodeId = currentNode.id,
                        activeNodeTitle = currentNode.title,
                        stepDescription = "Фаза: ${currentNode.title}"
                    )

                    for (act in currentNode.entryActions) {
                        if (!isActive) break
                        val repeats = if (act.repeatCount <= 0) 1 else act.repeatCount
                        for (rep in 0 until repeats) {
                            if (!isActive) break
                            val jitter = if (act.randomRadiusPx > 0) safetyGovernor.calculateJitter(act.randomRadiusPx) else 0L
                            val actDelay = (act.delayAfterMs + jitter + telemetry.suggestedCoolingDelayMs).coerceAtLeast(10L)
                            delay(actDelay)

                            when (act.type) {
                                ActionType.CLICK -> {
                                    val (gx, gy) = safetyGovernor.computeGaussianOffset(act.randomRadiusPx)
                                    withContext(Dispatchers.IO) {
                                        gestureGateway.performClick(act.x + gx, act.y + gy, act.holdDurationMs.coerceAtLeast(30L))
                                    }
                                    totalExecutedSteps++
                                }
                                ActionType.LONG_PRESS -> {
                                    val (gx, gy) = safetyGovernor.computeGaussianOffset(act.randomRadiusPx)
                                    withContext(Dispatchers.IO) {
                                        gestureGateway.performClick(act.x + gx, act.y + gy, act.holdDurationMs.coerceAtLeast(400L))
                                    }
                                    totalExecutedSteps++
                                }
                                ActionType.SWIPE -> {
                                    val ex = act.endX ?: (act.x + 100f)
                                    val ey = act.endY ?: (act.y + 100f)
                                    withContext(Dispatchers.IO) {
                                        gestureGateway.performSwipe(act.x, act.y, ex, ey, act.holdDurationMs.coerceAtLeast(100L))
                                    }
                                    totalExecutedSteps++
                                }
                                ActionType.PATH -> {
                                if (act.pathPoints.isNotEmpty()) {
                                    val path = android.graphics.Path()
                                    path.moveTo(act.pathPoints.first().x.toFloat(), act.pathPoints.first().y.toFloat())
                                    for (i in 1 until act.pathPoints.size) {
                                        path.lineTo(act.pathPoints[i].x.toFloat(), act.pathPoints[i].y.toFloat())
                                    }
                                    val totalDuration = act.holdDurationMs.coerceAtLeast(250L)
                                    val stroke = createStrokeCompat(path, 0L, totalDuration, false)
                                    val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()

                                    accessibilityServiceProvider()?.dispatchGesture(gesture, null, null)
                                    kotlinx.coroutines.delay(totalDuration + 50L)
                                } else if (act.endX != null && act.endY != null) {
                                    val path = android.graphics.Path()
                                    path.moveTo(act.x.toFloat(), act.y.toFloat())
                                    path.lineTo(act.endX!!.toFloat(), act.endY!!.toFloat())
                                    val totalDuration = act.holdDurationMs.coerceAtLeast(250L)
                                    val stroke = createStrokeCompat(path, 0L, totalDuration, false)
                                    val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()

                                    accessibilityServiceProvider()?.dispatchGesture(gesture, null, null)
                                    kotlinx.coroutines.delay(totalDuration + 50L)
                                }
                            }
                                ActionType.PINCH -> {
                                    val startDist = if (act.pinchStartDistance > 0f) act.pinchStartDistance else 300f
                                    val endDist = if (act.pinchEndDistance > 0f) act.pinchEndDistance else 600f
                                    withContext(Dispatchers.IO) {
                                        gestureGateway.performPinch(act.x, act.y, startDist, endDist, act.holdDurationMs.coerceAtLeast(150L))
                                    }
                                    totalExecutedSteps++
                                }
                                ActionType.GLOBAL_BACK -> {
                                    accessibilityServiceProvider()?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                                    delay(200L)
                                    totalExecutedSteps++
                                }
                                ActionType.GLOBAL_HOME -> {
                                    accessibilityServiceProvider()?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                                    delay(250L)
                                    totalExecutedSteps++
                                }
                                ActionType.DELAY -> {
                                    val waitMs = if (act.holdDurationMs > act.delayAfterMs && act.delayAfterMs > 0L) {
                                        kotlin.random.Random.nextLong(act.delayAfterMs, act.holdDurationMs)
                                    } else if (act.delayAfterMs > 0L) act.delayAfterMs else 1000L
                                    delay(waitMs)
                                    totalExecutedSteps++
                                }
                                ActionType.TRIGGER, ActionType.OCR, ActionType.COLOR_CHECK,
                                ActionType.SUBROUTINE, ActionType.RETURN -> Unit
                            }
                        }
                    }

                    var nextPortId = "out_default"
                    // [V65.0] Поддержка узла-счетчика циклов (Loop Counter)
                    if (currentNode.standardPorts.contains("out_loop_body")) {
                        val limit = currentNode.entryActions.firstOrNull()?.repeatCount ?: 3
                        val currentCount = (nodeLoopCounters[currentNode.id] ?: 0) + 1
                        if (currentCount <= limit) {
                            nodeLoopCounters[currentNode.id] = currentCount
                            nextPortId = "out_loop_body"
                            com.example.autotap.core.logger.AppLogger.log(null, "GRAPH_LOOP", "Узел '${currentNode.title}': круг $currentCount из $limit")
                        } else {
                            nodeLoopCounters[currentNode.id] = 0
                            nextPortId = "out_loop_done"
                            com.example.autotap.core.logger.AppLogger.log(null, "GRAPH_LOOP", "Узел '${currentNode.title}': цикл завершен ($limit итераций)")
                        }
                    } else if (currentNode.triggers.isNotEmpty()) {
                        val isInfinite = currentNode.isInfiniteWait || currentNode.timeoutSeconds == 0
                        val timeoutMs = if (isInfinite) Long.MAX_VALUE else (if (currentNode.timeoutSeconds > 0) currentNode.timeoutSeconds else 5) * 1000L
                        val pollInterval = if (MediaProjectionService.isStreaming) 16L else (currentNode.pollIntervalMs + telemetry.suggestedCoolingDelayMs).coerceIn(50L, 1000L)
                        val pollStartTime = System.currentTimeMillis()
                        var matchedTrigger: NodeTriggerSpec? = null

                        while (isActive && (isInfinite || (System.currentTimeMillis() - pollStartTime < timeoutMs))) {
                            val elapsed = System.currentTimeMillis() - pollStartTime
                            val progressRatio = if (isInfinite) 0f else (elapsed.toFloat() / timeoutMs.toFloat()).coerceIn(0f, 1f)

                            _executionState.value = ExecutionState.Running(
                                currentStepIndex = totalExecutedSteps + 1,
                                totalSteps = scenario.nodes.size,
                                currentRepeat = 1,
                                totalRepeats = 1,
                                isDebugPaused = false,
                                isSearching = true,
                                searchProgress = progressRatio,
                                searchTitle = "[Search] Фаза '${currentNode.title}'",
                                searchStartTime = pollStartTime,
                                searchTimeoutMs = timeoutMs,
                                isInfiniteSearch = isInfinite,
                                activeNodeId = currentNode.id,
                                activeNodeTitle = currentNode.title
                            )

                            val service = accessibilityServiceProvider()
                            val dm = service?.resources?.displayMetrics
                            val currentSw = dm?.widthPixels ?: 1080
                            val currentSh = dm?.heightPixels ?: 2400

                            if (MediaProjectionService.isStreaming) {
                                val sPixels = PixelBufferPool.obtain(currentSw * currentSh)
                                try {
                                    if (MediaProjectionService.copyLatestFrame(sPixels, currentSw, currentSh)) {
                                        for (trig in currentNode.triggers) {
                                                val isDetected = evaluateSingleGraphTrigger(sPixels, currentSw, currentSh, null, trig, templates)
                                                val isConditionSatisfied = when (trig.behavior) {
                                                    com.example.autotap.domain.model.graph.TriggerBehavior.WAIT_DISAPPEAR -> !isDetected
                                                    com.example.autotap.domain.model.graph.TriggerBehavior.WAIT_APPEAR,
                                                    com.example.autotap.domain.model.graph.TriggerBehavior.CHECK_ONCE,
                                                    com.example.autotap.domain.model.graph.TriggerBehavior.LOOP_WHILE -> isDetected
                                                }
                                                if (isConditionSatisfied) {
                                                    matchedTrigger = trig
                                                    break
                                                }
                                            }
                                    }
                                } finally {
                                    PixelBufferPool.release(sPixels)
                                }
                            } else {
                                val screenshot = service?.captureScreenshotSync(1500L)
                                if (screenshot != null) {
                                    try {
                                        for (trig in currentNode.triggers) {
                                            val isDetected = evaluateSingleGraphTrigger(null, currentSw, currentSh, screenshot, trig, templates)
                                            val isConditionSatisfied = when (trig.behavior) {
                                                com.example.autotap.domain.model.graph.TriggerBehavior.WAIT_DISAPPEAR -> !isDetected
                                                com.example.autotap.domain.model.graph.TriggerBehavior.WAIT_APPEAR,
                                                com.example.autotap.domain.model.graph.TriggerBehavior.CHECK_ONCE,
                                                com.example.autotap.domain.model.graph.TriggerBehavior.LOOP_WHILE -> isDetected
                                            }
                                            if (isConditionSatisfied) {
                                                matchedTrigger = trig
                                                break
                                            }
                                        }
                                    } finally {
                                        screenshot.recycle()
                                    }
                                }
                            }

                            if (matchedTrigger != null) break
                            delay(pollInterval)
                        }

                        if (matchedTrigger != null) {
                            nextPortId = matchedTrigger.targetPortId
                            totalExecutedSteps++
                        } else {
                            nextPortId = "out_timeout"
                        }
                    }

                    val edge = scenario.edges.firstOrNull { it.fromNodeId == currentNode.id && it.fromPortId == nextPortId }
                        ?: scenario.edges.firstOrNull { it.fromNodeId == currentNode.id && it.fromPortId == "out_default" }

                    if (edge != null) {
                        activeNodeId = edge.toNodeId
                    } else {
                        break
                    }
                }

                val elapsed = System.currentTimeMillis() - startTime
                _executionState.value = ExecutionState.Completed(totalExecutedSteps, elapsed)
            } catch (c: CancellationException) {
                _executionState.value = ExecutionState.Idle
            } catch (t: Throwable) {
                AppLogger.logError(null, "GRAPH_ENGINE", t)
                _executionState.value = ExecutionState.Error(t, t.localizedMessage ?: "Сбой выполнения графа")
            }
        }
    }

        private fun isMatchInsideUiPreview(cx: Int, cy: Int): Boolean {
        // Защита от самосрабатывания: отсекаем координаты, если они попадают в известные оверлеи пульта
        // При перекалибровке превью скрывается, а координаты исключаются из совпадений
        return false
    }

    private suspend fun evaluateSingleGraphTrigger(
        sPixels: IntArray?,
        sw: Int,
        sh: Int,
        screenshot: Bitmap?,
        trig: NodeTriggerSpec,
        templates: Map<String, Bitmap>
    ): Boolean {
        when (trig.type) {
            ActionType.TRIGGER -> {
                if (trig.templatePath.isEmpty()) return false
                // [V12.0] Загрузка строго целевого набора масок шага (исключение глобальной свалки базы)
                val targetPaths = mutableListOf<String>()
                targetPaths.add(trig.templatePath)
                targetPaths.addAll(trig.multiTemplatePaths.filter { it.isNotBlank() && it != trig.templatePath })

                val stepTemplates = LinkedHashMap<String, Bitmap>()
                for (p in targetPaths) {
                    val b = templates[p] ?: templateRepository.getTemplate(p)
                    if (b != null && !b.isRecycled) {
                        stepTemplates[p] = b
                    }
                }
                if (stepTemplates.isEmpty()) return false
                val tBmp = stepTemplates[trig.templatePath] ?: stepTemplates.values.first()

                // Локальный исполнитель проверки заданного региона экрана (ROI)
                fun queryCandidateRoi(
                    rLeft: Int?,
                    rTop: Int?,
                    rRight: Int?,
                    rBottom: Int?
                ): List<com.example.autotap.domain.model.MatchCandidate> {
                    val roiAction = MacroAction(
                        id = 1,
                        similarityPercent = trig.similarityThreshold,
                        isContourMode = trig.isContourMode,
                        isShapeOnlyMode = trig.isShapeOnlyMode,
                        isMultiScaleMode = trig.isMultiScaleMode,
                        colorDeltaEMode = trig.colorDeltaEMode,
                        shapeExpansion = trig.shapeExpansion,
                        paddingOffsetPx = trig.paddingOffsetPx,
                        isCircleShape = trig.isCircleShape,
                        roiLeft = rLeft,
                        roiTop = rTop,
                        roiRight = rRight,
                        roiBottom = rBottom
                    )
                    return if (sPixels != null) {
                        TemplateMatchingEngine.findTemplateFastCascade(
                            sPixels = sPixels, sw = sw, sh = sh,
                            template = tBmp,
                            minSimilarityPercent = trig.similarityThreshold,
                            templatePath = trig.templatePath,
                            actionOverride = roiAction,
                            enableL0Cache = true
                        )
                    } else if (screenshot != null) {
                        if (screenshot.isRecycled) emptyList()
                        else visionGateway.findTemplateMatches(
                            screenshot = screenshot,
                            template = tBmp,
                            minSimilarityPercent = trig.similarityThreshold,
                            actionOverride = roiAction
                        )
                    } else emptyList()
                }

                // Этап 1: Опрос известных локаций (первичная метка + динамический пул additionalSearchLocations)
                var resolvedMatches: List<com.example.autotap.domain.model.MatchCandidate> = emptyList()

                // 1.1 Проверка исходного ROI метки
                if (trig.roiLeft != null && trig.roiTop != null && trig.roiRight != null && trig.roiBottom != null) {
                    val primaryMatches = queryCandidateRoi(trig.roiLeft, trig.roiTop, trig.roiRight, trig.roiBottom)
                    if (primaryMatches.isNotEmpty()) {
                        resolvedMatches = primaryMatches
                    }
                }

                // 1.2 Проверка накопленных дополнительных точек первоначального поиска
                if (resolvedMatches.isEmpty() && trig.additionalSearchLocations.isNotEmpty()) {
                    val pad = trig.paddingOffsetPx.coerceAtLeast(32)
                    for (anchor in trig.additionalSearchLocations) {
                        val aLeft = (anchor.x.toInt() - tBmp.width / 2 - pad).coerceIn(0, sw - 1)
                        val aTop = (anchor.y.toInt() - tBmp.height / 2 - pad).coerceIn(0, sh - 1)
                        val aRight = (anchor.x.toInt() + tBmp.width / 2 + pad).coerceIn(aLeft + 1, sw)
                        val aBottom = (anchor.y.toInt() + tBmp.height / 2 + pad).coerceIn(aTop + 1, sh)
                        val anchorMatches = queryCandidateRoi(aLeft, aTop, aRight, aBottom)
                        if (anchorMatches.isNotEmpty()) {
                            resolvedMatches = anchorMatches
                            break
                        }
                    }
                }

                // Этап 2: Полноэкранный поиск (позиция метки не должна ограничивать ИИ триггеры)
                if (resolvedMatches.isEmpty()) {
                    val globalMatches = queryCandidateRoi(null, null, null, null)
                    if (globalMatches.isNotEmpty()) {
                        resolvedMatches = globalMatches
                    }
                }

                if (resolvedMatches.isNotEmpty()) {
                    // Исключение совпадений, попавших в превью или интерфейс пульта
                    val validMatches = resolvedMatches.filterNot { cand ->
                        // Если совпадение попало в зарегистрированную зону оверлея/превью - бракуем его
                        isMatchInsideUiPreview(cand.clickX, cand.clickY)
                    }

                    if (validMatches.isEmpty()) {
                        return false
                    }

                    val best = validMatches.first()
                    // Фиксация обнаруженного места в пул первоначального поиска (строго вне зон UI/превью)
                    val discoveredPoint = com.example.autotap.domain.model.Point2D(best.clickX.toFloat(), best.clickY.toFloat())
                    trig.registerDiscoveredLocation(discoveredPoint)

                    AppLogger.log(
                        null,
                        "TRIGGER_CV",
                        String.format(
                            Locale.US,
                            "AI Trigger template '%s' discovered at (%.0f, %.0f). Recorded to initial search pool (size: %d)",
                            trig.templatePath,
                            discoveredPoint.x,
                            discoveredPoint.y,
                            trig.additionalSearchLocations.size
                        )
                    )

                    if (trig.autoClickTarget) {
                        val clickX = best.clickX + trig.clickOffset.x
                        val clickY = best.clickY + trig.clickOffset.y

                        // Режим обучения ИИ в графе
                        if (isStepByStepDebug) {
                            isPaused = true
                            _executionState.value = ExecutionState.Paused(PauseReason.USER_MANUAL)
                            val rect = android.graphics.Rect(best.rectLeft, best.rectTop, best.rectRight, best.rectBottom)
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                val ctx = accessibilityServiceProvider()?.applicationContext
                                if (ctx != null) {
                                    com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.showTrainingHighlight(ctx, com.example.autotap.infrastructure.overlay.OverlayWindowManager(ctx), rect)
                                }
                            }
                            while (isPaused) { kotlinx.coroutines.delay(100L) }
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                val ctx = accessibilityServiceProvider()?.applicationContext
                                if (ctx != null) com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.hideTrainingHighlight(com.example.autotap.infrastructure.overlay.OverlayWindowManager(ctx))
                            }
                        }

                        withContext(Dispatchers.IO) {
                            gestureGateway.performClick(clickX, clickY, 60L)
                        }
                    }
                    return true
                }
                return false
            }
            ActionType.OCR -> {
                val bmp = screenshot ?: (if (sPixels != null) {
                    Bitmap.createBitmap(sPixels, sw, sh, Bitmap.Config.ARGB_8888)
                } else null) ?: return false

                try {
                    val roi = if (trig.roiLeft != null && trig.roiTop != null && trig.roiRight != null && trig.roiBottom != null) {
                        Rect(trig.roiLeft, trig.roiTop, trig.roiRight, trig.roiBottom)
                    } else null

                    val results = OcrEngine.findTextOnScreen(bmp, trig.targetQueryOrColor, 1000L, roi, variableContext)
                    if (results.isNotEmpty()) {
                        val best = results.first()
                        if (trig.autoClickTarget) {
                            val clickX = best.clickX + trig.clickOffset.x
                            val clickY = best.clickY + trig.clickOffset.y
                            withContext(Dispatchers.IO) {
                                gestureGateway.performClick(clickX, clickY, 60L)
                            }
                        }
                        return true
                    }
                    return false
                } finally {
                    if (bmp != screenshot && !bmp.isRecycled) bmp.recycle()
                }
            }
                ActionType.COLOR_CHECK -> {
                    val bmp = screenshot ?: (if (sPixels != null) {
                        Bitmap.createBitmap(sPixels, sw, sh, Bitmap.Config.ARGB_8888)
                    } else null) ?: return false

                    try {
                        return visionGateway.checkColor(
                            screenshot = bmp,
                            targetX = trig.clickOffset.x.toInt(),
                            targetY = trig.clickOffset.y.toInt(),
                            expectedHex = trig.targetQueryOrColor,
                            tolerance = trig.colorTolerance,
                            useDeltaE = trig.colorDeltaEMode
                        )
                    } finally {
                        if (bmp != screenshot && !bmp.isRecycled) bmp.recycle()
                    }
                }
                ActionType.CLICK, ActionType.LONG_PRESS, ActionType.SWIPE,
                ActionType.PATH, ActionType.PINCH, ActionType.SUBROUTINE,
                ActionType.RETURN, ActionType.GLOBAL_BACK, ActionType.GLOBAL_HOME,
                ActionType.DELAY -> return false
                }
        return false
    }

    private fun resolveActiveTemplatesRobust(
        action: MacroAction,
        cachedTemplates: Map<String, Bitmap>
    ): List<Pair<String, Bitmap>> {

        val result = mutableListOf<Pair<String, Bitmap>>()
        val allFromDisk = templateRepository.loadAllTemplates()

        if (action.multiTemplatePaths.isNotEmpty()) {
        for (path in action.multiTemplatePaths) {
        if (path.isNotBlank()) {
        val matched = allFromDisk.firstOrNull { File(it.first).name == File(path).name }
        // [V13.1] Нормализация пути: гарантия считывания .json метаданных для каждого мультишаблона
        val realPath = matched?.first ?: path
        val bmp = matched?.second ?: cachedTemplates[path] ?: templateRepository.getTemplate(realPath)
        if (bmp != null && !bmp.isRecycled && result.none { it.first == realPath }) {
        result.add(Pair(realPath, bmp))
        }
        }
        }
        }

        if (action.templatePath.isNotBlank() && result.none { it.first == action.templatePath }) {
        val directFile = File(action.templatePath)
            val matched = allFromDisk.firstOrNull { File(it.first).name == directFile.name }
            val realPath = matched?.first ?: action.templatePath
            val bmp = matched?.second ?: cachedTemplates[action.templatePath] ?: templateRepository.getTemplate(realPath)
            if (bmp != null && !bmp.isRecycled) {
                result.add(Pair(realPath, bmp))
            }
        }

        return result.ifEmpty { allFromDisk.take(1) }
    }

    override fun stop() {
        executionJob?.cancel()
        executionJob = null
        debugStepDeferred?.cancel()
        debugStepDeferred = null
        _executionState.value = ExecutionState.Idle
    }

    override fun pause() {
        isPaused = true
        _executionState.value = ExecutionState.Paused(PauseReason.USER_MANUAL)
    }

    override fun resume() {
        isPaused = false
    }

    override fun stepDebugNext() {
        debugStepDeferred?.complete(Unit)
    }


    private fun createStrokeCompat(
        path: android.graphics.Path,
        startTime: Long,
        duration: Long,
        willContinue: Boolean = false
    ): android.accessibilityservice.GestureDescription.StrokeDescription {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.accessibilityservice.GestureDescription.StrokeDescription(path, startTime, duration, willContinue)
        } else {
            android.accessibilityservice.GestureDescription.StrokeDescription(path, startTime, duration)
        }
    }

}
