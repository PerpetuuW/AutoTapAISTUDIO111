package com.example.autotap.infrastructure.orchestrator

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
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
import com.example.autotap.infrastructure.overlay.badge.RunningBadgeOverlay
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
import com.example.autotap.infrastructure.overlay.panel.ControlPanelListener
import com.example.autotap.infrastructure.overlay.panel.ControlPanelOverlay
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
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    val quickRingOverlay by lazy { TargetQuickRingOverlay(appContext, overlayWindowManager) }
    val graphOverlayView by lazy { GraphOverlayView(appContext) }
    val targetManager by lazy { TargetOverlayManager(appContext, overlayWindowManager, quickRingOverlay, graphOverlayView) }
    val controlPanelOverlay by lazy { ControlPanelOverlay(appContext, overlayWindowManager, this) }

    val screenLockOverlay by lazy { ScreenLockOverlay(appContext, overlayWindowManager) }
    val runningBadgeOverlay by lazy { RunningBadgeOverlay(appContext, overlayWindowManager) }
    val debuggerToolbarOverlay by lazy { DebuggerToolbarOverlay(appContext, overlayWindowManager) }

    val graphEditorOverlay by lazy {
        GraphEditorOverlay(appContext, overlayWindowManager, scenarioRepository).apply {
            onStartGraphRequested = { graphScenario -> startGraphScenarioExecution(graphScenario) }
        }
    }

    var globalClickDurationMs: Long = 120L
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

    private var activeEditDialog: EditActionDialog? = null

    init {
        AppLogger.init(appContext)
        targetManager.onActionEditRequested = { showEditDialog(it) }
        targetManager.onActionTestRequested = { testSingleAction(it) }
        targetManager.onActionAddTemplateRequested = { act -> startCaptureForStep(act) }
        targetManager.onActionCalibrateRequested = { act ->
            if (act.type == ActionType.COLOR_CHECK) {
                startEyedropper { hex -> targetManager.updateAction(act.copy(targetColorHex = hex)) }
            } else {
                startCalibration(act)
            }
        }

        targetManager.onActionsChanged = { persistActiveSession() }
        appScope.launch { macroEngine.executionState.collect { handleExecutionState(it) } }
        AutoTapAccessibilityService.instance?.gestureDispatcher?.onGesturePassthroughToggle = { screenLockOverlay.setPassthroughEnabled(it) }
    }

    fun showOverlays() {
        AutoTapAccessibilityService.instance?.gestureDispatcher?.onGesturePassthroughToggle = { screenLockOverlay.setPassthroughEnabled(it) }
        controlPanelOverlay.show()
        targetManager.attachOverlaysIfNeeded()
        targetManager.setOverlaysVisible(true)

        // [V80.0] Гарантированное восстановление последней активной сессии и шагов, которые использовались
        if (targetManager.getActions().isEmpty()) {
            val prefs = appContext.getSharedPreferences("autotap_prefs", Context.MODE_PRIVATE)
            val lastScript = prefs.getString("last_active_scenario_name", "ActiveSession") ?: "ActiveSession"
            val lastScenario = scenarioRepository.loadScenario(lastScript)
                ?: scenarioRepository.loadScenario("ActiveSession")
                ?: scenarioRepository.loadScenario("_last_active_session")
                ?: scenarioRepository.listScenarios().firstOrNull()?.let { scenarioRepository.loadScenario(it) }
            if (lastScenario != null && lastScenario.actions.isNotEmpty()) {
                targetManager.loadActions(lastScenario.actions)
            }
        }

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
                            hostViewProvider = { controlPanelOverlay.containerView }
                        ).show()
                    }
                }, 400L)
            }
        }
    }

    fun hideOverlays() {
        persistActiveSession()
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

    // [V80.0] Автоматическая фиксация активной сессии на диск при изменении или скрытии оверлея
    fun persistActiveSession() {
        val actions = targetManager.getActions()
        if (actions.isEmpty()) return
        val dm = appContext.resources.displayMetrics
        val specs = DeviceDisplaySpecs(dm.widthPixels, dm.heightPixels, dm.densityDpi, dm.density, dm.widthPixels > dm.heightPixels)
        val scenario = MacroScenario("ActiveSession", 1, specs, actions, globalClickDurationMs, globalSwipeDurationMs)
        scenarioRepository.saveScenario(scenario)
        appContext.getSharedPreferences("autotap_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("last_active_scenario_name", "ActiveSession")
            .apply()
    }

    override fun onGraphClicked() {
        val actions = targetManager.getActions()
        val dm = appContext.resources.displayMetrics
        val specs = DeviceDisplaySpecs(dm.widthPixels, dm.heightPixels, dm.densityDpi, dm.density, dm.widthPixels > dm.heightPixels)
        val scenario = MacroScenario("ActiveSession", 1, specs, actions, globalClickDurationMs, globalSwipeDurationMs)
        val graph = LinearToGraphMigrator.linearToGraph(scenario)
        scenarioRepository.saveGraphScenario(graph)
        openGraphEditor(graph.name)
    }

    fun openGraphEditor(name: String) {
        controlPanelOverlay.hide()
        targetManager.setOverlaysVisible(false)
        graphEditorOverlay.show(name)
    }

    fun startGraphScenarioExecution(graph: GraphMacroScenario) {
        templateRepository.clearCache()
        val templatesMap = LinkedHashMap<String, Bitmap>()
        templateRepository.loadAllTemplates().forEach { (path, bmp) -> templatesMap[path] = bmp }
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
        val isComplex = allActions.size >= 3
        val currentScenarioName = prefs.getString("LAST_ACTIVE_SCRIPT", "ActiveSession") ?: "ActiveSession"
        val hasGraph = scenarioRepository.hasGraphScenario(currentScenarioName)
        val dm = appContext.resources.displayMetrics
        val dp = { v: Int -> (v * dm.density).toInt() }
        val dpF = { v: Float -> v * dm.density }
        if (isComplex && !hasGraph) {
            targetManager.setOverlaysVisible(false)
            controlPanelOverlay.hide()
            val msg = "Обнаружено ${allActions.size} действий/шаблонов. Сгенерировать визуальный ГРАФ логики? Он свяжет все шаги, условия и переходы в наглядную схему принятия решений."
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
                    val linearScenario = scenarioRepository.loadScenario(currentScenarioName) ?: com.example.autotap.domain.model.MacroScenario(currentScenarioName, 1, com.example.autotap.domain.model.DeviceDisplaySpecs(1080, 2400, 480, 3f, false), allActions, globalClickDurationMs, globalSwipeDurationMs)
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
            onCompleted()
        }
    }

    fun startCaptureForStep(actionion: MacroAction) {
        targetManager.setOverlaysVisible(false)
        controlPanelOverlay.hide()

        lateinit var captureOverlay: CaptureFrameOverlay
        captureOverlay = CaptureFrameOverlay(
            context = appContext,
            overlayWindowManager = overlayWindowManager,
            onCaptureTriggered = { cropX, cropY, cropW, cropH, _ ->
                val designatedRoi = captureOverlay.designatedRoi
                val effectiveAction = if (designatedRoi != null) {
                    actionion.copy(roiLeft = designatedRoi.left, roiTop = designatedRoi.top, roiRight = designatedRoi.right, roiBottom = designatedRoi.bottom)
                } else actionion

                mainHandler.postDelayed({
                    val service = AutoTapAccessibilityService.instance
                    val screenshot = service?.captureScreenshotSync(2000L)
                    if (screenshot != null) {
                        val safeW = cropW.coerceIn(8, screenshot.width)
                        val safeH = cropH.coerceIn(8, screenshot.height)
                        val safeX = cropX.coerceIn(0, screenshot.width - safeW)
                        val safeY = cropY.coerceIn(0, screenshot.height - safeH)
                        val cropped = Bitmap.createBitmap(screenshot, safeX, safeY, safeW, safeH)

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
                                    TemplateMatchingEngine.lastMatchedPositions[path] = Pair(updatedAction.posX.toInt(), updatedAction.posY.toInt())
                                    targetManager.updateAction(updatedAction)
                                    if (targetManager.getActions().size >= 3) {
                                        checkAndPromptGraphGeneration(updatedAction) {}
                                    }
                                },
                                onCancelled = {
                                    controlPanelOverlay.show()
                                    targetManager.setOverlaysVisible(true)
                                }
                            ).show()
                        }
                    } else {
                        controlPanelOverlay.show()
                        targetManager.setOverlaysVisible(true)
                    }
                }, 80L)
            },
            onCancelled = {
                controlPanelOverlay.show()
                targetManager.setOverlaysVisible(true)
            }
        ).apply { show() }
    }


    private fun startRecaptureForStep(action: MacroAction) {
        if (action.templatePath.isEmpty()) {
            Toast.makeText(appContext, "Шаблон отсутствует, запуск обычного захвата", Toast.LENGTH_SHORT).show()
            startCaptureForStep(action)
            return
        }

        // [V20.1] Надежный поиск файлов шаблона с поддержкой относительных путей
        val maskFile = if (File(action.templatePath).isAbsolute && File(action.templatePath).exists()) {
            File(action.templatePath)
        } else {
            val tDir = File(appContext.filesDir, "templates")
            val direct = File(tDir, action.templatePath)
            if (direct.exists()) direct else {
                val fName = File(action.templatePath).name
                tDir.walkTopDown().filter { it.isFile && it.name == fName }.firstOrNull() ?: File(action.templatePath)
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
            else -> templateRepository.getTemplate(action.templatePath)
        }

        if (rawCropBitmap == null) {
            Toast.makeText(appContext, "Файл шаблона не найден на диске", Toast.LENGTH_SHORT).show()
            startCalibration(action)
            return
        }


        // [V39.0] Полное скрытие меню приложения перед захватом: гарантированно чистый сырой экран
        activeEditDialog?.dismiss()
        activeEditDialog = null
        targetManager.setOverlaysVisible(false)
        controlPanelOverlay.hide()

        mainHandler.postDelayed({
            val dm = appContext.resources.displayMetrics
            val effectiveScreenshot = historicalScreenshot ?: run {
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
                onCaptureTriggered = { cropX, cropY, cropW, cropH, _ ->
                    val safeW = cropW.coerceIn(4, effectiveScreenshot.width - cropX)
                    val safeH = cropH.coerceIn(4, effectiveScreenshot.height - cropY)
                    val newRawCrop = android.graphics.Bitmap.createBitmap(effectiveScreenshot, cropX, cropY, safeW, safeH)

                    com.example.autotap.infrastructure.overlay.capture.CalibrationOverlay(
                        context = appContext,
                        overlayWindowManager = overlayWindowManager,
                        screenshot = effectiveScreenshot,
                        rawTemplateBitmap = newRawCrop,
                        initialCandidates = emptyList(),
                        existingAction = action,
                        existingTemplatePath = action.templatePath,
                        targetStepId = action.id,
                        anchorCropX = cropX,
                        anchorCropY = cropY,
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

        // [V12.3] Упреждающее закрытие диалога редактирования и панели для исключения захвата собственного превью
        activeEditDialog?.dismiss()
        activeEditDialog = null
        targetManager.setOverlaysVisible(false)
        controlPanelOverlay.hide()

        mainHandler.postDelayed({
            executeCleanCalibration(service, action)
        }, 120L)
    }

    private fun executeCleanCalibration(service: AutoTapAccessibilityService, action: MacroAction) {
        var savedScreenBmp: Bitmap? = null
        if (action.templatePath.isNotEmpty()) {
            try {
                val maskFile = File(action.templatePath)
                val screenFile = File(maskFile.parentFile, "screen_" + maskFile.name.removePrefix("mask_").substringBeforeLast(".") + ".jpg")
                if (screenFile.exists()) {
                    savedScreenBmp = BitmapFactory.decodeFile(screenFile.absolutePath)
                } else {
                    val screenPngFile = File(maskFile.parentFile, "screen_" + maskFile.name.removePrefix("mask_"))
                    if (screenPngFile.exists()) savedScreenBmp = BitmapFactory.decodeFile(screenPngFile.absolutePath)
                }
            } catch (_: Exception) {}
        }

        // Если сохраненного экрана нет, делаем живой снимок
        val screenshot = savedScreenBmp ?: service.captureScreenshotSync(2000L) ?: run {
            Toast.makeText(appContext, "Не удалось получить снимок экрана", Toast.LENGTH_SHORT).show()
            return
        }

        val rawSourceBitmap: Bitmap? = if (action.templatePath.isNotEmpty()) {
            val maskFile = File(action.templatePath)
            val rawFile = File(maskFile.parentFile, "raw_" + maskFile.name.removePrefix("mask_"))
            if (rawFile.exists()) BitmapFactory.decodeFile(rawFile.absolutePath) else templateRepository.getTemplate(action.templatePath)
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

        if (action.templatePath.isNotEmpty()) {
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
                        newAnchors.add(Point2D(foundX.toFloat(), foundY.toFloat()))
                        targetManager.updateAction(action.copy(primaryAnchorPoints = newAnchors.takeLast(3)))
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
                existingAction = action,
                existingTemplatePath = action.templatePath,
                allActions = targetManager.getActions(),
                targetStepId = action.id,
                anchorCropX = anchorCandidate.rectLeft,
                anchorCropY = anchorCandidate.rectTop,
                onFinished = { updatedAction, path ->
                    controlPanelOverlay.show()
                    targetManager.setOverlaysVisible(true)
                    TemplateMatchingEngine.clearTemplateCache()
                    TemplateMatchingEngine.lastMatchedPositions[path] = Pair(updatedAction.posX.toInt(), updatedAction.posY.toInt())
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
                            val templates = templateRepository.loadAllTemplates()
                            val targetPair = templates.firstOrNull { it.first == action.templatePath }
                                ?: templates.getOrNull(action.selectedTemplateIndex)
                                ?: templates.firstOrNull()
                            if (targetPair != null) {
                                val matches = visionGateway.findTemplateMatches(screenshot, targetPair.second, action.similarityPercent, action)
                                withContext(Dispatchers.Main) {
                                    if (matches.isNotEmpty()) {
                                        val best = matches.first()
                                        val scorePct = (best.score * 100).toInt().coerceIn(0, 100)
                                        if (matches.size > 1) {
                                            TargetHighlightVisualizer.showMultiTemplateHighlights(
                                                context = appContext,
                                                overlayWindowManager = overlayWindowManager,
                                                candidates = matches,
                                                requiredThreshold = action.similarityPercent,
                                                durationMs = 2400L
                                            )
                                        } else {
                                            TargetHighlightVisualizer.showConfidenceHighlight(
                                                context = appContext,
                                                overlayWindowManager = overlayWindowManager,
                                                rect = Rect(best.rectLeft, best.rectTop, best.rectRight, best.rectBottom),
                                                moduleTag = "ШАБЛОН",
                                                scorePercent = scorePct,
                                                detailText = "порог ${action.similarityPercent}%",
                                                durationMs = 2200L
                                            )
                                        }
                                        if (action.clickAiTarget) {
                                            val finalClickX = best.clickX + (if (action.useCustomClickOffset) action.clickOffsetX else 0f)
                                            val finalClickY = best.clickY + (if (action.useCustomClickOffset) action.clickOffsetY else 0f)
                                            fallbackGestureGateway.performClick(finalClickX, finalClickY, 50L)
                                        }
                                        Toast.makeText(appContext, "Найдено: $scorePct% в (${best.clickX}, ${best.clickY})", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(appContext, "Цель не найдена на экране", Toast.LENGTH_SHORT).show()
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
                                        moduleTag = "OCR",
                                        scorePercent = scorePct,
                                        detailText = best.matchedText,
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
        val templatesMap = LinkedHashMap<String, Bitmap>()
        templateRepository.loadAllTemplates().forEach { (path, bmp) -> templatesMap[path] = bmp }
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
        val templatesMap = LinkedHashMap<String, Bitmap>()
        templateRepository.loadAllTemplates().forEach { (path, bmp) -> templatesMap[path] = bmp }
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
        // [V180.0] Дифференциация таймингов: 1000мс для шаблонов (стабилизация UI), 100мс для обычных шагов
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
                if (targetManager.getActions().size >= 3 || actions.size >= 3) {
                    checkAndPromptGraphGeneration(targetManager.getActions().lastOrNull() ?: actions.last()) {}
                }
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
            onCaptureTriggered = { cropX, cropY, cropW, cropH, _ ->
                val designatedRoi = captureOverlay.designatedRoi
                val nextId = (targetManager.getActions().maxOfOrNull { it.id } ?: 0) + 1
                val initialAction = if (designatedRoi != null) {
                    MacroAction(id = nextId, roiLeft = designatedRoi.left, roiTop = designatedRoi.top, roiRight = designatedRoi.right, roiBottom = designatedRoi.bottom)
                } else null

                mainHandler.postDelayed({
                    val service = AutoTapAccessibilityService.instance
                    val screenshot = service?.captureScreenshotSync(2000L)
                    if (screenshot != null) {
                        val safeW = cropW.coerceIn(8, screenshot.width); val safeH = cropH.coerceIn(8, screenshot.height)
                        val safeX = cropX.coerceIn(0, screenshot.width - safeW); val safeY = cropY.coerceIn(0, screenshot.height - safeH)
                        val cropped = Bitmap.createBitmap(screenshot, safeX, safeY, safeW, safeH)
                        val anchor = MatchCandidate(safeX + safeW / 2, safeY + safeH / 2, safeX, safeY, safeX + safeW, safeY + safeH, 1.0f)

                        LaserScanVisualizer.showSweepLaser(appContext, overlayWindowManager) {
                            CalibrationOverlay(
                                context = appContext, overlayWindowManager = overlayWindowManager, screenshot = screenshot,
                                rawTemplateBitmap = cropped, initialCandidates = listOf(anchor),
                                existingAction = initialAction, existingTemplatePath = null,
                                allActions = targetManager.getActions(), targetStepId = null,
                                anchorCropX = safeX,
                                anchorCropY = safeY,
                                onFinished = { updatedAction, path ->
                                    controlPanelOverlay.show(); targetManager.setOverlaysVisible(true)
                                    val currentActions = targetManager.getActions()
                                    if (currentActions.size == 1 && currentActions[0].type == ActionType.TRIGGER) {
                                        val firstAct = currentActions[0]
                                        val merged = updatedAction.copy(id = firstAct.id)
                                        TemplateMatchingEngine.lastMatchedPositions[path] = Pair(merged.posX.toInt(), merged.posY.toInt())
                                        targetManager.updateAction(merged)
                                    } else {
                                        TemplateMatchingEngine.lastMatchedPositions[path] = Pair(updatedAction.posX.toInt(), updatedAction.posY.toInt())
                                        targetManager.addAction(updatedAction)
                                    }
                                    if (targetManager.getActions().size >= 3) {
                                        checkAndPromptGraphGeneration(updatedAction) {}
                                    }
                                },
                                onCancelled = { controlPanelOverlay.show(); targetManager.setOverlaysVisible(true) }
                            ).show()
                        }
                    } else {
                        controlPanelOverlay.show(); targetManager.setOverlaysVisible(true)
                    }
                }, 80L)
            },
            onCancelled = { controlPanelOverlay.show(); targetManager.setOverlaysVisible(true) }
        ).apply { show() }
    }

    override fun onScriptsClicked() {
        ScriptsDialog(
            context = appContext, overlayWindowManager = overlayWindowManager, scenarioRepository = scenarioRepository,
            onSaveCurrentRequested = {
                val dm = appContext.resources.displayMetrics
                val specs = DeviceDisplaySpecs(dm.widthPixels, dm.heightPixels, dm.densityDpi, dm.density, dm.widthPixels > dm.heightPixels)
                scenarioRepository.saveScenario(MacroScenario(it, 1, specs, targetManager.getActions(), globalClickDurationMs, globalSwipeDurationMs))
            },
            onLoadRequested = { scenarioRepository.loadScenario(it)?.let { s -> targetManager.loadActions(s.actions) } },
            onExportRequested = { backupManager.exportSingleScriptZip(it)?.let { z -> backupManager.shareZipFile(z, "Export") } },
            onOpenGraphRequested = { openGraphEditor(it) }
        ).show()
    }

    override fun onSettingsClicked() {
        GlobalSettingsDialog(appContext, overlayWindowManager, globalClickDurationMs, globalSwipeDurationMs, globalPathDurationMs) { c, s, p ->
            globalClickDurationMs = c
            globalSwipeDurationMs = s
            globalPathDurationMs = p
        }.show()
    }


    override fun onScreenLockClicked() {
        // [Smart Punch-Through Lock] Подключение реактивного сквозного клика во время воспроизведения
        com.example.autotap.infrastructure.accessibility.AutoTapAccessibilityService.instance?.gestureDispatcher?.onGesturePassthroughToggle = { isPassthrough ->
            screenLockOverlay.setPassthroughEnabled(isPassthrough)
        }
        screenLockOverlay.show()
    }
    override fun onClearAllClicked() {
        targetManager.clearAll()
        Toast.makeText(appContext, "Все мишени удалены", Toast.LENGTH_SHORT).show()
    }
    override fun onToggleNumbersClicked() {
        targetManager.isNumbersHidden = !targetManager.isNumbersHidden
        targetManager.setOverlaysVisible(true)
        controlPanelOverlay.updateNumbersHiddenState(targetManager.isNumbersHidden)
        Toast.makeText(appContext, if (targetManager.isNumbersHidden) "Номера шагов скрыты" else "Номера шагов показаны", Toast.LENGTH_SHORT).show()
    }
    override fun onLogsClicked() { LogViewerDialog(appContext, overlayWindowManager).show() }
    override fun onHelpClicked() {
        controlPanelOverlay.applyDisplayMode(com.example.autotap.infrastructure.overlay.model.PanelDisplayMode.EXPANDED)
        com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay(
            context = appContext, overlayWindowManager = overlayWindowManager,
            mode = com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay.TutorialMode.CONTROL_PANEL,
            hostViewProvider = { controlPanelOverlay.containerView }
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
            onClone = { targetManager.addAction(it.copy(posX = it.posX + 30f, posY = it.posY + 30f)) },
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
                    val updated = if (r != null) act.copy(roiLeft = r.left, roiTop = r.top, roiRight = r.right, roiBottom = r.bottom) else act.copy(roiLeft = null, roiTop = null, roiRight = null, roiBottom = null)
                    targetManager.updateAction(updated)
                    mainHandler.postDelayed({ showEditDialog(updated) }, 150L)
                }
            },
            onNavigateStep = { stepId -> targetManager.getActions().find { it.id == stepId }?.let { showEditDialog(it) } },
            onTest = { testSingleAction(it) }
        )
        activeEditDialog?.show()
    }


    private var wasExecutingBeforeStop = false

    private fun handleExecutionState(state: ExecutionState) {

        mainHandler.post {
            when (state) {

                is ExecutionState.Running -> {
                    wasExecutingBeforeStop = true
                    controlPanelOverlay.hide(); targetManager.setTargetsTouchable(false); targetManager.setOverlaysVisible(false)

                    runningBadgeOverlay.show(onLockClick = { screenLockOverlay.show() }, onStopClick = { macroEngine.stop() })
                    runningBadgeOverlay.forceVisible() // Гарантирует появление кнопки СТОП
                    runningBadgeOverlay.updateState(state)
                }

                is ExecutionState.Completed -> {
                    val wasRunning = wasExecutingBeforeStop
                    wasExecutingBeforeStop = false
                    runningBadgeOverlay.dismiss(); debuggerToolbarOverlay.dismiss(); screenLockOverlay.dismiss()
                    controlPanelOverlay.show(); controlPanelOverlay.setPlayState(false); targetManager.setOverlaysVisible(true); targetManager.setTargetsTouchable(true)
                    val currentActions = targetManager.getActions()
                    if (currentActions.isNotEmpty()) {
                        val dm = appContext.resources.displayMetrics
                        val specs = DeviceDisplaySpecs(dm.widthPixels, dm.heightPixels, dm.densityDpi, dm.density, dm.widthPixels > dm.heightPixels)
                        scenarioRepository.saveScenario(MacroScenario("_last_active_session", 1, specs, currentActions, globalClickDurationMs, globalSwipeDurationMs))
                    }
                    if (wasRunning && currentActions.size >= 3) {
                        mainHandler.postDelayed({
                            checkAndPromptGraphGeneration(currentActions.first()) {}
                        }, 350L)
                    }
                }
                ExecutionState.Idle, is ExecutionState.Error -> {
                    val wasRunning = wasExecutingBeforeStop
                    wasExecutingBeforeStop = false
                    runningBadgeOverlay.dismiss(); debuggerToolbarOverlay.dismiss(); screenLockOverlay.dismiss()
                    controlPanelOverlay.show(); controlPanelOverlay.setPlayState(false); targetManager.setOverlaysVisible(true); targetManager.setTargetsTouchable(true)
                    if (wasRunning && state is ExecutionState.Idle) {
                        val currentActions = targetManager.getActions()
                        if (currentActions.size >= 3) {
                            mainHandler.postDelayed({
                                checkAndPromptGraphGeneration(currentActions.first()) {}
                            }, 350L)
                        }
                    }
                }

                is ExecutionState.Paused -> {
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
