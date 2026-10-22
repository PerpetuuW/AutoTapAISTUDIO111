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
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer

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
import kotlin.math.abs
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
    private var isShapeOnlyMode: Boolean = existingAction?.isShapeOnlyMode ?: false
    private var isCircleShape: Boolean = existingAction?.isCircleShape ?: false
    private var isNeuralEngineMode: Boolean = existingAction?.isNeuralEngine ?: false
    private var isSmartColorMode: Boolean = existingAction?.colorDeltaEMode ?: true
    private var activeAnchorX: Int = anchorCropX
    private var activeAnchorY: Int = anchorCropY
    private var btnSmartColorToggle: Button? = null

    private var useCustomClickOffset: Boolean = existingAction?.useCustomClickOffset ?: false
    private var clickOffsetX: Float = existingAction?.clickOffsetX ?: 0f
    private var clickOffsetY: Float = existingAction?.clickOffsetY ?: 0f

    private var isDraggingClickPoint = false
    private var customEditedMask: Bitmap? = null
    private val coVerificationTemplates = mutableListOf<String>()
    private var activeGeneratedMask: Bitmap? = null
    private var currentWorkingRaw: Bitmap = rawTemplateBitmap
    private var currentCropShiftX = 0
    private var currentCropShiftY = 0
    private var currentCropDeltaW = 0
    private var currentCropDeltaH = 0
    private var cropShiftStep = 1
    private var currentCropOffsetX = 0
    private var currentCropOffsetY = 0
    private var currentCandidates = mutableListOf<MatchCandidate>()
    private var activeRoiZones = mutableListOf<Rect>()


    private var previewRawView: MaskPreviewView? = null
    private var previewMaskView: MaskPreviewView? = null
    private var previewLiveView: MaskPreviewView? = null
    private var tvLiveScoreBadge: TextView? = null
    private var pBarReevaluate: ProgressBar? = null

    private var tvExpansionValue: TextView? = null
    private var tvPaddingValue: TextView? = null
    private var tvSimilarityValue: TextView? = null
    private var sbExpansion: SeekBar? = null
    private var sbPadding: SeekBar? = null
    private var sbSimilarity: SeekBar? = null
    private var isUserCustomSimilarity = false
    private var btnContourToggle: Button? = null
    private var btnShapeOnlyToggle: Button? = null
    private var btnCircleToggle: Button? = null
    private var btnClickOffsetToggle: Button? = null
    private var tvDownscaleBadgeRef: TextView? = null
    private var userDownscaleFactor: Int = existingAction?.downscaleFactor ?: 0
    private var effectiveDownscaleFactor: Int = 1

    private fun calculateOptimalDownscaleFactor(w: Int, h: Int): Int {
        val minDim = minOf(w, h)
        val area = w * h
        val isMicro = minDim < 28 || area <= 1600
        val isThin = (h in 1..20 && w >= 20) || (w in 1..20 && h >= 20)
        return when {
            isMicro || isThin -> 1
            minDim < 64 -> 2
            minDim < 140 -> 3
            else -> 4
        }
    }

    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    init {
        existingAction?.let { act ->
            if (act.roiLeft != null && act.roiTop != null && act.roiRight != null && act.roiBottom != null) {
                activeRoiZones.add(Rect(act.roiLeft, act.roiTop, act.roiRight, act.roiBottom))
            }
        }
        if (userDownscaleFactor == 0 && existingTemplatePath != null) {
            try {
                val f = File(existingTemplatePath)
                val metaFile = File(f.parentFile, "${f.nameWithoutExtension}.json")
                if (metaFile.exists()) {
                    val m = JSONObject(metaFile.readText())
                    val d = m.optInt("optimalDownscaleFactor", m.optInt("downscaleFactor", 0))
                    if (d in 1..4) {
                        userDownscaleFactor = d
                    }
                }
            } catch (_: Exception) {}
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
                    currentSimilarity = (opt.bestSimilarity - 5).coerceIn(55, 95)
                    isAutoContourMode = opt.useContourMode
                } finally {
                    PixelBufferPool.release(sPixels)
                }
            } catch (_: Exception) {}
        }

        currentCandidates.addAll(initialCandidates)
        if (existingTemplatePath != null) {
            templateRepository.getTemplateMetadata(existingTemplatePath)?.let { meta ->
                (meta["colorDeltaEMode"] as? Boolean ?: meta["isSmartColorMode"] as? Boolean)?.let {
                    isSmartColorMode = it
                }
                (meta["roiZones"] as? org.json.JSONArray)?.let { arr ->
                    if (activeRoiZones.isEmpty()) {
                        for (i in 0 until arr.length()) {
                            val z = arr.optJSONObject(i)
                            if (z != null) {
                                activeRoiZones.add(Rect(z.getInt("left"), z.getInt("top"), z.getInt("right"), z.getInt("bottom")))
                            }
                        }
                    }
                }
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
                                                                    val badgeSuffix = if (isSmartColorMode) " ΔE" else "%"
                                                                    val scoreBadgeText = "$pct$badgeSuffix"
                                                                    val textW = textScorePaint.measureText(scoreBadgeText)
                                                                    val badgeH = dpF(16f)
                                                                    val badgeTop = (top - badgeH - dpF(2f)).coerceAtLeast(0f)
                                                                    badgeBgPaint.color = if (isSmartColorMode) {
                                                                        if (idx == 0) "#E64F46E5".toColorInt() else "#E6312E81".toColorInt()
                                                                    } else {
                                                                        if (idx == 0) "#E610B981".toColorInt() else "#E60284C7".toColorInt()
                                                                    }
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
                                        activeAnchorX = selected.rectLeft
                                        activeAnchorY = selected.rectTop
                                        val pct = (selected.score * 100f).toInt().coerceIn(0, 100)
                                        tvLiveScoreBadge?.text = "$pct%"
                                        try {
                                            val sx = selected.rectLeft.coerceIn(0, (screenshot.width - 1).coerceAtLeast(0))
                                            val sy = selected.rectTop.coerceIn(0, (screenshot.height - 1).coerceAtLeast(0))
                                            val sw = (selected.rectRight - selected.rectLeft).coerceIn(1, (screenshot.width - sx).coerceAtLeast(1))
                                            val sh = (selected.rectBottom - selected.rectTop).coerceIn(1, (screenshot.height - sy).coerceAtLeast(1))
                                            val liveCrop = Bitmap.createBitmap(screenshot, sx, sy, sw, sh)
                                            previewLiveView?.bind(liveCrop, isMask = false, isCircle = isCircleShape)
                                        } catch (_: Exception) {}
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

    private fun updateWorkingCropBitmap() {
        customEditedMask = null
        if (!screenshot.isRecycled && screenshot.width > 0 && screenshot.height > 0) {
            val baseW = rawTemplateBitmap.width
            val baseH = rawTemplateBitmap.height
            val targetW = (baseW + currentCropDeltaW).coerceIn(8, screenshot.width)
            val targetH = (baseH + currentCropDeltaH).coerceIn(8, screenshot.height)
            val startX = (anchorCropX + currentCropShiftX).coerceIn(0, (screenshot.width - targetW).coerceAtLeast(0))
            val startY = (anchorCropY + currentCropShiftY).coerceIn(0, (screenshot.height - targetH).coerceAtLeast(0))
            try {
                val safeW = targetW.coerceAtMost(screenshot.width - startX)
                val safeH = targetH.coerceAtMost(screenshot.height - startY)
                if (safeW > 0 && safeH > 0) {
                    val newRaw = Bitmap.createBitmap(screenshot, startX, startY, safeW, safeH)
                    if (currentWorkingRaw != rawTemplateBitmap && !currentWorkingRaw.isRecycled) {
                        currentWorkingRaw.recycle()
                    }
                    currentWorkingRaw = newRaw
                }
            } catch (_: Exception) {}
        }
        previewRawView?.bind(currentWorkingRaw, isMask = false, isCircle = false)
    }

    private fun getActiveTemplateBitmap(): Bitmap {
        customEditedMask?.let { return it }
        val res = SmartMaskEngine.generateContourMaskWithCrop(
            src = currentWorkingRaw,
            shapeExpansion = currentShapeExpansion,
            paddingOffsetPx = currentPaddingOffset,
            isCircle = isCircleShape,
            isRawMode = !isAutoContourMode,
            isShapeOnly = isShapeOnlyMode
        )
        currentCropOffsetX = res.cropOffsetX + currentCropShiftX
        currentCropOffsetY = res.cropOffsetY + currentCropShiftY
        activeGeneratedMask = res.bitmap
        return res.bitmap
    }

    private fun showFolderSelectionModal(targetEditText: EditText) {
        val root = rootFrameLayout ?: return
        val folders = (templateRepository as? com.example.autotap.infrastructure.storage.TemplateRepositoryImpl)?.listFolders() ?: listOf("default")

        val modalOverlay = FrameLayout(context).apply {
            setBackgroundColor("#B30B0813".toColorInt())
            isClickable = true
            isFocusable = true
        }

        val dialogCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#1F2937".toColorInt(), "#111827".toColorInt())
            ).apply {
                cornerRadius = dpF(12f)
                setStroke(dp(1), "#8B5CF6".toColorInt())
            }
            val p = dp(12)
            setPadding(p, p, p, p)
            elevation = dpF(24f)
        }

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        val tvModalTitle = TextView(context).apply {
            text = "ВЫБОР ГРУППЫ ШАБЛОНОВ"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor("#A78BFA".toColorInt())
        }
        titleRow.addView(tvModalTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnCloseModal = createIconButton(VectorIconDrawer.IconType.CLOSE, "#1F2937", "#94A3B8", dp(24)) {
            root.removeView(modalOverlay)
        }
        titleRow.addView(btnCloseModal, LinearLayout.LayoutParams(dp(24), dp(24)))
        dialogCard.addView(titleRow)

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (folder in folders) {
            val count = try {
                File(context.filesDir, "templates/$folder").listFiles()?.count { it.extension == "png" && !it.name.contains("_mask") && !it.name.contains("_raw") } ?: 0
            } catch (_: Exception) { 0 }

            val itemRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(if (folder == targetEditText.text.toString().trim()) "#2E2250".toColorInt() else "#161B22".toColorInt())
                    cornerRadius = dpF(6f)
                    setStroke(dp(1), if (folder == targetEditText.text.toString().trim()) "#8B5CF6".toColorInt() else "#30363D".toColorInt())
                }
                val ip = dp(8)
                setPadding(ip, dp(6), ip, dp(6))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    targetEditText.setText(folder)
                    root.removeView(modalOverlay)
                }
            }

            val tvName = TextView(context).apply {
                text = folder
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            }
            itemRow.addView(tvName, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val tvBadge = TextView(context).apply {
                text = "$count шт."
                textSize = 7.5f
                setTextColor("#8B949E".toColorInt())
            }
            itemRow.addView(tvBadge)

            listContainer.addView(itemRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            })
        }

        scrollView.addView(listContainer)
        dialogCard.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(140)))

        modalOverlay.setOnClickListener {
            root.removeView(modalOverlay)
        }
        dialogCard.setOnClickListener { /* prevent dismissal when touching card */ }

        val cardParams = FrameLayout.LayoutParams(dp(260).coerceAtMost((dm.widthPixels * 0.9f).toInt()), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }
        modalOverlay.addView(dialogCard, cardParams)
        root.addView(modalOverlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun reevaluateMatching() {
        evalJob?.cancel()
        pBarReevaluate?.visibility = View.VISIBLE
        evalJob = overlayScope.launch {
            try {
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
                        val baseSafeX = if (anchorCropX in 0 until sw) anchorCropX else ((existingAction?.posX?.toInt() ?: (sw / 2)) - currentMask.width / 2)
                        val baseSafeY = if (anchorCropY in 0 until sh) anchorCropY else ((existingAction?.posY?.toInt() ?: (sh / 2)) - currentMask.height / 2)
                        val originX = (baseSafeX + currentCropOffsetX).coerceIn(0, sw - currentMask.width)
                        val originY = (baseSafeY + currentCropOffsetY).coerceIn(0, sh - currentMask.height)

                        val liveCropBmp = try {
                            Bitmap.createBitmap(screenshot, originX, originY, currentMask.width.coerceAtMost(sw - originX), currentMask.height.coerceAtMost(sh - originY))
                        } catch (_: Exception) { null }

                        val combinedBmp = if (liveCropBmp != null) {
                            TemplateMatchingEngine.combineMaskWithRawRgb(currentMask, liveCropBmp)
                        } else currentMask

                        val dynKey = "calib_${currentShapeExpansion}_${currentPaddingOffset}_${isAutoContourMode}_${isCircleShape}_${isShapeOnlyMode}_${liveCropBmp?.generationId ?: 0}"
                        val features = TemplateMatchingEngine.extractFeatures(combinedBmp, dynKey, "", currentSimilarity)
                        edgeCount = features?.shapeEdgeCount ?: 0

                        var peakScore = 0f
                        var peakX = originX
                        var peakY = originY

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
                                    val score = TemplateMatchingEngine.evaluateCandidateScore(
                                        sPixels, sw, sh, fx, fy, features, isShapeOnlyMode,
                                        useDeltaE = isSmartColorMode,
                                        selectedZones = activeRoiZones
                                    )
                                    if (score > peakScore) {
                                        peakScore = score
                                        peakX = fx
                                        peakY = fy
                                    }
                                }
                            }
                        }

                        if (peakScore > 0.40f && !isUserCustomSimilarity) {
                            val peakScorePct = (peakScore * 100).toInt()
                            val autoSimilarityWithMargin = (peakScorePct - 5).coerceIn(60, 95)
                            currentSimilarity = autoSimilarityWithMargin
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

                        val calculatedScale = calculateOptimalDownscaleFactor(currentMask.width, currentMask.height)
                        val calibDownscale = if (userDownscaleFactor in 1..4) userDownscaleFactor else calculatedScale
                        effectiveDownscaleFactor = calibDownscale
                        withContext(Dispatchers.Main) {
                            tvDownscaleBadgeRef?.text = "МАСШТАБ СЖАТИЯ: ${effectiveDownscaleFactor}x (${if (effectiveDownscaleFactor == 1) "1:1 МИКРО" else "1/${effectiveDownscaleFactor} ОТСЕВ"})"
                        }

                        val effectiveActionOverride = (existingAction ?: MacroAction(id = targetStepId ?: 1)).copy(
                            similarityPercent = currentSimilarity,
                            colorDeltaEMode = isSmartColorMode,
                            isShapeOnlyMode = isShapeOnlyMode,
                            downscaleFactor = calibDownscale,
                            roiLeft = activeRoiZones.firstOrNull()?.left,
                            roiTop = activeRoiZones.firstOrNull()?.top,
                            roiRight = activeRoiZones.firstOrNull()?.right,
                            roiBottom = activeRoiZones.firstOrNull()?.bottom,
                            primaryAnchorPoints = activeRoiZones.map { com.example.autotap.domain.model.Point2D(it.centerX().toFloat(), it.centerY().toFloat()) }
                        )
                        var cascadeMatches = if (isNeuralEngineMode) {
                            try {
                                com.example.autotap.infrastructure.vision.NeuralVisualMatcher.findMatches(
                                    screenshot = screenshot,
                                    template = currentMask,
                                    minSimilarityPercent = currentSimilarity.coerceAtLeast(40),
                                    actionOverride = effectiveActionOverride
                                )
                            } catch (_: Throwable) { emptyList() }
                        } else {
                            TemplateMatchingEngine.findTemplateFastCascade(
                                sPixels = sPixels, sw = sw, sh = sh, template = currentMask,
                                minSimilarityPercent = 60, templatePath = existingTemplatePath ?: "",
                                actionOverride = effectiveActionOverride, enableL0Cache = false, findAllMatches = true
                            ) { evalJob?.isCancelled == true }
                        }

                        if (userDownscaleFactor == 0 && calibDownscale > 1 && cascadeMatches.isEmpty() && localAnchorMatch == null && !isNeuralEngineMode) {
                            val fallbackOverride = effectiveActionOverride.copy(downscaleFactor = 1)
                            val fullResMatches = TemplateMatchingEngine.findTemplateFastCascade(
                                sPixels = sPixels, sw = sw, sh = sh, template = currentMask,
                                minSimilarityPercent = 60, templatePath = existingTemplatePath ?: "",
                                actionOverride = fallbackOverride, enableL0Cache = false, findAllMatches = true
                            ) { evalJob?.isCancelled == true }
                            if (fullResMatches.isNotEmpty()) {
                                cascadeMatches = fullResMatches
                                effectiveDownscaleFactor = 1
                                withContext(Dispatchers.Main) {
                                    tvDownscaleBadgeRef?.text = "МАСШТАБ СЖАТИЯ: 1x (1:1 АВТО-КОРРЕКЦИЯ)"
                                }
                            }
                        }

                        val candidateList = mutableListOf<MatchCandidate>()
                        if (localAnchorMatch != null) {
                            candidateList.add(localAnchorMatch)
                        }

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

                        if (activeRoiZones.isNotEmpty()) {
                            for (cIdx in candidateList.indices) {
                                val cCand = candidateList[cIdx]
                                val minDistToZone = activeRoiZones.minOf { z ->
                                    kotlin.math.hypot((cCand.clickX - z.centerX()).toDouble(), (cCand.clickY - z.centerY()).toDouble()).toFloat()
                                }
                                val isInsideZone = activeRoiZones.any { z -> z.contains(cCand.clickX, cCand.clickY) }
                                val distFactor = if (isInsideZone) 1.05f else (1.0f - (minDistToZone / (sw * 0.75f)).coerceIn(0f, 0.40f))
                                candidateList[cIdx] = cCand.copy(score = (cCand.score * distFactor).coerceIn(0f, 1f))
                            }
                        }
                        candidateList.sortByDescending { it.score }

                        val bestMatch = candidateList.firstOrNull()
                        val crop = if (bestMatch != null && !screenshot.isRecycled) {
                            val cX = bestMatch.rectLeft.coerceIn(0, sw - 1)
                            val cY = bestMatch.rectTop.coerceIn(0, sh - 1)
                            val cW = (bestMatch.rectRight - bestMatch.rectLeft).coerceIn(1, sw - cX)
                            val cH = (bestMatch.rectBottom - bestMatch.rectTop).coerceIn(1, sh - cY)
                            try {
                                Bitmap.createBitmap(screenshot, cX, cY, cW, cH)
                            } catch (_: Throwable) {
                                val safeBmp = Bitmap.createBitmap(cW, cH, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(safeBmp)
                                canvas.drawBitmap(screenshot, Rect(cX, cY, cX + cW, cY + cH), Rect(0, 0, cW, cH), null)
                                safeBmp
                            }
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
            } finally {
                pBarReevaluate?.visibility = View.INVISIBLE
            }
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
        val btnHideKeyboard = Button(context).apply {
            text = "СКРЫТЬ КЛАВИАТУРУ"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#312E81".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#6366F1".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = View.GONE
            setOnClickListener {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                val currentFocused = card.findFocus()
                val token = currentFocused?.windowToken ?: card.windowToken
                imm?.hideSoftInputFromWindow(token, 0)
                currentFocused?.clearFocus()
                card.clearFocus()
                visibility = View.GONE
            }
        }
        card.addView(btnHideKeyboard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)).apply {
            bottomMargin = dp(4)
        })

        card.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            card.getWindowVisibleDisplayFrame(r)
            val keypadHeight = dm.heightPixels - r.bottom
            val isFocused = card.findFocus() is EditText
            val isKeyboardOpen = keypadHeight > (dm.heightPixels * 0.15f)
            btnHideKeyboard.visibility = if (isKeyboardOpen || isFocused) View.VISIBLE else View.GONE
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
            tag = "HEADER"
        }

        val analysis = TemplateMorphologyClassifier.analyze(rawTemplateBitmap)
        val tvTitle = TextView(context).apply {
            text = "РЕДАКТОР [${analysis.archetype.name}]"
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
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
                    rawBitmap = currentWorkingRaw,
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
        val btnTut = createIconButton(VectorIconDrawer.IconType.HELP, "#21262D", "#38BDF8", dp(26)) {
            InteractiveTutorialOverlay(
                context = context,
                overlayWindowManager = overlayWindowManager,
                mode = InteractiveTutorialOverlay.TutorialMode.CALIBRATION,
                hostViewProvider = { rootFrameLayout }
            ).show()
        }
        headerRow.addView(btnTut, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(4) })

        val calibrationContentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        var isCardCollapsed = false
        val btnCollapse = createIconButton(VectorIconDrawer.IconType.PANEL_COLLAPSE, "#21262D", "#38BDF8", dp(26)) {
            isCardCollapsed = !isCardCollapsed
            calibrationContentLayout.visibility = if (isCardCollapsed) View.GONE else View.VISIBLE
            vibrate(20L)
        }
        headerRow.addView(btnCollapse, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(4) })

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
        card.addView(calibrationContentLayout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

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
                val bestC = currentCandidates.firstOrNull()
                val rawCx = bestC?.clickX?.toInt() ?: (if (anchorCropX > 0) anchorCropX + rawTemplateBitmap.width / 2 else sw / 2)
                val rawCy = bestC?.clickY?.toInt() ?: (if (anchorCropY > 0) anchorCropY + rawTemplateBitmap.height / 2 else sh / 2)
                val cx = rawCx.coerceIn(rw / 2, sw - rw / 2)
                val cy = rawCy.coerceIn(rh / 2, sh - rh / 2)
                activeRoiZones.add(Rect(cx - rw / 2, cy - rh / 2, cx + rw / 2, cy + rh / 2))
                if (!existingTemplatePath.isNullOrBlank()) {
                    com.example.autotap.infrastructure.vision.TemplateMatchingEngine.registerDiscoveredLocation(existingTemplatePath, cx, cy)
                }
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
            calibrationContentLayout.addView(roiBar)

            val pBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.INVISIBLE
            }
            pBarReevaluate = pBar
            calibrationContentLayout.addView(pBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).apply {
            topMargin = dp(2)
            bottomMargin = dp(2)
        })

        val previewRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            tag = "PREVIEW"
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

        calibrationContentLayout.addView(previewRow)

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
                if (isNeuralEngineMode) {
                    isShapeOnlyMode = false
                    btnShapeOnlyToggle?.isEnabled = false
                    btnShapeOnlyToggle?.text = "ГИБРИД"
                    btnShapeOnlyToggle?.background = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf("#475569".toColorInt(), "#334155".toColorInt())
                    ).apply {
                        cornerRadius = dpF(4f)
                    }
                } else {
                    btnShapeOnlyToggle?.isEnabled = true
                    btnShapeOnlyToggle?.text = if (isShapeOnlyMode) "ФОРМА" else "ГИБРИД"
                    btnShapeOnlyToggle?.background = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        if (isShapeOnlyMode) intArrayOf("#F59E0B".toColorInt(), "#D97706".toColorInt())
                        else intArrayOf("#38BDF8".toColorInt(), "#0284C7".toColorInt())
                    ).apply {
                        cornerRadius = dpF(4f)
                    }
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
                customEditedMask = null
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
                reevaluateMatching()
            }
        }
        modesRow1.addView(btnContourToggle, LinearLayout.LayoutParams(0, dp(30), 1f))
        modesContainer.addView(modesRow1)

        val modesRow2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        btnShapeOnlyToggle = Button(context).apply {
            isEnabled = !isNeuralEngineMode
            text = if (isNeuralEngineMode) "ГИБРИД" else (if (isShapeOnlyMode) "ФОРМА" else "ГИБРИД")
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (isNeuralEngineMode) intArrayOf("#475569".toColorInt(), "#334155".toColorInt())
                else if (isShapeOnlyMode) intArrayOf("#F59E0B".toColorInt(), "#D97706".toColorInt())
                else intArrayOf("#38BDF8".toColorInt(), "#0284C7".toColorInt())
            ).apply {
                cornerRadius = dpF(4f)
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                if (isNeuralEngineMode) return@setOnClickListener
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
                if (isShapeOnlyMode) {
                    isSmartColorMode = false
                    btnSmartColorToggle?.text = "ЦВЕТ: СТАНДАРТ"
                    btnSmartColorToggle?.background = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf("#334155".toColorInt(), "#1E293B".toColorInt())
                    ).apply { cornerRadius = dpF(4f) }
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
                reevaluateMatching()
            }
        }
        modesRow2.addView(btnCircleToggle, LinearLayout.LayoutParams(0, dp(30), 1f))
        modesContainer.addView(modesRow2)

        val modesRow3 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
        }

        btnSmartColorToggle = Button(context).apply {
            text = if (isSmartColorMode) "ЦВЕТ: CIELAB ΔE (УМНЫЙ)" else "ЦВЕТ: СТАНДАРТ"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (isSmartColorMode) intArrayOf("#4F46E5".toColorInt(), "#3730A3".toColorInt())
                else intArrayOf("#334155".toColorInt(), "#1E293B".toColorInt())
            ).apply {
                cornerRadius = dpF(4f)
                if (isSmartColorMode) setStroke(dp(1), "#818CF8".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                isSmartColorMode = !isSmartColorMode
                text = if (isSmartColorMode) "ЦВЕТ: CIELAB ΔE (УМНЫЙ)" else "ЦВЕТ: СТАНДАРТ"
                if (isSmartColorMode) {
                    isShapeOnlyMode = false
                    btnShapeOnlyToggle?.text = "ГИБРИД"
                    btnShapeOnlyToggle?.background = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf("#38BDF8".toColorInt(), "#0284C7".toColorInt())
                    ).apply { cornerRadius = dpF(4f) }
                }
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    if (isSmartColorMode) intArrayOf("#4F46E5".toColorInt(), "#3730A3".toColorInt())
                    else intArrayOf("#334155".toColorInt(), "#1E293B".toColorInt())
                ).apply {
                    cornerRadius = dpF(4f)
                    if (isSmartColorMode) setStroke(dp(1), "#818CF8".toColorInt())
                }
                reevaluateMatching()
            }
        }
        modesRow3.addView(btnSmartColorToggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)))
        modesContainer.addView(modesRow3)
        modesContainer.tag = "MODES"
        calibrationContentLayout.addView(modesContainer)

        fun createSliderRow(label: String, initialVal: String, sliderTag: String = ""): Pair<TextView, SeekBar> {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(1), 0, dp(1))
                if (sliderTag.isNotBlank()) tag = sliderTag
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
                if (sliderTag.isNotBlank()) tag = sliderTag
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(sb)
            calibrationContentLayout.addView(row)
            return Pair(tvVal, sb)
        }

        val (tvExp, sbExp) = createSliderRow("Охват формы:", "$currentShapeExpansion%", "CONTOUR")
        tvExpansionValue = tvExp
        sbExpansion = sbExp
        sbExp.max = 60
        sbExp.progress = currentShapeExpansion
        sbExp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    customEditedMask = null
                    currentShapeExpansion = progress
                    tvExp.text = "$currentShapeExpansion%"
                    reevaluateMatching()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Динамический адаптивный диапазон отступа (до +-50% от размера маски)
        val maskMinDimension = minOf(rawTemplateBitmap.width, rawTemplateBitmap.height)
        val adaptiveMaxPad = (maskMinDimension * 0.5f).toInt().coerceIn(12, 45)

        val (tvPad, sbPad) = createSliderRow("Отступ краев:", "${currentPaddingOffset}px", "CONTOUR")
        tvPaddingValue = tvPad
        sbPadding = sbPad
        sbPad.max = adaptiveMaxPad
        sbPad.progress = (currentPaddingOffset + 5).coerceIn(0, adaptiveMaxPad)
        sbPad.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    customEditedMask = null
                    currentPaddingOffset = progress - 5
                    tvPad.text = "${currentPaddingOffset}px"
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
            tag = "SIMILARITY"
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
                isUserCustomSimilarity = true
                currentSimilarity = (currentSimilarity - 5).coerceAtLeast(45)
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
                isUserCustomSimilarity = true
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
        calibrationContentLayout.addView(simRow)

        val cropControlBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor("#111827".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#1E293B".toColorInt())
            }
            val cp = dp(4)
            setPadding(cp, cp, cp, cp)
        }

        // [V182.0] D-Pad смещение кропа стрелочками (◀ ▶ ▲ ▼)
        val rowCropShift = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(3))
        }

        val tvCropShift = TextView(context).apply {
            text = "СМЕЩЕНИЕ X:${currentCropShiftX} Y:${currentCropShiftY}"
            textSize = 7.5f
            typeface = Typeface.MONOSPACE
            setTextColor("#38BDF8".toColorInt())
        }
        rowCropShift.addView(tvCropShift, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnStepToggle = Button(context).apply {
            text = "${cropShiftStep}px"
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(4), dp(2), dp(4), dp(2))
            setOnClickListener {
                cropShiftStep = if (cropShiftStep == 1) 5 else 1
                text = "${cropShiftStep}px"
            }
        }
        rowCropShift.addView(btnStepToggle, LinearLayout.LayoutParams(dp(32), dp(22)).apply { marginEnd = dp(3) })

        var tvCropSizeRef: TextView? = null

        fun createCropArrowBtn(symbol: String, dx: Int, dy: Int): Button {
            return Button(context).apply {
                text = symbol
                textSize = 8f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor("#1E293B".toColorInt())
                    cornerRadius = dpF(4f)
                    setStroke(dp(1), "#6366F1".toColorInt())
                }
                minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    currentCropShiftX = (currentCropShiftX + dx * cropShiftStep).coerceIn(-100, 100)
                    currentCropShiftY = (currentCropShiftY + dy * cropShiftStep).coerceIn(-100, 100)
                    tvCropShift.text = "СМЕЩЕНИЕ X:${currentCropShiftX} Y:${currentCropShiftY}"
                    updateWorkingCropBitmap()
                    tvCropSizeRef?.text = "РАЗМЕР: ${currentWorkingRaw.width}×${currentWorkingRaw.height}"
                    reevaluateMatching()
                }
            }
        }

        rowCropShift.addView(createCropArrowBtn("◀", -1, 0), LinearLayout.LayoutParams(dp(26), dp(22)).apply { marginEnd = dp(2) })
        rowCropShift.addView(createCropArrowBtn("▶", 1, 0), LinearLayout.LayoutParams(dp(26), dp(22)).apply { marginEnd = dp(2) })
        rowCropShift.addView(createCropArrowBtn("▲", 0, -1), LinearLayout.LayoutParams(dp(26), dp(22)).apply { marginEnd = dp(2) })
        rowCropShift.addView(createCropArrowBtn("▼", 0, 1), LinearLayout.LayoutParams(dp(26), dp(22)))

        cropControlBox.addView(rowCropShift)

        // [V182.0] Расширение и уменьшение кропа для детальной настройки
        val rowCropSize = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvCropSize = TextView(context).apply {
            text = "РАЗМЕР: ${currentWorkingRaw.width}×${currentWorkingRaw.height}"
            textSize = 7.5f
            typeface = Typeface.MONOSPACE
            setTextColor("#A78BFA".toColorInt())
        }
        tvCropSizeRef = tvCropSize
        rowCropSize.addView(tvCropSize, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnShrinkCrop = Button(context).apply {
            text = "—"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#1E293B".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#A78BFA".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                val step = cropShiftStep * 2
                currentCropDeltaW = (currentCropDeltaW - step).coerceAtLeast(-(rawTemplateBitmap.width - 12))
                currentCropDeltaH = (currentCropDeltaH - step).coerceAtLeast(-(rawTemplateBitmap.height - 12))
                updateWorkingCropBitmap()
                tvCropSize.text = "РАЗМЕР: ${currentWorkingRaw.width}×${currentWorkingRaw.height}"
                reevaluateMatching()
            }
        }
        rowCropSize.addView(btnShrinkCrop, LinearLayout.LayoutParams(dp(26), dp(22)).apply { marginEnd = dp(2) })

        val btnExpandCrop = Button(context).apply {
            text = "+"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#1E293B".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#A78BFA".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                val step = cropShiftStep * 2
                currentCropDeltaW = (currentCropDeltaW + step).coerceAtMost(100)
                currentCropDeltaH = (currentCropDeltaH + step).coerceAtMost(100)
                updateWorkingCropBitmap()
                tvCropSize.text = "РАЗМЕР: ${currentWorkingRaw.width}×${currentWorkingRaw.height}"
                reevaluateMatching()
            }
        }
        rowCropSize.addView(btnExpandCrop, LinearLayout.LayoutParams(dp(26), dp(22)).apply { marginEnd = dp(2) })

        val btnResetCrop = Button(context).apply {
            text = "СБРОС"
            textSize = 6.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#94A3B8".toColorInt())
            background = GradientDrawable().apply {
                setColor("#161B22".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener {
                currentCropShiftX = 0
                currentCropShiftY = 0
                currentCropDeltaW = 0
                currentCropDeltaH = 0
                tvCropShift.text = "СМЕЩЕНИЕ X:0 Y:0"
                updateWorkingCropBitmap()
                tvCropSize.text = "РАЗМЕР: ${currentWorkingRaw.width}×${currentWorkingRaw.height}"
                reevaluateMatching()
            }
        }
        rowCropSize.addView(btnResetCrop, LinearLayout.LayoutParams(dp(36), dp(22)))

        cropControlBox.addView(rowCropSize)
        calibrationContentLayout.addView(cropControlBox)

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
        clickControlRow.addView(btnClickOffsetToggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)))
        calibrationContentLayout.addView(clickControlRow)

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
        calibrationContentLayout.addView(coVerifRow)

        val nameRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        val tvNameLabel = TextView(context).apply {
            text = "ИМЯ:"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor("#8B949E".toColorInt())
            layoutParams = LinearLayout.LayoutParams(dp(50), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        nameRow.addView(tvNameLabel)

        val defaultTplName = if (existingTemplatePath != null) {
            File(existingTemplatePath).nameWithoutExtension.removePrefix("mask_")
        } else {
            val existingNames = templateRepository.loadAllTemplates().map {
                File(it.first).nameWithoutExtension.removePrefix("mask_")
            }.toSet()
            var idx = existingNames.size + 1
            while (existingNames.contains("Цель_$idx") || existingNames.contains("цель_$idx")) {
                idx++
            }
            "Цель_$idx"
        }
        val etTemplateName = EditText(context).apply {
            setText(defaultTplName)
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28))
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    v.requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    @Suppress("DEPRECATION")
                    imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }
                false
            }
        }
        nameRow.addView(etTemplateName)
        calibrationContentLayout.addView(nameRow)

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

        val btnPrevFolder = Button(context).apply {
            text = "◀"
            textSize = 9f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(4), 0, dp(4), 0)
        }
        folderRow.addView(btnPrevFolder, LinearLayout.LayoutParams(dp(28), dp(28)))

        val etFolder = EditText(context).apply {
            hint = "default"
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
            layoutParams = LinearLayout.LayoutParams(0, dp(28), 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    v.requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    @Suppress("DEPRECATION")
                    imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }
                false
            }
        }
        folderRow.addView(etFolder)

        val btnNextFolder = Button(context).apply {
            text = "▶"
            textSize = 9f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(4), 0, dp(4), 0)
        }
        folderRow.addView(btnNextFolder, LinearLayout.LayoutParams(dp(28), dp(28)))

        var foldersList = listOf("default")
        var currentFolderIndex = 0
        try {
            val repo = templateRepository as? com.example.autotap.infrastructure.storage.TemplateRepositoryImpl
            val repoFolders = repo?.listFolders()
            if (repoFolders != null && repoFolders.isNotEmpty()) {
                foldersList = repoFolders
            }
            val curFolder = if (existingTemplatePath != null) File(existingTemplatePath).parentFile?.name ?: "default" else "default"
            if (!foldersList.contains(curFolder)) {
                foldersList = foldersList + curFolder
            }
            currentFolderIndex = foldersList.indexOf(curFolder).coerceAtLeast(0)
            etFolder.setText(foldersList[currentFolderIndex])
        } catch (_: Exception) {}

        btnPrevFolder.setOnClickListener {
            if (foldersList.isNotEmpty()) {
                currentFolderIndex = if (currentFolderIndex - 1 < 0) foldersList.size - 1 else currentFolderIndex - 1
                etFolder.setText(foldersList[currentFolderIndex])
            }
        }
        btnNextFolder.setOnClickListener {
            if (foldersList.isNotEmpty()) {
                currentFolderIndex = (currentFolderIndex + 1) % foldersList.size
                etFolder.setText(foldersList[currentFolderIndex])
            }
        }

        calibrationContentLayout.addView(folderRow)

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        val btnSave = Button(context).apply {
            tag = "APPLY"
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

                val minDim = kotlin.math.min(finalMask.width, finalMask.height)
                val calculatedGridStep = kotlin.math.max(12, kotlin.math.min(minDim / 3, 24))

                val finalDownscale = if (userDownscaleFactor in 1..4) userDownscaleFactor else effectiveDownscaleFactor

                val metaObj = JSONObject().apply {
                    put("similarityPercent", currentSimilarity.coerceAtLeast(60))
                    put("shapeExpansion", currentShapeExpansion)
                    put("paddingOffsetPx", currentPaddingOffset)
                    put("isContourMode", isAutoContourMode)
                    put("isShapeOnlyMode", isShapeOnlyMode)
                    put("isCircleShape", isCircleShape)
                    put("colorDeltaEMode", isSmartColorMode)
                    put("isSmartColorMode", isSmartColorMode)
                    put("isStrictRoiOnly", false)
                    put("isNeuralEngine", isNeuralEngineMode)
                    put("optimalDownscaleFactor", finalDownscale)
                    put("downscaleFactor", finalDownscale)
                    put("useCustomClickOffset", useCustomClickOffset)
                    put("clickOffsetX", clickOffsetX.toDouble())
                    put("clickOffsetY", clickOffsetY.toDouble())

                    put("calibratedX", finalCalibX)
                    put("calibratedY", finalCalibY)
                    put("optimalGridStep", calculatedGridStep)
                    put("cropLeft", anchorCropX + currentCropOffsetX)
                    put("cropTop", anchorCropY + currentCropOffsetY)
                    put("cropWidth", finalMask.width)
                    put("cropHeight", finalMask.height)
                    put("tightCropX", 0)
                    put("tightCropY", 0)
                    put("tightCropW", finalMask.width)
                    put("tightCropH", finalMask.height)
                    put("updatedAt", System.currentTimeMillis())
                    val verifArr = org.json.JSONArray()
                    coVerificationTemplates.forEach { verifArr.put(it) }
                    put("coVerificationTemplates", verifArr)
                    val discArr = org.json.JSONArray()
                    val targetExistingPath = existingTemplatePath
                    if (!targetExistingPath.isNullOrBlank()) {
                        try {
                            val oldMaskFile = if (targetExistingPath.startsWith("/")) {
                                File(targetExistingPath)
                            } else {
                                val tDir = File(context.filesDir, "templates")
                                val f = File(tDir, targetExistingPath)
                                if (f.exists()) f else {
                                    val fName = File(targetExistingPath).name
                                    tDir.walkTopDown().filter { it.isFile && it.name == fName }.firstOrNull() ?: File(targetExistingPath)
                                }
                            }
                            val oldMetaFile = if (oldMaskFile.parentFile != null) File(oldMaskFile.parentFile, "${oldMaskFile.nameWithoutExtension}.json") else null
                            if (oldMetaFile != null && oldMetaFile.exists()) {
                                val oldMeta = JSONObject(oldMetaFile.readText())
                                val oldDisc = oldMeta.optJSONArray("discoveredLocations")
                                if (oldDisc != null) {
                                    for (i in 0 until oldDisc.length()) {
                                        val item = oldDisc.optJSONObject(i)
                                        if (item != null) discArr.put(item)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    activeRoiZones.forEach { z ->
                        val zx = z.centerX()
                        val zy = z.centerY()
                        var alreadyHas = false
                        for (i in 0 until discArr.length()) {
                            val item = discArr.optJSONObject(i)
                            if (item != null && abs(item.optInt("x") - zx) < 24 && abs(item.optInt("y") - zy) < 24) {
                                alreadyHas = true
                                break
                            }
                        }
                        if (!alreadyHas) {
                            if (discArr.length() >= 12) discArr.remove(0)
                            discArr.put(JSONObject().apply {
                                put("x", zx)
                                put("y", zy)
                            })
                        }
                    }
                    
                    val reqThresh = currentSimilarity / 100f
                    currentCandidates.forEach { cand ->
                        if (cand.score >= reqThresh) {
                            val cx = cand.clickX.toInt()
                            val cy = cand.clickY.toInt()
                            var alreadyHas = false
                            for (i in 0 until discArr.length()) {
                                val item = discArr.optJSONObject(i)
                                if (item != null && abs(item.optInt("x") - cx) < 24 && abs(item.optInt("y") - cy) < 24) {
                                    alreadyHas = true
                                    break
                                }
                            }
                            if (!alreadyHas) {
                                if (discArr.length() >= 12) discArr.remove(0)
                                discArr.put(JSONObject().apply {
                                    put("x", cx)
                                    put("y", cy)
                                })
                            }
                        }
                    }

                    if (discArr.length() > 0) {
                        put("discoveredLocations", discArr)
                        put("additionalSearchLocations", discArr)
                    }
                    if (activeRoiZones.isNotEmpty()) {
                        val rArr = org.json.JSONArray()
                        activeRoiZones.forEach { z ->
                            rArr.put(JSONObject().apply {
                                put("left", z.left)
                                put("top", z.top)
                                put("right", z.right)
                                put("bottom", z.bottom)
                            })
                        }
                        put("roiZones", rArr)
                    }
                }

                // [V12.1] Синхронный кроп сырого кадра точно по смещению маски для 100% совпадения пикселей с маской
                val finalRawCropped = try {
                    val safeCropW = finalMask.width.coerceIn(1, currentWorkingRaw.width)
                    val safeCropH = finalMask.height.coerceIn(1, currentWorkingRaw.height)
                    val localCropX = if (customEditedMask != null) 0 else (currentCropOffsetX - currentCropShiftX).coerceAtLeast(0)
                    val localCropY = if (customEditedMask != null) 0 else (currentCropOffsetY - currentCropShiftY).coerceAtLeast(0)
                    val safeCropX = localCropX.coerceIn(0, currentWorkingRaw.width - safeCropW)
                    val safeCropY = localCropY.coerceIn(0, currentWorkingRaw.height - safeCropH)
                    Bitmap.createBitmap(currentWorkingRaw, safeCropX, safeCropY, safeCropW, safeCropH)
                } catch (_: Exception) {
                    currentWorkingRaw
                }

                val rawCustomName = etTemplateName.text.toString().trim()
                val customName = if (rawCustomName.isNotBlank()) rawCustomName else null
                val effectiveExistingPath = existingTemplatePath

                val tPath = templateRepository.saveTemplate(
                    folderName = folderName,
                    existingPath = effectiveExistingPath,
                    maskBmp = finalMask,
                    rawBmp = finalRawCropped,
                    screenBmp = screenshot,
                    metadata = metaObj,
                    customFileName = customName
                )
                com.example.autotap.infrastructure.vision.TemplateMatchingEngine.clearTemplateCache()
                com.example.autotap.infrastructure.vision.TemplateMatchingEngine.registerDiscoveredLocation(tPath, finalCalibX, finalCalibY)
                activeRoiZones.forEach { z ->
                    com.example.autotap.infrastructure.vision.TemplateMatchingEngine.registerDiscoveredLocation(tPath, z.centerX(), z.centerY())
                }

                val best = currentCandidates.firstOrNull()
                val targetX = if (existingAction != null && existingAction.posX > 10f && best == null) existingAction.posX
                              else (best?.clickX?.toFloat() ?: (anchorCropX + finalMask.width / 2f).coerceIn(100f, dm.widthPixels - 100f))
                val targetY = if (existingAction != null && existingAction.posY > 10f && best == null) existingAction.posY
                              else (best?.clickY?.toFloat() ?: (anchorCropY + finalMask.height / 2f).coerceIn(150f, dm.heightPixels - 150f))

                val firstRoi = activeRoiZones.firstOrNull()
                val updatedRoiAnchors: List<Point2D> = activeRoiZones.map { zone -> Point2D(zone.centerX().toFloat(), zone.centerY().toFloat()) }
                val combinedAnchors = (existingAction?.primaryAnchorPoints ?: emptyList()).toMutableList()
                for (pt in updatedRoiAnchors) {
                    if (combinedAnchors.none { abs(it.x - pt.x) < 24f && abs(it.y - pt.y) < 24f }) {
                        if (combinedAnchors.size >= 8) combinedAnchors.removeAt(0)
                        combinedAnchors.add(pt)
                    }
                }
                val existingMulti = existingAction?.multiTemplatePaths?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
                if (!existingAction?.templatePath.isNullOrBlank() && !existingMulti.contains(existingAction!!.templatePath)) {
                    existingMulti.add(existingAction!!.templatePath)
                }
                val oldPath = existingTemplatePath
                if (!oldPath.isNullOrBlank() && existingMulti.contains(oldPath)) {
                    val idx = existingMulti.indexOf(oldPath)
                    existingMulti[idx] = tPath
                } else if (!existingMulti.contains(tPath)) {
                    existingMulti.add(tPath)
                }

                val primaryTemplatePath = if (!existingAction?.templatePath.isNullOrBlank() && existingTemplatePath == null) {
                    existingAction!!.templatePath
                } else {
                    tPath
                }

                val isAddingToExistingMulti = existingAction != null && (existingAction.multiTemplatePaths.isNotEmpty() || existingAction.templatePath.isNotBlank())

                val action = (existingAction ?: MacroAction(id = targetStepId ?: 1, type = ActionType.TRIGGER)).copy(
                    type = ActionType.TRIGGER,
                    posX = targetX,
                    posY = targetY,
                    templatePath = primaryTemplatePath,
                    multiTemplatePaths = existingMulti.distinct(),
                    similarityPercent = currentSimilarity,
                    shapeExpansion = currentShapeExpansion,
                    paddingOffsetPx = currentPaddingOffset,
                    isContourMode = isAutoContourMode,
                    isShapeOnlyMode = isShapeOnlyMode,
                    isCircleShape = isCircleShape,
                    isNeuralEngine = isNeuralEngineMode,
                    colorDeltaEMode = isSmartColorMode,
                    downscaleFactor = finalDownscale,
                    useCustomClickOffset = useCustomClickOffset,
                    clickOffsetX = clickOffsetX,
                    clickOffsetY = clickOffsetY,
                    roiLeft = if (isAddingToExistingMulti) null else (firstRoi?.left ?: existingAction?.roiLeft),
                    roiTop = if (isAddingToExistingMulti) null else (firstRoi?.top ?: existingAction?.roiTop),
                    roiRight = if (isAddingToExistingMulti) null else (firstRoi?.right ?: existingAction?.roiRight),
                    roiBottom = if (isAddingToExistingMulti) null else (firstRoi?.bottom ?: existingAction?.roiBottom),
                    primaryAnchorPoints = combinedAnchors
                )
                onFinished(action, tPath)
                dismiss()
            }
        }
        actionRow.addView(btnSave, LinearLayout.LayoutParams(0, dp(34), 1.5f).apply { marginEnd = dp(4) })

        val btnCancel = Button(context).apply {
            text = "ОТМЕНА"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor("#F04438".toColorInt())
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#F04438".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(12), dp(4), dp(12), dp(4))
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
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            x = 0
            y = 0
        }
        root.fitsSystemWindows = false
        overlayWindowManager.addViewSafe(root, params)

        reevaluateMatching()
    }

    private fun createIconButton(
        type: VectorIconDrawer.IconType,
        bgHex: String,
        iconHex: String,
        sizePx: Int,
        onClick: () -> Unit
    ): View {
        return object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(5f), dpF(5f), width - dpF(5f), height - dpF(5f))
                VectorIconDrawer.drawIcon(canvas, type, bounds, iconHex.toColorInt(), dpF(1.8f))
            }
        }.apply {
            background = GradientDrawable().apply {
                setColor(bgHex.toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), iconHex.toColorInt())
            }
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            setOnClickListener { onClick() }
        }
    }

    fun dismiss() {
        AppLogger.log(context, "OVERLAY_CANCEL", "Закрытие/отмена оверлея калибровки CalibrationOverlay (targetStepId=$targetStepId)")
        evalJob?.cancel()
        rootFrameLayout?.let { overlayWindowManager.removeViewSafe(it) }
        rootFrameLayout = null
        if (!screenshot.isRecycled) screenshot.recycle()
        if (currentWorkingRaw != rawTemplateBitmap && !currentWorkingRaw.isRecycled) currentWorkingRaw.recycle()
        if (!rawTemplateBitmap.isRecycled) rawTemplateBitmap.recycle()
        activeGeneratedMask?.let { if (!it.isRecycled) it.recycle() }
        customEditedMask?.let { if (!it.isRecycled) it.recycle() }
    }
}
