package com.example.autotap.infrastructure.engine

import android.graphics.Bitmap
import android.graphics.Rect
import android.widget.Toast
import com.example.autotap.core.logger.AppLogger
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

    override val isDebugRunning: Boolean get() = isStepByStepDebug

    private fun isScreenInteractive(): Boolean {
        val ctx = accessibilityServiceProvider()?.applicationContext ?: return true
        val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        return pm?.isInteractive ?: true
    }

    @Volatile
    private var isPaused = false

    @Volatile
    private var cachedOverlayWindowManager: com.example.autotap.infrastructure.overlay.OverlayWindowManager? = null
    private fun getOverlayWindowManager(ctx: android.content.Context): com.example.autotap.infrastructure.overlay.OverlayWindowManager {
        val existing = cachedOverlayWindowManager
        if (existing != null) return existing
        val created = com.example.autotap.infrastructure.overlay.OverlayWindowManager(ctx)
        cachedOverlayWindowManager = created
        return created
    }

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
                    if (!isScreenInteractive()) {
                        AppLogger.log(null, "MACRO_ENGINE", "Экран выключен — остановка сценария")
                        stop()
                        break
                    }
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
                                val effHold = action.holdDurationMs.coerceIn(10L, 10000L)
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
                            ActionType.SWIPE, ActionType.PATH -> {
                                val executor = com.example.autotap.infrastructure.gesture.ActionExecutor(
                                    com.example.autotap.infrastructure.gesture.GesturePathPlayer { accessibilityServiceProvider() },
                                    com.example.autotap.infrastructure.gesture.VirtualStickPathPlayer(object : com.example.autotap.infrastructure.gesture.VirtualStick {
                                        override fun setDirection(dx: Float, dy: Float, strength: Float) {
                                            com.example.autotap.core.logger.AppLogger.log(accessibilityServiceProvider(), "VIRTUAL_STICK", "Симуляция стика: dx=%.2f, dy=%.2f".format(dx, dy))
                                        }
                                        override fun release() {
                                            com.example.autotap.core.logger.AppLogger.log(accessibilityServiceProvider(), "VIRTUAL_STICK", "Стик отпущен")
                                        }
                                    })
                                )
                                withContext(Dispatchers.IO) {
                                    executor.execute(action)
                                }
                                totalExecutedSteps++
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
                                                        val ocrQueries = action.multiTemplatePaths.filter { it.startsWith("ocr:") }.map { it.removePrefix("ocr:") }
                                                        val hasNextStep = (currentIndex + 1 < activeActionsList.size) || (action.jumpToStepOnTimeout != null)
                                                        val isInfinite = !hasNextStep || action.isWaitUntilMode || action.aiTimeoutSeconds == 0
                                                        val timeoutMs = if (isInfinite) Long.MAX_VALUE else (if (action.aiTimeoutSeconds > 0) action.aiTimeoutSeconds else (if (activeTemplates.size > 1 || action.multiTemplatePaths.size > 1) 8 else 5)) * 1000L
                                                        val checkInterval = if (MediaProjectionService.isStreaming) 33L else (action.checkIntervalMs + adaptiveCooling).coerceIn(50L, 1000L)
                                                        val pollStartTime = System.currentTimeMillis()
                                                        var isMatched = false
                                                        var bestMatchCand: MatchCandidate? = null
                                                        val baseSimilarity = action.similarityPercent.coerceIn(45, 98)

                                                        com.example.autotap.core.logger.AppLogger.log(
                                                            accessibilityServiceProvider() ?: return@launch,
                                                            "CV_EXECUTION",
                                                            String.format(Locale.US, "Старт поиска шага #%d: графических=%d, ocr=%d, порог=%d%%, таймаут=%s",
                                                                action.id, activeTemplates.size, ocrQueries.size, baseSimilarity, if (isInfinite) "БЕСКОНЕЧНО" else "${action.aiTimeoutSeconds}сек")
                                                        )

                                                        var lastStateEmittedTime = 0L
                                                        while (isActive && (isInfinite || (System.currentTimeMillis() - pollStartTime < timeoutMs))) {
                                                            if (!isScreenInteractive()) {
                                                                stop()
                                                                break
                                                            }
                                                            val elapsed = System.currentTimeMillis() - pollStartTime
                                                            val progressRatio = if (isInfinite) 0f else (elapsed.toFloat() / timeoutMs.toFloat()).coerceIn(0f, 1f)

                                                            if (elapsed - lastStateEmittedTime >= 250L || lastStateEmittedTime == 0L) {
                                                                lastStateEmittedTime = elapsed
                                                                _executionState.value = ExecutionState.Running(
                                                                    currentStepIndex = currentIndex + 1,
                                                                    totalSteps = activeActionsList.size,
                                                                    currentRepeat = currentRepeat + 1,
                                                                    totalRepeats = repeats,
                                                                    isDebugPaused = false,
                                                                    isSearching = true,
                                                                    searchProgress = progressRatio,
                                                                    searchTitle = if (activeTemplates.size + ocrQueries.size > 1) "Мультипоиск (${activeTemplates.size + ocrQueries.size} целей)" else if (activeTemplates.isNotEmpty()) "Поиск: ${File(activeTemplates.first().first).nameWithoutExtension.removePrefix("mask_").trim()}" else "Поиск шаблона",
                                                                    searchStartTime = pollStartTime,
                                                                    searchTimeoutMs = timeoutMs,
                                                                    isInfiniteSearch = isInfinite,
                                                                    stepType = ActionType.TRIGGER
                                                                )
                                                            }

                                                            val service = accessibilityServiceProvider()
                                                            val dm = service?.resources?.displayMetrics
                                                            val currentSw = dm?.widthPixels ?: 1080
                                                            val currentSh = dm?.heightPixels ?: 2400

                                                            ensureCleanScreenBeforeCapture()

                                                            var streamedMatched = false
                                                            if (MediaProjectionService.isStreaming && activeTemplates.isNotEmpty() && ocrQueries.isEmpty()) {
                                                                val sW = MediaProjectionService.currentFrameWidth.takeIf { it > 0 } ?: currentSw
                                                                val sH = MediaProjectionService.currentFrameHeight.takeIf { it > 0 } ?: currentSh
                                                                val sPixels = PixelBufferPool.obtain(sW * sH)
                                                                try {
                                                                    if (MediaProjectionService.copyLatestFrame(sPixels, sW, sH)) {
                                                                        val streamCandidates = mutableListOf<MatchCandidate>()
                                                                        if (action.isNeuralEngine) {
                                                                            for (tpl in activeTemplates) {
                                                                                if (!tpl.second.isRecycled) {
                                                                                    val nm = com.example.autotap.infrastructure.vision.NeuralVisualMatcher.findMatches(
                                                                                        sPixels, sW, sH, tpl.second, baseSimilarity, action.copy(templatePath = tpl.first)
                                                                                    )
                                                                                    streamCandidates.addAll(nm)
                                                                                }
                                                                            }
                                                                        } else {
                                                                            streamCandidates.addAll(
                                                                                TemplateMatchingEngine.findMultiTemplatesFastCascade(
                                                                                    sPixels = sPixels,
                                                                                    sw = sW,
                                                                                    sh = sH,
                                                                                    templates = activeTemplates,
                                                                                    minSimilarityPercent = baseSimilarity,
                                                                                    actionOverride = action,
                                                                                    isCancelled = { !isActive }
                                                                                )
                                                                            )
                                                                        }
                                                                        if (streamCandidates.isNotEmpty()) {
                                                                            streamCandidates.sortByDescending { it.score }
                                                                            bestMatchCand = streamCandidates.first()
                                                                            isMatched = true
                                                                            streamedMatched = true
                                                                        }
                                                                    }
                                                                } finally {
                                                                    PixelBufferPool.release(sPixels)
                                                                }
                                                            }
                                                            if (streamedMatched) break
                                                            if (MediaProjectionService.isStreaming) {
                                                                delay(30L)
                                                                continue
                                                            }

                                                            val loopStartMs = System.currentTimeMillis()
                                                            val screenshot = service?.captureScreenshotAsync(1500L)
                                                            if (screenshot != null) {
                                                                try {
                                                                    val candidateList = mutableListOf<MatchCandidate>()

                                                                    if (activeTemplates.isNotEmpty()) {
                                                                        val imageMatches = if (action.isNeuralEngine) {
                                                                            val aiMatches = mutableListOf<MatchCandidate>()
                                                                            for (tpl in activeTemplates) {
                                                                                if (!tpl.second.isRecycled) {
                                                                                    aiMatches.addAll(
                                                                                        com.example.autotap.infrastructure.vision.NeuralVisualMatcher.findMatches(
                                                                                            screenshot, tpl.second, baseSimilarity, action.copy(templatePath = tpl.first)
                                                                                        )
                                                                                    )
                                                                                }
                                                                            }
                                                                            aiMatches.ifEmpty {
                                                                                visionGateway.findMultiTemplateMatches(
                                                                                    screenshot = screenshot,
                                                                                    templates = activeTemplates,
                                                                                    minSimilarityPercent = baseSimilarity,
                                                                                    actionOverride = action,
                                                                                    isCancelled = { !isActive }
                                                                                )
                                                                            }
                                                                        } else {
                                                                            visionGateway.findMultiTemplateMatches(
                                                                                screenshot = screenshot,
                                                                                templates = activeTemplates,
                                                                                minSimilarityPercent = baseSimilarity,
                                                                                actionOverride = action,
                                                                                isCancelled = { !isActive }
                                                                            )
                                                                        }
                                                                        candidateList.addAll(imageMatches)
                                                                    }

                                                                    if (ocrQueries.isNotEmpty()) {
                                                                        val roi = if (action.roiLeft != null && action.roiRight != null) {
                                                                            Rect(action.roiLeft, action.roiTop ?: 0, action.roiRight, action.roiBottom ?: 0)
                                                                        } else null
                                                                        for (q in ocrQueries) {
                                                                            val ocrResults = com.example.autotap.infrastructure.ocr.OcrEngine.findTextOnScreen(screenshot, q, 1000L, roi, variableContext)
                                                                            for (res in ocrResults) {
                                                                                candidateList.add(
                                                                                    MatchCandidate(
                                                                                        clickX = res.clickX,
                                                                                        clickY = res.clickY,
                                                                                        rectLeft = res.rectLeft,
                                                                                        rectTop = res.rectTop,
                                                                                        rectRight = res.rectRight,
                                                                                        rectBottom = res.rectBottom,
                                                                                        score = 0.95f,
                                                                                        templatePath = "ocr:$q"
                                                                                    )
                                                                                )
                                                                            }
                                                                        }
                                                                    }

                                                                    if (candidateList.isNotEmpty()) {
                                                                        candidateList.sortByDescending { it.score }
                                                                        bestMatchCand = candidateList.first()
                                                                        isMatched = true
                                                                        break
                                                                    }
                                                                } finally {
                                                                    screenshot.recycle()
                                                                }
                                                            }
                                                            val loopElapsedMs = System.currentTimeMillis() - loopStartMs
                                                            val remainingDelay = (checkInterval - loopElapsedMs).coerceAtLeast(10L)
                                                            if (!isMatched) delay(remainingDelay)
                                                        }

                                                        if (!isMatched) {
                                                            com.example.autotap.core.logger.AppLogger.log(
                                                                accessibilityServiceProvider() ?: return@launch,
                                                                "CV_EXECUTION",
                                                                "Шаг #${action.id}: ТАЙМАУТ ПОИСКА. Ни одна из целей не достигла порога $baseSimilarity% за выделенное время."
                                                            )
                                                        }

                                                        if (isMatched && bestMatchCand != null) {
                                                            com.example.autotap.core.logger.AppLogger.log(
                                                                accessibilityServiceProvider() ?: return@launch,
                                                                "CV_EXECUTION",
                                                                String.format(Locale.US, "Шаг #${action.id}: УСПЕХ [MATCH_SUCCESS]. Найдена цель '%s' со сходимостью %.1f%%", bestMatchCand.templateName, bestMatchCand.score * 100f)
                                                            )

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

                                    if (bestMatchCand.score >= 0.80f && bestMatchCand.templatePath.isNotBlank()) {
                                        val history = com.example.autotap.infrastructure.vision.TemplateMatchingEngine.historicalSearchAnchors.getOrPut(bestMatchCand.templatePath) { java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap()) }
                                        if (history.none { kotlin.math.abs(it.first - bestMatchCand.clickX) < 20 && kotlin.math.abs(it.second - bestMatchCand.clickY) < 20 }) {
                                            if (history.size >= 8) history.remove(history.first())
                                            history.add(Pair(bestMatchCand.clickX, bestMatchCand.clickY))
                                        }
                                    }

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
                                                val vib = androidx.core.content.ContextCompat.getSystemService(srv, android.os.Vibrator::class.java)
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
                                    val templateBmp = templateRepository.getTemplate(bestMatchCand.templatePath)
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        val ctx = accessibilityServiceProvider()?.applicationContext
                                        if (ctx != null) {
                                            if (isStepByStepDebug) {
                                                val diag = TemplateMatchingEngine.lastDiagnostics
                                                val rL = action.roiLeft
                                                val rT = action.roiTop
                                                val rR = action.roiRight
                                                val rB = action.roiBottom
                                                val curRoi = if (rL != null && rT != null && rR != null && rB != null) {
                                                    android.graphics.Rect(rL, rT, rR, rB)
                                                } else null
                                                com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.showSearchDebugVisualization(
                                                    context = ctx,
                                                    overlayWindowManager = getOverlayWindowManager(ctx),
                                                    roi = curRoi,
                                                    bestCandidate = bestMatchCand,
                                                    rejectedCandidates = diag?.rejectedCandidates ?: emptyList(),
                                                    thresholdPct = baseSimilarity,
                                                    methodName = diag?.methodName ?: "ГИБРИД + УМНЫЙ ЦВЕТ",
                                                    elapsedMs = diag?.elapsedMs ?: 0L,
                                                    durationMs = 1800L
                                                )
                                            } else {
                                                val tName = bestMatchCand.templateName.removePrefix("mask_").ifBlank { "Шаблон #${action.id}" }
                                                com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.showConfidenceHighlight(
                                                    context = ctx,
                                                    overlayWindowManager = getOverlayWindowManager(ctx),
                                                    rect = matchRect,
                                                    detailText = tName,
                                                    scorePercent = matchScorePct,
                                                    durationMs = 1400L,
                                                    templateBitmap = templateBmp
                                                )
                                            }
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
                                                    com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.showTrainingHighlight(ctx, getOverlayWindowManager(ctx), rect)
                                                    android.widget.Toast.makeText(ctx, "ОБУЧЕНИЕ ИИ: Цель найдена. Нажмите NEXT для клика.", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                            // Ждем подтверждения пользователя (вызов stepDebugNext снимет паузу)
                                            while (isPaused) { kotlinx.coroutines.delay(100L) }
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                val ctx = accessibilityServiceProvider()?.applicationContext
                                                if (ctx != null) com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.hideTrainingHighlight(getOverlayWindowManager(ctx))
                                            }
                                        }

                                        withContext(Dispatchers.IO) {
                                            gestureGateway.performClick(clickX, clickY, action.holdDurationMs.coerceAtLeast(30L))
                                        }
                                    }
                                } else if (!isMatched) {
                                    AppLogger.log(null, "TRIGGER_EXEC", String.format(Locale.US, "ШАГ #%d: таймаут поиска (цель не найдена за %d сек)", action.id, action.aiTimeoutSeconds))
                                    if (isStepByStepDebug) {
                                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            val ctx = accessibilityServiceProvider()?.applicationContext
                                            if (ctx != null) {
                                                val diag = TemplateMatchingEngine.lastDiagnostics
                                                val rL = action.roiLeft
                                                val rT = action.roiTop
                                                val rR = action.roiRight
                                                val rB = action.roiBottom
                                                val curRoi = if (rL != null && rT != null && rR != null && rB != null) {
                                                    android.graphics.Rect(rL, rT, rR, rB)
                                                } else null
                                                com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.showSearchDebugVisualization(
                                                    context = ctx,
                                                    overlayWindowManager = getOverlayWindowManager(ctx),
                                                    roi = curRoi,
                                                    bestCandidate = null,
                                                    rejectedCandidates = diag?.rejectedCandidates ?: emptyList(),
                                                    thresholdPct = baseSimilarity,
                                                    methodName = diag?.methodName ?: "ГИБРИД CV",
                                                    elapsedMs = diag?.elapsedMs ?: 0L,
                                                    durationMs = 2400L
                                                )
                                            }
                                        }
                                    }
                                    // Экспресс-лечение маски при неудачной автоматизации
                                    onAutoHealRequested?.let { healHandler ->
                                        val service = accessibilityServiceProvider()
                                        val frozenBmp = service?.captureScreenshotAsync(1000L)
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
                                        searchTitle = "Поиск текста: '$query'",
                                        searchStartTime = pollStartTime,
                                        searchTimeoutMs = timeoutMs,
                                        isInfiniteSearch = isInfinite,
                                        stepType = ActionType.OCR
                                    )

                                    ensureCleanScreenBeforeCapture()

                                    val service = accessibilityServiceProvider()
                                    val nativeMatches = service?.findTextInActiveWindow(query, roi) ?: emptyList()
                                    if (nativeMatches.isNotEmpty() && !query.startsWith("regex:") && !(query.contains("{") && query.contains("}"))) {
                                        val targetIdx = action.targetOccurrenceIndex.coerceIn(0, nativeMatches.size - 1)
                                        val selectedMatch = nativeMatches[targetIdx]
                                        isMatched = true
                                        val offX = if (action.useCustomClickOffset) action.clickOffsetX else 0f
                                        val offY = if (action.useCustomClickOffset) action.clickOffsetY else 0f
                                        clickTargetX = selectedMatch.clickX.toFloat() + offX
                                        clickTargetY = selectedMatch.clickY.toFloat() + offY
                                        matchedOcrRect = Rect(selectedMatch.rectLeft, selectedMatch.rectTop, selectedMatch.rectRight, selectedMatch.rectBottom)
                                        variableContext["last_ocr"] = selectedMatch.matchedText
                                        break
                                    }

                                    ensureCleanScreenBeforeCapture()
                                    val screenshot = service?.captureScreenshotAsync(1500L)
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
                                                    val targetIdx = action.targetOccurrenceIndex.coerceIn(0, results.size - 1)
                                                    val selectedMatch = results[targetIdx]
                                                    isMatched = true
                                                    val offX = if (action.useCustomClickOffset) action.clickOffsetX else 0f
                                                    val offY = if (action.useCustomClickOffset) action.clickOffsetY else 0f
                                                    clickTargetX = selectedMatch.clickX.toFloat() + offX
                                                    clickTargetY = selectedMatch.clickY.toFloat() + offY
                                                    matchedOcrRect = Rect(selectedMatch.rectLeft, selectedMatch.rectTop, selectedMatch.rectRight, selectedMatch.rectBottom)
                                                    variableContext["last_ocr"] = selectedMatch.matchedText
                                                    break
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

                                if (!isMatched && !query.contains("{") && query.isNotBlank()) {
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

                                if (isMatched) {
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
                                                overlayWindowManager = getOverlayWindowManager(ctx),
                                                rect = ocrRect,
                                                scorePercent = 100,
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
                                        searchTitle = "Поиск цвета: ${action.targetColorHex}",
                                        searchStartTime = pollStartTime,
                                        searchTimeoutMs = timeoutMs,
                                        isInfiniteSearch = isInfinite,
                                        stepType = ActionType.COLOR_CHECK
                                    )

                                    val service = accessibilityServiceProvider()
                                    ensureCleanScreenBeforeCapture()
                                    val screenshot = service?.captureScreenshotAsync(1000L)
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
                                val subName = action.targetScriptOrQuery.ifBlank { action.subroutineTag }.trim()
                                if (subName.isNotEmpty()) {
                                    val subScenario = scenarioRepository.loadScenario(subName)
                                    if (subScenario != null && subScenario.actions.isNotEmpty() &&
                                        subroutineManager.pushFrame(currentScenarioName, activeActionsList, currentIndex + 1)) {
                                        AppLogger.log(accessibilityServiceProvider(), "SUBROUTINE", "Переход к подпрограмме '$subName' из '$currentScenarioName' (Глубина: ${subroutineManager.currentDepth})")
                                        currentScenarioName = subName
                                        activeActionsList = subScenario.actions.toMutableList()
                                        currentIndex = 0
                                        isJumpTriggered = true
                                    } else {
                                        AppLogger.log(accessibilityServiceProvider(), "SUBROUTINE", "ОШИБКА ПОДПРОГРАММЫ: Не удалось вызвать '$subName' (Превышена глубина или сценарий не найден)")
                                    }
                                }
                            }
                            ActionType.RETURN -> {
                                val frame = subroutineManager.popFrame()
                                if (frame != null) {
                                    AppLogger.log(accessibilityServiceProvider(), "SUBROUTINE", "Возврат из подпрограммы '$currentScenarioName' в '${frame.scenarioName}' (Глубина: ${subroutineManager.currentDepth})")
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
            } catch (_: CancellationException) {
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
                    if (!isScreenInteractive()) {
                        AppLogger.log(null, "GRAPH_ENGINE", "Экран выключен — остановка выполнения графа")
                        stop()
                        break
                    }
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
                            while (isPaused && isActive) delay(200L)
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
                                ActionType.SWIPE, ActionType.PATH -> {
                                    val actMacro = MacroAction(
                                        id = 1,
                                        type = act.type,
                                        posX = act.x,
                                        posY = act.y,
                                        endX = act.endX,
                                        endY = act.endY,
                                        pathPoints = act.pathPoints,
                                        holdDurationMs = act.holdDurationMs
                                    )
                                    val executor = com.example.autotap.infrastructure.gesture.ActionExecutor(
                                        com.example.autotap.infrastructure.gesture.GesturePathPlayer { accessibilityServiceProvider() },
                                        com.example.autotap.infrastructure.gesture.VirtualStickPathPlayer(object : com.example.autotap.infrastructure.gesture.VirtualStick {
                                            override fun setDirection(dx: Float, dy: Float, strength: Float) {
                                                com.example.autotap.core.logger.AppLogger.log(accessibilityServiceProvider(), "VIRTUAL_STICK", "Симуляция стика: dx=%.2f, dy=%.2f".format(dx, dy))
                                            }
                                            override fun release() {
                                                com.example.autotap.core.logger.AppLogger.log(accessibilityServiceProvider(), "VIRTUAL_STICK", "Стик отпущен")
                                            }
                                        })
                                    )
                                    withContext(Dispatchers.IO) {
                                        executor.execute(actMacro)
                                    }
                                    totalExecutedSteps++
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
                        val pollInterval = if (MediaProjectionService.isStreaming) 33L else (currentNode.pollIntervalMs + telemetry.suggestedCoolingDelayMs).coerceIn(50L, 1000L)
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
                                searchTitle = "[Поиск] Фаза '${currentNode.title}'",
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

                            ensureCleanScreenBeforeCapture()

                            var frameProcessedViaStream = false
                            if (MediaProjectionService.isStreaming) {
                                val streamW = MediaProjectionService.currentFrameWidth.takeIf { it > 0 } ?: currentSw
                                val streamH = MediaProjectionService.currentFrameHeight.takeIf { it > 0 } ?: currentSh
                                val sPixels = PixelBufferPool.obtain(streamW * streamH)
                                try {
                                    if (MediaProjectionService.copyLatestFrame(sPixels, streamW, streamH)) {
                                        frameProcessedViaStream = true
                                        for (trig in currentNode.triggers) {
                                            val isDetected = evaluateSingleGraphTrigger(sPixels, streamW, streamH, null, trig, templates)
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
                            }
                            if (!frameProcessedViaStream) {
                                val screenshot = service?.captureScreenshotAsync(1500L)
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
                            delay(pollInterval.coerceAtLeast(180L))
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
                val rawPaths = (listOf(trig.templatePath) + trig.multiTemplatePaths)
                    .filter { it.isNotBlank() }
                    .distinct()
                if (rawPaths.isEmpty()) return false

                val ocrQueries = rawPaths.filter { it.startsWith("ocr:") }.map { it.removePrefix("ocr:") }
                val imagePaths = rawPaths.filterNot { it.startsWith("ocr:") }

                // 1. Точечное разрешение реальных путей и Bitmap без потери .json метаданных
                val stepTemplates = LinkedHashMap<String, Bitmap>()
                for (p in imagePaths) {
                    val pName = File(p).name
                    val cachedEntry = templates[p]?.let { p to it }
                        ?: templates.entries.firstOrNull { File(it.key).name == pName }?.let { it.key to it.value }

                    val realPath = cachedEntry?.first ?: templateRepository.resolveTemplatePath(p)
                    if (stepTemplates.containsKey(realPath)) continue

                    val b = cachedEntry?.second?.takeIf { !it.isRecycled }
                        ?: templateRepository.getTemplate(realPath)?.takeIf { !it.isRecycled }

                    if (b != null) {
                        stepTemplates[realPath] = b
                    }
                }

                if (stepTemplates.isEmpty() && ocrQueries.isEmpty()) {
                    AppLogger.log(null, "GRAPH_TRIGGER", "Шаблоны не найдены для триггера '${trig.name}'")
                    return false
                }

                val stepTemplatesList = stepTemplates.map { Pair(it.key, it.value) }
                val primaryRealPath = stepTemplatesList.firstOrNull()?.first ?: trig.templatePath
                val isMulti = stepTemplatesList.size > 1

                // 2. Формируем actionOverride: передаем реальный templatePath и собственные якоря триггера
                val evalAction = MacroAction(
                    id = 1,
                    templatePath = primaryRealPath,
                    multiTemplatePaths = stepTemplatesList.map { it.first },
                    similarityPercent = trig.similarityThreshold,
                    isContourMode = trig.isContourMode,
                    isShapeOnlyMode = trig.isShapeOnlyMode,
                    isMultiScaleMode = trig.isMultiScaleMode,
                    colorDeltaEMode = trig.colorDeltaEMode,
                    shapeExpansion = trig.shapeExpansion,
                    paddingOffsetPx = trig.paddingOffsetPx,
                    isCircleShape = trig.isCircleShape,
                    roiLeft = if (isMulti) null else trig.roiLeft,
                    roiTop = if (isMulti) null else trig.roiTop,
                    roiRight = if (isMulti) null else trig.roiRight,
                    roiBottom = if (isMulti) null else trig.roiBottom,
                    primaryAnchorPoints = trig.additionalSearchLocations.toList()
                )

                val candidateList = mutableListOf<com.example.autotap.domain.model.MatchCandidate>()

                // 3. Единый вызов каскадного поиска (Tier 0 -> Tier 1 -> Tier 2 уже внутри движка!)
                if (stepTemplatesList.isNotEmpty()) {
                    val imageMatches = if (sPixels != null) {
                        TemplateMatchingEngine.findMultiTemplatesFastCascade(
                            sPixels = sPixels,
                            sw = sw,
                            sh = sh,
                            templates = stepTemplatesList,
                            minSimilarityPercent = trig.similarityThreshold,
                            actionOverride = evalAction,
                            enableL0Cache = true
                        )
                    } else if (screenshot != null && !screenshot.isRecycled) {
                        visionGateway.findMultiTemplateMatches(
                            screenshot = screenshot,
                            templates = stepTemplatesList,
                            minSimilarityPercent = trig.similarityThreshold,
                            actionOverride = evalAction
                        )
                    } else emptyList()

                    candidateList.addAll(imageMatches)
                }

                // 4. Поддержка OCR-целей в мультипоиске графа
                if (ocrQueries.isNotEmpty() && candidateList.isEmpty()) {
                    val bmp = screenshot ?: (if (sPixels != null) Bitmap.createBitmap(sPixels, sw, sh, Bitmap.Config.ARGB_8888) else null)
                    if (bmp != null) {
                        try {
                            val roi = if (trig.roiLeft != null && trig.roiRight != null) {
                                Rect(trig.roiLeft, trig.roiTop ?: 0, trig.roiRight, trig.roiBottom ?: 0)
                            } else null
                            for (q in ocrQueries) {
                                val ocrRes = OcrEngine.findTextOnScreen(bmp, q, 1000L, roi, variableContext)
                                for (res in ocrRes) {
                                    candidateList.add(
                                        com.example.autotap.domain.model.MatchCandidate(
                                            clickX = res.clickX,
                                            clickY = res.clickY,
                                            rectLeft = res.rectLeft,
                                            rectTop = res.rectTop,
                                            rectRight = res.rectRight,
                                            rectBottom = res.rectBottom,
                                            score = 0.95f,
                                            templatePath = "ocr:$q"
                                        )
                                    )
                                }
                            }
                        } finally {
                            if (bmp != screenshot && !bmp.isRecycled) bmp.recycle()
                        }
                    }
                }

                val validMatches = candidateList
                    .filterNot { cand -> isMatchInsideUiPreview(cand.clickX, cand.clickY) }
                    .sortedByDescending { it.score }

                if (validMatches.isEmpty()) {
                    AppLogger.log(accessibilityServiceProvider(), "GRAPH_CV_EXECUTION", "Триггер '${trig.name}' НЕ НАЙДЕН (масок: ${stepTemplatesList.size}, порог: ${trig.similarityThreshold}%)")
                    return false
                }

                val best = validMatches.first()
                val discoveredPoint = Point2D(best.clickX.toFloat(), best.clickY.toFloat())
                trig.registerDiscoveredLocation(discoveredPoint)
                if (best.templatePath.isNotBlank() && !best.templatePath.startsWith("ocr:")) {
                    TemplateMatchingEngine.registerDiscoveredLocation(best.templatePath, best.clickX, best.clickY)
                }

                AppLogger.log(
                    accessibilityServiceProvider(),
                    "GRAPH_CV_EXECUTION",
                    String.format(
                        Locale.US,
                        "Триггер '%s' УСПЕШНО НАЙДЕН на (%.0f, %.0f) со сходимостью %.1f%%",
                        best.templateName,
                        discoveredPoint.x,
                        discoveredPoint.y,
                        best.score * 100f
                    )
                )

                if (trig.autoClickTarget) {
                    val offX = if (isMulti) best.clickOffsetX else if (trig.useCustomClick) trig.clickOffset.x else best.clickOffsetX
                    val offY = if (isMulti) best.clickOffsetY else if (trig.useCustomClick) trig.clickOffset.y else best.clickOffsetY
                    val clickX = best.clickX + offX
                    val clickY = best.clickY + offY

                    if (isStepByStepDebug) {
                        isPaused = true
                        _executionState.value = ExecutionState.Paused(PauseReason.USER_MANUAL)
                        val rect = Rect(best.rectLeft, best.rectTop, best.rectRight, best.rectBottom)
                        withContext(Dispatchers.Main) {
                            val ctx = accessibilityServiceProvider()?.applicationContext
                            if (ctx != null) {
                                com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.showTrainingHighlight(ctx, getOverlayWindowManager(ctx), rect)
                            }
                        }
                        while (isPaused) { delay(100L) }
                        withContext(Dispatchers.Main) {
                            val ctx = accessibilityServiceProvider()?.applicationContext
                            if (ctx != null) com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.hideTrainingHighlight(getOverlayWindowManager(ctx))
                        }
                    }

                    withContext(Dispatchers.IO) {
                        gestureGateway.performClick(clickX, clickY, 60L)
                    }
                }
                return true
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
                        val targetIdx = trig.targetOccurrenceIndex.coerceIn(0, results.size - 1)
                        val best = results[targetIdx]
                        if (trig.autoClickTarget) {
                            val offX = if (trig.useCustomClick) trig.clickOffset.x else 0f
                            val offY = if (trig.useCustomClick) trig.clickOffset.y else 0f
                            val clickX = best.clickX + offX
                            val clickY = best.clickY + offY
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

        // Вспомогательная функция точечного поиска 1 шаблона без загрузки всей базы с диска
        fun resolveSingle(path: String) {
            if (path.isBlank() || path.startsWith("ocr:")) return

            val fileName = File(path).name
            // 1. Сначала ищем в уже переданной мапе сессии (по полному пути или по имени файла)
            val matched = cachedTemplates[path]?.let { path to it }
                ?: cachedTemplates.entries.firstOrNull { File(it.key).name == fileName }?.let { it.key to it.value }

            val realPath = matched?.first ?: path
            val finalPath = if (File(realPath).exists()) realPath else templateRepository.resolveTemplatePath(path)
            if (result.any { it.first == finalPath }) return

            // 2. Берем живой Bitmap из мапы или точечно через LruCache репозитория (1 файл)
            val bmp = matched?.second?.takeIf { !it.isRecycled }
                ?: templateRepository.getTemplate(finalPath)?.takeIf { !it.isRecycled }

            if (bmp != null) {
                result.add(Pair(finalPath, bmp))
            }
        }

        // 1. Собираем все шаблоны мультипоиска
        if (action.multiTemplatePaths.isNotEmpty()) {
            for (path in action.multiTemplatePaths) {
                resolveSingle(path)
            }
        }

        // 2. Добавляем основной шаблон шага, если его еще нет в списке
        if (action.templatePath.isNotBlank()) {
            resolveSingle(action.templatePath)
        }

        // 3. Фолбэк только на случай, если пути в шаге вообще не заданы
        val hasOnlyOcrTargets = action.multiTemplatePaths.isNotEmpty() && action.multiTemplatePaths.all { it.startsWith("ocr:") }
        if (!hasOnlyOcrTargets && result.isEmpty()) {
            val fallback = cachedTemplates.entries.firstOrNull { !it.value.isRecycled }?.let { it.key to it.value }
                ?: templateRepository.loadAllTemplates().firstOrNull()
            if (fallback != null) {
                result.add(fallback)
            }
        }

        return result
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
        isPaused = false
        debugStepDeferred?.complete(Unit)
    }


    private suspend fun ensureCleanScreenBeforeCapture() {
        var hadHighlights = false
        withContext(Dispatchers.Main) {
            val service = accessibilityServiceProvider()
            val ctx = service?.applicationContext
            if (ctx != null) {
                if (com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.hasActiveHighlights()) {
                    hadHighlights = true
                    com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.hideAllHighlights(
                        getOverlayWindowManager(ctx)
                    )
                }
            }
        }
        if (hadHighlights) {
            delay(350L) // Генерация детерминированной чистой задержки 350мс для прогрузки кадра без оверлея дебага
        }
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
