package com.example.autotap.infrastructure.overlay.capture
import java.util.Locale

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import com.example.autotap.core.logger.AppLogger

import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.MatchCandidate
import com.example.autotap.domain.model.Point2D
import com.example.autotap.infrastructure.accessibility.AutoTapAccessibilityService
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay
import com.example.autotap.infrastructure.storage.TemplateRepositoryImpl

import android.graphics.PixelFormat
import android.view.WindowManager
import com.example.autotap.infrastructure.vision.PixelBufferPool
import com.example.autotap.infrastructure.vision.SmartMaskEngine
import com.example.autotap.infrastructure.vision.TemplateMatchingEngine
import com.example.autotap.infrastructure.vision.TemplateMorphologyClassifier

import com.example.autotap.infrastructure.visualizer.TargetHighlightVisualizer
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class CalibrationOverlay(
private val context: Context,
private val overlayWindowManager: OverlayWindowManager,
private val screenshot: Bitmap,
private val rawTemplateBitmap: Bitmap,
initialCandidates: List<MatchCandidate>,
private val existingAction: MacroAction?,
private val existingTemplatePath: String?,
@Suppress("UNUSED_PARAMETER") allActions: List<MacroAction> = emptyList(),
private val targetStepId: Int? = null,
    private val anchorCropX: Int = 0,
    private val anchorCropY: Int = 0,
    private val onFinished: (MacroAction, String) -> Unit,
    private val onCancelled: () -> Unit
) {
    private val overlayScope = CoroutineScope(Dispatchers.Main)
    private var evalJob: Job? = null

    private var rootFrameLayout: FrameLayout? = null
    private var candidatesCanvasView: CandidatesCanvasView? = null
    private val templateRepository by lazy { TemplateRepositoryImpl(context) }

    private var currentSimilarity: Int = existingAction?.similarityPercent ?: 88
    private var currentShapeExpansion: Int = existingAction?.shapeExpansion ?: 45
    private var currentPaddingOffset: Int = existingAction?.paddingOffsetPx ?: 2
    private var isAutoContourMode: Boolean = existingAction?.isContourMode ?: true
    private var isShapeOnlyMode: Boolean = existingAction?.isShapeOnlyMode ?: com.example.autotap.infrastructure.vision.TemplateMorphologyClassifier.analyze(rawTemplateBitmap).isShapeOnlyRecommended
    private var isCircleShape: Boolean = existingAction?.isCircleShape ?: false
    private var isNeuralEngineMode: Boolean = existingAction?.isNeuralEngine ?: false

    private var useCustomClickOffset: Boolean = existingAction?.useCustomClickOffset ?: false
    private var clickOffsetX: Float = existingAction?.clickOffsetX ?: 0f
    private var clickOffsetY: Float = existingAction?.clickOffsetY ?: 0f

    private var isDraggingClickPoint = false
    private var customEditedMask: Bitmap? = null
    private val coVerificationTemplates = mutableListOf<String>()
    private var activeGeneratedMask: Bitmap? = null
    private var currentCropOffsetX = 0
    private var currentCropOffsetY = 0
    private var currentCandidates = mutableListOf<MatchCandidate>()
    private var activeRoiZones = mutableListOf<Rect>()


    private var previewRawView: MaskPreviewView? = null
    private var previewMaskView: MaskPreviewView? = null
    private var previewLiveView: MaskPreviewView? = null
    private var tvLiveScoreBadge: TextView? = null

    private var tvExpansionValue: TextView? = null
    private var tvPaddingValue: TextView? = null
    private var tvSimilarityValue: TextView? = null
    private var sbExpansion: SeekBar? = null
    private var sbPadding: SeekBar? = null
    private var sbSimilarity: SeekBar? = null
    private var btnContourToggle: Button? = null
    private var btnShapeOnlyToggle: Button? = null
    private var btnCircleToggle: Button? = null
    private var btnClickOffsetToggle: Button? = null

    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    init {
        existingAction?.let { act ->
            if (act.roiLeft != null && act.roiTop != null && act.roiRight != null && act.roiBottom != null) {
                activeRoiZones.add(Rect(act.roiLeft, act.roiTop, act.roiRight, act.roiBottom))
            }
        }
        // [V63.0] Автоматическая генерация оптимизированной маски для вновь захваченного шаблона
        if (existingTemplatePath == null && screenshot.width > 0 && screenshot.height > 0) {
            try {
                val sw = screenshot.width
                val sh = screenshot.height
                val sPixels = PixelBufferPool.obtain(sw * sh)
                try {
                    screenshot.getPixels(sPixels, 0, sw, 0, 0, sw, sh)
                    val baseSafeX = if (anchorCropX > 0) anchorCropX else (sw / 2)
                    val baseSafeY = if (anchorCropY > 0) anchorCropY else (sh / 2)
                    val opt = SmartMaskEngine.autoOptimizeGlyphSegmentationFast(
                        rawTemplate = rawTemplateBitmap,
                        screenshotPixels = sPixels,
                        sw = sw,
                        sh = sh,
                        anchorX = baseSafeX,
                        anchorY = baseSafeY,
                        isCircle = isCircleShape,
                        isShapeOnly = isShapeOnlyMode
                    )
                    currentShapeExpansion = opt.optimalExpansion
                    currentPaddingOffset = opt.optimalPadding
                    currentSimilarity = opt.bestSimilarity
                    isAutoContourMode = opt.useContourMode
                } finally {
                    PixelBufferPool.release(sPixels)
                }
            } catch (_: Exception) {}
        }

        currentCandidates.addAll(initialCandidates)
        if (existingTemplatePath != null) {
            templateRepository.getTemplateMetadata(existingTemplatePath)?.let { meta ->
                (meta["coVerificationTemplates"] as? List<*>)?.forEach { pathObj ->
                    val pStr = pathObj.toString()
                    if (pStr.isNotBlank() && !coVerificationTemplates.contains(pStr)) {
                        coVerificationTemplates.add(pStr)
                    }
                }
            }
        }
        }

                                                                private inner class CandidatesCanvasView(context: Context) : View(context) {
                                                                    private val boxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                                                        style = Paint.Style.STROKE
                                                                        strokeWidth = dpF(2.5f)
                                                                        color = "#38BDF8".toColorInt()
                                                                    }
                                private val roiZoneFillPaint = Paint().apply {
                                style = Paint.Style.FILL
                                color = "#18FFB703".toColorInt()
                                }
                                private val roiZoneBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.STROKE
                                strokeWidth = dpF(2.2f)
                                color = "#FFB703".toColorInt()
                                pathEffect = DashPathEffect(floatArrayOf(dpF(8f), dpF(5f)), 0f)
                                }
                                private val centroidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.STROKE
                                strokeWidth = dpF(2f)
                                color = "#FFB703".toColorInt()
                                }
                                private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.STROKE
                                strokeWidth = dpF(2.5f)
                                color = "#F04438".toColorInt()
                                }
                                private val crosshairFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.FILL
                                color = "#80F04438".toColorInt()
                                }

                                private val cachedRoiBounds = RectF()
                                private val cachedBoxBounds = RectF()
                                private val cachedBadgeRect = RectF()
                                private val fullScreenDstRect = RectF()

                                private val textScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    textSize = dpF(10.5f)
                                    typeface = Typeface.DEFAULT_BOLD
                                    color = Color.WHITE
                                }
                                private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    style = Paint.Style.FILL
                                    color = "#E610B981".toColorInt()
                                }
                                private val altBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    style = Paint.Style.STROKE
                                    strokeWidth = dpF(1.8f)
                                    color = "#38BDF8".toColorInt()
                                }

                                init { setWillNotDraw(false) }

                                override fun onDraw(canvas: Canvas) {
                                super.onDraw(canvas)
                                if (screenshot.isRecycled) return

                                // [V20.5] Отрисовка оригинального полноэкранного снимка дисплея 1:1
                                fullScreenDstRect.set(0f, 0f, width.toFloat(), height.toFloat())
                                canvas.drawBitmap(screenshot, null, fullScreenDstRect, null)

                                val scaleX = width / screenshot.width.toFloat()
                                val scaleY = height / screenshot.height.toFloat()


                                for (zone in activeRoiZones) {
                                    cachedRoiBounds.set(zone.left * scaleX, zone.top * scaleY, zone.right * scaleX, zone.bottom * scaleY)
                                    canvas.drawRect(cachedRoiBounds, roiZoneFillPaint)
                                    canvas.drawRect(cachedRoiBounds, roiZoneBorderPaint)

                                    // [V21.0] Угловые маркеры изменения размера зоны (Resize Handles)
                                    val handleR = dpF(5f)
                                    canvas.drawCircle(cachedRoiBounds.left, cachedRoiBounds.top, handleR, centroidPaint)
                                    canvas.drawCircle(cachedRoiBounds.right, cachedRoiBounds.top, handleR, centroidPaint)
                                    canvas.drawCircle(cachedRoiBounds.right, cachedRoiBounds.bottom, handleR, centroidPaint)
                                    canvas.drawCircle(cachedRoiBounds.left, cachedRoiBounds.bottom, handleR, centroidPaint)

                                    // Маркер быстрого удаления зоны «×» в правом верхнем углу
                                    canvas.drawCircle(cachedRoiBounds.right - dpF(8f), cachedRoiBounds.top + dpF(8f), dpF(7f), crosshairFillPaint)
                                    canvas.drawLine(cachedRoiBounds.right - dpF(11f), cachedRoiBounds.top + dpF(5f), cachedRoiBounds.right - dpF(5f), cachedRoiBounds.top + dpF(11f), crosshairPaint)
                                    canvas.drawLine(cachedRoiBounds.right - dpF(5f), cachedRoiBounds.top + dpF(5f), cachedRoiBounds.right - dpF(11f), cachedRoiBounds.top + dpF(11f), crosshairPaint)
                                }

                                                                // [V17.0] Отрисовка ВСЕХ найденных кандидатов с Zero-Allocation в цикле
                                                                currentCandidates.forEachIndexed { idx, cand ->
                                                                    val left = cand.rectLeft * scaleX
                                                                    val top = cand.rectTop * scaleY
                                                                    val right = cand.rectRight * scaleX
                                                                    val bottom = cand.rectBottom * scaleY
                                                                    cachedBoxBounds.set(left, top, right, bottom)
                                                                    canvas.drawRoundRect(cachedBoxBounds, dpF(5f), dpF(5f), if (idx == 0) boxStrokePaint else altBoxPaint)

                                                                    // Плашка точного процента сходимости над рамкой без аллокаций
                                                                    val pct = (cand.score * 100f).toInt()
                                                                    val scoreBadgeText = "$pct%"
                                                                    val textW = textScorePaint.measureText(scoreBadgeText)
                                                                    val badgeH = dpF(16f)
                                                                    val badgeTop = (top - badgeH - dpF(2f)).coerceAtLeast(0f)
                                                                    badgeBgPaint.color = if (idx == 0) "#E610B981".toColorInt() else "#E60284C7".toColorInt()
                                                                    cachedBadgeRect.set(left, badgeTop, left + textW + dpF(10f), badgeTop + badgeH)
                                                                    canvas.drawRoundRect(cachedBadgeRect, dpF(4f), dpF(4f), badgeBgPaint)
                                                                    canvas.drawText(scoreBadgeText, left + dpF(5f), badgeTop + dpF(12f), textScorePaint)
                                                                }

                                val bestCandidate = currentCandidates.firstOrNull() ?: return
                                val cX = (bestCandidate.clickX + if (useCustomClickOffset) clickOffsetX else 0f) * scaleX
                                val cY = (bestCandidate.clickY + if (useCustomClickOffset) clickOffsetY else 0f) * scaleY

                                // [V13.4] МИКРО-МЕТКА ОФФСЕТА: аккуратный пин радиусом 6dp без заливки (не перекрывает объект)
                                val pinR = dpF(6f)
                                val crossSz = dpF(4f)
                                if (useCustomClickOffset) {
                                    canvas.drawCircle(cX, cY, pinR, crosshairPaint)
                                    canvas.drawCircle(cX, cY, dpF(1.5f), centroidPaint)
                                                                        canvas.drawLine(cX - pinR - crossSz, cY, cX - pinR, cY, crosshairPaint)
                                    canvas.drawLine(cX + pinR, cY, cX + pinR + crossSz, cY, crosshairPaint)
                                    canvas.drawLine(cX, cY - pinR - crossSz, cX, cY - pinR, crosshairPaint)
                                    canvas.drawLine(cX, cY + pinR, cX, cY + pinR + crossSz, crosshairPaint)
                                } else {
                                    canvas.drawCircle(cX, cY, dpF(3f), centroidPaint)
                                }
                                }


                                private var selectedRoiIdx = -1
                                private var isDraggingRoi = false
                                private var isResizingRoi = false
                                private var activeCorner = -1
                                private var lastRoiTouchX = 0f
                                private var lastRoiTouchY = 0f

                                override fun onTouchEvent(event: MotionEvent): Boolean {
                                val scaleX = width / screenshot.width.toFloat()
                                val scaleY = height / screenshot.height.toFloat()

                                // [V21.0] Интерактивное управление зонами поиска ROI (Drag, Resize, Remove)
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        // 1. Проверка клика по крестику удаления зоны
                                        for (i in activeRoiZones.indices.reversed()) {
                                            val z = activeRoiZones[i]
                                            val closeX = z.right * scaleX - dpF(8f)
                                            val closeY = z.top * scaleY + dpF(8f)
                                            if (hypot((event.x - closeX).toDouble(), (event.y - closeY).toDouble()) < dpF(16f)) {
                                                activeRoiZones.removeAt(i)
                                                vibrate(25L)
                                                invalidate()
                                                reevaluateMatching()
                                                return true
                                            }
                                        }

                                        // 2. Проверка зажатия угловых маркеров Resize
                                        for (i in activeRoiZones.indices.reversed()) {
                                            val z = activeRoiZones[i]
                                            val l = z.left * scaleX; val t = z.top * scaleY
                                            val r = z.right * scaleX; val b = z.bottom * scaleY
                                            val rLimit = dpF(20f)
                                            val corner = when {
                                                hypot((event.x - l).toDouble(), (event.y - t).toDouble()) < rLimit -> 0
                                                hypot((event.x - r).toDouble(), (event.y - t).toDouble()) < rLimit -> 1
                                                hypot((event.x - r).toDouble(), (event.y - b).toDouble()) < rLimit -> 2
                                                hypot((event.x - l).toDouble(), (event.y - b).toDouble()) < rLimit -> 3
                                                else -> -1
                                            }
                                            if (corner != -1) {
                                                selectedRoiIdx = i
                                                activeCorner = corner
                                                isResizingRoi = true
                                                lastRoiTouchX = event.x; lastRoiTouchY = event.y
                                                vibrate(20L)
                                                return true
                                            }
                                        }

                                        // 3. Проверка перетаскивания всего тела зоны ROI (Move)
                                        for (i in activeRoiZones.indices.reversed()) {
                                            val z = activeRoiZones[i]
                                            if (event.x >= z.left * scaleX && event.x <= z.right * scaleX &&
                                                event.y >= z.top * scaleY && event.y <= z.bottom * scaleY) {
                                                selectedRoiIdx = i
                                                isDraggingRoi = true
                                                lastRoiTouchX = event.x; lastRoiTouchY = event.y
                                                vibrate(15L)
                                                return true
                                            }
                                        }
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        if (isResizingRoi && selectedRoiIdx in activeRoiZones.indices) {
                                            val z = activeRoiZones[selectedRoiIdx]
                                            val dx = ((event.x - lastRoiTouchX) / scaleX).toInt()
                                            val dy = ((event.y - lastRoiTouchY) / scaleY).toInt()
                                            var nl = z.left; var nt = z.top; var nr = z.right; var nb = z.bottom
                                            when (activeCorner) {
                                                0 -> { nl = (nl + dx).coerceIn(0, nr - 24); nt = (nt + dy).coerceIn(0, nb - 24) }
                                                1 -> { nr = (nr + dx).coerceIn(nl + 24, screenshot.width); nt = (nt + dy).coerceIn(0, nb - 24) }
                                                2 -> { nr = (nr + dx).coerceIn(nl + 24, screenshot.width); nb = (nb + dy).coerceIn(nt + 24, screenshot.height) }
                                                3 -> { nl = (nl + dx).coerceIn(0, nr - 24); nb = (nb + dy).coerceIn(nt + 24, screenshot.height) }
                                            }
                                            activeRoiZones[selectedRoiIdx] = Rect(nl, nt, nr, nb)
                                            lastRoiTouchX = event.x; lastRoiTouchY = event.y
                                            invalidate()
                                            return true
                                        }
                                        if (isDraggingRoi && selectedRoiIdx in activeRoiZones.indices) {
                                            val z = activeRoiZones[selectedRoiIdx]
                                            val dx = ((event.x - lastRoiTouchX) / scaleX).toInt()
                                            val dy = ((event.y - lastRoiTouchY) / scaleY).toInt()
                                            val w = z.width(); val h = z.height()
                                            val nl = (z.left + dx).coerceIn(0, screenshot.width - w)
                                            val nt = (z.top + dy).coerceIn(0, screenshot.height - h)
                                            activeRoiZones[selectedRoiIdx] = Rect(nl, nt, nl + w, nt + h)
                                            lastRoiTouchX = event.x; lastRoiTouchY = event.y
                                            invalidate()
                                            return true
                                        }
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        if (isDraggingRoi || isResizingRoi) {
                                            isDraggingRoi = false
                                            isResizingRoi = false
                                            selectedRoiIdx = -1
                                            activeCorner = -1
                                            reevaluateMatching()
                                            return true
                                        }
                                    }
                                }

                                val bestCandidate = currentCandidates.firstOrNull() ?: return false

                                // Тап по альтернативному кандидату делает его активным
                                if (event.action == MotionEvent.ACTION_DOWN && currentCandidates.size > 1) {
                                    val clickedIdx = currentCandidates.indexOfFirst { cand ->
                                        val l = cand.rectLeft * scaleX
                                        val t = cand.rectTop * scaleY
                                        val r = cand.rectRight * scaleX
                                        val b = cand.rectBottom * scaleY
                                        event.x in (l - dpF(12f))..(r + dpF(12f)) && event.y in (t - dpF(12f))..(b + dpF(12f))
                                    }
                                    if (clickedIdx > 0) {
                                        val selected = currentCandidates.removeAt(clickedIdx)
                                        currentCandidates.add(0, selected)
                                        vibrate(25L)
                                        invalidate()
                                        return true
                                    }
                                }

                                if (useCustomClickOffset) {
                                val cX = (bestCandidate.clickX + clickOffsetX) * scaleX
                                val cY = (bestCandidate.clickY + clickOffsetY) * scaleY

                                when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                if (hypot((event.x - cX).toDouble(), (event.y - cY).toDouble()) < dpF(48f)) {
                                isDraggingClickPoint = true
                                vibrate(20L)
                                return true
                                }
                                }
                                MotionEvent.ACTION_MOVE -> {
                                if (isDraggingClickPoint) {
                                clickOffsetX = (event.x / scaleX) - bestCandidate.clickX
                                clickOffsetY = (event.y / scaleY) - bestCandidate.clickY
                                updateClickOffsetButtonText()
                                invalidate()
                                return true
                                }
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                if (isDraggingClickPoint) {
                                isDraggingClickPoint = false
                                vibrate(15L)
                                return true
                                }
                                }
                                }
                                }
                                return false
                                }
                                }

    private fun vibrate(durationMs: Long) {
        try {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }

    private fun getActiveTemplateBitmap(): Bitmap {
        customEditedMask?.let { return it }
        val res = SmartMaskEngine.generateContourMaskWithCrop(
            src = rawTemplateBitmap,
            shapeExpansion = currentShapeExpansion,
            paddingOffsetPx = currentPaddingOffset,
            isCircle = isCircleShape,
            isRawMode = !isAutoContourMode,
            isShapeOnly = isShapeOnlyMode
        )
        currentCropOffsetX = res.cropOffsetX
        currentCropOffsetY = res.cropOffsetY
        activeGeneratedMask = res.bitmap
        return res.bitmap
    }

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                private fun reevaluateMatching() {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                evalJob?.cancel()
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                evalJob = overlayScope.launch {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                delay(150L)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val currentMask = getActiveTemplateBitmap()
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                previewMaskView?.bind(currentMask, isMask = true, isCircle = isCircleShape)

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                if (screenshot.isRecycled) return@launch
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val sw = screenshot.width
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val sh = screenshot.height

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                var edgeCount = 0
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val (evaluatedCandidates, liveCrop) = withContext(Dispatchers.Default) {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val sPixels = PixelBufferPool.obtain(sw * sh)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                try {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                screenshot.getPixels(sPixels, 0, sw, 0, 0, sw, sh)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val dynKey = "calib_${currentShapeExpansion}_${currentPaddingOffset}_${isAutoContourMode}_${isCircleShape}_${isShapeOnlyMode}"
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val features = TemplateMatchingEngine.extractFeatures(currentMask, dynKey, "", currentSimilarity)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                edgeCount = features?.shapeEdgeCount ?: 0

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val baseSafeX = if (anchorCropX in 0 until sw) anchorCropX else ((existingAction?.posX?.toInt() ?: (sw / 2)) - currentMask.width / 2)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val baseSafeY = if (anchorCropY in 0 until sh) anchorCropY else ((existingAction?.posY?.toInt() ?: (sh / 2)) - currentMask.height / 2)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val originX = (baseSafeX + currentCropOffsetX).coerceIn(0, sw - currentMask.width)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val originY = (baseSafeY + currentCropOffsetY).coerceIn(0, sh - currentMask.height)

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                var peakScore = 0f
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                var peakX = originX
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                var peakY = originY

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                // [V85.0] Честный расчет сходимости в калибровке: AI использует ZNCC, OpenCV использует Собель
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                if (isNeuralEngineMode) {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    val (aiScore, aiCoords) = com.example.autotap.infrastructure.vision.NeuralVisualMatcher.evaluateAnchorZNCC(
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        sPixels, sw, sh, currentMask, originX, originY, radius = 16
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    )
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    peakScore = aiScore
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    peakX = aiCoords.first
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    peakY = aiCoords.second
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                } else if (features != null) {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    for (dy in -16..16) {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        for (dx in -16..16) {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            val fx = (originX + dx).coerceIn(0, sw - currentMask.width)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            val fy = (originY + dy).coerceIn(0, sh - currentMask.height)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            val score = TemplateMatchingEngine.evaluateCandidateScore(sPixels, sw, sh, fx, fy, features, isShapeOnlyMode)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            if (score > peakScore) {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                peakScore = score
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                peakX = fx
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                peakY = fy
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            }
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        }
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    }
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                }

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val localAnchorMatch = if (peakScore > 0.20f) {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    val cX = features?.centroidX ?: (currentMask.width / 2)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    val cY = features?.centroidY ?: (currentMask.height / 2)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    MatchCandidate(
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        clickX = peakX + cX,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        clickY = peakY + cY,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        rectLeft = peakX,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        rectTop = peakY,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        rectRight = peakX + currentMask.width,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        rectBottom = peakY + currentMask.height,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        score = peakScore,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        templatePath = existingTemplatePath ?: ""
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    )
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                } else null

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                // Полноэкранный каскадный поиск ВСЕХ совпадений со сходимостью >= 60% без раннего выхода
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                // [V33.4] Реальный расчет NeuralVisualMatcher при переключении на AI

                    // [V33.5] Реальный расчет NeuralVisualMatcher при переключении на AI
                    val cascadeMatches = if (isNeuralEngineMode) {
                        try {
                            com.example.autotap.infrastructure.vision.NeuralVisualMatcher.findMatches(
                                screenshot = screenshot,
                                template = currentMask,
                                minSimilarityPercent = currentSimilarity.coerceAtLeast(40),
                                actionOverride = existingAction
                            )
                        } catch (_: Throwable) { emptyList() }
                    } else {
                        TemplateMatchingEngine.findTemplateFastCascade(
                            sPixels = sPixels, sw = sw, sh = sh, template = currentMask,
                            minSimilarityPercent = 60, templatePath = existingTemplatePath ?: "",
                            actionOverride = existingAction, enableL0Cache = false, findAllMatches = true
                        ) { evalJob?.isCancelled == true }
                    }

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val candidateList = mutableListOf<MatchCandidate>()
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                if (localAnchorMatch != null) {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    candidateList.add(localAnchorMatch)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                }

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                // Дедупликация и объединение с локальным совпадением
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                for (cand in cascadeMatches) {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    if (cand.score < 0.60f) continue
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    val isDuplicate = candidateList.any { existing ->
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        kotlin.math.abs(existing.rectLeft - cand.rectLeft) < 18 &&
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        kotlin.math.abs(existing.rectTop - cand.rectTop) < 18
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    }
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    if (!isDuplicate) {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        candidateList.add(cand)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    }
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                }

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                // Сортировка по убыванию сходимости
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                candidateList.sortByDescending { it.score }

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val bestMatch = candidateList.firstOrNull()
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val crop = if (bestMatch != null && !screenshot.isRecycled) {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val cX = bestMatch.rectLeft.coerceIn(0, sw - 1)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val cY = bestMatch.rectTop.coerceIn(0, sh - 1)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val cW = (bestMatch.rectRight - bestMatch.rectLeft).coerceIn(1, sw - cX)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val cH = (bestMatch.rectBottom - bestMatch.rectTop).coerceIn(1, sh - cY)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                Bitmap.createBitmap(screenshot, cX, cY, cW, cH)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                } else null

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                Pair(candidateList, crop)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                } finally {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                PixelBufferPool.release(sPixels)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                }
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                }

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                currentCandidates.clear()
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                currentCandidates.addAll(evaluatedCandidates)

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val bestCandidate = currentCandidates.firstOrNull()
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                if (bestCandidate != null) {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val scorePct = (bestCandidate.score * 100).roundToInt()
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                val isPass = scorePct >= currentSimilarity
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                AppLogger.log(context, "CV_CALIBRATION", String.format(Locale.US, "Калибровка: score=%d%% (%s, порог=%d%%) в (%d,%d), маска=%dx%d, ребер=%d, режим=%s",
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                scorePct, if (isPass) "PASS" else "FAIL", currentSimilarity, bestCandidate.clickX, bestCandidate.clickY,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                currentMask.width, currentMask.height, edgeCount, if (isShapeOnlyMode) "ТОЛЬКО_ФОРМА" else "ГИБРИД"))
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                tvLiveScoreBadge?.text = "$scorePct% ${if (isPass) "[PASS]" else "[FAIL]"}"
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                tvLiveScoreBadge?.setTextColor((if (isPass) "#34D399" else "#F04438").toColorInt())
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                previewLiveView?.bind(liveCrop, isMask = false, isCircle = isCircleShape)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                } else {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                AppLogger.log(context, "CV_CALIBRATION", String.format(Locale.US, "Калибровка: ЦЕЛЬ НЕ НАЙДЕНА (порог=%d%%, маска=%dx%d)", currentSimilarity, currentMask.width, currentMask.height))
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                tvLiveScoreBadge?.text = "0% [НЕТ ЦЕЛИ]"
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                tvLiveScoreBadge?.setTextColor("#F04438".toColorInt())
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                previewLiveView?.bind(null, isMask = false)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                }
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                candidatesCanvasView?.invalidate()
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                }
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                }

    private fun updateClickOffsetButtonText() {
        if (useCustomClickOffset) {
            val ox = clickOffsetX.roundToInt()
            val oy = clickOffsetY.roundToInt()
            val signX = if (ox >= 0) "+$ox" else "$ox"
            val signY = if (oy >= 0) "+$oy" else "$oy"
            btnClickOffsetToggle?.text = "ОФСЕТ: ВКЛ ($signX, $signY)"
        } else {
            btnClickOffsetToggle?.text = "ОФСЕТ: ЦЕНТР (ВЫКЛ)"
        }
    }

    fun show() {
        if (rootFrameLayout != null) return

        val root = FrameLayout(context).apply { setBackgroundColor(Color.TRANSPARENT) }
        rootFrameLayout = root

        candidatesCanvasView = CandidatesCanvasView(context)
        root.addView(candidatesCanvasView, FrameLayout.LayoutParams(-1, -1))

        val cardW = dp(336).coerceAtMost((dm.widthPixels * 0.96f).toInt())
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8161B22".toColorInt(), "#F80D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(16f)
                setStroke(dp(1), "#C084FC".toColorInt())
            }
            val p = dp(10)
            setPadding(p, p, p, p)
            elevation = dpF(24f)
        }


        // [V22.0] Просторная шапка: заголовок, Студия и Авто без обрезания кнопок
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }

        val analysis = TemplateMorphologyClassifier.analyze(rawTemplateBitmap)
        val tvTitle = TextView(context).apply {
            text = "РЕДАКТОР [${analysis.archetype.name.take(8)}]"
            setTextColor("#C084FC".toColorInt())
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }
        headerRow.addView(tvTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnStudio = Button(context).apply {
            text = "МАСКА"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#1F6FEB".toColorInt())
                cornerRadius = dpF(4f)
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(3), dp(6), dp(3))
            setOnClickListener {
                val currentMask = getActiveTemplateBitmap()
                MagicWandEditorOverlay(
                    context = context,
                    overlayWindowManager = overlayWindowManager,
                    rawBitmap = rawTemplateBitmap,
                    initialMask = currentMask,
                    onApplied = { editedMask ->
                        customEditedMask = editedMask
                        activeGeneratedMask = editedMask
                        previewMaskView?.bind(editedMask, isMask = true, isCircle = isCircleShape)
                        reevaluateMatching()
                    },
                    onCancelled = {}
                ).show()
            }
        }
        headerRow.addView(btnStudio, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(26)).apply { marginEnd = dp(4) })

        // [V180.0] Интерактивная справка и обучение в редакторе шаблона (CalibrationOverlay)
        val btnTut = Button(context).apply {
            text = "[?] СПРАВКА"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(3), dp(6), dp(3))
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#1E293B".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            setOnClickListener {
                InteractiveTutorialOverlay(
                    context = context,
                    overlayWindowManager = overlayWindowManager,
                    mode = InteractiveTutorialOverlay.TutorialMode.CALIBRATION,
                    hostViewProvider = { rootFrameLayout }
                ).show()
            }
        }
        headerRow.addView(btnTut, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(26)).apply { marginEnd = dp(4) })

        val btnAuto = Button(context).apply {
            text = "АВТО"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#34D399".toColorInt(), "#059669".toColorInt())
            ).apply {
                cornerRadius = dpF(4f)
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(3), dp(6), dp(3))
            setOnClickListener {
                val sw = screenshot.width
                val sh = screenshot.height
                val sPixels = PixelBufferPool.obtain(sw * sh)
                try {
                    screenshot.getPixels(sPixels, 0, sw, 0, 0, sw, sh)
                    val baseSafeX = if (anchorCropX > 0) anchorCropX else (sw / 2)
                    val baseSafeY = if (anchorCropY > 0) anchorCropY else (sh / 2)

                    val opt = SmartMaskEngine.autoOptimizeGlyphSegmentationFast(
                        rawTemplate = rawTemplateBitmap,
                        screenshotPixels = sPixels,
                        sw = sw,
                        sh = sh,
                        anchorX = baseSafeX,
                        anchorY = baseSafeY,
                        isCircle = isCircleShape,
                        isShapeOnly = isShapeOnlyMode
                    )

                    customEditedMask = null
                    currentShapeExpansion = opt.optimalExpansion
                    currentPaddingOffset = opt.optimalPadding
                    currentSimilarity = opt.bestSimilarity
                    isAutoContourMode = opt.useContourMode

                    sbExpansion?.progress = currentShapeExpansion
                    sbPadding?.progress = currentPaddingOffset + 5
                    sbSimilarity?.progress = currentSimilarity

                    tvExpansionValue?.text = "$currentShapeExpansion%"
                    tvPaddingValue?.text = "${currentPaddingOffset}px"
                    tvSimilarityValue?.text = "$currentSimilarity%"

                    Toast.makeText(context, opt.strategyExplanation, Toast.LENGTH_SHORT).show()
                    reevaluateMatching()
                } finally {
                    PixelBufferPool.release(sPixels)
                }
            }
        }

        headerRow.addView(btnAuto, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(26)))
        card.addView(headerRow)

        // [V22.0] Выделенная аккуратная панель управления зонами поиска (ROI)
        val roiBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }

        val tvRoiLabel = TextView(context).apply {
            text = "ЗОНЫ ПОИСКА (ROI):"
            textSize = 7.5f
            typeface = Typeface.MONOSPACE
            setTextColor("#94A3B8".toColorInt())
        }
        roiBar.addView(tvRoiLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnRoiAdd = Button(context).apply {
            text = "+ ROI ЗОНА"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#38BDF8".toColorInt())
            background = GradientDrawable().apply {
                setColor("#1E293B".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener {
                val sw = screenshot.width; val sh = screenshot.height
                val rw = (rawTemplateBitmap.width * 2.2f).toInt().coerceIn(dp(60), sw / 2)
                val rh = (rawTemplateBitmap.height * 2.2f).toInt().coerceIn(dp(60), sh / 2)
                val cx = anchorCropX.coerceIn(rw / 2, sw - rw / 2)
                val cy = anchorCropY.coerceIn(rh / 2, sh - rh / 2)
                activeRoiZones.add(Rect(cx - rw / 2, cy - rh / 2, cx + rw / 2, cy + rh / 2))
                vibrate(20L)
                candidatesCanvasView?.invalidate()
                reevaluateMatching()
            }
        }
        roiBar.addView(btnRoiAdd, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(24)).apply { marginEnd = dp(4) })

        val btnRoiClear = Button(context).apply {
            text = "СБРОС"
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#94A3B8".toColorInt())
            background = GradientDrawable().apply {
                setColor("#161B22".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener {
                activeRoiZones.clear()
                vibrate(15L)
                candidatesCanvasView?.invalidate()
                reevaluateMatching()
            }
        }
        roiBar.addView(btnRoiClear, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(24)))
        card.addView(roiBar)

        val previewRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor("#161B22".toColorInt())
                cornerRadius = dpF(8f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            val p = dp(6)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
        }

        // [V20.1] Отображение оригинального сырого кадра
        val rawContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnClickListener { btnStudio.performClick() }
        }
        val tvRawLabel = TextView(context).apply {
            text = "СЫРОЙ ЭТАЛОН"
            textSize = 7.5f
            typeface = Typeface.MONOSPACE
            setTextColor("#38BDF8".toColorInt())
        }
        rawContainer.addView(tvRawLabel)
        previewRawView = MaskPreviewView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50))
            bind(rawTemplateBitmap, isMask = false, isCircle = false)
        }
        rawContainer.addView(previewRawView)
        previewRow.addView(rawContainer, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val maskContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnClickListener { btnStudio.performClick() }
        }
        val tvMaskLabel = TextView(context).apply {
            text = "МАСКА ИИ"
            textSize = 7.5f
            typeface = Typeface.MONOSPACE
            setTextColor("#38BDF8".toColorInt())
        }
        maskContainer.addView(tvMaskLabel)
        previewMaskView = MaskPreviewView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50))
        }
        maskContainer.addView(previewMaskView)
        previewRow.addView(maskContainer, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))


        val centerScoreLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val tvVs = TextView(context).apply {
            text = "СХОДИМОСТЬ"
            textSize = 7f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#94A3B8".toColorInt())
        }
        centerScoreLayout.addView(tvVs)
        val tvScore = TextView(context).apply {
            text = "---%"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            includeFontPadding = false
            setTextColor("#34D399".toColorInt())
        }
        tvLiveScoreBadge = tvScore
        centerScoreLayout.addView(tvScore)
        previewRow.addView(centerScoreLayout, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f))

        val liveContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val tvLiveLabel = TextView(context).apply {
            text = "ЭКРАН 1:1"
            textSize = 7.5f
            typeface = Typeface.MONOSPACE
            setTextColor("#C084FC".toColorInt())
        }
        liveContainer.addView(tvLiveLabel)
        previewLiveView = MaskPreviewView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
        }
        liveContainer.addView(previewLiveView)
        previewRow.addView(liveContainer, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        card.addView(previewRow)

        // [V32.0] Просторная 2x2 матрица режимов: полное исключение обрезания текста
        val modesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(4))
        }

        val modesRow1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(4))
        }

        val btnEngineToggle = Button(context).apply {
            text = if (isNeuralEngineMode) "AI ⇄ OPENCV" else "OPENCV ⇄ AI"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(if (isNeuralEngineMode) "#312E81".toColorInt() else "#1A142E".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), if (isNeuralEngineMode) "#818CF8".toColorInt() else "#38BDF8".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                isNeuralEngineMode = !isNeuralEngineMode
                text = if (isNeuralEngineMode) "AI ⇄ OPENCV" else "OPENCV ⇄ AI"
                (background as? GradientDrawable)?.apply {
                    setColor(if (isNeuralEngineMode) "#312E81".toColorInt() else "#1A142E".toColorInt())
                    setStroke(dp(1), if (isNeuralEngineMode) "#818CF8".toColorInt() else "#38BDF8".toColorInt())
                }
                reevaluateMatching()
            }
        }
        modesRow1.addView(btnEngineToggle, LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(4) })

        btnContourToggle = Button(context).apply {
            text = if (isAutoContourMode) "КОНТУР" else "КАРТИНКА"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (isAutoContourMode) intArrayOf("#34D399".toColorInt(), "#059669".toColorInt())
                else intArrayOf("#334155".toColorInt(), "#1E293B".toColorInt())
            ).apply {
                cornerRadius = dpF(4f)
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                isAutoContourMode = !isAutoContourMode
                text = if (isAutoContourMode) "КОНТУР" else "КАРТИНКА"
                setTextColor(Color.WHITE)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    if (isAutoContourMode) intArrayOf("#34D399".toColorInt(), "#059669".toColorInt())
                    else intArrayOf("#334155".toColorInt(), "#1E293B".toColorInt())
                ).apply {
                    cornerRadius = dpF(4f)
                }
                customEditedMask = null
                reevaluateMatching()
            }
        }
        modesRow1.addView(btnContourToggle, LinearLayout.LayoutParams(0, dp(30), 1f))
        modesContainer.addView(modesRow1)

        val modesRow2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        btnShapeOnlyToggle = Button(context).apply {
            text = if (isShapeOnlyMode) "ФОРМА" else "ГИБРИД"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (isShapeOnlyMode) intArrayOf("#F59E0B".toColorInt(), "#D97706".toColorInt())
                else intArrayOf("#38BDF8".toColorInt(), "#0284C7".toColorInt())
            ).apply {
                cornerRadius = dpF(4f)
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                isShapeOnlyMode = !isShapeOnlyMode
                text = if (isShapeOnlyMode) "ФОРМА" else "ГИБРИД"
                setTextColor(Color.WHITE)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    if (isShapeOnlyMode) intArrayOf("#F59E0B".toColorInt(), "#D97706".toColorInt())
                    else intArrayOf("#38BDF8".toColorInt(), "#0284C7".toColorInt())
                ).apply {
                    cornerRadius = dpF(4f)
                }
                reevaluateMatching()
            }
        }
        modesRow2.addView(btnShapeOnlyToggle, LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(4) })

        btnCircleToggle = Button(context).apply {
            text = if (isCircleShape) "КРУГ" else "КВАДРАТ"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (isCircleShape) intArrayOf("#C084FC".toColorInt(), "#7C3AED".toColorInt())
                else intArrayOf("#334155".toColorInt(), "#1E293B".toColorInt())
            ).apply {
                cornerRadius = dpF(4f)
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                isCircleShape = !isCircleShape
                text = if (isCircleShape) "КРУГ" else "КВАДРАТ"
                setTextColor(Color.WHITE)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    if (isCircleShape) intArrayOf("#C084FC".toColorInt(), "#7C3AED".toColorInt())
                    else intArrayOf("#334155".toColorInt(), "#1E293B".toColorInt())
                ).apply {
                    cornerRadius = dpF(4f)
                }
                customEditedMask = null
                reevaluateMatching()
            }
        }
        modesRow2.addView(btnCircleToggle, LinearLayout.LayoutParams(0, dp(30), 1f))
        modesContainer.addView(modesRow2)
        card.addView(modesContainer)

        fun createSliderRow(label: String, initialVal: String): Pair<TextView, SeekBar> {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(1), 0, dp(1))
            }
            val tvLbl = TextView(context).apply {
                text = label
                textSize = 8f
                includeFontPadding = false
                setTextColor("#8B949E".toColorInt())
                layoutParams = LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            row.addView(tvLbl)

            val tvVal = TextView(context).apply {
                text = initialVal
                textSize = 8.5f
                typeface = Typeface.MONOSPACE
                includeFontPadding = false
                setTextColor("#38BDF8".toColorInt())
                layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            row.addView(tvVal)

            val sb = SeekBar(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(sb)
            card.addView(row)
            return Pair(tvVal, sb)
        }

        val (tvExp, sbExp) = createSliderRow("Охват формы:", "$currentShapeExpansion%")
        tvExpansionValue = tvExp
        sbExpansion = sbExp
        sbExp.max = 60
        sbExp.progress = currentShapeExpansion
        sbExp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentShapeExpansion = progress
                    tvExp.text = "$currentShapeExpansion%"
                    customEditedMask = null
                    reevaluateMatching()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val (tvPad, sbPad) = createSliderRow("Отступ краев:", "${currentPaddingOffset}px")
        tvPaddingValue = tvPad
        sbPadding = sbPad
        sbPad.max = 15
        sbPad.progress = currentPaddingOffset + 5
        sbPad.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentPaddingOffset = progress - 5
                    tvPad.text = "${currentPaddingOffset}px"
                    customEditedMask = null
                    reevaluateMatching()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val simRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }

        val tvSim = TextView(context).apply {
            text = "ПОРОГ: $currentSimilarity%"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor("#FB923C".toColorInt())
        }
        tvSimilarityValue = tvSim
        simRow.addView(tvSim, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnSimMinus = Button(context).apply {
            text = "-5%"
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(4), dp(3), dp(4), dp(3))
            setOnClickListener {
                currentSimilarity = (currentSimilarity - 5).coerceAtLeast(35)
                tvSim.text = "ПОРОГ: $currentSimilarity%"
                reevaluateMatching()
            }
        }
        simRow.addView(btnSimMinus, LinearLayout.LayoutParams(dp(36), dp(24)).apply { marginEnd = dp(2) })

        val btnSimPlus = Button(context).apply {
            text = "+5%"
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(4), dp(3), dp(4), dp(3))
            setOnClickListener {
                currentSimilarity = (currentSimilarity + 5).coerceAtMost(98)
                tvSim.text = "ПОРОГ: $currentSimilarity%"
                reevaluateMatching()
            }
        }
        sbSimilarity = SeekBar(context).apply {
            max = 98
            progress = currentSimilarity
            visibility = View.GONE
        }
        simRow.addView(btnSimPlus, LinearLayout.LayoutParams(dp(36), dp(24)))
        card.addView(simRow)

        val clickControlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }

        btnClickOffsetToggle = Button(context).apply {
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#21262D".toColorInt(), "#161B22".toColorInt())
            ).apply {
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                useCustomClickOffset = !useCustomClickOffset
                if (!useCustomClickOffset) {
                    clickOffsetX = 0f
                    clickOffsetY = 0f
                }
                updateClickOffsetButtonText()
                candidatesCanvasView?.invalidate()
            }
        }
        updateClickOffsetButtonText()
        clickControlRow.addView(btnClickOffsetToggle, LinearLayout.LayoutParams(0, dp(32), 1.5f).apply { marginEnd = dp(4) })


        val btnTestClick = Button(context).apply {
            text = "ТЕСТ КЛИКА"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.BLACK)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#FBBF24".toColorInt(), "#F59E0B".toColorInt())
            ).apply {
                cornerRadius = dpF(4f)
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                val best = currentCandidates.firstOrNull()
                if (best == null) {
                    Toast.makeText(context, "Базовый шаблон не обнаружен на экране", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // [V23.2] Валидация составного шаблона: клик выполняется ТОЛЬКО при совпадении ВСЕХ проверочных меток
                if (coVerificationTemplates.isNotEmpty()) {
                    val sPixels = PixelBufferPool.obtain(screenshot.width * screenshot.height)
                    try {
                        screenshot.getPixels(sPixels, 0, screenshot.width, 0, 0, screenshot.width, screenshot.height)
                        for (chkPath in coVerificationTemplates) {
                            val chkBmp = templateRepository.getTemplate(chkPath) ?: continue
                            val chkMatches = TemplateMatchingEngine.findTemplateFastCascade(
                                sPixels, screenshot.width, screenshot.height, chkBmp,
                                minSimilarityPercent = 65, templatePath = chkPath, enableL0Cache = false
                            )
                            if (chkMatches.isEmpty() || chkMatches.maxOf { it.score } < 0.65f) {
                                vibrate(60L)
                                Toast.makeText(context, "Проверочный шаблон '${File(chkPath).nameWithoutExtension}' не найден! Клик заблокирован.", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                        }
                    } finally {
                        PixelBufferPool.release(sPixels)
                    }
                }

                val finalX = best.clickX + (if (useCustomClickOffset) clickOffsetX else 0f)
                val finalY = best.clickY + (if (useCustomClickOffset) clickOffsetY else 0f)
                vibrate(30L)
                AppLogger.log(context, "CALIB_TEST", String.format(Locale.US, "ТЕСТ-КЛИК КАЛИБРОВКИ: цель '%s' score=%.1f%% -> клик в (%.0f, %.0f)",
                    best.templateName, best.score * 100f, finalX, finalY))
                TargetHighlightVisualizer.showSpringHighlight(
                    context, overlayWindowManager,
                    Rect(best.rectLeft, best.rectTop, best.rectRight, best.rectBottom), 1000L
                )
                thread {
                    AutoTapAccessibilityService.instance?.gestureDispatcher?.performClick(finalX, finalY, 60L)
                }
                Toast.makeText(context, "Все проверки пройдены! Клик в (${finalX.toInt()}, ${finalY.toInt()})", Toast.LENGTH_SHORT).show()
            }
        }
        clickControlRow.addView(btnTestClick, LinearLayout.LayoutParams(0, dp(28), 1f))
        card.addView(clickControlRow)

        // [V23.2] Панель составных проверочных шаблонов для гарантированного клика в офсет
        val coVerifRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }

        val btnAddCoVerif = Button(context).apply {
            text = "+ ШАБЛОН ПРОВЕРКИ (${coVerificationTemplates.size})"
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#38BDF8".toColorInt())
            background = GradientDrawable().apply {
                setColor("#1E293B".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                val allTpls = templateRepository.loadAllTemplates().filter { it.first != existingTemplatePath }
                if (allTpls.isEmpty()) {
                    Toast.makeText(context, "Нет других сохраненных шаблонов в библиотеке", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val names = allTpls.map { File(it.first).nameWithoutExtension }.toTypedArray()
                val dialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Добавить проверочный шаблон")
                    .setItems(names) { _, which ->
                        val selectedPath = allTpls[which].first
                        if (!coVerificationTemplates.contains(selectedPath)) {
                            coVerificationTemplates.add(selectedPath)
                            text = "+ ШАБЛОН ПРОВЕРКИ (${coVerificationTemplates.size})"
                            Toast.makeText(context, "Добавлен: ${names[which]}", Toast.LENGTH_SHORT).show()
                            reevaluateMatching()
                        }
                    }
                    .setNegativeButton("Отмена", null)
                    .create()
                dialog.window?.setType(overlayWindowManager.getOverlayType(false))
                dialog.show()
            }
        }
        coVerifRow.addView(btnAddCoVerif, LinearLayout.LayoutParams(0, dp(28), 1f).apply { marginEnd = dp(4) })

        val btnClearCoVerif = Button(context).apply {
            text = "СБРОС"
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#94A3B8".toColorInt())
            background = GradientDrawable().apply {
                setColor("#161B22".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                coVerificationTemplates.clear()
                btnAddCoVerif.text = "+ ШАБЛОН ПРОВЕРКИ (0)"
                Toast.makeText(context, "Проверочные шаблоны очищены", Toast.LENGTH_SHORT).show()
            }
        }
        coVerifRow.addView(btnClearCoVerif, LinearLayout.LayoutParams(dp(54), dp(28)))
        card.addView(coVerifRow)

        val folderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }

        val tvFolderLabel = TextView(context).apply {
            text = "ГРУППА:"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor("#8B949E".toColorInt())
            layoutParams = LinearLayout.LayoutParams(dp(50), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        folderRow.addView(tvFolderLabel)

        val etFolder = EditText(context).apply {
            hint = "default"
            val curFolder = if (existingTemplatePath != null) File(existingTemplatePath).parentFile?.name ?: "default" else "default"
            setText(curFolder)
            setTextColor(Color.WHITE)
            setHintTextColor("#8B949E".toColorInt())
            textSize = 10f
            background = GradientDrawable().apply {
                setColor("#161B22".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            val p = dp(4)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(28), 1f)
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    v.requestFocus()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        v.windowInsetsController?.show(WindowInsets.Type.ime())
                    }
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    @Suppress("DEPRECATION")
                    imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }
                false
            }
        }
        folderRow.addView(etFolder)
        card.addView(folderRow)

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        val btnSave = Button(context).apply {
            text = "СОХРАНИТЬ ШАБЛОН"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#10B981".toColorInt())
                cornerRadius = dpF(6f)
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener {
                val folderName = etFolder.text.toString().trim().ifEmpty { "default" }
                File(context.filesDir, "templates/$folderName").mkdirs()

                val finalMask = getActiveTemplateBitmap()

                                val bestCand = currentCandidates.firstOrNull()
                val finalCalibX = bestCand?.clickX?.toInt() ?: (anchorCropX + finalMask.width / 2)
                val finalCalibY = bestCand?.clickY?.toInt() ?: (anchorCropY + finalMask.height / 2)

                                val metaObj = JSONObject().apply {
                    put("similarityPercent", currentSimilarity.coerceAtLeast(60))
                    put("shapeExpansion", currentShapeExpansion)
                    put("paddingOffsetPx", currentPaddingOffset)
                    put("isContourMode", isAutoContourMode)
                    put("isShapeOnlyMode", isShapeOnlyMode)
                    put("isCircleShape", isCircleShape)
                    put("useCustomClickOffset", useCustomClickOffset)
                    put("clickOffsetX", clickOffsetX.toDouble())
                    put("clickOffsetY", clickOffsetY.toDouble())

                    put("calibratedX", finalCalibX)
                    put("calibratedY", finalCalibY)
                    put("updatedAt", System.currentTimeMillis())
                    val verifArr = org.json.JSONArray()
                    coVerificationTemplates.forEach { verifArr.put(it) }
                    put("coVerificationTemplates", verifArr)
                    }

                                // [V12.1] Синхронный кроп сырого кадра для 100% совпадения габаритов с маской
                val finalRawCropped = try {
                    val safeCropW = finalMask.width.coerceIn(1, rawTemplateBitmap.width)
                    val safeCropH = finalMask.height.coerceIn(1, rawTemplateBitmap.height)
                    val safeCropX = currentCropOffsetX.coerceIn(0, rawTemplateBitmap.width - safeCropW)
                    val safeCropY = currentCropOffsetY.coerceIn(0, rawTemplateBitmap.height - safeCropH)
                    Bitmap.createBitmap(rawTemplateBitmap, safeCropX, safeCropY, safeCropW, safeCropH)
                } catch (_: Exception) {
                    rawTemplateBitmap
                }

                val tPath = templateRepository.saveTemplate(
                    folderName = folderName,
                    existingPath = existingTemplatePath,
                    maskBmp = finalMask,
                    rawBmp = finalRawCropped,
                    metadata = metaObj
                )

                val best = currentCandidates.firstOrNull()
                val targetX = if (existingAction != null && existingAction.posX > 10f && best == null) existingAction.posX
                              else (best?.clickX?.toFloat() ?: (anchorCropX + finalMask.width / 2f).coerceIn(100f, dm.widthPixels - 100f))
                val targetY = if (existingAction != null && existingAction.posY > 10f && best == null) existingAction.posY
                              else (best?.clickY?.toFloat() ?: (anchorCropY + finalMask.height / 2f).coerceIn(150f, dm.heightPixels - 150f))



                val firstRoi = activeRoiZones.firstOrNull()
                val updatedRoiAnchors: List<Point2D> = activeRoiZones.map { zone -> Point2D(zone.centerX().toFloat(), zone.centerY().toFloat()) }
                val action = (existingAction ?: MacroAction(id = targetStepId ?: 1, type = ActionType.TRIGGER)).copy(
                    type = existingAction?.type ?: ActionType.TRIGGER,
                    posX = targetX,
                    posY = targetY,
                    templatePath = tPath,
                    similarityPercent = currentSimilarity,
                    shapeExpansion = currentShapeExpansion,
                    paddingOffsetPx = currentPaddingOffset,
                    isContourMode = isAutoContourMode,
                    isShapeOnlyMode = isShapeOnlyMode,
                    isCircleShape = isCircleShape,
                    isNeuralEngine = isNeuralEngineMode,
                    useCustomClickOffset = useCustomClickOffset,
                    clickOffsetX = clickOffsetX,
                    clickOffsetY = clickOffsetY,
                    roiLeft = firstRoi?.left,
                    roiTop = firstRoi?.top,
                    roiRight = firstRoi?.right,
                    roiBottom = firstRoi?.bottom,
                    primaryAnchorPoints = if (updatedRoiAnchors.isNotEmpty()) updatedRoiAnchors else (existingAction?.primaryAnchorPoints ?: emptyList())
                )
                onFinished(action, tPath)
                dismiss()
            }
        }
        actionRow.addView(btnSave, LinearLayout.LayoutParams(0, dp(34), 1.5f).apply { marginEnd = dp(4) })

        val btnCancel = Button(context).apply {
            text = "ОТМЕНА"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor("#F04438".toColorInt())
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#F04438".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener {
                onCancelled()
                dismiss()
            }
        }
        actionRow.addView(btnCancel, LinearLayout.LayoutParams(0, dp(34), 1f))
        card.addView(actionRow)

        headerRow.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0f; private var initY = 0f
            private var touchX = 0f; private var touchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = card.translationX; initY = card.translationY
                        touchX = event.rawX; touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        card.translationX = initX + (event.rawX - touchX)
                        card.translationY = initY + (event.rawY - touchY)
                        return true
                    }
                }
                return false
            }
        })

        // Умное размещение карточки: исключаем перекрытие шаблона на экране
        val targetCenterY = anchorCropY + rawTemplateBitmap.height / 2f
        val isTargetInTopHalf = targetCenterY < dm.heightPixels * 0.48f

        val cardGravity = if (isTargetInTopHalf) {
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        } else {
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        val cardMarginTop = if (!isTargetInTopHalf) dp(36) else 0
        val cardMarginBottom = if (isTargetInTopHalf) dp(28) else 0

        root.addView(card, FrameLayout.LayoutParams(cardW, FrameLayout.LayoutParams.WRAP_CONTENT, cardGravity).apply {
            topMargin = cardMarginTop
            bottomMargin = cardMarginBottom
        })



        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowManager.getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        root.fitsSystemWindows = false
        overlayWindowManager.addViewSafe(root, params)

        reevaluateMatching()
    }

    fun dismiss() {
        evalJob?.cancel()
        rootFrameLayout?.let { overlayWindowManager.removeViewSafe(it) }
        rootFrameLayout = null
        if (!screenshot.isRecycled) screenshot.recycle()
        if (!rawTemplateBitmap.isRecycled) rawTemplateBitmap.recycle()
        activeGeneratedMask?.let { if (!it.isRecycled) it.recycle() }
        customEditedMask?.let { if (!it.isRecycled) it.recycle() }
    }
}
