package com.example.autotap.infrastructure.orchestrator

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.core.math.LinearToGraphMigrator
import com.example.autotap.core.safety.SafetyGovernor
import com.example.autotap.domain.gateway.IGestureGateway
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.DeviceDisplaySpecs
import com.example.autotap.domain.model.ExecutionState
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.MacroScenario
import com.example.autotap.domain.model.MatchCandidate
import com.example.autotap.domain.model.Point2D
import com.example.autotap.domain.model.graph.GraphMacroScenario
import com.example.autotap.domain.repository.IScenarioRepository
import com.example.autotap.domain.repository.ITemplateRepository
import com.example.autotap.infrastructure.accessibility.AutoTapAccessibilityService
import com.example.autotap.infrastructure.engine.MacroExecutionEngine
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.capture.CalibrationOverlay
import com.example.autotap.infrastructure.overlay.capture.CaptureFrameOverlay
import com.example.autotap.infrastructure.overlay.capture.EyedropperOverlay
import com.example.autotap.infrastructure.overlay.capture.RoiSelectorOverlay
import com.example.autotap.infrastructure.overlay.debug.DebuggerToolbarOverlay
import com.example.autotap.infrastructure.overlay.dialog.EditActionDialog
import com.example.autotap.infrastructure.overlay.dialog.GlobalSettingsDialog
import com.example.autotap.infrastructure.overlay.dialog.LogViewerDialog
import com.example.autotap.infrastructure.overlay.dialog.ScriptsDialog
import com.example.autotap.infrastructure.overlay.graph.GraphEditorOverlay
import com.example.autotap.infrastructure.overlay.graph.GraphOverlayView
import com.example.autotap.infrastructure.overlay.lock.ScreenLockOverlay
import com.example.autotap.infrastructure.overlay.model.OverlayVisibilityMode
import com.example.autotap.infrastructure.overlay.panel.ControlPanelListener
import com.example.autotap.infrastructure.overlay.record.GestureRecorderOverlay
import com.example.autotap.infrastructure.overlay.ring.TargetQuickRingOverlay
import com.example.autotap.infrastructure.overlay.target.TargetOverlayManager
import com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay
import com.example.autotap.infrastructure.storage.AppProfileDetector
import com.example.autotap.infrastructure.storage.PackageBackupManager
import com.example.autotap.infrastructure.storage.ScenarioRepositoryImpl
import com.example.autotap.infrastructure.storage.TemplateRepositoryImpl
import com.example.autotap.infrastructure.vision.PixelBufferPool
import com.example.autotap.infrastructure.vision.TemplateMatchingEngine
import com.example.autotap.infrastructure.vision.VisionGatewayImpl
import com.example.autotap.infrastructure.visualizer.LaserScanVisualizer
import com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer
import java.io.File
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.hypot
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutoTapOrchestrator private constructor(context: Context) : ControlPanelListener {

    private val appContext: Context = context.applicationContext

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: AutoTapOrchestrator? = null

        fun getInstance(context: Context): AutoTapOrchestrator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AutoTapOrchestrator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val overlayWindowManager by lazy { OverlayWindowManager(appContext) }
    val scenarioRepository: IScenarioRepository by lazy { ScenarioRepositoryImpl(appContext) }
    val templateRepository: ITemplateRepository by lazy { TemplateRepositoryImpl(appContext) }
    val backupManager by lazy { PackageBackupManager(appContext) }
    val profileDetector by lazy { AppProfileDetector(appContext) }
    val safetyGovernor by lazy { SafetyGovernor(appContext) }
    val visionGateway by lazy { VisionGatewayImpl() }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val appScope = CoroutineScope(Dispatchers.Main)
    private var persistJob: Job? = null
    private val prefs by lazy { appContext.getSharedPreferences("autotap_prefs", Context.MODE_PRIVATE) }

    val quickRingOverlay by lazy { TargetQuickRingOverlay(appContext, overlayWindowManager) }
    val graphOverlayView by lazy { GraphOverlayView(appContext) }
    val targetManager by lazy { TargetOverlayManager(appContext, overlayWindowManager, quickRingOverlay, graphOverlayView) }
    val controlPanelOverlay by lazy { com.example.autotap.infrastructure.overlay.panel.ControlPanelOverlay(appContext, overlayWindowManager, this) }

    val screenLockOverlay by lazy { ScreenLockOverlay(appContext, overlayWindowManager) }
    val runningBadgeOverlay by lazy { com.example.autotap.infrastructure.overlay.badge.RunningBadgeOverlay(appContext, overlayWindowManager, onLockClick = { screenLockOverlay.show() }, onStopClick = { macroEngine.stop() }) }
    val debuggerToolbarOverlay by lazy { DebuggerToolbarOverlay(appContext, overlayWindowManager) }

    val graphEditorOverlay by lazy {
        GraphEditorOverlay(appContext, overlayWindowManager, scenarioRepository).apply {
            onStartGraphRequested = { graphScenario -> startGraphScenarioExecution(graphScenario) }
        }
    }

    private var isRestoringOrLoadingScenario = false
    private var lastObservedActionIds = emptySet<Int>()
    private val sessionCreatedActionIds = mutableSetOf<Int>()

    var globalClickDurationMs: Long = 30L
    var globalClickPauseMs: Long = 60L
    var globalSwipeDurationMs: Long = 300L
    var globalPathDurationMs: Long = 5000L

    private val fallbackGestureGateway = object : IGestureGateway {
        override fun performClick(x: Float, y: Float, durationMs: Long) = AutoTapAccessibilityService.instance?.gestureDispatcher?.performClick(x, y, durationMs) ?: false
        override fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long) = AutoTapAccessibilityService.instance?.gestureDispatcher?.performSwipe(startX, startY, endX, endY, durationMs) ?: false
        override fun performPath(points: List<Point2D>, durationMs: Long) = AutoTapAccessibilityService.instance?.gestureDispatcher?.performPath(points, durationMs) ?: false
        override fun performPinch(centerX: Float, centerY: Float, startDistance: Float, endDistance: Float, durationMs: Long) = AutoTapAccessibilityService.instance?.gestureDispatcher?.performPinch(centerX, centerY, startDistance, endDistance, durationMs) ?: false
        override fun vibrateFeedback(durationMs: Long) { AutoTapAccessibilityService.instance?.gestureDispatcher?.vibrateFeedback(durationMs) }
    }

    val macroEngine by lazy {
        MacroExecutionEngine(fallbackGestureGateway, visionGateway, safetyGovernor, scenarioRepository, templateRepository) { AutoTapAccessibilityService.instance }
    }

    val screenOffMonitor by lazy {
        ScreenOffStopMonitor(appContext) {
            if (macroEngine.executionState.value is ExecutionState.Running) {
                AppLogger.log(appContext, "ORCHESTRATOR", "Экран выключен (ACTION_SCREEN_OFF) — отмена воспроизведения макроса")
                macroEngine.stop()
            }
        }
    }

    private var activeEditDialog: EditActionDialog? = null

    init {
        AppLogger.init(appContext)
        com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer.init(appContext)
        globalClickDurationMs = prefs.getLong("PREF_GLOBAL_CLICK_DURATION", 30L)
        globalClickPauseMs = prefs.getLong("PREF_GLOBAL_CLICK_PAUSE", 60L)
        globalSwipeDurationMs = prefs.getLong("PREF_GLOBAL_SWIPE_DURATION", 300L)
        globalPathDurationMs = prefs.getLong("PREF_GLOBAL_PATH_DURATION", 5000L)

        targetManager.onActionEditRequested = { showEditDialog(it) }
        targetManager.onActionScriptsRequested = { onScriptsClicked() }
        targetManager.onActionTestRequested = { testSingleAction(it) }
        targetManager.onActionAddTemplateRequested = { act -> startAddTemplateForStep(act) }
        targetManager.onActionCalibrateRequested = { act ->
            if (act.type == ActionType.COLOR_CHECK) {
                startEyedropper { hex -> targetManager.updateAction(act.copy(targetColorHex = hex)) }
            } else {
                startCalibration(act)
            }
        }

        targetManager.onActionsChanged = { list ->
            val currentIds = list.map { it.id }.toSet()
            if (isRestoringOrLoadingScenario) {
                sessionCreatedActionIds.clear()
            } else {
                val addedIds = currentIds - lastObservedActionIds
                sessionCreatedActionIds.addAll(addedIds)
                sessionCreatedActionIds.retainAll(currentIds)
                if (list.isEmpty()) {
                    sessionCreatedActionIds.clear()
                }
            }
            lastObservedActionIds = currentIds
            persistActiveSession()
        }
        targetManager.onVisibilityModeChanged = { mode ->
            controlPanelOverlay.updateVisibilityMode(mode)
        }
        appScope.launch { macroEngine.executionState.collect { handleExecutionState(it) } }
        AutoTapAccessibilityService.instance?.gestureDispatcher?.onGesturePassthroughToggle = { enabled, rect -> screenLockOverlay.setPassthroughEnabled(enabled, rect) }
    }

    fun showOverlays() {
        AutoTapAccessibilityService.instance?.gestureDispatcher?.onGesturePassthroughToggle = { enabled, rect -> screenLockOverlay.setPassthroughEnabled(enabled, rect) }
        controlPanelOverlay.show()
        targetManager.attachOverlaysIfNeeded()
        targetManager.setOverlaysVisible(true)

        // [V80.0] Гарантированное восстановление последней активной сессии и шагов, которые использовались
        isRestoringOrLoadingScenario = true
        if (targetManager.getActions().isEmpty()) {
            val prefs = appContext.getSharedPreferences("autotap_prefs", Context.MODE_PRIVATE)
            val lastScript = prefs.getString("last_active_scenario_name", null)
            if (!lastScript.isNullOrBlank()) {
                val lastScenario = scenarioRepository.loadScenario(lastScript)
                if (lastScenario != null && lastScenario.actions.isNotEmpty()) {
                    targetManager.loadActions(lastScenario.actions)
                }
            }
        }
        isRestoringOrLoadingScenario = false

        val currentActions = targetManager.getActions()
        if (currentActions.isEmpty()) {
            val prefs = appContext.getSharedPreferences("autotap_prefs", Context.MODE_PRIVATE)
            val alreadyShown = prefs.getBoolean("onboarding_tutorial_shown", false)
            if (!alreadyShown) {
                prefs.edit().putBoolean("onboarding_tutorial_shown", true).apply()
                mainHandler.postDelayed({
                    if (targetManager.getActions().isEmpty()) {
                        InteractiveTutorialOverlay(
                            context = appContext,
                            overlayWindowManager = overlayWindowManager,
                            mode = InteractiveTutorialOverlay.TutorialMode.CONTROL_PANEL,
                            hostViewProvider = { controlPanelOverlay }
                        ).show()
                    }
                }, 400L)
            }
        }
    }

    fun hideOverlays() {
        persistActiveSession(immediate = true)
        activeEditDialog?.dismiss()
        activeEditDialog = null
        controlPanelOverlay.hide()
        screenLockOverlay.dismiss()
        runningBadgeOverlay.dismiss()
        debuggerToolbarOverlay.dismiss()
        quickRingOverlay.dismiss()
        targetManager.setOverlaysVisible(false)
        macroEngine.stop()
    }

    // [V80.0] Асинхронная дебаунс-фиксация активной сессии на диск (отсекает спам при перетаскивании мишеней)
    fun persistActiveSession(immediate: Boolean = false) {
        persistJob?.cancel()
        val actions = targetManager.getActions()
        val dm = appContext.resources.displayMetrics
        val specs = DeviceDisplaySpecs(dm.widthPixels, dm.heightPixels, dm.densityDpi, dm.density, dm.widthPixels > dm.heightPixels)
        val scenario = MacroScenario("ActiveSession", 1, specs, actions, globalClickDurationMs, globalSwipeDurationMs)

        persistJob = appScope.launch(Dispatchers.IO) {
            if (!immediate) {
                delay(com.example.autotap.core.config.AppConfig.Timing.DEBOUNCE_PERSIST_DELAY_MS)
            }
            scenarioRepository.saveScenario(scenario)

            // [V152.0] Граф активной сессии обновляется точечно без сброса связей при числе шаблонов/шагов >= 2
            val totalTemplates = actions.sumOf { act ->
                (listOf(act.templatePath) + act.multiTemplatePaths).filter { it.isNotBlank() && !it.startsWith("ocr:") }.distinct().size
            }
            val isGraphEligible = totalTemplates >= 2 || actions.size >= 2
            if (isGraphEligible) {
                val currentScenarioName = appContext.getSharedPreferences("autotap_prefs", Context.MODE_PRIVATE)
                    .getString("last_active_scenario_name", "ActiveSession") ?: "ActiveSession"
                val existingGraph = scenarioRepository.loadGraphScenario(currentScenarioName)
                val updatedGraph = if (existingGraph != null) {
                    var updatedNodeGraph: com.example.autotap.domain.model.graph.GraphMacroScenario = existingGraph
                    for (act in actions) {
                        updatedNodeGraph = LinearToGraphMigrator.updateNodeForAction(updatedNodeGraph, act)
                    }
                    updatedNodeGraph
                } else {
                    LinearToGraphMigrator.linearToGraph(scenario)
                }
                scenarioRepository.saveGraphScenario(updatedGraph)
            }

            appContext.getSharedPreferences("autotap_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_active_scenario_name", if (actions.isNotEmpty()) "ActiveSession" else "")
                .apply()
        }
    }

    override fun onGraphClicked() {
        val actions = targetManager.getActions()
        val dm = appContext.resources.displayMetrics
        val specs = DeviceDisplaySpecs(dm.widthPixels, dm.heightPixels, dm.densityDpi, dm.density, dm.widthPixels > dm.heightPixels)
        val scenario = MacroScenario("ActiveSession", 1, specs, actions, globalClickDurationMs, globalSwipeDurationMs)
        val existingGraph = scenarioRepository.loadGraphScenario("ActiveSession")
        val updatedGraph = if (existingGraph != null) {
            var g: com.example.autotap.domain.model.graph.GraphMacroScenario = existingGraph
            for (act in actions) {
                val updatedNode = LinearToGraphMigrator.updateNodeForAction(g, act)
                if (updatedNode != null) g = updatedNode
            }
            g
        } else {
            LinearToGraphMigrator.linearToGraph(scenario)
        }
        scenarioRepository.saveGraphScenario(updatedGraph)
        openGraphEditor(updatedGraph.name)
    }

    fun openGraphEditor(name: String) {
        controlPanelOverlay.hide()
        targetManager.setOverlaysVisible(false)
        graphEditorOverlay.show(name)
    }

    private fun loadScenarioTemplates(actions: List<MacroAction>): Map<String, Bitmap> {
        val templatesMap = LinkedHashMap<String, Bitmap>()
        val usedPaths = actions
            .flatMap { listOf(it.templatePath) + it.multiTemplatePaths }
            .filter { it.isNotBlank() && !it.startsWith("ocr:") }
            .distinct()

        for (path in usedPaths) {
            val realPath = templateRepository.resolveTemplatePath(path)
            templateRepository.getTemplate(realPath)?.let { bmp ->
                templatesMap[realPath] = bmp
                if (path != realPath) templatesMap[path] = bmp
                com.example.autotap.infrastructure.vision.TemplateMatchingEngine.extractFeatures(bmp, realPath, realPath)
            }
        }
        return templatesMap
    }

    fun startGraphScenarioExecution(graph: GraphMacroScenario) {
        templateRepository.clearCache()
        val allGraphTriggers = graph.nodes.values.flatMap { node ->
            node.triggers.map { t -> MacroAction(id = 1, templatePath = t.templatePath, multiTemplatePaths = t.multiTemplatePaths) }
        }
        val templatesMap = loadScenarioTemplates(allGraphTriggers)
        macroEngine.startGraph(graph, templatesMap, isDebug = false)
    }

    private fun checkAndPromptGraphGeneration(action: MacroAction, onCompleted: () -> Unit) {
        val prefs = appContext.getSharedPreferences("autotap_prefs", Context.MODE_PRIVATE)
        val shouldPrompt = prefs.getBoolean("PREF_PROMPT_AUTO_GRAPH", true)
        if (!shouldPrompt) {
            onCompleted()
            return
        }
        val allActions = targetManager.getActions()
        val sessionActions = allActions.filter { it.id in sessionCreatedActionIds }
        val hasMultiSearchMoreThan3 = sessionActions.any { it.multiTemplatePaths.size > 3 }
        val isComplex = sessionActions.size > 3 || hasMultiSearchMoreThan3
        if (!isComplex) {
            onCompleted()
            return
        }
        val sessionHash = allActions.map { "${it.id}_${it.type}_${it.posX.toInt()}_${it.posY.toInt()}_${it.templatePath}_${it.multiTemplatePaths.size}" }.hashCode()
        val lastPromptedHash = prefs.getInt("LAST_PROMPTED_GRAPH_SESSION_HASH", 0)
        if (lastPromptedHash == sessionHash) {
            onCompleted()
            return
        }

        val currentScenarioName = prefs.getString("last_active_scenario_name", "ActiveSession") ?: "ActiveSession"
        val hasGraph = scenarioRepository.hasGraphScenario(currentScenarioName)
        val dm = appContext.resources.displayMetrics
        val dp = { v: Int -> (v * dm.density).toInt() }
        val dpF = { v: Float -> v * dm.density }
        if (!hasGraph) {
            targetManager.setOverlaysVisible(false)
            controlPanelOverlay.hide()
            val msg = "Обнаружено ${allActions.size} действий/шаблонов в сессии (>3). Сгенерировать визуальный ГРАФ логики? Он свяжет все шаги, условия и переходы в наглядную схему принятия решений."
            val root = android.widget.FrameLayout(appContext).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#99000000"))
            }
            val card = android.widget.LinearLayout(appContext).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#161B22"))
                    cornerRadius = dpF(12f)
                    setStroke(dp(1), android.graphics.Color.parseColor("#38BDF8"))
                }
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
            val dismissPrompt = {
                prefs.edit().putInt("LAST_PROMPTED_GRAPH_SESSION_HASH", sessionHash).apply()
                overlayWindowManager.removeViewSafe(root)
            }
            root.setOnClickListener {
                dismissPrompt()
                controlPanelOverlay.show()
                targetManager.setOverlaysVisible(true)
                onCompleted()
            }
            card.setOnClickListener { /* Предотвращаем закрытие при тапе по самой карточке */ }

            val tv = android.widget.TextView(appContext).apply {
                text = msg; textSize = 10f; setTextColor(android.graphics.Color.WHITE); setPadding(0, 0, 0, dp(12))
            }
            val cbDontAsk = android.widget.CheckBox(appContext).apply {
                text = "Больше не предлагать (можно включить в Настройках)"; textSize = 8f; setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            }
            val btnRow = android.widget.LinearLayout(appContext).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
            val btnYes = android.widget.Button(appContext).apply {
                text = "СГЕНЕРИРОВАТЬ ГРАФ"; setTextColor(android.graphics.Color.parseColor("#10B981")); background = null
                setOnClickListener {
                    if (cbDontAsk.isChecked) prefs.edit().putBoolean("PREF_PROMPT_AUTO_GRAPH", false).apply()
                    dismissPrompt()
                    val specs = DeviceDisplaySpecs(dm.widthPixels, dm.heightPixels, dm.densityDpi, dm.density, dm.widthPixels > dm.heightPixels)
                    val linearScenario = MacroScenario(currentScenarioName, 1, specs, allActions, globalClickDurationMs, globalSwipeDurationMs)
                    val graph = com.example.autotap.core.math.LinearToGraphMigrator.linearToGraph(linearScenario)
                    scenarioRepository.saveGraphScenario(graph)
                    openGraphEditor(currentScenarioName)
                    onCompleted()
                }
            }
            val btnNo = android.widget.Button(appContext).apply {
                text = "ОСТАВИТЬ СПИСКОМ"; setTextColor(android.graphics.Color.parseColor("#F43F5E")); background = null
                setOnClickListener {
                    if (cbDontAsk.isChecked) prefs.edit().putBoolean("PREF_PROMPT_AUTO_GRAPH", false).apply()
                    dismissPrompt()
                    controlPanelOverlay.show()
                    targetManager.setOverlaysVisible(true)
                    onCompleted()
                }
            }
            btnRow.addView(btnNo, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            btnRow.addView(btnYes, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(tv); card.addView(cbDontAsk); card.addView(btnRow)
            root.addView(card, android.widget.FrameLayout.LayoutParams(-1, -2).apply {
                gravity = android.view.Gravity.CENTER
                setMargins(dp(16), dp(16), dp(16), dp(16))
            })
            val lp = overlayWindowManager.createLayoutParams(width = -1, height = -1, flags = android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            lp.dimAmount = 0.6f
            overlayWindowManager.addViewSafe(root, lp)
        } else {
            // [V152.0] Если граф сценария уже существует и сессия удовлетворяет условиям,
            // актуализируем узел активной сессии без перезаписи топологии связей
            val specs = DeviceDisplaySpecs(dm.widthPixels, dm.heightPixels, dm.densityDpi, dm.density, dm.widthPixels > dm.heightPixels)
            val linearScenario = MacroScenario(currentScenarioName, 1, specs, allActions, globalClickDurationMs, globalSwipeDurationMs)
            val existingGraph = scenarioRepository.loadGraphScenario(currentScenarioName)
            val updatedGraph = if (existingGraph != null) {
                var updatedNodeGraph: com.example.autotap.domain.model.graph.GraphMacroScenario = existingGraph
                for (act in allActions) {
                    updatedNodeGraph = LinearToGraphMigrator.updateNodeForAction(updatedNodeGraph, act)
                }
                updatedNodeGraph
            } else {
                LinearToGraphMigrator.linearToGraph(linearScenario)
            }
            scenarioRepository.saveGraphScenario(updatedGraph)
            prefs.edit().putInt("LAST_PROMPTED_GRAPH_SESSION_HASH", sessionHash).apply()
            onCompleted()
        }
    }

    fun startCaptureForStep(actionion: MacroAction) {
        startTemplateCalibration(actionion)
    }

    fun startTemplateCalibration(actionion: MacroAction) {
        targetManager.setOverlaysVisible(false)
        controlPanelOverlay.hide()

        appScope.launch(Dispatchers.Default) {
            delay(150L) // Гарантия сокрытия оверлеев
            val service = AutoTapAccessibilityService.instance
            val screenshot = service?.captureScreenshotAsync(2500L) ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Не удалось получить снимок экрана", Toast.LENGTH_SHORT).show()
                    controlPanelOverlay.show()
                    targetManager.setOverlaysVisible(true)
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                lateinit var captureOverlay: CaptureFrameOverlay
                captureOverlay = CaptureFrameOverlay(
                    context = appContext,
                    overlayWindowManager = overlayWindowManager,
                    overrideScreenshot = screenshot,
                    onCaptureTriggered = { cropX, cropY, cropW, cropH, _, roi ->
                        val safeW = cropW.coerceIn(8, screenshot.width)
                        val safeH = cropH.coerceIn(8, screenshot.height)
                        val safeX = cropX.coerceIn(0, screenshot.width - safeW)
                        val safeY = cropY.coerceIn(0, screenshot.height - safeH)
                        val cropped = Bitmap.createBitmap(screenshot, safeX, safeY, safeW, safeH)

                        val effectiveRoi = roi ?: Rect(
                            (safeX - 100).coerceAtLeast(0),
                            (safeY - 100).coerceAtLeast(0),
                            (safeX + safeW + 100).coerceAtMost(screenshot.width),
                            (safeY + safeH + 100).coerceAtMost(screenshot.height)
                        )

                        val cropCenter = Point2D(cropX + cropW / 2f, cropY + cropH / 2f)
                        val currentAnchors = actionion.primaryAnchorPoints.toMutableList()
                        if (currentAnchors.none { abs(it.x - cropCenter.x) < 24f && abs(it.y - cropCenter.y) < 24f }) {
                            if (currentAnchors.size >= 8) currentAnchors.removeAt(0)
                            currentAnchors.add(cropCenter)
                        }
                        
                        val effectiveAction = actionion.copy(
                            primaryAnchorPoints = currentAnchors,
                            roiLeft = effectiveRoi.left,
                            roiTop = effectiveRoi.top,
                            roiRight = effectiveRoi.right,
                            roiBottom = effectiveRoi.bottom
                        )

                        val initialAnchorCandidate = MatchCandidate(safeX + safeW / 2, safeY + safeH / 2, safeX, safeY, safeX + safeW, safeY + safeH, 1.0f)

                        LaserScanVisualizer.showSweepLaser(appContext, overlayWindowManager) {
                            CalibrationOverlay(
                                context = appContext,
                                overlayWindowManager = overlayWindowManager,
                                screenshot = screenshot,
                                rawTemplateBitmap = cropped,
                                initialCandidates = listOf(initialAnchorCandidate),
                                existingAction = effectiveAction,
                                existingTemplatePath = effectiveAction.templatePath.takeIf { it.isNotBlank() },
                                allActions = targetManager.getActions(),
                                targetStepId = actionion.id,
                                anchorCropX = safeX,
                                anchorCropY = safeY,
                                onFinished = { updatedAction, path ->
                                    controlPanelOverlay.show()
                                    targetManager.setOverlaysVisible(true)
                                    TemplateMatchingEngine.registerDiscoveredLocation(path, updatedAction.posX.toInt(), updatedAction.posY.toInt())
                                    targetManager.updateAction(updatedAction)
                                    checkAndPromptGraphGeneration(updatedAction) {}
                                },
                                onCancelled = {
                                    controlPanelOverlay.show()
                                    targetManager.setOverlaysVisible(true)
                                }
                            ).show()
                        }
                    },
                    onCancelled = {
                        controlPanelOverlay.show()
                        targetManager.setOverlaysVisible(true)
                    }
                ).apply { show() }
            }
        }
    }

    fun startAddTemplateForStep(actionion: MacroAction) {
        targetManager.setOverlaysVisible(false)
        controlPanelOverlay.hide()

        appScope.launch(Dispatchers.Default) {
            delay(150L) // Гарантия сокрытия оверлеев
            val service = AutoTapAccessibilityService.instance
            val screenshot = service?.captureScreenshotAsync(2500L) ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Не удалось получить снимок экрана", Toast.LENGTH_SHORT).show()
                    controlPanelOverlay.show()
                    targetManager.setOverlaysVisible(true)
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                lateinit var captureOverlay: CaptureFrameOverlay
                captureOverlay = CaptureFrameOverlay(
                    context = appContext,
                    overlayWindowManager = overlayWindowManager,
                    overrideScreenshot = screenshot,
                    onCaptureTriggered = { cropX, cropY, cropW, cropH, _, roi ->
                        val safeW = cropW.coerceIn(8, screenshot.width)
                        val safeH = cropH.coerceIn(8, screenshot.height)
                        val safeX = cropX.coerceIn(0, screenshot.width - safeW)
                        val safeY = cropY.coerceIn(0, screenshot.height - safeH)
                        val cropped = Bitmap.createBitmap(screenshot, safeX, safeY, safeW, safeH)

                        val effectiveRoi = roi ?: Rect(
                            (safeX - 100).coerceAtLeast(0),
                            (safeY - 100).coerceAtLeast(0),
                            (safeX + safeW + 100).coerceAtMost(screenshot.width),
                            (safeY + safeH + 100).coerceAtMost(screenshot.height)
                        )

                        val cropCenter = Point2D(cropX + cropW / 2f, cropY + cropH / 2f)
                        val currentAnchors = actionion.primaryAnchorPoints.toMutableList()
                        if (currentAnchors.none { abs(it.x - cropCenter.x) < 24f && abs(it.y - cropCenter.y) < 24f }) {
                            if (currentAnchors.size >= 8) currentAnchors.removeAt(0)
                            currentAnchors.add(cropCenter)
                        }
                        
                        val effectiveAction = actionion.copy(
                            primaryAnchorPoints = currentAnchors,
                            roiLeft = effectiveRoi.left,
                            roiTop = effectiveRoi.top,
                            roiRight = effectiveRoi.right,
                            roiBottom = effectiveRoi.bottom
                        )

                        val initialAnchorCandidate = MatchCandidate(safeX + safeW / 2, safeY + safeH / 2, safeX, safeY, safeX + safeW, safeY + safeH, 1.0f)

                        LaserScanVisualizer.showSweepLaser(appContext, overlayWindowManager) {
                            CalibrationOverlay(
                                context = appContext,
                                overlayWindowManager = overlayWindowManager,
                                screenshot = screenshot,
                                rawTemplateBitmap = cropped,
                                initialCandidates = listOf(initialAnchorCandidate),
                                existingAction = effectiveAction,
                                existingTemplatePath = null,
                                allActions = targetManager.getActions(),
                                targetStepId = actionion.id,
                                anchorCropX = safeX,
                                anchorCropY = safeY,
                                onFinished = { updatedAction, path ->
                                    controlPanelOverlay.show()
                                    targetManager.setOverlaysVisible(true)
                                    TemplateMatchingEngine.registerDiscoveredLocation(path, updatedAction.posX.toInt(), updatedAction.posY.toInt())

                                    val existingMulti = actionion.multiTemplatePaths.filter { it.isNotBlank() }.toMutableList()
                                    if (!existingMulti.contains(path)) {
                                        existingMulti.add(path)
                                    }
                                    if (actionion.templatePath.isNotBlank() && !existingMulti.contains(actionion.templatePath)) {
                                        existingMulti.add(0, actionion.templatePath)
                                    }
                                    val primaryPath = actionion.templatePath.ifBlank { path }
                                    val multiAction = updatedAction.copy(
                                        templatePath = primaryPath,
                                        multiTemplatePaths = existingMulti.distinct()
                                    )
                                    targetManager.updateAction(multiAction)
                                    Toast.makeText(appContext, "Шаблон добавлен к мультипоиску шага #${actionion.id} (всего: ${existingMulti.distinct().size})", Toast.LENGTH_SHORT).show()
                                    checkAndPromptGraphGeneration(multiAction) {}
                                },
                                onCancelled = {
                                    controlPanelOverlay.show()
                                    targetManager.setOverlaysVisible(true)
                                }
                            ).show()
                        }
                    },
                    onCancelled = {
                        controlPanelOverlay.show()
                        targetManager.setOverlaysVisible(true)
                    }
                ).apply { show() }
            }
        }
    }


    private fun startRecaptureForStep(action: MacroAction) {
        val primaryPath = if (action.selectedTemplateIndex in action.multiTemplatePaths.indices) {
            action.multiTemplatePaths[action.selectedTemplateIndex]
        } else {
            action.templatePath.ifBlank { action.multiTemplatePaths.firstOrNull { it.isNotBlank() } ?: "" }
        }
        if (primaryPath.isEmpty()) {
            Toast.makeText(appContext, "Шаблон отсутствует, запуск обычного захвата", Toast.LENGTH_SHORT).show()
            startCaptureForStep(action)
            return
        }

        // [V20.1] Надежный поиск файлов шаблона с поддержкой относительных путей
        val maskFile = if (File(primaryPath).isAbsolute && File(primaryPath).exists()) {
            File(primaryPath)
        } else {
            val tDir = File(appContext.filesDir, "templates")
            val direct = File(tDir, primaryPath)
            if (direct.exists()) direct else {
                val fName = File(primaryPath).name
                tDir.walkTopDown().filter { it.isFile && it.name == fName }.firstOrNull() ?: File(primaryPath)
            }
        }

        val parentDir = maskFile.parentFile ?: File(appContext.filesDir, "templates")
        val baseName = maskFile.name.removePrefix("mask_").substringBeforeLast(".")
        val screenJpg = File(parentDir, "screen_$baseName.jpg")
        val screenPng = File(parentDir, "screen_$baseName.png")
        val rawFile = File(parentDir, "raw_$baseName.png")

        val historicalScreenshot: Bitmap? = when {
            screenJpg.exists() -> BitmapFactory.decodeFile(screenJpg.absolutePath)
            screenPng.exists() -> BitmapFactory.decodeFile(screenPng.absolutePath)
            else -> null
        }

        val rawCropBitmap = when {
            rawFile.exists() -> BitmapFactory.decodeFile(rawFile.absolutePath)
            maskFile.exists() -> BitmapFactory.decodeFile(maskFile.absolutePath)
            else -> templateRepository.getTemplate(primaryPath)
        }

        if (rawCropBitmap == null) {
            Toast.makeText(appContext, "Файл шаблона не найден на диске", Toast.LENGTH_SHORT).show()
            startCalibration(action)
            return
        }


        showCalibrationChoiceModal(action, historicalScreenshot, rawCropBitmap)
    }

    private fun showCalibrationChoiceModal(action: MacroAction, historicalScreenshot: Bitmap?, rawCropBitmap: Bitmap) {
        if (historicalScreenshot == null) {
            executeRecaptureWithScreenshot(action, null, rawCropBitmap)
            return
        }

        activeEditDialog?.dismiss()
        activeEditDialog = null
        targetManager.setOverlaysVisible(false)
        controlPanelOverlay.hide()

        val density = appContext.resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        val card = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F8161B22"))
                cornerRadius = 16f * density
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val tvTitle = TextView(appContext).apply {
            text = "ВЫБОР ЭКРАНА КАЛИБРОВКИ"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, dp(8))
        }
        card.addView(tvTitle)

        val btnLive = Button(appContext).apply {
            text = "НА ТЕКУЩЕМ ЭКРАНЕ"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F6FEB"))
                cornerRadius = 6f * density
            }
            minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                overlayWindowManager.removeViewSafe(card)
                executeRecaptureWithScreenshot(action, null, rawCropBitmap)
            }
        }
        card.addView(btnLive, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply { bottomMargin = dp(4) })

        val btnHist = Button(appContext).apply {
            text = "НА СОХРАНЕННОМ СКРИНШОТЕ"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#312E81"))
                cornerRadius = 6f * density
            }
            minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                overlayWindowManager.removeViewSafe(card)
                executeRecaptureWithScreenshot(action, historicalScreenshot, rawCropBitmap)
            }
        }
        card.addView(btnHist, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply { bottomMargin = dp(4) })

        val btnCancel = Button(appContext).apply {
            text = "ОТМЕНА"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#21262D"))
                cornerRadius = 6f * density
            }
            minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                overlayWindowManager.removeViewSafe(card)
                controlPanelOverlay.show()
                targetManager.setOverlaysVisible(true)
            }
        }
        card.addView(btnCancel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)))

        val params = overlayWindowManager.createLayoutParams(
            width = dp(280),
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.CENTER
        )
        overlayWindowManager.addViewSafe(card, params)
    }

    private fun executeRecaptureWithScreenshot(action: MacroAction, forcedScreenshot: Bitmap?, rawCropBitmap: Bitmap) {
        activeEditDialog?.dismiss()
        activeEditDialog = null
        targetManager.setOverlaysVisible(false)
        controlPanelOverlay.hide()

        mainHandler.postDelayed({
            val dm = appContext.resources.displayMetrics
            val effectiveScreenshot = forcedScreenshot ?: run {
                val service = AutoTapAccessibilityService.instance
                service?.captureScreenshotSync(1500L) ?: run {
                    val synth = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(synth)
                    c.drawColor(android.graphics.Color.rgb(18, 20, 28))
                    val drawX = action.posX.coerceIn(0f, (dm.widthPixels - rawCropBitmap.width).toFloat())
                    val drawY = action.posY.coerceIn(0f, (dm.heightPixels - rawCropBitmap.height).toFloat())
                    c.drawBitmap(rawCropBitmap, drawX, drawY, null)
                    synth
                }
            }

            com.example.autotap.infrastructure.overlay.capture.CaptureFrameOverlay(
                context = appContext,
                overlayWindowManager = overlayWindowManager,
                overrideScreenshot = effectiveScreenshot,
                onCaptureTriggered = { cropX, cropY, cropW, cropH, _, roi ->
                    try {
                        val maxX = (effectiveScreenshot.width - 4).coerceAtLeast(0)
                        val maxY = (effectiveScreenshot.height - 4).coerceAtLeast(0)
                        val safeX = cropX.coerceIn(0, maxX)
                        val safeY = cropY.coerceIn(0, maxY)
                        val safeW = cropW.coerceIn(4, (effectiveScreenshot.width - safeX).coerceAtLeast(4))
                        val safeH = cropH.coerceIn(4, (effectiveScreenshot.height - safeY).coerceAtLeast(4))
                        val newRawCrop = android.graphics.Bitmap.createBitmap(effectiveScreenshot, safeX, safeY, safeW, safeH)

                        com.example.autotap.infrastructure.overlay.capture.CalibrationOverlay(
                            context = appContext,
                            overlayWindowManager = overlayWindowManager,
                            screenshot = effectiveScreenshot,
                            rawTemplateBitmap = newRawCrop,
                            initialCandidates = emptyList(),
                            existingAction = action,
                            existingTemplatePath = action.templatePath,
                            targetStepId = action.id,
                            anchorCropX = safeX,
                            anchorCropY = safeY,
                            onFinished = { updatedAction, _ ->
                                controlPanelOverlay.show()
                                targetManager.setOverlaysVisible(true)
                                targetManager.updateAction(updatedAction)
                            },
                            onCancelled = {
                                controlPanelOverlay.show()
                                targetManager.setOverlaysVisible(true)
                            }
                        ).show()
                    } catch (e: Throwable) {
                        AppLogger.logError(appContext, "RECAPTURE_CRASH", e)
                        Toast.makeText(appContext, "Ошибка перекалибровки: ${e.message}", Toast.LENGTH_SHORT).show()
                        controlPanelOverlay.show()
                        targetManager.setOverlaysVisible(true)
                    }
                },
                onCancelled = {
                    controlPanelOverlay.show()
                    targetManager.setOverlaysVisible(true)
                }
            ).show()
        }, 120L)
    }

    private fun startCalibration(action: MacroAction) {
        val service = AutoTapAccessibilityService.instance ?: run {
            Toast.makeText(appContext, "Включите службу кликера в Спец. возможностях", Toast.LENGTH_SHORT).show()
            return
        }

        activeEditDialog?.dismiss()
        activeEditDialog = null
        targetManager.setOverlaysVisible(false)
        controlPanelOverlay.hide()

        appScope.launch(Dispatchers.Main) {
            delay(150L) // Гарантия того, что SurfaceFlinger успел стереть UI оверлея
            executeCleanCalibration(service, action)
        }
    }

    private fun executeCleanCalibration(service: AutoTapAccessibilityService, action: MacroAction) {
        val primaryPath = if (action.selectedTemplateIndex in action.multiTemplatePaths.indices) {
            action.multiTemplatePaths[action.selectedTemplateIndex]
        } else {
            action.templatePath.ifBlank { action.multiTemplatePaths.firstOrNull { it.isNotBlank() } ?: "" }
        }
        var savedScreenBmp: Bitmap? = null
        if (primaryPath.isNotEmpty()) {
            try {
                val maskFile = File(primaryPath)
                var screenFile = File(maskFile.parentFile, "screen_" + maskFile.name.removePrefix("mask_").substringBeforeLast(".") + ".jpg")
                if (!screenFile.exists()) {
                    screenFile = File(maskFile.parentFile, "screen_" + maskFile.name.removePrefix("mask_"))
                }
                if (!screenFile.exists()) {
                    val baseName = maskFile.nameWithoutExtension.removePrefix("mask_")
                    screenFile = File(maskFile.parentFile, "screen_$baseName.png")
                }
                if (screenFile.exists()) {
                    savedScreenBmp = BitmapFactory.decodeFile(screenFile.absolutePath)
                }
            } catch (_: Exception) {}
        }

        val screenshot = savedScreenBmp ?: run {
            val liveScreen = service.captureScreenshotSync(2500L)
            if (liveScreen == null) {
                Toast.makeText(appContext, "Не удалось получить снимок экрана", Toast.LENGTH_SHORT).show()
                controlPanelOverlay.show()
                targetManager.setOverlaysVisible(true)
                return
            }
            liveScreen
        }

        val rawSourceBitmap: Bitmap? = if (primaryPath.isNotEmpty()) {
            val maskFile = File(primaryPath)
            var rawFile = File(maskFile.parentFile, "raw_" + maskFile.name.removePrefix("mask_"))
            if (!rawFile.exists()) {
                val baseName = maskFile.nameWithoutExtension.removePrefix("mask_")
                rawFile = File(maskFile.parentFile, "raw_$baseName.png")
            }
            if (rawFile.exists()) BitmapFactory.decodeFile(rawFile.absolutePath) else templateRepository.getTemplate(primaryPath)
        } else null

        val template = rawSourceBitmap ?: run {
            Toast.makeText(appContext, "Шаблон не найден. Вырежьте область для шага #${action.id}", Toast.LENGTH_SHORT).show()
            startCaptureForStep(action)
            return
        }

        targetManager.setOverlaysVisible(false)
        controlPanelOverlay.hide()

        var foundX = action.posX.toInt()
        var foundY = action.posY.toInt()

        if (primaryPath.isNotEmpty()) {
            val sPixels = PixelBufferPool.obtain(screenshot.width * screenshot.height)
            try {
                screenshot.getPixels(sPixels, 0, screenshot.width, 0, 0, screenshot.width, screenshot.height)
                val matches = TemplateMatchingEngine.findTemplateFastCascade(
                    sPixels, screenshot.width, screenshot.height, template,
                    minSimilarityPercent = action.similarityPercent.coerceAtLeast(60),
                    templatePath = action.templatePath,
                    enableL0Cache = false
                )
                val topMatch = matches.maxByOrNull { it.score }
                if (topMatch != null && topMatch.score >= 0.60f) {
                    foundX = topMatch.clickX
                    foundY = topMatch.clickY

                    val dist = hypot((foundX - action.posX).toDouble(), (foundY - action.posY).toDouble())
                    if (dist > 30.0) {
                        val newAnchors = action.primaryAnchorPoints.toMutableList()
                        val newPt = Point2D(foundX.toFloat(), foundY.toFloat())
                        if (newAnchors.none { abs(it.x - newPt.x) < 24f && abs(it.y - newPt.y) < 24f }) {
                            if (newAnchors.size >= 8) newAnchors.removeAt(0)
                            newAnchors.add(newPt)
                        }
                        targetManager.updateAction(action.copy(primaryAnchorPoints = newAnchors))
                    }
                }
            } catch (_: Exception) {
            } finally {
                PixelBufferPool.release(sPixels)
            }
        }

        val anchorCandidate = MatchCandidate(
            clickX = foundX, clickY = foundY,
            rectLeft = (foundX - template.width / 2f).toInt().coerceAtLeast(0),
            rectTop = (foundY - template.height / 2f).toInt().coerceAtLeast(0),
            rectRight = (foundX + template.width / 2f).toInt().coerceAtMost(screenshot.width),
            rectBottom = (foundY + template.height / 2f).toInt().coerceAtMost(screenshot.height),
            score = 1.0f, templatePath = action.templatePath
        )

        LaserScanVisualizer.showSweepLaser(appContext, overlayWindowManager) {
            CalibrationOverlay(
                context = appContext,
                overlayWindowManager = overlayWindowManager,
                screenshot = screenshot,
                rawTemplateBitmap = template,
                initialCandidates = listOf(anchorCandidate),
                existingAction = action.copy(templatePath = primaryPath),
                existingTemplatePath = primaryPath,
                allActions = targetManager.getActions(),
                targetStepId = action.id,
                anchorCropX = anchorCandidate.rectLeft,
                anchorCropY = anchorCandidate.rectTop,
                onFinished = { updatedAction, path ->
                    controlPanelOverlay.show()
                    targetManager.setOverlaysVisible(true)
                    TemplateMatchingEngine.clearTemplateCache()
                    TemplateMatchingEngine.registerDiscoveredLocation(path, updatedAction.posX.toInt(), updatedAction.posY.toInt())
                    targetManager.updateAction(updatedAction)

                    // Атомарная синхронизация сценария ActiveSession на диск
                    try {
                        val actions = targetManager.getActions()
                        val dm = appContext.resources.displayMetrics
                        val specs = DeviceDisplaySpecs(dm.widthPixels, dm.heightPixels, dm.densityDpi, dm.density, dm.widthPixels > dm.heightPixels)
                        val scenario = MacroScenario("ActiveSession", 1, specs, actions, globalClickDurationMs, globalSwipeDurationMs)
                        scenarioRepository.saveScenario(scenario)
                    } catch (_: Exception) {}
                    checkAndPromptGraphGeneration(updatedAction) {}
                },
                onCancelled = {
                    controlPanelOverlay.show()
                    targetManager.setOverlaysVisible(true)
                }
            ).show()
        }
    }

    fun testSingleAction(action: MacroAction) {
        appScope.launch(Dispatchers.Default) {
            when (action.type) {
                ActionType.CLICK -> {
                    val (gx, gy) = safetyGovernor.computeGaussianOffset(action.randomRadiusPx)
                    val tx = action.posX + gx
                    val ty = action.posY + gy
                    withContext(Dispatchers.IO) {
                        fallbackGestureGateway.performClick(tx, ty, action.holdDurationMs.coerceAtLeast(30L))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "Тест клика: (${tx.toInt()}, ${ty.toInt()})", Toast.LENGTH_SHORT).show()
                    }
                }
                ActionType.LONG_PRESS -> {
                    val (gx, gy) = safetyGovernor.computeGaussianOffset(action.randomRadiusPx)
                    val tx = action.posX + gx
                    val ty = action.posY + gy
                    withContext(Dispatchers.IO) {
                        fallbackGestureGateway.performClick(tx, ty, action.holdDurationMs.coerceAtLeast(400L))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "Тест удержания: (${tx.toInt()}, ${ty.toInt()})", Toast.LENGTH_SHORT).show()
                    }
                }
                ActionType.GLOBAL_BACK -> {
                    AutoTapAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    withContext(Dispatchers.Main) { Toast.makeText(appContext, "Тест: НАЗАД", Toast.LENGTH_SHORT).show() }
                }
                ActionType.GLOBAL_HOME -> {
                    AutoTapAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                    withContext(Dispatchers.Main) { Toast.makeText(appContext, "Тест: ДОМОЙ", Toast.LENGTH_SHORT).show() }
                }
                ActionType.DELAY -> {
                    withContext(Dispatchers.Main) { Toast.makeText(appContext, "Тест: ПАУЗА", Toast.LENGTH_SHORT).show() }
                }
                ActionType.SWIPE -> {
                    val sx = action.posX
                    val sy = action.posY
                    val ex = action.endX ?: (sx + 100f)
                    val ey = action.endY ?: (sy + 100f)
                    withContext(Dispatchers.IO) {
                        fallbackGestureGateway.performSwipe(sx, sy, ex, ey, action.holdDurationMs.coerceAtLeast(100L))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "Тест свайпа: (${sx.toInt()}, ${sy.toInt()}) -> (${ex.toInt()}, ${ey.toInt()})", Toast.LENGTH_SHORT).show()
                    }
                }
                ActionType.PATH -> {
                    if (action.pathPoints.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            fallbackGestureGateway.performPath(action.pathPoints, action.holdDurationMs.coerceAtLeast(50L))
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(appContext, "Тест пути (${action.pathPoints.size} точек)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                ActionType.PINCH -> {
                    val startDist = if (action.pinchStartDistance > 0f) action.pinchStartDistance else 300f
                    val endDist = if (action.pinchEndDistance > 0f) action.pinchEndDistance else 600f
                    withContext(Dispatchers.IO) {
                        fallbackGestureGateway.performPinch(action.posX, action.posY, startDist, endDist, action.holdDurationMs.coerceAtLeast(150L))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "Тест пинча ($startDist -> $endDist px)", Toast.LENGTH_SHORT).show()
                    }
                }
                ActionType.TRIGGER -> {
                    val service = AutoTapAccessibilityService.instance
                    val screenshot = service?.captureScreenshotSync(1500L)
                    if (screenshot != null) {
                        try {
                            val activeTemplates = loadScenarioTemplates(listOf(action)).toList()
                            if (activeTemplates.isNotEmpty()) {
                                val matches = visionGateway.findMultiTemplateMatches(screenshot, activeTemplates, action.similarityPercent, action)
                                withContext(Dispatchers.Main) {
                                    if (matches.isNotEmpty()) {
                                        val best = matches.first()
                                        val scorePct = (best.score * 100).toInt().coerceIn(0, 100)
                                        if (matches.size > 1) {
                                            val bitmapsMap = matches.mapNotNull { cand ->
                                                val b = templateRepository.getTemplate(cand.templatePath)
                                                if (b != null) cand.templatePath to b else null
                                            }.toMap()
                                            TargetHighlightVisualizer.showMultiTemplateHighlights(
                                                context = appContext,
                                                overlayWindowManager = overlayWindowManager,
                                                candidates = matches,
                                                requiredThreshold = action.similarityPercent,
                                                durationMs = 2400L,
                                                templateBitmaps = bitmapsMap
                                            )
                                        } else {
                                            val tplBmp = templateRepository.getTemplate(best.templatePath)
                                            TargetHighlightVisualizer.showConfidenceHighlight(
                                                context = appContext,
                                                overlayWindowManager = overlayWindowManager,
                                                rect = Rect(best.rectLeft, best.rectTop, best.rectRight, best.rectBottom),
                                                scorePercent = scorePct,
                                                durationMs = 2200L,
                                                templateBitmap = tplBmp
                                            )
                                        }
                                        if (action.clickAiTarget) {
                                            val finalClickX = best.clickX + (if (action.useCustomClickOffset) action.clickOffsetX else 0f)
                                            val finalClickY = best.clickY + (if (action.useCustomClickOffset) action.clickOffsetY else 0f)
                                            fallbackGestureGateway.performClick(finalClickX, finalClickY, 50L)
                                        }
                                        Toast.makeText(appContext, "$scorePct% • (${best.clickX.toInt()}, ${best.clickY.toInt()})", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(appContext, "Цель не найдена", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } finally {
                            screenshot.recycle()
                        }
                    }
                }
                ActionType.OCR -> {
                    val service = AutoTapAccessibilityService.instance
                    val screenshot = service?.captureScreenshotSync(1500L)
                    if (screenshot != null) {
                        try {
                            val roi = if (action.roiLeft != null && action.roiTop != null && action.roiRight != null && action.roiBottom != null) {
                                Rect(action.roiLeft, action.roiTop, action.roiRight, action.roiBottom)
                            } else null
                            val results = visionGateway.findText(screenshot, action.targetScriptOrQuery, 1500L, roi)
                            withContext(Dispatchers.Main) {
                                if (results.isNotEmpty()) {
                                    val best = results.first()
                                    val scorePct = (best.confidence * 100).toInt().coerceIn(0, 100)
                                    TargetHighlightVisualizer.showConfidenceHighlight(
                                        context = appContext,
                                        overlayWindowManager = overlayWindowManager,
                                        rect = Rect(best.rectLeft, best.rectTop, best.rectRight, best.rectBottom),
                                        scorePercent = scorePct,
                                        durationMs = 2200L
                                    )
                                    if (action.clickAiTarget) {
                                        fallbackGestureGateway.performClick(best.clickX.toFloat(), best.clickY.toFloat(), 50L)
                                    }
                                    Toast.makeText(appContext, "OCR: '$scorePct%' · '${best.matchedText}'", Toast.LENGTH_SHORT).show()
                                } else {
                                    val query = action.targetScriptOrQuery
                                    val shouldFallback = com.example.autotap.infrastructure.ocr.OcrQueryMetadataManager.registerOcrFailure(query)
                                    if (shouldFallback) {
                                        Toast.makeText(appContext, "Текст '$query' не найден после 3 попыток. Укажите область текста на экране!", Toast.LENGTH_LONG).show()
                                        startRoiSelector(roi) { selectedRoi ->
                                            if (selectedRoi != null) {
                                                com.example.autotap.infrastructure.ocr.OcrQueryMetadataManager.savePersistentRoi(appContext, query, selectedRoi)
                                                val updated = action.copy(roiLeft = selectedRoi.left, roiTop = selectedRoi.top, roiRight = selectedRoi.right, roiBottom = selectedRoi.bottom)
                                                targetManager.updateAction(updated)
                                                Toast.makeText(appContext, "Метаданные ROI и высота для '$query' закреплены за шаблоном!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(appContext, "Текст '${action.targetScriptOrQuery}' не найден", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } finally {
                            screenshot.recycle()
                        }
                    }
                }
                ActionType.COLOR_CHECK -> {
                    val service = AutoTapAccessibilityService.instance
                    val screenshot = service?.captureScreenshotSync(1000L)
                    if (screenshot != null) {
                        try {
                            val matched = visionGateway.checkColor(screenshot, action.posX.toInt(), action.posY.toInt(), action.targetColorHex, action.colorTolerance, action.colorDeltaEMode)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(appContext, if (matched) "Цвет ${action.targetColorHex} СОВПАЛ!" else "Цвет не совпал", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            screenshot.recycle()
                        }
                    }
                }
                                ActionType.SUBROUTINE -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "Подпрограмма: ${action.targetScriptOrQuery.ifEmpty { action.subroutineTag }}", Toast.LENGTH_SHORT).show()
                    }
                }
                ActionType.RETURN -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "Точка возврата", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onPlayClicked() {
        if (macroEngine.executionState.value is ExecutionState.Running) {
            macroEngine.stop()
            return
        }

        val actions = targetManager.getActions()
        if (actions.isEmpty()) {
            Toast.makeText(appContext, "Добавьте действие [+] или вырежьте цель", Toast.LENGTH_SHORT).show()
            return
        }
        val dm = appContext.resources.displayMetrics
        val specs = DeviceDisplaySpecs(dm.widthPixels, dm.heightPixels, dm.densityDpi, dm.density, dm.widthPixels > dm.heightPixels)
        val scenario = MacroScenario("ActiveSession", 1, specs, actions, globalClickDurationMs, globalSwipeDurationMs)

        templateRepository.clearCache()
        val templatesMap = loadScenarioTemplates(actions)
        macroEngine.start(scenario, templatesMap, isDebug = false)
    }

    override fun onPlayLongClicked() {
        if (macroEngine.executionState.value is ExecutionState.Running) {
            macroEngine.stop()
        }

        val actions = targetManager.getActions()
        if (actions.isEmpty()) return
        val dm = appContext.resources.displayMetrics
        val specs = DeviceDisplaySpecs(dm.widthPixels, dm.heightPixels, dm.densityDpi, dm.density, dm.widthPixels > dm.heightPixels)
        val scenario = MacroScenario("DebugSession", 1, specs, actions, globalClickDurationMs, globalSwipeDurationMs)

        templateRepository.clearCache()
        val templatesMap = loadScenarioTemplates(actions)
        macroEngine.start(scenario, templatesMap, isDebug = true)
        debuggerToolbarOverlay.show(onStepNext = { macroEngine.stepDebugNext() }, onStop = { macroEngine.stop() })
    }

        override fun onAddClicked() {
        onAddActionSelected(com.example.autotap.domain.model.ActionType.CLICK, false)
    }

    override fun onAddActionSelected(type: com.example.autotap.domain.model.ActionType) {
        onAddActionSelected(type, isNeural = false)
    }

    override fun onAddActionSelected(type: com.example.autotap.domain.model.ActionType, isNeural: Boolean) {
        val cx = appContext.resources.displayMetrics.widthPixels / 2f
        val cy = appContext.resources.displayMetrics.heightPixels / 2f
        val defaultDelay = if (type == ActionType.TRIGGER || type == ActionType.COLOR_CHECK || type == ActionType.OCR) 1000L else 100L
        when (type) {
            ActionType.TRIGGER -> {
                targetManager.addActionAt(cx, cy, ActionType.TRIGGER, delayMs = defaultDelay)
            }
            ActionType.COLOR_CHECK -> {
                targetManager.addActionAt(cx, cy, ActionType.COLOR_CHECK, delayMs = defaultDelay)
                targetManager.getActions().lastOrNull()?.let { newAction ->
                    startEyedropper { hex ->
                        targetManager.updateAction(newAction.copy(targetColorHex = hex))
                    }
                }
            }
            ActionType.PATH, ActionType.OCR, ActionType.SUBROUTINE -> {
                targetManager.addActionAt(cx, cy, type, delayMs = defaultDelay)
                targetManager.getActions().lastOrNull()?.let { newAction ->
                    showEditDialog(newAction)
                }
            }
            ActionType.CLICK, ActionType.LONG_PRESS, ActionType.SWIPE, ActionType.DELAY, ActionType.GLOBAL_BACK, ActionType.GLOBAL_HOME -> {
                targetManager.addActionAt(cx, cy, type, delayMs = defaultDelay)
            }
            else -> {
                targetManager.addActionAt(cx, cy, type, delayMs = defaultDelay)
            }
        }
    }

    private fun launchRecorderOverlay(smartMode: Boolean) {
        com.example.autotap.core.logger.AppLogger.log(appContext, "RECORDER", "launchRecorderOverlay: smartMode=$smartMode")
        targetManager.setOverlaysVisible(false)
        controlPanelOverlay.hide()
        GestureRecorderOverlay(
            context = appContext,
            overlayWindowManager = overlayWindowManager,
            initialSmartMode = smartMode,
            onRecordedActions = { actions ->
                com.example.autotap.core.logger.AppLogger.log(appContext, "RECORDER", "onRecordedActions: получено ${actions.size} шагов")
                controlPanelOverlay.show()
                targetManager.setOverlaysVisible(true)
                actions.forEach { act -> targetManager.addAction(act) }
                checkAndPromptGraphGeneration(targetManager.getActions().lastOrNull() ?: actions.last()) {}
            },
            onCancelled = {
                com.example.autotap.core.logger.AppLogger.log(appContext, "RECORDER", "Запись отменена пользователем")
                controlPanelOverlay.show()
                targetManager.setOverlaysVisible(true)
            }
        ).show()
    }

    override fun onRecordClicked() {
        val isUnlocked = com.example.autotap.core.license.LicenseManager.isFeatureUnlocked(appContext, com.example.autotap.core.license.LicenseManager.Feature.RECORDING)
        com.example.autotap.core.logger.AppLogger.log(appContext, "RECORDER", "onRecordClicked: unlocked=$isUnlocked, isDev=${com.example.autotap.core.license.LicenseManager.isDeveloperDevice(appContext)}, isSub=${com.example.autotap.core.license.LicenseManager.isSubscribed(appContext)}")
        if (!isUnlocked) {
            android.widget.Toast.makeText(appContext, "Требуется PRO: подписка 50 руб или 5 роликов в меню", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        launchRecorderOverlay(smartMode = false)
    }

    override fun onSmartRecordClicked() {
        val isUnlocked = com.example.autotap.core.license.LicenseManager.isFeatureUnlocked(appContext, com.example.autotap.core.license.LicenseManager.Feature.RECORDING)
        com.example.autotap.core.logger.AppLogger.log(appContext, "RECORDER", "onSmartRecordClicked: unlocked=$isUnlocked")
        if (!isUnlocked) {
            android.widget.Toast.makeText(appContext, "Требуется PRO: подписка 50 руб или 5 роликов в меню", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        launchRecorderOverlay(smartMode = true)
    }

    override fun onCaptureClicked() {
        // [V150.0] Создание шаблонов и воспроизведение полностью БЕСПЛАТНЫ по умолчанию для любых экранов (включая рекламу)
        targetManager.setOverlaysVisible(false)
        controlPanelOverlay.hide()
        lateinit var captureOverlay: CaptureFrameOverlay
        captureOverlay = CaptureFrameOverlay(
            context = appContext, overlayWindowManager = overlayWindowManager,
            onCaptureTriggered = { cropX, cropY, cropW, cropH, _, roi ->
                appScope.launch(Dispatchers.Default) {
                    delay(150L) // Гарантия сокрытия синей рамки видоискателя
                    val service = AutoTapAccessibilityService.instance
                    val screenshot = service?.captureScreenshotAsync(2500L)
                    withContext(Dispatchers.Main) {
                        if (screenshot != null) {
                            val safeW = cropW.coerceIn(8, screenshot.width); val safeH = cropH.coerceIn(8, screenshot.height)
                            val safeX = cropX.coerceIn(0, screenshot.width - safeW); val safeY = cropY.coerceIn(0, screenshot.height - safeH)
                            
                            val effectiveRoi = roi ?: Rect(
                                (safeX - 100).coerceAtLeast(0),
                                (safeY - 100).coerceAtLeast(0),
                                (safeX + safeW + 100).coerceAtMost(screenshot.width),
                                (safeY + safeH + 100).coerceAtMost(screenshot.height)
                            )
                            
                            val nextId = (targetManager.getActions().maxOfOrNull { it.id } ?: 0) + 1
                            val cropCenter = Point2D(cropX + cropW / 2f, cropY + cropH / 2f)
                            val initialAction = MacroAction(
                                id = nextId,
                                type = ActionType.TRIGGER,
                                roiLeft = effectiveRoi.left,
                                roiTop = effectiveRoi.top,
                                roiRight = effectiveRoi.right,
                                roiBottom = effectiveRoi.bottom,
                                primaryAnchorPoints = listOf(cropCenter)
                            )

                            val cropped = Bitmap.createBitmap(screenshot, safeX, safeY, safeW, safeH)
                            val anchor = MatchCandidate(safeX + safeW / 2, safeY + safeH / 2, safeX, safeY, safeX + safeW, safeY + safeH, 1.0f)

                            LaserScanVisualizer.showSweepLaser(appContext, overlayWindowManager) {
                                CalibrationOverlay(
                                    context = appContext, overlayWindowManager = overlayWindowManager, screenshot = screenshot,
                                    rawTemplateBitmap = cropped, initialCandidates = listOf(anchor),
                                    existingAction = initialAction, existingTemplatePath = initialAction?.templatePath?.takeIf { it.isNotBlank() },
                                    allActions = targetManager.getActions(), targetStepId = null,
                                    anchorCropX = safeX,
                                    anchorCropY = safeY,
                                    onFinished = { updatedAction, path ->
                                        controlPanelOverlay.show()
                                        targetManager.setOverlaysVisible(true)
                                        TemplateMatchingEngine.registerDiscoveredLocation(path, updatedAction.posX.toInt(), updatedAction.posY.toInt())
                                        targetManager.addAction(updatedAction.copy(type = ActionType.TRIGGER))
                                        checkAndPromptGraphGeneration(updatedAction) {}
                                    },
                                    onCancelled = { controlPanelOverlay.show(); targetManager.setOverlaysVisible(true) }
                                ).show()
                            }
                        } else {
                            controlPanelOverlay.show(); targetManager.setOverlaysVisible(true)
                        }
                    }
                }
            },
            onCancelled = { controlPanelOverlay.show(); targetManager.setOverlaysVisible(true) }
        ).apply { show() }
    }

    override fun onSaveClicked() {
        val actions = targetManager.getActions()
        if (actions.isEmpty()) {
            Toast.makeText(appContext, "Добавьте действия перед сохранением", Toast.LENGTH_SHORT).show()
            return
        }
        val dm = appContext.resources.displayMetrics
        val specs = DeviceDisplaySpecs(dm.widthPixels, dm.heightPixels, dm.densityDpi, dm.density, dm.widthPixels > dm.heightPixels)
        val defaultName = "Сценарий_${java.text.SimpleDateFormat("HH-mm-ss", java.util.Locale.US).format(java.util.Date())}"
        val scenario = MacroScenario(defaultName, 1, specs, actions, globalClickDurationMs, globalSwipeDurationMs)
        val saved = scenarioRepository.saveScenario(scenario)
        if (saved) {
            val graph = LinearToGraphMigrator.linearToGraph(scenario)
            scenarioRepository.saveGraphScenario(graph)
            Toast.makeText(appContext, "Сценарий '$defaultName' сохранен!", Toast.LENGTH_SHORT).show()
            onScriptsClicked()
        } else {
            Toast.makeText(appContext, "Ошибка сохранения сценария", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onScriptsClicked() {
        ScriptsDialog(
            context = appContext, overlayWindowManager = overlayWindowManager, scenarioRepository = scenarioRepository,
            onSaveCurrentRequested = {
                val dm = appContext.resources.displayMetrics
                val specs = DeviceDisplaySpecs(dm.widthPixels, dm.heightPixels, dm.densityDpi, dm.density, dm.widthPixels > dm.heightPixels)
                scenarioRepository.saveScenario(MacroScenario(it, 1, specs, targetManager.getActions(), globalClickDurationMs, globalSwipeDurationMs))
            },
            onLoadRequested = { 
                isRestoringOrLoadingScenario = true
                scenarioRepository.loadScenario(it)?.let { s -> targetManager.loadActions(s.actions) }
                isRestoringOrLoadingScenario = false
            },
            onExportRequested = { backupManager.exportSingleScriptZip(it)?.let { z -> backupManager.shareZipFile(z, "Export") } },
            onOpenGraphRequested = { openGraphEditor(it) }
        ).show()
    }

    override fun onSettingsClicked() {
        GlobalSettingsDialog(
            context = appContext,
            overlayWindowManager = overlayWindowManager,
            currentClickDuration = globalClickDurationMs,
            currentSwipeDuration = globalSwipeDurationMs,
            currentPathDuration = globalPathDurationMs,
            currentClickPause = globalClickPauseMs,
            onSaved = { c, s, p ->
                globalClickDurationMs = c
                globalSwipeDurationMs = s
                globalPathDurationMs = p
            },
            onSavedFull = { c, s, p, pause ->
                globalClickDurationMs = c
                globalSwipeDurationMs = s
                globalPathDurationMs = p
                globalClickPauseMs = pause
                prefs.edit()
                    .putLong("PREF_GLOBAL_CLICK_DURATION", c)
                    .putLong("PREF_GLOBAL_SWIPE_DURATION", s)
                    .putLong("PREF_GLOBAL_PATH_DURATION", p)
                    .putLong("PREF_GLOBAL_CLICK_PAUSE", pause)
                    .apply()
            }
        ).show()
    }


    override fun onScreenLockClicked() {
        // [Smart Punch-Through Lock] Подключение реактивного сквозного клика во время воспроизведения
        com.example.autotap.infrastructure.accessibility.AutoTapAccessibilityService.instance?.gestureDispatcher?.onGesturePassthroughToggle = { isPassthrough, rect ->
            screenLockOverlay.setPassthroughEnabled(isPassthrough, rect)
        }
        screenLockOverlay.show()
    }
    override fun onClearAllClicked() {
        targetManager.clearAll()
        persistActiveSession()
        Toast.makeText(appContext, "Все мишени удалены", Toast.LENGTH_SHORT).show()
    }
    override fun onToggleNumbersClicked() {
        val nextMode = when (targetManager.visibilityMode) {
            OverlayVisibilityMode.FULL -> OverlayVisibilityMode.TRANSPARENT
            OverlayVisibilityMode.TRANSPARENT -> OverlayVisibilityMode.HIDDEN
            OverlayVisibilityMode.HIDDEN -> OverlayVisibilityMode.FULL
        }
        targetManager.applyVisibilityMode(nextMode)
        controlPanelOverlay.updateVisibilityMode(nextMode)
        val toastText = when (nextMode) {
            OverlayVisibilityMode.FULL -> "Оверлеи: Полная видимость"
            OverlayVisibilityMode.TRANSPARENT -> "Оверлеи: Прозрачный режим"
            OverlayVisibilityMode.HIDDEN -> "Оверлеи: Метки и иконки скрыты"
        }
        Toast.makeText(appContext, toastText, Toast.LENGTH_SHORT).show()
    }
    override fun onLogsClicked() { LogViewerDialog(appContext, overlayWindowManager).show() }
    override fun onHelpClicked() {
        controlPanelOverlay.applyDisplayMode(com.example.autotap.infrastructure.overlay.model.PanelDisplayMode.EXPANDED)
        com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay(
            context = appContext, overlayWindowManager = overlayWindowManager,
            mode = com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay.TutorialMode.CONTROL_PANEL,
            hostViewProvider = { controlPanelOverlay }
        ).show()
    }
    override fun onCloseClicked() { hideOverlays() }

    fun startEyedropper(onColorPicked: (String) -> Unit) {
        val service = AutoTapAccessibilityService.instance ?: return
        val screenshot = service.captureScreenshotSync(2000L) ?: return
        targetManager.setOverlaysVisible(false); controlPanelOverlay.hide()
        EyedropperOverlay(appContext, overlayWindowManager, screenshot, onColorPicked = { controlPanelOverlay.show(); targetManager.setOverlaysVisible(true); onColorPicked(it) }, onCancelled = { controlPanelOverlay.show(); targetManager.setOverlaysVisible(true) }).show()
    }

    fun startRoiSelector(currentRoi: Rect?, onRoiConfirmed: (Rect?) -> Unit) {
        targetManager.setOverlaysVisible(false); controlPanelOverlay.hide()
        RoiSelectorOverlay(appContext, overlayWindowManager, currentRoi, onRoiConfirmed = { controlPanelOverlay.show(); targetManager.setOverlaysVisible(true); onRoiConfirmed(it) }, onCancelled = { controlPanelOverlay.show(); targetManager.setOverlaysVisible(true) }).show()
    }

    private fun showEditDialog(action: MacroAction) {
        activeEditDialog?.dismiss()
        activeEditDialog = EditActionDialog(
            context = appContext, overlayWindowManager = overlayWindowManager, templateRepository = templateRepository, scenarioRepository = scenarioRepository,
            action = action, totalActionsCount = targetManager.getActions().size,
            onSave = { updated -> targetManager.updateAction(updated); checkAndPromptGraphGeneration(updated) {} },
            onClone = { targetManager.cloneAction(it) },
            onDelete = { targetManager.removeAction(it.id) },
            onCalibrate = { act -> 
                activeEditDialog?.dismiss()
                if (act.type == ActionType.COLOR_CHECK) startEyedropper { targetManager.updateAction(act.copy(targetColorHex = it)) } else startCalibration(act) 
            },
            onRecapture = { act -> 
                activeEditDialog?.dismiss()
                startRecaptureForStep(act) 
            },
            onSelectRoi = { act ->
                val curRect = if (act.roiLeft != null && act.roiRight != null) Rect(act.roiLeft, act.roiTop ?: 0, act.roiRight, act.roiBottom ?: 0) else null
                startRoiSelector(curRect) { r ->
                    val updated = if (r != null) {
                        val currentAnchors = act.primaryAnchorPoints.toMutableList()
                        val roiCenter = Point2D(r.centerX().toFloat(), r.centerY().toFloat())
                        if (currentAnchors.none { abs(it.x - roiCenter.x) < 24f && abs(it.y - roiCenter.y) < 24f }) {
                            if (currentAnchors.size >= 8) currentAnchors.removeAt(0)
                            currentAnchors.add(roiCenter)
                        }

                        val primaryPath = if (act.selectedTemplateIndex in act.multiTemplatePaths.indices) {
                            act.multiTemplatePaths[act.selectedTemplateIndex]
                        } else {
                            act.templatePath.ifBlank { act.multiTemplatePaths.firstOrNull { it.isNotBlank() } ?: "" }
                        }

                        if (primaryPath.isNotBlank()) {
                            TemplateMatchingEngine.registerDiscoveredLocation(primaryPath, r.centerX(), r.centerY())
                            val history = TemplateMatchingEngine.historicalSearchAnchors.getOrPut(primaryPath) { java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap()) }
                            history.add(Pair(r.centerX(), r.centerY()))

                            try {
                                val maskFile = if (File(primaryPath).isAbsolute && File(primaryPath).exists()) {
                                    File(primaryPath)
                                } else {
                                    val tDir = File(appContext.filesDir, "templates")
                                    val direct = File(tDir, primaryPath)
                                    if (direct.exists()) direct else {
                                        val fName = File(primaryPath).name
                                        tDir.walkTopDown().filter { it.isFile && it.name == fName }.firstOrNull() ?: File(primaryPath)
                                    }
                                }
                                val metaFile = File(maskFile.parentFile, "${maskFile.nameWithoutExtension}.json")
                                if (metaFile.exists()) {
                                    val metaJson = JSONObject(metaFile.readText())
                                    val discArr = metaJson.optJSONArray("discoveredLocations") ?: org.json.JSONArray()
                                    var alreadyPresent = false
                                    for (i in 0 until discArr.length()) {
                                        val obj = discArr.optJSONObject(i)
                                        if (obj != null && abs(obj.optInt("x") - r.centerX()) < 24 && abs(obj.optInt("y") - r.centerY()) < 24) {
                                            alreadyPresent = true
                                            break
                                        }
                                    }
                                    if (!alreadyPresent) {
                                        if (discArr.length() >= 8) discArr.remove(0)
                                        discArr.put(JSONObject().apply {
                                            put("x", r.centerX())
                                            put("y", r.centerY())
                                        })
                                        metaJson.put("discoveredLocations", discArr)
                                        metaFile.writeText(metaJson.toString())
                                        TemplateMatchingEngine.clearTemplateCache()
                                    }
                                }
                            } catch (e: Exception) {
                                AppLogger.logError(appContext, "ROI_META_ADD", e)
                            }
                        }

                        act.copy(
                            roiLeft = r.left,
                            roiTop = r.top,
                            roiRight = r.right,
                            roiBottom = r.bottom,
                            primaryAnchorPoints = currentAnchors
                        )
                    } else {
                        act.copy(roiLeft = null, roiTop = null, roiRight = null, roiBottom = null)
                    }
                    targetManager.updateAction(updated)
                    mainHandler.postDelayed({ showEditDialog(updated) }, 150L)
                }
            },
            onNavigateStep = { stepId -> targetManager.getActions().find { it.id == stepId }?.let { showEditDialog(it) } },
            onTest = { testSingleAction(it) },
            onSelectScreenOcrText = { act -> startScreenTextOcrSelection(act) }
        )
        activeEditDialog?.show()
    }

    fun startScreenTextOcrSelection(action: MacroAction) {
        targetManager.setOverlaysVisible(false)
        controlPanelOverlay.hide()

        appScope.launch(Dispatchers.Default) {
            val service = AutoTapAccessibilityService.instance
            val screenshot = service?.captureScreenshotAsync(2500L)
            if (screenshot == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Не удалось сделать снимок экрана", Toast.LENGTH_SHORT).show()
                    controlPanelOverlay.show()
                    targetManager.setOverlaysVisible(true)
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                CaptureFrameOverlay(
                    context = appContext,
                    overlayWindowManager = overlayWindowManager,
                    overrideScreenshot = screenshot,
                    onCaptureTriggered = { cropX, cropY, cropW, cropH, _, roi ->
                        appScope.launch(Dispatchers.Default) {
                            try {
                                val maxX = (screenshot.width - 4).coerceAtLeast(0)
                                val maxY = (screenshot.height - 4).coerceAtLeast(0)
                                val safeX = cropX.coerceIn(0, maxX)
                                val safeY = cropY.coerceIn(0, maxY)
                                val safeW = cropW.coerceIn(4, (screenshot.width - safeX).coerceAtLeast(4))
                                val safeH = cropH.coerceIn(4, (screenshot.height - safeY).coerceAtLeast(4))
                                val cropped = Bitmap.createBitmap(screenshot, safeX, safeY, safeW, safeH)
                                val ocrResults = try {
                                    com.example.autotap.infrastructure.ocr.OcrEngine.findTextOnScreen(cropped, "", 2000L, null, mutableMapOf())
                                } finally {
                                    if (!cropped.isRecycled) cropped.recycle()
                                }
                                val globalOcrResults = ocrResults.map { res ->
                                    res.copy(
                                        clickX = res.clickX + safeX,
                                        clickY = res.clickY + safeY,
                                        rectLeft = res.rectLeft + safeX,
                                        rectTop = res.rectTop + safeY,
                                        rectRight = res.rectRight + safeX,
                                        rectBottom = res.rectBottom + safeY
                                    )
                                }

                                withContext(Dispatchers.Main) {
                                    if (globalOcrResults.isNotEmpty()) {
                                        com.example.autotap.infrastructure.overlay.dialog.OcrCandidatePickerDialog(
                                            context = appContext,
                                            overlayWindowManager = overlayWindowManager,
                                            candidates = globalOcrResults,
                                            onDismiss = {
                                                controlPanelOverlay.show()
                                                targetManager.setOverlaysVisible(true)
                                                showEditDialog(action)
                                            },
                                            onCandidateSelected = { idx, selectedMatch ->
                                                controlPanelOverlay.show()
                                                targetManager.setOverlaysVisible(true)
                                                val cleanText = selectedMatch.matchedText.trim()
                                                if (cleanText.isNotBlank()) {
                                                    val existingMulti = action.multiTemplatePaths.filter { it.isNotBlank() }.toMutableList()
                                                    val ocrEntry = "ocr:$cleanText"
                                                    if (!existingMulti.contains(ocrEntry)) {
                                                        existingMulti.add(ocrEntry)
                                                    }
                                                    val updated = action.copy(multiTemplatePaths = existingMulti.distinct())
                                                    targetManager.updateAction(updated)
                                                    com.example.autotap.infrastructure.ocr.OcrQueryMetadataManager.saveOcrQueryToHistory(appContext, cleanText)
                                                    Toast.makeText(appContext, "Выбран OCR текст: '$cleanText'", Toast.LENGTH_SHORT).show()
                                                    mainHandler.postDelayed({ showEditDialog(updated) }, 150L)
                                                }
                                            }
                                        ).show()
                                    } else {
                                        controlPanelOverlay.show()
                                        targetManager.setOverlaysVisible(true)
                                        Toast.makeText(appContext, "Текст на выделенном фрагменте не распознан", Toast.LENGTH_SHORT).show()
                                        showEditDialog(action)
                                    }
                                }
                            } catch (e: Throwable) {
                                AppLogger.logError(appContext, "OCR_SELECTION_CRASH", e)
                                withContext(Dispatchers.Main) {
                                    controlPanelOverlay.show()
                                    targetManager.setOverlaysVisible(true)
                                    Toast.makeText(appContext, "Ошибка считывания текста: ${e.message}", Toast.LENGTH_SHORT).show()
                                    showEditDialog(action)
                                }
                            } finally {
                                screenshot.recycle()
                            }
                        }
                    },
                    onCancelled = {
                        screenshot.recycle()
                        controlPanelOverlay.show()
                        targetManager.setOverlaysVisible(true)
                        showEditDialog(action)
                    }
                ).show()
            }
        }
    }


    private var wasExecutingBeforeStop = false

    private fun handleExecutionState(state: ExecutionState) {

        mainHandler.post {
            when (state) {

                is ExecutionState.Running -> {
                    wasExecutingBeforeStop = true
                    screenOffMonitor.startMonitoring()
                    controlPanelOverlay.hide(); targetManager.setTargetsTouchable(false); targetManager.setOverlaysVisible(false)

                    if (state.isDebugPaused) {
                        debuggerToolbarOverlay.updateStepInfo(state.currentStepIndex, state.totalSteps, state.stepType.name)
                    }
                    if (!macroEngine.isDebugRunning) {
                        runningBadgeOverlay.show()
                        runningBadgeOverlay.forceVisible() // Гарантирует появление кнопки СТОП
                        runningBadgeOverlay.updateState(state)
                    } else {
                        runningBadgeOverlay.dismiss()
                    }
                }

                is ExecutionState.Completed -> {
                    wasExecutingBeforeStop = false
                    screenOffMonitor.stopMonitoring()
                    runningBadgeOverlay.dismiss(); debuggerToolbarOverlay.dismiss(); screenLockOverlay.dismiss()
                    controlPanelOverlay.show(); controlPanelOverlay.setPlayState(false); targetManager.setOverlaysVisible(true); targetManager.setTargetsTouchable(true)
                    persistActiveSession(immediate = true)
                }
                ExecutionState.Idle, is ExecutionState.Error -> {
                    wasExecutingBeforeStop = false
                    screenOffMonitor.stopMonitoring()
                    runningBadgeOverlay.dismiss(); debuggerToolbarOverlay.dismiss(); screenLockOverlay.dismiss()
                    controlPanelOverlay.show(); controlPanelOverlay.setPlayState(false); targetManager.setOverlaysVisible(true); targetManager.setTargetsTouchable(true)
                }

                is ExecutionState.Paused -> {
                    screenOffMonitor.stopMonitoring()
                    controlPanelOverlay.show(); controlPanelOverlay.setPlayState(false); targetManager.setOverlaysVisible(true); targetManager.setTargetsTouchable(true)
                }
            }
        }
    }

    override fun onJoystickClicked() {
        // MASTER PROTOCOL [C-07]: Auto-injected to satisfy interface contract
        try {
            // TODO: Implement joystick toggle logic
        } catch (_: Exception) {}
    }
}
