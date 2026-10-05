package com.example.autotap.infrastructure.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.domain.model.ExecutionState
import com.example.autotap.infrastructure.gesture.GestureDispatcher
import com.example.autotap.infrastructure.orchestrator.AutoTapOrchestrator
import com.example.autotap.infrastructure.visualizer.GestureVisualizerManager
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AutoTapAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var serviceRef: WeakReference<AutoTapAccessibilityService>? = null

        val instance: AutoTapAccessibilityService?
            get() = serviceRef?.get()

        val isLiveRunning: Boolean
            get() = instance != null && instance?.isServiceConnectedState == true
    }

    private val bgScreenshotExecutor = Executors.newSingleThreadExecutor()
    val visualizerManager by lazy { GestureVisualizerManager(this) }
    val gestureDispatcher by lazy { GestureDispatcher({ this }, visualizerManager) }

    @Volatile
    var isServiceConnectedState = false
        private set

    @Volatile
    private var lastForegroundPackage: String = ""

    val currentForegroundPackage: String
        get() = lastForegroundPackage

    private val isScreenshotInProgress = AtomicBoolean(false)
    private var lastScreenshotCallTime = 0L

    // Системные лаунчеры, клавиатуры и UI, не являющиеся целевыми играми
    private val ignoredSystemPackages = setOf(
        "com.android.systemui", "android", "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard", "com.sec.android.inputmethod",
        "com.google.android.apps.nexuslauncher", "com.miui.home",
        "com.sec.android.app.launcher", "com.huawei.android.launcher",
        "com.oppo.launcher", "com.coloros.launcher", "com.vivo.launcher",
        "com.android.launcher3", "com.android.launcher", "com.teslacoilsw.launcher"
    )

    private val externalStoreAndBrowsers = setOf(
        "com.android.vending", "com.android.chrome", "org.mozilla.firefox",
        "com.opera.browser", "com.yandex.browser", "com.sec.android.app.sbrowser",
        "com.google.android.packageinstaller"
    )

    override fun onCreate() {
        super.onCreate()
        try {
            val canStartDaemon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                checkSelfPermission("android.permission.FOREGROUND_SERVICE_SPECIAL_USE") == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            if (canStartDaemon) {
                val daemonIntent = Intent(this, KeepAliveDaemonService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(daemonIntent)
                } else {
                    startService(daemonIntent)
                }
                AppLogger.log(this, "DAEMON", "KeepAliveDaemonService успешно запущен")
            } else {
                AppLogger.log(this, "DAEMON", "Запуск KeepAliveDaemonService пропущен: отсутствует FOREGROUND_SERVICE_SPECIAL_USE")
            }
        } catch (e: Throwable) {
            AppLogger.logError(this, "DAEMON", e)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            serviceRef = WeakReference(this)
            isServiceConnectedState = true

            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info

            // [V21.2] Подключение транзита жестов макроса сквозь экран блокировки
            val orchestrator = com.example.autotap.infrastructure.orchestrator.AutoTapOrchestrator.getInstance(this)
            gestureDispatcher.onGesturePassthroughToggle = { enabled ->
                orchestrator.screenLockOverlay.setPassthroughEnabled(enabled)
            }

            AppLogger.log(this, "ACCESSIBILITY", "Служба кликера успешно подключена и активна")
        } catch (e: Throwable) {
            AppLogger.logError(this, "ACCESSIBILITY", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        AppLogger.log(this, "ACCESSIBILITY", "onTaskRemoved: Приложение смахнули из Recents. Экстренная зачистка WindowManager...")
        try {
            val orchestrator = AutoTapOrchestrator.getInstance(this)
            orchestrator.hideOverlays()
            orchestrator.overlayWindowManager.removeAll()
        } catch (e: Throwable) {
            AppLogger.logError(this, "ACCESSIBILITY", e)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceConnectedState = false
        serviceRef = null
        try {
            val orchestrator = AutoTapOrchestrator.getInstance(this)
            orchestrator.hideOverlays()
            orchestrator.overlayWindowManager.removeAll()
        } catch (e: Throwable) {
            AppLogger.logError(this, "ACCESSIBILITY", e)
        }
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        serviceRef = WeakReference(this)
        isServiceConnectedState = true
    }

    override fun onDestroy() {
        isServiceConnectedState = false
        serviceRef = null
        try {
            bgScreenshotExecutor.shutdownNow()
            val orchestrator = AutoTapOrchestrator.getInstance(this)
            orchestrator.hideOverlays()
            orchestrator.overlayWindowManager.removeAll()
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val pkg = event.packageName?.toString() ?: return
                // Игнорируем AutoTap, клавиатуры, шторку и лаунчеры рабочего стола для сохранения игры
                if (pkg != packageName && !ignoredSystemPackages.contains(pkg) && !pkg.startsWith("com.example.autotap")) {
                    if (pkg != lastForegroundPackage) {
                        lastForegroundPackage = pkg
                    }
                    val orchestrator = AutoTapOrchestrator.getInstance(this)
                    val isRunning = orchestrator.macroEngine.executionState.value is ExecutionState.Running
                    if (isRunning && externalStoreAndBrowsers.contains(pkg)) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        return
                    }
                    // [V170.0] Детекция страниц рекламы: привлечение внимания к созданию шаблонов
                    val className = event.className?.toString() ?: ""
                    val isAdActivity = className.contains("AdActivity", ignoreCase = true) ||
                            className.contains("Interstitial", ignoreCase = true) ||
                            className.contains("Rewarded", ignoreCase = true) ||
                            pkg.contains("vungle") || pkg.contains("unity3d") || pkg.contains("ironsource") || pkg.contains("applovin")

                    if (isAdActivity && com.example.autotap.core.license.LicenseManager.isAdAssistantEnabled(this)) {
                        val canDetect = com.example.autotap.core.license.LicenseManager.isProActive(this) ||
                                com.example.autotap.core.license.LicenseManager.isRewardAdSessionActive
                        if (canDetect) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                android.widget.Toast.makeText(this, "Обнаружена реклама: вырежьте крестик или стрелочку [CAPTURE]", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    val boundScriptName = orchestrator.profileDetector.getScriptForPackage(pkg)
                    if (boundScriptName != null) {
                        val scenario = orchestrator.scenarioRepository.loadScenario(boundScriptName)
                        if (scenario != null) orchestrator.targetManager.loadActions(scenario.actions)
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        try {
            if (event?.action == KeyEvent.ACTION_DOWN) {
                val orchestrator = AutoTapOrchestrator.getInstance(this)
                when (event.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        gestureDispatcher.vibrateFeedback(40L)
                        orchestrator.macroEngine.stop()
                        orchestrator.hideOverlays()
                        orchestrator.showOverlays()
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        gestureDispatcher.vibrateFeedback(25L)
                        val curState = orchestrator.macroEngine.executionState.value
                        if (curState is ExecutionState.Running) orchestrator.macroEngine.pause()
                        else if (curState is ExecutionState.Paused) orchestrator.macroEngine.resume()
                        else orchestrator.onPlayClicked()
                        return true
                    }
                }
            }
        } catch (_: Throwable) {}
        return super.onKeyEvent(event)
    }

    fun wakeUpScreenIfNeeded() {
        try {
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager ?: return
            if (!pm.isInteractive) {
                @Suppress("DEPRECATION")
                val wl = pm.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "AutoTap:ScreenWake")
                wl.acquire(3000L)
            }
        } catch (_: Exception) {}
    }

    fun captureScreenshotSync(timeoutMs: Long = 1500L): Bitmap? {
        wakeUpScreenIfNeeded()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val now = System.currentTimeMillis()
        if (now - lastScreenshotCallTime < 30L) return null
        if (!isScreenshotInProgress.compareAndSet(false, true)) return null
        lastScreenshotCallTime = now
        val latch = CountDownLatch(1)
        var capturedBitmap: Bitmap? = null

        val orchestrator = AutoTapOrchestrator.getInstance(this)
        val lockScreenShowing = orchestrator.screenLockOverlay.isShowing()
        if (lockScreenShowing) {
            orchestrator.screenLockOverlay.setTemporarilyTransparent(true)
        }
        val badgeShowing = orchestrator.runningBadgeOverlay.isShowing()
        if (badgeShowing) {
            orchestrator.runningBadgeOverlay.setTemporarilyTransparent(true)
        }

        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, bgScreenshotExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                    val hwBuffer = screenshotResult.hardwareBuffer
                    var hwBitmap: Bitmap? = null
                    try {
                        val colorSpace = screenshotResult.colorSpace
                        hwBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace) else null
                        capturedBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Throwable) {
                    } finally {
                        hwBitmap?.let { if (!it.isRecycled) it.recycle() }
                        try { hwBuffer.close() } catch (_: Throwable) {}
                        latch.countDown()
                    }
                }
                override fun onFailure(errorCode: Int) { latch.countDown() }
            })
            try { latch.await(timeoutMs, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) {}
        } catch (_: Throwable) {
        } finally {
            if (lockScreenShowing) orchestrator.screenLockOverlay.setTemporarilyTransparent(false)
            if (badgeShowing) orchestrator.runningBadgeOverlay.setTemporarilyTransparent(false)
            isScreenshotInProgress.set(false)
        }
        return capturedBitmap
    }

    fun findTextInActiveWindow(query: String, roi: Rect? = null): List<com.example.autotap.domain.model.OcrMatchResult> {
        val root = rootInActiveWindow ?: return emptyList()
        val results = mutableListOf<com.example.autotap.domain.model.OcrMatchResult>()
        val cleanQuery = query.trim().lowercase(java.util.Locale.ROOT).replace('ё', 'е').trim()
        val queryNoSpaces = cleanQuery.replace(" ", "")

        fun traverse(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if (node == null) return
            try {
                val nodePkg = node.packageName?.toString() ?: ""
                if (nodePkg == packageName || nodePkg.startsWith("com.example.autotap")) {
                    return
                }
                val nodeText = node.text?.toString() ?: node.contentDescription?.toString()
                if (!nodeText.isNullOrBlank()) {
                    val cleanText = nodeText.lowercase(java.util.Locale.ROOT).replace('ё', 'е').trim()
                    val textNoSpaces = cleanText.replace(" ", "")

                    val isMatch = cleanQuery.isBlank() ||
                            cleanText.contains(cleanQuery) ||
                            (queryNoSpaces.isNotEmpty() && textNoSpaces.contains(queryNoSpaces))

                    if (isMatch) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (roi == null || Rect.intersects(rect, roi)) {
                            results.add(
                                com.example.autotap.domain.model.OcrMatchResult(
                                    matchedText = nodeText,
                                    clickX = rect.centerX(),
                                    clickY = rect.centerY(),
                                    rectLeft = rect.left,
                                    rectTop = rect.top,
                                    rectRight = rect.right,
                                    rectBottom = rect.bottom,
                                    confidence = 1.0f
                                )
                            )
                        }
                    }
                }
                for (i in 0 until node.childCount) {
                    traverse(node.getChild(i))
                }
            } catch (_: Throwable) {}
        }

        traverse(root)
        return results
    }
}
