package com.example.autotap.infrastructure.overlay.record

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.core.math.PathCompressionEngine
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.Point2D
import com.example.autotap.infrastructure.accessibility.AutoTapAccessibilityService
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.launch

@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class GestureRecorderOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val initialSmartMode: Boolean = true,
    private val onRecordedActions: (List<MacroAction>) -> Unit,
    private val onCancelled: () -> Unit
) {
    // [R-06] Single-Phase Tap Invariant: фиксация клика ТОЛЬКО на ACTION_UP с барьером 350мс
    private var lastRecordedClickTime: Long = 0L
    private var touchDownX: Float = 0f
    private var touchDownY: Float = 0f

    private fun shouldRecordTapOnUp(upX: Float, upY: Float): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastRecordedClickTime < 350L) {
            android.util.Log.d("GestureRecorderOverlay", "Отброшен дублирующий клик по времени: ${now - lastRecordedClickTime}ms")
            return false
        }
        val dx = Math.abs(upX - touchDownX)
        val dy = Math.abs(upY - touchDownY)
        if (dx > 30f || dy > 30f) {
            // Это свайп, а не клик
            return false
        }
        lastRecordedClickTime = now
        return true
    }

    private var lastRecordedTapMillis: Long = 0L
    private var lastRecordedTapPointX: Float = -999f
    private var lastRecordedTapPointY: Float = -999f

    private fun isDebouncedDuplicate(x: Float, y: Float): Boolean {
        val now = System.currentTimeMillis()
        val dt = now - lastRecordedTapMillis
        val dx = Math.abs(x - lastRecordedTapPointX)
        val dy = Math.abs(y - lastRecordedTapPointY)
        if (dt < 400L && dx < 30f && dy < 30f) {
            return true
        }
        lastRecordedTapMillis = now
        lastRecordedTapPointX = x
        lastRecordedTapPointY = y
        return false
    }

    private var lastRecordEventTime: Long = 0L

    var isSmartRecording: Boolean = false
    @Volatile private var isCapturingCleanFrame: Boolean = false
    @Volatile private var isDispatchingSyntheticClick: Boolean = false
    private var isLiveStreamingActive: Boolean = false
    private var liveLastPointX: Float = 0f
    private var liveLastPointY: Float = 0f

    private var canvasLayer: View? = null
    private var badgeLayer: View? = null
    private val recordedActions = ArrayList<MacroAction>()
    private val currentTouchPoints = ArrayList<Point2D>()
    private var touchStartTime = 0L
    private var tvRecordCount: TextView? = null
    private var isSmartTemplateMode = initialSmartMode
    private var btnModeToggleRef: TextView? = null
    private val templateRepo by lazy { com.example.autotap.infrastructure.storage.TemplateRepositoryImpl(context) }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var restoreTouchRunnable: Runnable? = null
    private var canvasParams: WindowManager.LayoutParams? = null
    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density
    private fun restoreTouchAfter(delayMs: Long) {
        restoreTouchRunnable?.let { mainHandler.removeCallbacks(it) }
        val restoreTask = Runnable {
            isDispatchingSyntheticClick = false
            val layer = canvasLayer
            val params = canvasParams ?: (layer?.layoutParams as? WindowManager.LayoutParams)
            if (layer != null && params != null) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                overlayWindowManager.updateViewSafe(layer, params)
            }
        }
        restoreTouchRunnable = restoreTask
        mainHandler.postDelayed(restoreTask, delayMs)
    }
    fun show() {
        AppLogger.log(context, "RECORDER", "Открыт режим записи жестов с RDP-оптимизацией")
        val canvasParams = overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        this.canvasParams = canvasParams

        val canvas = object : View(context) {
            private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpF(4f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = Color.parseColor("#38BDF8")
            }
            private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.TRANSPARENT
            }
            private val drawPath = Path()

            override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
                if (currentTouchPoints.size >= 2) {
                    drawPath.rewind()
                    drawPath.moveTo(currentTouchPoints[0].x, currentTouchPoints[0].y)
                    for (i in 1 until currentTouchPoints.size) {
                        drawPath.lineTo(currentTouchPoints[i].x, currentTouchPoints[i].y)
                    }
                    canvas.drawPath(drawPath, trailPaint)
                } else if (currentTouchPoints.size == 1) {
                    // Dot removed to eliminate capture artifacts
                }
            }
        }
        canvasLayer = canvas

        canvas.setOnTouchListener { _, event ->
            if (isDispatchingSyntheticClick) {
                com.example.autotap.core.logger.AppLogger.log(context, "RECORDER", "Тач пропущен: идет синтетическая инжекция")
                return@setOnTouchListener true
            }

            val curX = event.rawX
            val curY = event.rawY

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    com.example.autotap.core.logger.AppLogger.log(context, "RECORDER", "ACTION_DOWN: x=$curX, y=$curY")
                    touchDownX = curX
                    touchDownY = curY
                    touchStartTime = System.currentTimeMillis()
                    currentTouchPoints.clear()
                    currentTouchPoints.add(Point2D(curX, curY))
                    liveLastPointX = curX
                    liveLastPointY = curY
                    isLiveStreamingActive = true
                    AutoTapAccessibilityService.instance?.gestureDispatcher?.sendLivePathStart(curX, curY)
                    canvas.invalidate()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // [Live Motion Streaming] Потоковая передача движения
                    val lastPt = currentTouchPoints.lastOrNull()
                    val dist = hypot((curX - (lastPt?.x ?: curX)).toDouble(), (curY - (lastPt?.y ?: curY)).toDouble())
                    if (lastPt == null || dist > 3.0) {
                        currentTouchPoints.add(Point2D(curX, curY, System.currentTimeMillis() - touchStartTime))
                        if (isLiveStreamingActive && dist > 3.0) {
                            AutoTapAccessibilityService.instance?.gestureDispatcher?.sendLivePathUpdate(liveLastPointX, liveLastPointY, curX, curY)
                            liveLastPointX = curX
                            liveLastPointY = curY
                        }
                        canvas.invalidate()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasStreaming = isLiveStreamingActive
                    if (isLiveStreamingActive) {
                        AutoTapAccessibilityService.instance?.gestureDispatcher?.sendLivePathFinish(curX, curY)
                        isLiveStreamingActive = false
                    }
                    val duration = (System.currentTimeMillis() - touchStartTime).coerceAtLeast(30L)
                    val startPt = currentTouchPoints.firstOrNull() ?: Point2D(curX, curY)
                    val endPt = Point2D(curX, curY)
                    val totalDistance = hypot((endPt.x - startPt.x).toDouble(), (endPt.y - startPt.y).toDouble()).toFloat()

                    val nextId = recordedActions.size + 1
                    val dispatcher = AutoTapAccessibilityService.instance?.gestureDispatcher

                    canvasParams.flags = canvasParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    overlayWindowManager.updateViewSafe(canvasLayer, canvasParams)

                    val gestureDuration: Long

                    if (totalDistance < dpF(12f) && duration < 350L) {
                        if (isDebouncedDuplicate(startPt.x, startPt.y)) {
                            currentTouchPoints.clear()
                            canvas.invalidate()
                            return@setOnTouchListener true
                        }
                        gestureDuration = duration.coerceIn(40L, 120L)
                        isDispatchingSyntheticClick = true
                        // VSYNC Flush: очистка холста и мгновенная инжекция клика без блокировки UI
                        currentTouchPoints.clear()
                        canvas.alpha = 0f
                        canvas.invalidate()

                        if (isSmartTemplateMode) {
                            // Оффлоад тяжелой задачи в фоновый поток (Zero Main Thread Block)
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                val service = AutoTapAccessibilityService.instance

                                // [V12.0] Активация экрана перед захватом
                                val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                                @Suppress("DEPRECATION")
                                val wakeLock = pm?.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "AutoTap:SmartRecord")
                                wakeLock?.acquire(3000L)

                                // Делаем скриншот ДО инжекции жеста (чистый кадр без кружка нажатия)
                                val screenshot = service?.captureScreenshotSync(1200L)

                                // Мгновенно выполняем клик
                                service?.gestureDispatcher?.performClick(startPt.x, startPt.y, gestureDuration)
                                kotlinx.coroutines.delay(120L)

                                if (screenshot != null && !screenshot.isRecycled) {
                                    try {
                                        val cropSize = dp(96).coerceIn(64, 160)
                                        val half = cropSize / 2
                                        val safeX = (startPt.x.toInt() - half).coerceIn(0, screenshot.width - cropSize)
                                        val safeY = (startPt.y.toInt() - half).coerceIn(0, screenshot.height - cropSize)
                                        val cropped = android.graphics.Bitmap.createBitmap(screenshot, safeX, safeY, cropSize, cropSize)

                                        // Прогоняем сырой квадрат через SmartMaskEngine для выжигания фона
                                        val sw = cropped.width
                                        val sh = cropped.height
                                        val sPixels = com.example.autotap.infrastructure.vision.PixelBufferPool.obtain(sw * sh)
                                        cropped.getPixels(sPixels, 0, sw, 0, 0, sw, sh)

                                        val opt = com.example.autotap.infrastructure.vision.SmartMaskEngine.autoOptimizeGlyphSegmentationFast(
                                            rawTemplate = cropped,
                                            screenshotPixels = sPixels,
                                            sw = sw,
                                            sh = sh,
                                            anchorX = sw / 2,
                                            anchorY = sh / 2,
                                            isCircle = false,
                                            isShapeOnly = false
                                        )
                                        com.example.autotap.infrastructure.vision.PixelBufferPool.release(sPixels)

                                        val finalMask = opt.bestMask
                                        val morph = com.example.autotap.infrastructure.vision.TemplateMorphologyClassifier.analyze(finalMask)

                                        // [V16.0] Центрирование относительно обнаруженного объекта + точный оффсет клика
                                        val objCenterX = safeX + opt.cropOffsetX + (finalMask.width / 2f)
                                        val objCenterY = safeY + opt.cropOffsetY + (finalMask.height / 2f)
                                        val clickOffX = startPt.x - objCenterX
                                        val clickOffY = startPt.y - objCenterY

                                        // Вырезаем согласованный сырой фрагмент точно под размер маски
                                        val rawCropped = try {
                                            val rx = opt.cropOffsetX.coerceIn(0, (cropped.width - finalMask.width).coerceAtLeast(0))
                                            val ry = opt.cropOffsetY.coerceIn(0, (cropped.height - finalMask.height).coerceAtLeast(0))
                                            val rw = finalMask.width.coerceIn(1, cropped.width - rx)
                                            val rh = finalMask.height.coerceIn(1, cropped.height - ry)
                                            android.graphics.Bitmap.createBitmap(cropped, rx, ry, rw, rh)
                                        } catch (_: Exception) { cropped }

                                        val meta = org.json.JSONObject().apply {
                                            put("similarityPercent", opt.bestSimilarity.coerceAtLeast(80))
                                            put("isShapeOnlyMode", morph.isShapeOnlyRecommended)
                                            put("isContourMode", true)
                                            put("shapeExpansion", opt.optimalExpansion)
                                            put("paddingOffsetPx", opt.optimalPadding)
                                            put("cropX", safeX + opt.cropOffsetX)
                                            put("cropY", safeY + opt.cropOffsetY)
                                            put("cropSize", maxOf(finalMask.width, finalMask.height))
                                            put("useCustomClickOffset", kotlin.math.abs(clickOffX) > 1.5f || kotlin.math.abs(clickOffY) > 1.5f)
                                            put("clickOffsetX", clickOffX)
                                            put("clickOffsetY", clickOffY)
                                            put("calibratedX", objCenterX.toInt())
                                            put("calibratedY", objCenterY.toInt())
                                            put("createdAt", System.currentTimeMillis())
                                        }

                                        // Сохраняем маску, синхронный кроп и полный экран для последующей калибровки
                                        val repoImpl = templateRepo as? com.example.autotap.infrastructure.storage.TemplateRepositoryImpl
                                        val tPath = if (repoImpl != null) {
                                            repoImpl.saveTemplateWithScreen("smart_record", null, finalMask, rawCropped, screenshot, meta)
                                            } else {
                                            templateRepo.saveTemplate("smart_record", null, finalMask, rawCropped, meta)
                                            }

                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            recordedActions.add(MacroAction(
                                                id = nextId,
                                                type = ActionType.TRIGGER,
                                                posX = objCenterX,
                                                posY = objCenterY,
                                                templatePath = tPath,
                                                similarityPercent = opt.bestSimilarity.coerceAtLeast(80),
                                                isShapeOnlyMode = morph.isShapeOnlyRecommended,
                                                clickAiTarget = true,
                                                useCustomClickOffset = kotlin.math.abs(clickOffX) > 1.5f || kotlin.math.abs(clickOffY) > 1.5f,
                                                clickOffsetX = clickOffX,
                                                clickOffsetY = clickOffY,
                                                holdDurationMs = gestureDuration,
                                                delayMs = 400L
                                            ))
                                            tvRecordCount?.text = "REC: ${recordedActions.size}"
                                            Toast.makeText(context, "Шаблон #${nextId} захвачен", Toast.LENGTH_SHORT).show()
                                            canvas.alpha = 1f
                                            restoreTouchAfter(40L)
                                            }
                                            } catch (_: Exception) {}
                                            finally {
                                            screenshot.recycle()
                                            }
                                            } else {
                                            // Фолбэк на обычный клик, если скриншот не удался
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            recordedActions.add(MacroAction(id = nextId, type = ActionType.CLICK, posX = startPt.x, posY = startPt.y, holdDurationMs = gestureDuration, delayMs = 400L))
                                            tvRecordCount?.text = "REC: ${recordedActions.size}"
                                            canvas.alpha = 1f
                                            restoreTouchAfter(40L)
                                            }
                                            }
                                            }
                                            } else {
                                            // Режим обычных жестов: мгновенный вброс и запись
                                            dispatcher?.performClick(startPt.x, startPt.y, gestureDuration)
                                            recordedActions.add(MacroAction(id = nextId, type = ActionType.CLICK, posX = startPt.x, posY = startPt.y, holdDurationMs = gestureDuration, delayMs = 400L))
                                            canvas.alpha = 1f
                                            restoreTouchAfter(gestureDuration + 40L)
                                            }
                                            } else if (totalDistance < dpF(12f) && duration >= 350L) {
                        val gestureDuration = duration.coerceAtLeast(350L)
                        dispatcher?.performClick(startPt.x, startPt.y, gestureDuration)
                        val actualDelay = if (lastRecordEventTime > 0L) (touchStartTime - lastRecordEventTime).coerceIn(20L, 60000L) else 250L
                        lastRecordEventTime = System.currentTimeMillis()
                        recordedActions.add(MacroAction(id = nextId, type = ActionType.LONG_PRESS, posX = startPt.x, posY = startPt.y, holdDurationMs = gestureDuration, delayMs = actualDelay))
                        restoreTouchAfter(gestureDuration + 40L)
                    } else {
                        // [Сегментированная запись путей]: завершение текущего участка и сохранение параметров
                        val compressedPoints = PathCompressionEngine.compressPath(currentTouchPoints.toList(), maxPoints = 80)
                        val instantDuration = duration.coerceIn(100L, 2500L)
                        dispatcher?.performPath(compressedPoints, instantDuration)
                        val actualDelay = if (lastRecordEventTime > 0L) (touchStartTime - lastRecordEventTime).coerceIn(20L, 60000L) else 250L
                        lastRecordEventTime = System.currentTimeMillis()
                        val defaultPathDur = com.example.autotap.infrastructure.orchestrator.AutoTapOrchestrator.getInstance(context).globalPathDurationMs
                        recordedActions.add(MacroAction(
                            id = nextId,
                            type = ActionType.PATH,
                            posX = startPt.x,
                            posY = startPt.y,
                            endX = endPt.x,
                            endY = endPt.y,
                            holdDurationMs = defaultPathDur,
                            delayMs = actualDelay,
                            pathPoints = compressedPoints
                        ))
                        restoreTouchAfter(instantDuration + 60L)
                    }
                    currentTouchPoints.clear()
                    tvRecordCount?.text = "REC: ${recordedActions.size}"
                    canvas.invalidate()
                    true
                    }
                else -> false
            }
        }

        overlayWindowManager.addViewSafe(canvas, canvasParams)

        val cardW = dp(96)
        val badgeParams = overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.START
        ).apply {
            x = (dm.widthPixels - cardW - dp(12)).coerceAtLeast(dp(8))
            y = dp(110)
        }

        val rootBadge = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F817112B"), Color.parseColor("#F80E091A"))
            ).apply {
                cornerRadius = dpF(18f)
                setStroke(dp(1), Color.parseColor("#8B5CF6"))
            }
            val p = dp(6)
            setPadding(p, p, p, p)
            elevation = dpF(24f)
        }
        badgeLayer = rootBadge

        val tvStatus = TextView(context).apply {
            text = "REC: 0"
            setTextColor(Color.parseColor("#F04438"))
            textSize = 7.5f
            typeface = Typeface.MONOSPACE
            includeFontPadding = false
            setPadding(0, 0, 0, dp(2))
        }
        tvRecordCount = tvStatus
        rootBadge.addView(tvStatus)
        val btnModeToggle = TextView(context).apply {
            text = if (isSmartTemplateMode) "УМНЫЙ: ШАБЛОН" else "РЕЖИМ: КЛИК"
            textSize = 7.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(if (isSmartTemplateMode) "#86EFAC" else "#C4B5FD"))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor(if (isSmartTemplateMode) "#12201A" else "#22183D"))
                cornerRadius = dpF(6f)
                setStroke(dp(1), Color.parseColor(if (isSmartTemplateMode) "#1E3E30" else "#3E2B6B"))
            }
            setPadding(dp(6), dp(3), dp(6), dp(3))
            setOnClickListener {
                isSmartTemplateMode = !isSmartTemplateMode
                text = if (isSmartTemplateMode) "УМНЫЙ: ШАБЛОН" else "РЕЖИМ: КЛИК"
                setTextColor(Color.parseColor(if (isSmartTemplateMode) "#86EFAC" else "#C4B5FD"))
                (background as android.graphics.drawable.GradientDrawable).apply {
                    setColor(Color.parseColor(if (isSmartTemplateMode) "#12201A" else "#22183D"))
                    setStroke(dp(1), Color.parseColor(if (isSmartTemplateMode) "#1E3E30" else "#3E2B6B"))
                }
            }
        }
        btnModeToggleRef = btnModeToggle
        rootBadge.addView(btnModeToggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(22)).apply {
            bottomMargin = dp(6)
        })



        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val btnUndo = object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(9f), dpF(9f), width - dpF(9f), height - dpF(9f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.UNDO, bounds, Color.WHITE, dpF(2.0f))
            }
        }.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4B5563"))
                setStroke(dpF(1.5f).toInt(), Color.parseColor("#9CA3AF"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(6) }
            setOnClickListener {
                if (recordedActions.isNotEmpty()) {
                    val last = recordedActions.removeAt(recordedActions.lastIndex)
                    tvRecordCount?.text = "REC: ${recordedActions.size}"
                    Toast.makeText(context, "Последний шаг #${last.id} удален", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Нет действий для отмены", Toast.LENGTH_SHORT).show()
                }
            }
        }
        buttonsRow.addView(btnUndo)
        val btnSave = object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(9f), dpF(9f), width - dpF(9f), height - dpF(9f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.CHECK, bounds, Color.WHITE, dpF(2.4f))
            }
        }.apply {
            outlineProvider = ViewOutlineProvider.BACKGROUND
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#10B981"), Color.parseColor("#047857"))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dpF(1.5f).toInt(), Color.parseColor("#86EFAC"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                if (recordedActions.isEmpty()) {
                    Toast.makeText(context, "Коснитесь экрана для записи действий", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dismiss()
                onRecordedActions(recordedActions)
            }
        }
        buttonsRow.addView(btnSave)

        val btnCancel = object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(9f), dpF(9f), width - dpF(9f), height - dpF(9f))
                VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.CLOSE, bounds, Color.WHITE, dpF(2.4f))
            }
        }.apply {
            outlineProvider = ViewOutlineProvider.BACKGROUND
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#991B1B"), Color.parseColor("#7F1D1D"))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dpF(1.5f).toInt(), Color.parseColor("#FDA4AF"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener {
                dismiss()
                onCancelled()
            }
        }
        buttonsRow.addView(btnCancel)
        rootBadge.addView(buttonsRow)

        rootBadge.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0
            private var initY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = badgeParams.x
                        initY = badgeParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = abs(event.rawX - touchX)
                        val dy = abs(event.rawY - touchY)
                        if (dx > dp(6) || dy > dp(6)) {
                            val maxPosX = (dm.widthPixels - cardW - dp(8)).coerceAtLeast(0)
                            badgeParams.x = (initX + (event.rawX - touchX).toInt()).coerceIn(dp(6), maxPosX)
                            badgeParams.y = (initY + (event.rawY - touchY).toInt()).coerceIn(dp(20), dm.heightPixels - dp(80))
                            overlayWindowManager.updateViewSafe(badgeLayer, badgeParams)
                            return true
                        }
                    }
                }
                return false
            }
        })

        setupRecordBadgeIcons(rootBadge)
        overlayWindowManager.addViewSafe(rootBadge, badgeParams)
    }

    fun dismiss() {
        restoreTouchRunnable?.let {
            mainHandler.removeCallbacks(it)
            restoreTouchRunnable = null
        }
        canvasLayer?.let {
            overlayWindowManager.removeViewSafe(it)
            canvasLayer = null
        }
        badgeLayer?.let {
            overlayWindowManager.removeViewSafe(it)
            badgeLayer = null
            tvRecordCount = null
        }
    }

    // MASTER PROTOCOL v64.0: Canvas Vector Icons for Recording Confirmation
    private fun applyVectorCheckmark(container: android.view.ViewGroup) {
        container.removeAllViews()
        val d = container.context.resources.displayMetrics.density
        val checkView = object : android.view.View(container.context) {
            private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF86EFAC.toInt() // Pastel mint
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2.5f * d
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            private val path = android.graphics.Path()
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                path.reset()
                val w = width.toFloat()
                val h = height.toFloat()
                path.moveTo(w * 0.28f, h * 0.52f)
                path.lineTo(w * 0.44f, h * 0.68f)
                path.lineTo(w * 0.72f, h * 0.34f)
                canvas.drawPath(path, p)
            }
        }
        container.addView(checkView, android.widget.FrameLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt(), android.view.Gravity.CENTER))
    }

    private fun applyVectorCross(container: android.view.ViewGroup) {
        container.removeAllViews()
        val d = container.context.resources.displayMetrics.density
        val crossView = object : android.view.View(container.context) {
            private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFDA4AF.toInt() // Pastel rose
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2.5f * d
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                canvas.drawLine(w * 0.30f, h * 0.30f, w * 0.70f, h * 0.70f, p)
                canvas.drawLine(w * 0.70f, h * 0.30f, w * 0.30f, h * 0.70f, p)
            }
        }
        container.addView(crossView, android.widget.FrameLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt(), android.view.Gravity.CENTER))
    }


    // =========================================================================
    // MASTER PROTOCOL v64.0: INDESTRUCTIBLE CANVAS VECTOR DRAWABLES
    // =========================================================================
    private class CheckmarkVectorDrawable(private val color: Int, private val strokeWidth: Float) : android.graphics.drawable.Drawable() {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@CheckmarkVectorDrawable.color
            style = android.graphics.Paint.Style.STROKE
            this.strokeWidth = this@CheckmarkVectorDrawable.strokeWidth
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        private val path = android.graphics.Path()
        override fun draw(canvas: android.graphics.Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            path.reset()
            path.moveTo(b.left + w * 0.28f, b.top + h * 0.52f)
            path.lineTo(b.left + w * 0.44f, b.top + h * 0.68f)
            path.lineTo(b.left + w * 0.72f, b.top + h * 0.34f)
            canvas.drawPath(path, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private class CrossVectorDrawable(private val color: Int, private val strokeWidth: Float) : android.graphics.drawable.Drawable() {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@CrossVectorDrawable.color
            style = android.graphics.Paint.Style.STROKE
            this.strokeWidth = this@CrossVectorDrawable.strokeWidth
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        override fun draw(canvas: android.graphics.Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            canvas.drawLine(b.left + w * 0.30f, b.top + h * 0.30f, b.left + w * 0.70f, b.top + h * 0.70f, paint)
            canvas.drawLine(b.left + w * 0.70f, b.top + h * 0.30f, b.left + w * 0.30f, b.top + h * 0.70f, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun setupRecordBadgeIcons(rootBadgeView: android.view.View?) {
        if (rootBadgeView !is android.view.ViewGroup) return
        try {
            val d = rootBadgeView.context.resources.displayMetrics.density
            val checkDrawable = CheckmarkVectorDrawable(0xFF86EFAC.toInt(), 2.5f * d)
            val crossDrawable = CrossVectorDrawable(0xFFFDA4AF.toInt(), 2.5f * d)

            // Recursively search and populate child views in the badge
            val allViews = mutableListOf<android.view.View>()
            fun collect(vg: android.view.ViewGroup) {
                for (i in 0 until vg.childCount) {
                    val child = vg.getChildAt(i)
                    allViews.add(child)
                    if (child is android.view.ViewGroup) collect(child)
                }
            }
            collect(rootBadgeView)

            // Find confirm and cancel buttons
            for (v in allViews) {
                val tag = (v.tag as? String)?.lowercase() ?: ""
                val resName = try { if (v.id != android.view.View.NO_ID) v.resources.getResourceEntryName(v.id).lowercase() else "" } catch (_: Exception) { "" }

                if (resName.contains("confirm") || resName.contains("save") || resName.contains("done") || resName.contains("ok") || tag.contains("confirm")) {
                    if (v is android.widget.ImageView) v.setImageDrawable(checkDrawable)
                    else if (v is android.widget.TextView) { v.text = ""; v.setCompoundDrawablesWithIntrinsicBounds(checkDrawable, null, null, null) }
                    else if (v is android.view.ViewGroup && v.childCount == 0) v.addView(android.view.View(v.context).apply { background = checkDrawable }, android.widget.FrameLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt(), android.view.Gravity.CENTER))
                }

                if (resName.contains("cancel") || resName.contains("discard") || resName.contains("close") || resName.contains("delete") || tag.contains("cancel")) {
                    if (v is android.widget.ImageView) v.setImageDrawable(crossDrawable)
                    else if (v is android.widget.TextView) { v.text = ""; v.setCompoundDrawablesWithIntrinsicBounds(crossDrawable, null, null, null) }
                    else if (v is android.view.ViewGroup && v.childCount == 0) v.addView(android.view.View(v.context).apply { background = crossDrawable }, android.widget.FrameLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt(), android.view.Gravity.CENTER))
                }
            }
        } catch (_: Exception) {}
    }

}
