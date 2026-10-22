package com.example.autotap.infrastructure.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.MatchCandidate
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object TemplateMatchingEngine {

    data class SearchDiagnostics(
        val roi: Rect? = null,
        val bestCandidate: MatchCandidate? = null,
        val rejectedCandidates: List<Pair<MatchCandidate, String>> = emptyList(),
        val thresholdPct: Int = 80,
        val methodName: String = "ГИБРИД + УМНЫЙ ЦВЕТ",
        val downscaleFactor: Int = 1,
        val elapsedMs: Long = 0L
    )

    @Volatile
    var lastDiagnostics: SearchDiagnostics? = null

    private val NEIGHBOR_DX = intArrayOf(0, -1, 1, 0, 0)
    private val NEIGHBOR_DY = intArrayOf(0, 0, 0, -1, 1)

    class TemplateFeatures(
        val tw: Int,
        val th: Int,
        val shapeCount: Int,
        val shapeDx: IntArray,
        val shapeDy: IntArray,
        val shapeR: IntArray,
        val shapeG: IntArray,
        val shapeB: IntArray,
        val shapeGradGx: FloatArray,
        val shapeGradGy: FloatArray,
        val shapeGradMag: FloatArray,
        val shapeQuadrant: IntArray,
        val hasQuadrant0: Boolean,
        val hasQuadrant1: Boolean,
        val hasQuadrant2: Boolean,
        val hasQuadrant3: Boolean,
        val l1SampleCount: Int,
        val l1Dx: IntArray,
        val l1Dy: IntArray,
        val l1R: IntArray,
        val l1G: IntArray,
        val l1B: IntArray,
        val l1Gx: FloatArray,
        val l1Gy: FloatArray,
        val l1HasGrad: BooleanArray,
        val centroidX: Int,
        val centroidY: Int,
        val shapeEdgeCount: Int,
        val isMicroIcon: Boolean,
        val isThinLine: Boolean,
        val isRawImage: Boolean,
        val analysis: com.example.autotap.domain.model.TemplateAnalysisResult,
        val individualThreshold: Float,
        val isShapeOnlyFromMeta: Boolean = false,
        val isSmartColorFromMeta: Boolean = false,
        val isStrictRoiOnlyFromMeta: Boolean = false,
        val clickOffsetXFromMeta: Float = 0f,
        val clickOffsetYFromMeta: Float = 0f,
        val useCustomOffsetFromMeta: Boolean = false,
        val calibratedX: Int? = null,
        val calibratedY: Int? = null,
        val optimalGridStepFromMeta: Int = 0,
        val optimalDownscaleFactorFromMeta: Int = 1,
        val optimalDownscaleScaleFromMeta: Float = 1.0f,
        val shapeLabL: FloatArray = FloatArray(0),
        val shapeLabA: FloatArray = FloatArray(0),
        val shapeLabB: FloatArray = FloatArray(0),
        val l1LabL: FloatArray = FloatArray(0),
        val l1LabA: FloatArray = FloatArray(0),
        val l1LabB: FloatArray = FloatArray(0),
        val metaAnchorLocations: List<com.example.autotap.domain.model.Point2D> = emptyList(),
        val spatialWeights: FloatArray = FloatArray(0),
        val keyClusterIndices: IntArray = IntArray(0)
    )

    val historicalSearchAnchors = ConcurrentHashMap<String, MutableSet<Pair<Int, Int>>>()
    val additionalSearchLocations = ConcurrentHashMap<String, MutableSet<Pair<Int, Int>>>()
    private val templateFeatureCache = ConcurrentHashMap<String, TemplateFeatures>()

    private fun addLocationBoundedSafe(
        map: ConcurrentHashMap<String, MutableSet<Pair<Int, Int>>>,
        tPath: String,
        x: Int,
        y: Int,
        minDist: Int
    ) {
        if (tPath.isEmpty()) return
        val set = map.getOrPut(tPath) { Collections.synchronizedSet(LinkedHashSet()) }
        synchronized(set) {
            if (set.none { abs(it.first - x) < minDist && abs(it.second - y) < minDist }) {
                // Первичная точка (index 0) хранится бессрочно. Новые найденные точки добавляются как ДОПОЛНИТЕЛЬНЫЕ
                if (set.size >= 16) {
                    val list = set.toList()
                    if (list.size > 1) {
                        set.clear()
                        set.add(list[0]) // Первичная оригинальная точка сохраняется всегда
                        set.addAll(list.subList(2, list.size)) // Удаляем устаревшую второстепенную точку
                    }
                }
                set.add(Pair(x, y))
            }
        }
    }

    fun registerDiscoveredLocation(tPath: String, x: Int, y: Int) {
        addLocationBoundedSafe(additionalSearchLocations, tPath, x, y, minDist = 24)
    }

    private fun recordFoundLocation(tPath: String, clickX: Int, clickY: Int) {
        addLocationBoundedSafe(historicalSearchAnchors, tPath, clickX, clickY, minDist = 20)
        addLocationBoundedSafe(additionalSearchLocations, tPath, clickX, clickY, minDist = 20)
    }

    /**
     * Точечная инвалидация кэша конкретного шаблона (не сбрасывает L0-якоря других масок).
     */
    fun invalidateTemplate(tPath: String) {
        if (tPath.isEmpty()) return
        historicalSearchAnchors.remove(tPath)
        additionalSearchLocations.remove(tPath)
        val prefix = "${tPath}_"
        val keysToRemove = templateFeatureCache.keys().asSequence()
            .filter { it == tPath || it.startsWith(prefix) }
            .toList()
        keysToRemove.forEach { templateFeatureCache.remove(it) }
    }

    /**
     * Перенос накопленных L0-якорей со старого пути на новый при переименовании шаблона.
     */
    fun migrateTemplatePath(oldPath: String, newPath: String) {
        if (oldPath.isEmpty() || newPath.isEmpty() || oldPath == newPath) return
        historicalSearchAnchors.remove(oldPath)?.let { historicalSearchAnchors[newPath] = it }
        additionalSearchLocations.remove(oldPath)?.let { additionalSearchLocations[newPath] = it }
        val prefix = "${oldPath}_"
        templateFeatureCache.keys().asSequence()
            .filter { it == oldPath || it.startsWith(prefix) }
            .toList()
            .forEach { templateFeatureCache.remove(it) }
    }

    fun clearTemplateCache() {
        templateFeatureCache.clear()
        AppLogger.log(null, "VISION", "Кэши дескрипторов шаблонов очищены (накопленный опыт первичных точек сохранен)")
    }

    fun combineMaskWithRawRgb(maskBmp: Bitmap, rawBmp: Bitmap): Bitmap {
        if (maskBmp.isRecycled || maskBmp.width <= 0 || maskBmp.height <= 0) return maskBmp
        if (rawBmp.isRecycled || rawBmp.width <= 0 || rawBmp.height <= 0) return maskBmp

        val w = maskBmp.width
        val h = maskBmp.height
        val mPixels = IntArray(w * h)
        val rPixels = IntArray(w * h)
        maskBmp.getPixels(mPixels, 0, w, 0, 0, w, h)

        if (rawBmp.width == w && rawBmp.height == h) {
            rawBmp.getPixels(rPixels, 0, w, 0, 0, w, h)
        } else {
            val scaledRaw = Bitmap.createScaledBitmap(rawBmp, w, h, true)
            scaledRaw.getPixels(rPixels, 0, w, 0, 0, w, h)
            scaledRaw.recycle()
        }

        val combined = IntArray(w * h)
        for (i in 0 until w * h) {
            val alpha = (mPixels[i] ushr 24) and 0xFF
            val rgb = rPixels[i] and 0x00FFFFFF
            combined[i] = (alpha shl 24) or rgb
        }
        return Bitmap.createBitmap(combined, w, h, Bitmap.Config.ARGB_8888)
    }

    // Перегрузка 1: Вызов с Bitmap (основная)
    fun extractFeatures(template: Bitmap, cacheKey: String, tPath: String = "", defaultSim: Int = 80): TemplateFeatures? {
        return try {
            if (template.isRecycled || template.width <= 0 || template.height <= 0) return null

            val tw = template.width
            val th = template.height

            val metaLastMod = if (tPath.isNotEmpty()) {
                try { File(tPath).lastModified() } catch (_: Exception) { 0L }
            } else 0L

            val stableKey = if (tPath.isNotEmpty()) {
                "${tPath}_${tw}x${th}_${metaLastMod}_$defaultSim"
            } else if (cacheKey.isNotEmpty()) {
                "${cacheKey}_${tw}x${th}_$defaultSim"
            } else {
                "dyn_${tw}x${th}_${template.generationId}_$defaultSim"
            }

            val cached = templateFeatureCache[stableKey]
            if (cached != null) return cached

            val tPixels = IntArray(tw * th)
            template.getPixels(tPixels, 0, tw, 0, 0, tw, th)

            extractFeatures(tPixels, tw, th, stableKey, defaultSim, tPath, false)
        } catch (t: Throwable) {
            AppLogger.logError(null, "CV_EXTRACT", t)
            null
        }
    }

    // Перегрузка 2: Вызов с IntArray (для SmartMaskEngine и Unit тестов)
    fun extractFeatures(
        tPixels: IntArray,
        tw: Int,
        th: Int,
        cacheKey: String,
        defaultSim: Int = 80,
        tPath: String = "",
        isRawPixelsParam: Boolean = false
    ): TemplateFeatures? {
        return try {
            if (tPixels.isEmpty() || tw <= 0 || th <= 0) return null

            val metaLastMod = if (tPath.isNotEmpty()) {
                try { File(tPath).lastModified() } catch (_: Exception) { 0L }
            } else 0L

            val stableKey = if (cacheKey.contains("_$metaLastMod")) cacheKey else if (tPath.isNotEmpty()) {
                "${tPath}_${tw}x${th}_${metaLastMod}_$defaultSim"
            } else {
                "${cacheKey}_${tw}x${th}_$defaultSim"
            }

            val cached = templateFeatureCache[stableKey]
            if (cached != null) return cached

        var templateSim = defaultSim
        var metaShapeOnly = false
        var metaSmartColor = false
        var metaStrictRoi = false
        var metaClickX = 0f
        var metaClickY = 0f
        var metaUseOffset = false
        var metaCalibX: Int? = null
        var metaCalibY: Int? = null
        var metaCropLeft: Int? = null
        var metaCropTop: Int? = null
        var metaGridStep = 0
        var metaDownscaleFactor = 1
        val metaAnchors = mutableListOf<com.example.autotap.domain.model.Point2D>()

        if (tPath.isNotEmpty()) {
            try {
                val maskFile = File(tPath)
                val metaFile = File(maskFile.parentFile, "${maskFile.nameWithoutExtension}.json")
                if (metaFile.exists()) {
                    val meta = JSONObject(metaFile.readText())
                    val rawSim = if (meta.has("similarityPercent")) meta.getInt("similarityPercent") else 75
                    templateSim = rawSim.coerceIn(60, 98)
                    metaShapeOnly = meta.optBoolean("isShapeOnlyMode", false)
                    metaSmartColor = meta.optBoolean("colorDeltaEMode", false) || meta.optBoolean("isSmartColorMode", false)
                    metaStrictRoi = meta.optBoolean("isStrictRoiOnly", false)
                    metaClickX = meta.optDouble("clickOffsetX", 0.0).toFloat()
                    metaClickY = meta.optDouble("clickOffsetY", 0.0).toFloat()
                    metaUseOffset = meta.optBoolean("useCustomClickOffset", false)
                    if (meta.has("cropLeft")) metaCropLeft = meta.getInt("cropLeft")
                    if (meta.has("cropTop")) metaCropTop = meta.getInt("cropTop")
                    if (meta.has("calibratedX")) metaCalibX = meta.getInt("calibratedX")
                    else if (meta.has("originalX")) metaCalibX = meta.getInt("originalX")
                    if (meta.has("calibratedY")) metaCalibY = meta.getInt("calibratedY")
                    else if (meta.has("originalY")) metaCalibY = meta.getInt("originalY")

                    if (meta.has("discoveredLocations")) {
                        val arr = meta.getJSONArray("discoveredLocations")
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i)
                            if (o != null && o.has("x") && o.has("y")) {
                                metaAnchors.add(com.example.autotap.domain.model.Point2D(o.getInt("x").toFloat(), o.getInt("y").toFloat()))
                            }
                        }
                    }
                    if (meta.has("roiZones")) {
                        val arr = meta.getJSONArray("roiZones")
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i)
                            if (o != null && o.has("left") && o.has("right") && o.has("top") && o.has("bottom")) {
                                val cx = (o.getInt("left") + o.getInt("right")) / 2f
                                val cy = (o.getInt("top") + o.getInt("bottom")) / 2f
                                metaAnchors.add(com.example.autotap.domain.model.Point2D(cx, cy))
                            }
                        }
                    }

                    if (meta.has("optimalGridStep")) metaGridStep = meta.getInt("optimalGridStep")
                    if (meta.has("optimalDownscaleFactor")) {
                        metaDownscaleFactor = meta.getInt("optimalDownscaleFactor").coerceIn(1, 4)
                    } else if (meta.has("downscaleFactor")) {
                        metaDownscaleFactor = meta.getInt("downscaleFactor").coerceIn(1, 4)
                    }
                }
            } catch (_: Exception) {
            }
        }

        val area = tw * th
        val isMicro = area <= 1600 || tw <= 36 || th <= 36
        val isThin = (th in 1..20 && tw >= 20) || (tw in 1..20 && th >= 20)

        val minD = min(tw, th)
        val calculatedDownscale = if (metaDownscaleFactor > 1) metaDownscaleFactor else when {
            minD < 28 || isMicro -> 1
            minD < 64 -> 2
            minD < 140 -> 3
            else -> 4
        }

        // [V12.0] Загрузка истинных RGB цветов из raw_*.png вместо бинарной маски
        var colorSourcePixels = tPixels
        if (tPath.isNotEmpty()) {
            try {
                val maskFile = File(tPath)
                if (maskFile.exists()) {
                    val rawFile = File(maskFile.parentFile, "raw_" + maskFile.name.removePrefix("mask_"))
                    if (rawFile.exists()) {
                        val rawBmp = BitmapFactory.decodeFile(rawFile.absolutePath)
                        if (rawBmp != null) {
                            if (rawBmp.width == tw && rawBmp.height == th) {
                                val rp = IntArray(tw * th)
                                rawBmp.getPixels(rp, 0, tw, 0, 0, tw, th)
                                colorSourcePixels = rp
                            }
                            rawBmp.recycle()
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        var cropMinX = tw
        var cropMinY = th
        var cropMaxX = -1
        var cropMaxY = -1

        for (ty in 0 until th) {
            val row = ty * tw
            for (tx in 0 until tw) {
                val a = (tPixels[row + tx] ushr 24) and 0xFF
                if (a > 25) {
                    if (tx < cropMinX) cropMinX = tx
                    if (tx > cropMaxX) cropMaxX = tx
                    if (ty < cropMinY) cropMinY = ty
                    if (ty > cropMaxY) cropMaxY = ty
                }
            }
        }

        val safeColorPixels = if (colorSourcePixels.size == tw * th) colorSourcePixels else tPixels

        val effTw = tw
        val effTh = th
        val effPixels = tPixels
        val effColorPixels = safeColorPixels
        val cropShiftX = 0
        val cropShiftY = 0

        val maxPoints = effTw * effTh
        val shapeDx = IntArray(maxPoints)
        val shapeDy = IntArray(maxPoints)
        val shapeR = IntArray(maxPoints)
        val shapeG = IntArray(maxPoints)
        val shapeB = IntArray(maxPoints)
        val shapeGradGx = FloatArray(maxPoints)
        val shapeGradGy = FloatArray(maxPoints)
        val shapeGradMag = FloatArray(maxPoints)
        val shapeQuadrant = IntArray(maxPoints)

        var shapeCount = 0
        var shapeEdgeCount = 0
        var sumX = 0L
        var sumY = 0L
        var transparentCount = 0

        val lum = IntArray(effTw * effTh)
        val mask = BooleanArray(effTw * effTh)
        for (i in 0 until effTw * effTh) {
            val c = effPixels[i]
            val a = (c ushr 24) and 0xFF
            if (a <= 25) transparentCount++
            mask[i] = a > 25
            lum[i] = if (a > 25) {
                (((c ushr 16) and 0xFF) * 299 + ((c ushr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            } else 0
        }

        val isRawImage = isRawPixelsParam || metaSmartColor || (transparentCount < (effTw * effTh * 0.05))

        // [Adaptive Grayscale Contrast Normalization]
        if (!isRawImage && transparentCount > 0) {
            var minL = 255
            var maxL = 0
            val fgHist = IntArray(256)
            var fgCount = 0
            for (i in 0 until effTw * effTh) {
                if (mask[i]) {
                    val l = lum[i]
                    if (l < minL) minL = l
                    if (l > maxL) maxL = l
                    fgHist[l]++
                    fgCount++
                }
            }
            val range = maxL - minL
            if (range in 10..254 && fgCount > 0) {
                val cdf = IntArray(256)
                var cdfSum = 0
                var cdfMin = -1
                for (v in 0..255) {
                    cdfSum += fgHist[v]
                    cdf[v] = cdfSum
                    if (fgHist[v] > 0 && cdfMin == -1) cdfMin = cdfSum
                }
                val cdfDenominator = (fgCount - (cdfMin.coerceAtLeast(0))).coerceAtLeast(1)
                for (i in 0 until effTw * effTh) {
                    if (mask[i]) {
                        val rawL = lum[i]
                        val normVal = ((rawL - minL) * 255 / range).coerceIn(0, 255)
                        val eqVal = if (cdf[rawL] >= (cdfMin.coerceAtLeast(0))) {
                            (((cdf[rawL] - cdfMin).toDouble() / cdfDenominator.toDouble()) * 255.0).toInt().coerceIn(0, 255)
                        } else normVal
                        lum[i] = (normVal * 0.4f + eqVal * 0.6f).toInt().coerceIn(0, 255)
                    }
                }
            }
        }

        val cxEstimate = effTw / 2
        val cyEstimate = effTh / 2
        var q0EdgeCount = 0
        var q1EdgeCount = 0
        var q2EdgeCount = 0
        var q3EdgeCount = 0

        for (ty in 0 until effTh) {
            val row = ty * effTw
            for (tx in 0 until effTw) {
                val idx = row + tx
                if (mask[idx]) {
                    val c = effColorPixels[idx]
                    shapeDx[shapeCount] = tx
                    shapeDy[shapeCount] = ty
                    shapeR[shapeCount] = (c ushr 16) and 0xFF
                    shapeG[shapeCount] = (c ushr 8) and 0xFF
                    shapeB[shapeCount] = c and 0xFF
                    sumX += tx
                    sumY += ty

                    val q = (if (tx >= cxEstimate) 1 else 0) or (if (ty >= cyEstimate) 2 else 0)
                    shapeQuadrant[shapeCount] = q

                    val gx: Float
                    val gy: Float

                    val isBorder = (tx == 0 || tx == effTw - 1 || ty == 0 || ty == effTh - 1) ||
                            (!mask[idx - 1] || !mask[idx + 1] || !mask[idx - effTw] || !mask[idx + effTw])

                    if (!isRawImage && isBorder) {
                        val m00 = if (tx > 0 && ty > 0 && mask[idx - effTw - 1]) 255 else 0
                        val m01 = if (ty > 0 && mask[idx - effTw]) 255 else 0
                        val m02 = if (tx < effTw - 1 && ty > 0 && mask[idx - effTw + 1]) 255 else 0

                        val m10 = if (tx > 0 && mask[idx - 1]) 255 else 0
                        val m12 = if (tx < effTw - 1 && mask[idx + 1]) 255 else 0

                        val m20 = if (tx > 0 && ty < effTh - 1 && mask[idx + effTw - 1]) 255 else 0
                        val m21 = if (ty < effTh - 1 && mask[idx + effTw]) 255 else 0
                        val m22 = if (tx < effTw - 1 && ty < effTh - 1 && mask[idx + effTw + 1]) 255 else 0

                        gx = (m02 + 2 * m12 + m22 - (m00 + 2 * m10 + m20)).toFloat()
                        gy = (m20 + 2 * m21 + m22 - (m00 + 2 * m01 + m02)).toFloat()
                    } else {
                        val l00 = if (tx > 0 && ty > 0) lum[idx - effTw - 1] else lum[idx]
                        val l01 = if (ty > 0) lum[idx - effTw] else lum[idx]
                        val l02 = if (tx < effTw - 1 && ty > 0) lum[idx - effTw + 1] else lum[idx]

                        val l10 = if (tx > 0) lum[idx - 1] else lum[idx]
                        val l12 = if (tx < effTw - 1) lum[idx + 1] else lum[idx]

                        val l20 = if (tx > 0 && ty < effTh - 1) lum[idx + effTw - 1] else lum[idx]
                        val l21 = if (ty < effTh - 1 && lum.size > idx + effTw) lum[idx + effTw] else lum[idx]
                        val l22 = if (tx < effTw - 1 && ty < effTh - 1 && lum.size > idx + effTw + 1) lum[idx + effTw + 1] else lum[idx]

                        gx = (l02 + 2 * l12 + l22 - (l00 + 2 * l10 + l20)).toFloat()
                        gy = (l20 + 2 * l21 + l22 - (l00 + 2 * l01 + l02)).toFloat()
                    }

                    val mag = sqrt((gx * gx + gy * gy).toDouble()).toFloat()
                    if (mag > 8.0f) {
                        shapeGradGx[shapeCount] = gx / mag
                        shapeGradGy[shapeCount] = gy / mag
                        shapeGradMag[shapeCount] = mag
                        shapeEdgeCount++
                        when (q) {
                            0 -> q0EdgeCount++
                            1 -> q1EdgeCount++
                            2 -> q2EdgeCount++
                            3 -> q3EdgeCount++
                        }
                    } else {
                        shapeGradGx[shapeCount] = 0f
                        shapeGradGy[shapeCount] = 0f
                        shapeGradMag[shapeCount] = 0f
                    }

                    shapeCount++
                }
            }
        }

        if (shapeCount < 4) return null

        val cX = (sumX / shapeCount).toInt()
        val cY = (sumY / shapeCount).toInt()

        val l1Count = min(24, shapeCount)
        val l1Dx = IntArray(l1Count)
        val l1Dy = IntArray(l1Count)
        val l1R = IntArray(l1Count)
        val l1G = IntArray(l1Count)
        val l1B = IntArray(l1Count)
        val l1Gx = FloatArray(l1Count)
        val l1Gy = FloatArray(l1Count)
        val l1HasGrad = BooleanArray(l1Count)

        val stepIdx = max(1, shapeCount / l1Count)
        for (i in 0 until l1Count) {
            val srcIdx = min(shapeCount - 1, i * stepIdx)
            l1Dx[i] = shapeDx[srcIdx]
            l1Dy[i] = shapeDy[srcIdx]
            l1R[i] = shapeR[srcIdx]
            l1G[i] = shapeG[srcIdx]
            l1B[i] = shapeB[srcIdx]
            l1Gx[i] = shapeGradGx[srcIdx]
            l1Gy[i] = shapeGradGy[srcIdx]
            l1HasGrad[i] = shapeGradMag[srcIdx] > 6.0f
        }

        val analysis = TemplateMorphologyClassifier.analyze(tPixels, tw, th)

        val shapeLabL = FloatArray(shapeCount)
        val shapeLabA = FloatArray(shapeCount)
        val shapeLabB = FloatArray(shapeCount)
        val tempLab = FloatArray(3)
        for (i in 0 until shapeCount) {
            val c = (shapeR[i] shl 16) or (shapeG[i] shl 8) or shapeB[i]
            ColorDetector.colorToLabFast(c, tempLab)
            shapeLabL[i] = tempLab[0]
            shapeLabA[i] = tempLab[1]
            shapeLabB[i] = tempLab[2]
        }

        val l1LabL = FloatArray(l1Count)
        val l1LabA = FloatArray(l1Count)
        val l1LabB = FloatArray(l1Count)
        for (i in 0 until l1Count) {
            val c = (l1R[i] shl 16) or (l1G[i] shl 8) or l1B[i]
            ColorDetector.colorToLabFast(c, tempLab)
            l1LabL[i] = tempLab[0]
            l1LabA[i] = tempLab[1]
            l1LabB[i] = tempLab[2]
        }

        // [V200.0] Предрассчитанные пространственные веса и групповые кластеры пикселей
        val spatialWeights = FloatArray(shapeCount)
        val maxR = max(tw, th) / 2f
        for (i in 0 until shapeCount) {
            val dxCenter = shapeDx[i] - cX
            val dyCenter = shapeDy[i] - cY
            val distCenter = hypot(dxCenter.toDouble(), dyCenter.toDouble()).toFloat()
            spatialWeights[i] = 1.0f + 0.5f * (1.0f - (distCenter / maxR).coerceIn(0f, 1f))
        }

        val clusterSize = min(8, shapeCount)
        val keyClusterIndices = (0 until shapeCount)
            .sortedBy { i ->
                val dx = shapeDx[i] - cX
                val dy = shapeDy[i] - cY
                dx * dx + dy * dy - (shapeGradMag[i] * 10f).toInt()
            }
            .take(clusterSize)
            .toIntArray()

        val features = TemplateFeatures(
            tw = tw, th = th, shapeCount = shapeCount,
            shapeDx = shapeDx.copyOf(shapeCount), shapeDy = shapeDy.copyOf(shapeCount),
            shapeR = shapeR.copyOf(shapeCount), shapeG = shapeG.copyOf(shapeCount), shapeB = shapeB.copyOf(shapeCount),
            shapeGradGx = shapeGradGx.copyOf(shapeCount), shapeGradGy = shapeGradGy.copyOf(shapeCount),
            shapeGradMag = shapeGradMag.copyOf(shapeCount), shapeQuadrant = shapeQuadrant.copyOf(shapeCount),
            hasQuadrant0 = q0EdgeCount >= 4, hasQuadrant1 = q1EdgeCount >= 4,
            hasQuadrant2 = q2EdgeCount >= 4, hasQuadrant3 = q3EdgeCount >= 4,
            l1SampleCount = l1Count, l1Dx = l1Dx, l1Dy = l1Dy,
            l1R = l1R, l1G = l1G, l1B = l1B, l1Gx = l1Gx, l1Gy = l1Gy, l1HasGrad = l1HasGrad,
            centroidX = cX, centroidY = cY, shapeEdgeCount = shapeEdgeCount,
            isMicroIcon = isMicro, isThinLine = isThin, isRawImage = isRawImage,
            analysis = analysis, individualThreshold = templateSim / 100f,
            isShapeOnlyFromMeta = metaShapeOnly,
            isSmartColorFromMeta = metaSmartColor,
            isStrictRoiOnlyFromMeta = metaStrictRoi,
            clickOffsetXFromMeta = metaClickX,
            clickOffsetYFromMeta = metaClickY,
            useCustomOffsetFromMeta = metaUseOffset,
            calibratedX = if (metaCropLeft != null) metaCropLeft!! + cX else metaCalibX,
            calibratedY = if (metaCropTop != null) metaCropTop!! + cY else metaCalibY,
            optimalGridStepFromMeta = metaGridStep,
            optimalDownscaleFactorFromMeta = calculatedDownscale,
            optimalDownscaleScaleFromMeta = 1.0f / calculatedDownscale.toFloat(),
            shapeLabL = shapeLabL,
            shapeLabA = shapeLabA,
            shapeLabB = shapeLabB,
            l1LabL = l1LabL,
            l1LabA = l1LabA,
            l1LabB = l1LabB,
            metaAnchorLocations = metaAnchors,
            spatialWeights = spatialWeights,
            keyClusterIndices = keyClusterIndices
        )
        templateFeatureCache[stableKey] = features
        features
        } catch (t: Throwable) {
            AppLogger.logError(null, "CV_EXTRACT", t)
            null
        }
    }

    fun findTemplateFastCascade(
        sPixels: IntArray, sw: Int, sh: Int, template: Bitmap,
        minSimilarityPercent: Int = 30, templatePath: String = "",
        actionOverride: MacroAction? = null, enableL0Cache: Boolean = true,
        findAllMatches: Boolean = false,
        isCancelled: () -> Boolean = { false }
    ): List<MatchCandidate> {
        return findMultiTemplatesFastCascade(
            sPixels, sw, sh, listOf(Pair(templatePath, template)), minSimilarityPercent, actionOverride, enableL0Cache, findAllMatches, isCancelled
        )
    }

    private class PreparedTemplateContext(
        val tPath: String,
        val tBmp: Bitmap,
        val features: TemplateFeatures,
        val templateThreshold: Float,
        val isShapeDriven: Boolean,
        val scanLimitX: Int,
        val scanLimitY: Int,
        val anchorProbes: List<com.example.autotap.domain.model.Point2D>
    )

    fun findMultiTemplatesFastCascade(
        sPixels: IntArray, sw: Int, sh: Int, templates: List<Pair<String, Bitmap>>,
        minSimilarityPercent: Int = 30, actionOverride: MacroAction? = null,
        enableL0Cache: Boolean = true, findAllMatches: Boolean = false,
        isCancelled: () -> Boolean = { false }
    ): List<MatchCandidate> {
        return try {
            if (templates.isEmpty() || sPixels.isEmpty() || sw <= 0 || sh <= 0) return emptyList()

        val perfStartTime = System.currentTimeMillis()
        var statL0Hits = 0
        var statL1Sectors = 0
        var statL2Evals = 0

        val allCandidates = ConcurrentHashMap.newKeySet<MatchCandidate>()
        val rejectedCandidatesList = Collections.synchronizedList(mutableListOf<Pair<MatchCandidate, String>>())
        val override = actionOverride
        val defaultSim = override?.similarityPercent ?: minSimilarityPercent
        val isMultiSearch = templates.size > 1

        val roiMinX: Int
        val roiMinY: Int
        val roiMaxX: Int
        val roiMaxY: Int
        val hasRoi: Boolean
        if (override != null && override.roiLeft != null && override.roiTop != null &&
            override.roiRight != null && override.roiBottom != null
        ) {
            hasRoi = true
            roiMinX = override.roiLeft.coerceIn(0, sw - 1)
            roiMinY = override.roiTop.coerceIn(0, sh - 1)
            roiMaxX = override.roiRight.coerceIn(roiMinX + 1, sw)
            roiMaxY = override.roiBottom.coerceIn(roiMinY + 1, sh)
        } else {
            hasRoi = false
            roiMinX = 0
            roiMinY = 0
            roiMaxX = sw
            roiMaxY = sh
        }

        val isDeltaEMode = override?.colorDeltaEMode ?: false
        val selectedRoiZones = if (hasRoi) listOf(Rect(roiMinX, roiMinY, roiMaxX, roiMaxY)) else emptyList()
        val selectedAnchors = override?.primaryAnchorPoints ?: emptyList()
        var lastUsedDownscale = override?.downscaleFactor?.takeIf { it in 1..4 } ?: 1

        val preparedContexts = mutableListOf<PreparedTemplateContext>()
        val featStartTime = System.currentTimeMillis()

        for ((rawPath, tBmp) in templates) {
            if (isCancelled() || tBmp.isRecycled) continue

            val rawFile = File(rawPath)
            val tPath = if (rawPath.isBlank() || rawPath.startsWith("ocr:") || rawFile.exists()) rawPath else {
                rawFile.absolutePath
            }
            val features = extractFeatures(tBmp, tPath, tPath, defaultSim) ?: continue
            val currentTemplateDownscale = override?.downscaleFactor?.takeIf { it in 1..4 }
                ?: features.optimalDownscaleFactorFromMeta
            if (override?.downscaleFactor == null) {
                lastUsedDownscale = currentTemplateDownscale
            }

            val rawThreshold = if (findAllMatches) (defaultSim / 100f) else features.individualThreshold
            val templateThreshold = rawThreshold.coerceIn(0.55f, 0.98f)
            val isShapeDriven = if (findAllMatches && actionOverride != null) actionOverride.isShapeOnlyMode else ((actionOverride?.isShapeOnlyMode == true) || features.isShapeOnlyFromMeta)

            val scanLimitX = (roiMaxX - features.tw).coerceAtLeast(roiMinX)
            val scanLimitY = (roiMaxY - features.th).coerceAtLeast(roiMinY)

            val anchorProbes = mutableListOf<com.example.autotap.domain.model.Point2D>()

            // 1. ПРИОРИТЕТ №1: Родные координаты калибровки ЭТОГО шаблона из его JSON
            if (features.calibratedX != null && features.calibratedY != null) {
                anchorProbes.add(com.example.autotap.domain.model.Point2D(features.calibratedX.toFloat(), features.calibratedY.toFloat()))
            }

            // 2. ПРИОРИТЕТ №2: Опорные точки и ROI зоны из meta.json ЭТОГО шаблона
            features.metaAnchorLocations.forEach { pt ->
                if (anchorProbes.none { p -> abs(p.x - pt.x) < 16f && abs(p.y - pt.y) < 16f }) {
                    anchorProbes.add(pt)
                }
            }

            // 3. ПРИОРИТЕТ №3: Накопленный L0 Cache именно для этого tPath и его имени файла
            if (enableL0Cache && tPath.isNotEmpty()) {
                val normKey = File(tPath).name
                val setsToProbe = listOfNotNull(historicalSearchAnchors[tPath], historicalSearchAnchors[normKey], additionalSearchLocations[tPath], additionalSearchLocations[normKey])
                for (set in setsToProbe) {
                    synchronized(set) {
                        for (loc in set) {
                            val pt = com.example.autotap.domain.model.Point2D(loc.first.toFloat(), loc.second.toFloat())
                            if (anchorProbes.none { p -> abs(p.x - pt.x) < 16f && abs(p.y - pt.y) < 16f }) {
                                anchorProbes.add(pt)
                            }
                        }
                    }
                }
            }

            // 4. ПРИОРИТЕТ №4: Общие координаты шага MacroAction (добавлять только в обычном поиске или при отсутствии родных якорей)
            if (!isMultiSearch || anchorProbes.isEmpty()) {
                if (actionOverride != null && (actionOverride.posX > 1f || actionOverride.posY > 1f)) {
                    val pt = com.example.autotap.domain.model.Point2D(actionOverride.posX, actionOverride.posY)
                    if (anchorProbes.none { abs(it.x - pt.x) < 16f && abs(it.y - pt.y) < 16f }) {
                        anchorProbes.add(pt)
                    }
                }

                if (actionOverride != null && actionOverride.primaryAnchorPoints.isNotEmpty()) {
                    actionOverride.primaryAnchorPoints.forEach { pt ->
                        if (anchorProbes.none { p -> abs(p.x - pt.x) < 16f && abs(p.y - pt.y) < 16f }) {
                            anchorProbes.add(pt)
                        }
                    }
                }
            }

            preparedContexts.add(
                PreparedTemplateContext(
                    tPath = tPath,
                    tBmp = tBmp,
                    features = features,
                    templateThreshold = templateThreshold,
                    isShapeDriven = isShapeDriven,
                    scanLimitX = scanLimitX,
                    scanLimitY = scanLimitY,
                    anchorProbes = anchorProbes
                )
            )
        }

        val featExtractMs = System.currentTimeMillis() - featStartTime
        val matchedTemplates = mutableSetOf<String>()
        var statL1Hits = 0

        // =========================================================================
        // ЭТАП 1: Умная сверхбыстрая проверка ВСЕХ масок по первичным опорным точкам
        // (TIER 0: +-12 px, затем TIER 1: +-48 px)
        // =========================================================================
        for (ctx in preparedContexts) {
            if (isCancelled()) break

            val tPath = ctx.tPath
            val features = ctx.features
            val confidentThreshold = ctx.templateThreshold
            val isShapeDriven = ctx.isShapeDriven
            val scanLimitX = ctx.scanLimitX
            val scanLimitY = ctx.scanLimitY

            // TIER 0: Точечная проверка в окрестности опорной точки (+-12 px, < 1 мс)
            var tier0Found = false
            for (probe in ctx.anchorProbes) {
                val expLeft = (probe.x - features.centroidX).toInt().coerceIn(roiMinX, scanLimitX)
                val expTop = (probe.y - features.centroidY).toInt().coerceIn(roiMinY, scanLimitY)

                var bestMicroScore = 0f
                var bestMicroX = expLeft
                var bestMicroY = expTop

                for (dy in -12..12) {
                    val qy = (expTop + dy).coerceIn(roiMinY, scanLimitY)
                    for (dx in -12..12) {
                        val qx = (expLeft + dx).coerceIn(roiMinX, scanLimitX)
                        val score = evaluateCandidateScore(sPixels, sw, sh, qx, qy, features, isShapeDriven, useDeltaE = isDeltaEMode, selectedZones = selectedRoiZones, selectedAnchors = selectedAnchors)
                        if (score > bestMicroScore) {
                            bestMicroScore = score
                            bestMicroX = qx
                            bestMicroY = qy
                        }
                    }
                }

                if (bestMicroScore >= confidentThreshold) {
                    val candidate = MatchCandidate(
                        bestMicroX + features.centroidX, bestMicroY + features.centroidY,
                        bestMicroX, bestMicroY, bestMicroX + features.tw, bestMicroY + features.th,
                        bestMicroScore, tPath,
                        if (features.useCustomOffsetFromMeta) features.clickOffsetXFromMeta else 0f,
                        if (features.useCustomOffsetFromMeta) features.clickOffsetYFromMeta else 0f,
                        isShapeDriven
                    )
                    allCandidates.add(candidate)
                    recordFoundLocation(tPath, candidate.clickX, candidate.clickY)
                    tier0Found = true
                    statL0Hits++
                    matchedTemplates.add(tPath)
                    if (!findAllMatches && candidate.score >= confidentThreshold) {
                        val searchRoi = if (hasRoi) Rect(roiMinX, roiMinY, roiMaxX, roiMaxY) else null
                        val downscaleTag = if (lastUsedDownscale > 1) " [Сжатие: ${lastUsedDownscale}x]" else " [Сжатие: 1:1]"
                        val elapsed = System.currentTimeMillis() - perfStartTime
                        lastDiagnostics = SearchDiagnostics(
                            roi = searchRoi,
                            bestCandidate = candidate,
                            rejectedCandidates = emptyList(),
                            thresholdPct = defaultSim,
                            methodName = ((if (isDeltaEMode) "ГИБРИД + УМНЫЙ ЦВЕТ (ΔE)" else if (actionOverride?.isShapeOnlyMode == true) "ФОРМА / КОНТУР" else "ГИБРИД CV") + downscaleTag),
                            downscaleFactor = lastUsedDownscale,
                            elapsedMs = elapsed
                        )
                        AppLogger.log(
                            null,
                            "CV_PROFILER",
                            String.format(
                                java.util.Locale.US,
                                "Мультипоиск [%d масок]: %dms [Дескрипторы=%dms, L0=%d, L1=0, L2=0] | Результат: 1 целей (Победитель: %s - %.1f%%)",
                                templates.size,
                                elapsed,
                                featExtractMs,
                                statL0Hits,
                                candidate.templateName,
                                candidate.score * 100f
                            )
                        )
                        return listOf(candidate)
                    }
                }
            }
            if (tier0Found && !findAllMatches) continue

            // TIER 1: Быстрая двухпроходная окрестность UI (в пределах ROI или +-48 px)
            var tier1Found = false
            val primaryProbes = ctx.anchorProbes.take(3)
            val isRoiActive = hasRoi || (actionOverride?.isStrictRoiOnly == true)
            val localRange = if (isRoiActive) {
                val roiW = (scanLimitX - roiMinX).coerceAtLeast(100)
                val roiH = (scanLimitY - roiMinY).coerceAtLeast(100)
                max(roiW, roiH) / 2 + 16
            } else 48

            val l1Step = if (isRoiActive) {
                (min(features.tw, features.th) / 3).coerceIn(4, 16)
            } else 12

            for (probe in primaryProbes) {
                val expLeft = (probe.x - features.centroidX).toInt().coerceIn(roiMinX, scanLimitX)
                val expTop = (probe.y - features.centroidY).toInt().coerceIn(roiMinY, scanLimitY)

                var bestLocalScore = 0f
                var bestLocalX = expLeft
                var bestLocalY = expTop

                for (dy in -localRange..localRange step l1Step) {
                    val qy = (expTop + dy).coerceIn(roiMinY, scanLimitY)
                    for (dx in -localRange..localRange step l1Step) {
                        val qx = (expLeft + dx).coerceIn(roiMinX, scanLimitX)
                        val score = evaluateCandidateScore(sPixels, sw, sh, qx, qy, features, isShapeDriven, useDeltaE = isDeltaEMode, selectedZones = selectedRoiZones, selectedAnchors = selectedAnchors)
                        if (score > bestLocalScore) {
                            bestLocalScore = score
                            bestLocalX = qx
                            bestLocalY = qy
                        }
                    }
                }

                if (bestLocalScore >= (confidentThreshold * 0.65f)) {
                    val peakX = bestLocalX
                    val peakY = bestLocalY
                    for (fdy in -2..2) {
                        val fqy = (peakY + fdy).coerceIn(roiMinY, scanLimitY)
                        for (fdx in -2..2) {
                            val fqx = (peakX + fdx).coerceIn(roiMinX, scanLimitX)
                            val score = evaluateCandidateScore(sPixels, sw, sh, fqx, fqy, features, isShapeDriven, useDeltaE = isDeltaEMode, selectedZones = selectedRoiZones, selectedAnchors = selectedAnchors)
                            if (score > bestLocalScore) {
                                bestLocalScore = score
                                bestLocalX = fqx
                                bestLocalY = fqy
                            }
                        }
                    }
                }

                if (bestLocalScore >= confidentThreshold) {
                    val candidate = MatchCandidate(
                        bestLocalX + features.centroidX, bestLocalY + features.centroidY,
                        bestLocalX, bestLocalY, bestLocalX + features.tw, bestLocalY + features.th,
                        bestLocalScore, tPath,
                        if (features.useCustomOffsetFromMeta) features.clickOffsetXFromMeta else 0f,
                        if (features.useCustomOffsetFromMeta) features.clickOffsetYFromMeta else 0f,
                        isShapeDriven
                    )
                    allCandidates.add(candidate)
                    recordFoundLocation(tPath, candidate.clickX, candidate.clickY)
                    tier1Found = true
                    statL1Hits++
                    matchedTemplates.add(tPath)
                    if (!findAllMatches) {
                        val searchRoi = if (hasRoi) Rect(roiMinX, roiMinY, roiMaxX, roiMaxY) else null
                        val downscaleTag = if (lastUsedDownscale > 1) " [Сжатие: ${lastUsedDownscale}x]" else " [Сжатие: 1:1]"
                        val elapsed = System.currentTimeMillis() - perfStartTime
                        lastDiagnostics = SearchDiagnostics(
                            roi = searchRoi,
                            bestCandidate = candidate,
                            rejectedCandidates = emptyList(),
                            thresholdPct = defaultSim,
                            methodName = ((if (isDeltaEMode) "ГИБРИД + УМНЫЙ ЦВЕТ (ΔE)" else if (actionOverride?.isShapeOnlyMode == true) "ФОРМА / КОНТУР" else "ГИБРИД CV") + downscaleTag),
                            downscaleFactor = lastUsedDownscale,
                            elapsedMs = elapsed
                        )
                        AppLogger.log(
                            null,
                            "CV_PROFILER",
                            String.format(
                                java.util.Locale.US,
                                "Мультипоиск [%d масок]: %dms [Дескрипторы=%dms, L0=%d, L1=%d, L2=0] | Результат: 1 целей (Победитель: %s - %.1f%%)",
                                templates.size,
                                elapsed,
                                featExtractMs,
                                statL0Hits,
                                statL1Hits,
                                candidate.templateName,
                                candidate.score * 100f
                            )
                        )
                        return listOf(candidate)
                    }
                    recordFoundLocation(tPath, candidate.clickX, candidate.clickY)
                    tier1Found = true
                    statL1Hits++
                    matchedTemplates.add(tPath)
                    if (!findAllMatches) break
                }
            }
            if (tier1Found && !findAllMatches) continue
        }

        if (!findAllMatches && allCandidates.isNotEmpty()) {
            val finalMatches = nmsFilter(allCandidates.toSet())
            val topWinner = finalMatches.firstOrNull()
            val elapsed = System.currentTimeMillis() - perfStartTime
            val searchRoi = if (hasRoi) Rect(roiMinX, roiMinY, roiMaxX, roiMaxY) else null
            val downscaleTag = if (lastUsedDownscale > 1) " [Сжатие: ${lastUsedDownscale}x]" else " [Сжатие: 1:1]"
            lastDiagnostics = SearchDiagnostics(
                roi = searchRoi,
                bestCandidate = topWinner,
                rejectedCandidates = emptyList(),
                thresholdPct = defaultSim,
                methodName = ((if (isDeltaEMode) "ГИБРИД + УМНЫЙ ЦВЕТ (ΔE)" else if (actionOverride?.isShapeOnlyMode == true) "ФОРМА / КОНТУР" else "ГИБРИД CV") + downscaleTag),
                downscaleFactor = lastUsedDownscale,
                elapsedMs = elapsed
            )
            AppLogger.log(
                null,
                "CV_PROFILER",
                String.format(
                    java.util.Locale.US,
                    "Мультипоиск [%d масок]: %dms [Дескрипторы=%dms, L0=%d, L1=%d, L2=0] | Результат: %d целей (%s)",
                    templates.size,
                    elapsed,
                    featExtractMs,
                    statL0Hits,
                    statL1Hits,
                    finalMatches.size,
                    if (topWinner != null) String.format(java.util.Locale.US, "Победитель: %s - %.1f%%", topWinner.templateName, topWinner.score * 100f) else "Не найдено"
                )
            )
            return finalMatches
        }

        // =========================================================================
        // ЭТАП 2: Адаптивное регулярное сеточное сканирование (TIER 2 Grid Scan)
        // Запускается ТОЛЬКО для масок, еще не найденных на опорных точках, если не включен "Только ROI шаблона"!
        // =========================================================================
        val isStrictRoi = hasRoi || (actionOverride?.isStrictRoiOnly == true)
        val contextsForGridScan = preparedContexts.filter { !matchedTemplates.contains(it.tPath) }

        for (ctx in contextsForGridScan) {
            if (isCancelled()) break
            if (isStrictRoi) continue

            val tPath = ctx.tPath
            val features = ctx.features
            val templateThreshold = ctx.templateThreshold
            val isShapeDriven = ctx.isShapeDriven
            val scanLimitX = ctx.scanLimitX
            val scanLimitY = ctx.scanLimitY

            val isSmall = features.isMicroIcon || features.isThinLine
            val safeCoarseThreshold = if (isSmall) {
                (templateThreshold * 0.20f).coerceIn(0.12f, 0.26f)
            } else {
                (templateThreshold * 0.22f).coerceIn(0.15f, 0.28f)
            }

            val metaStep = features.optimalGridStepFromMeta
            val minStep = 2
            val baseStepX = if (metaStep in 2..16) metaStep else (if (isSmall) (features.tw / 3).coerceIn(2, 10) else (features.tw / 2).coerceIn(6, 28))
            val baseStepY = if (metaStep in 2..16) metaStep else (if (isSmall) (features.th / 3).coerceIn(2, 10) else (features.th / 2).coerceIn(6, 28))
            val gridStepX = baseStepX
            val gridStepY = baseStepY
            val spatialSectors = HashMap<Long, Triple<Int, Int, Float>>()
            val sectorBinSize = if (isSmall) 16 else 32

            val estCount = ((scanLimitY - roiMinY) / gridStepY + 1) * ((scanLimitX - roiMinX) / gridStepX + 1)
            val scanPoints = ArrayList<Pair<Int, Int>>(maxOf(16, estCount))
            for (gy in roiMinY..scanLimitY step gridStepY) {
                for (gx in roiMinX..scanLimitX step gridStepX) {
                    scanPoints.add(Pair(gx, gy))
                }
            }

            if (selectedRoiZones.isNotEmpty() || selectedAnchors.isNotEmpty()) {
                scanPoints.sortBy { pt ->
                    val cx = pt.first + features.centroidX
                    val cy = pt.second + features.centroidY
                    var minD = Float.MAX_VALUE
                    for (z in selectedRoiZones) {
                        if (z.contains(cx, cy)) {
                            minD = 0f
                            break
                        }
                        val zX = z.centerX().toFloat()
                        val zY = z.centerY().toFloat()
                        val d = hypot((cx - zX).toDouble(), (cy - zY).toDouble()).toFloat()
                        if (d < minD) minD = d
                    }
                    for (a in selectedAnchors) {
                        val d = hypot((cx - a.x).toDouble(), (cy - a.y).toDouble()).toFloat()
                        if (d < minD) minD = d
                    }
                    minD
                }
            }
            val coarseStep = maxOf(gridStepX, gridStepY)
            val effectiveDownscale = override?.downscaleFactor?.takeIf { it in 1..4 } ?: features.optimalDownscaleFactorFromMeta
            lastUsedDownscale = effectiveDownscale
            val sampleStride = if (isSmall) 1 else (effectiveDownscale / 2).coerceIn(1, 2)

            for (pt in scanPoints) {
                if (isCancelled()) break
                val x = pt.first
                val y = pt.second
                val rowOffset = y * sw

                var colorScoreSum = 0f
                var edgeEnergyHits = 0
                var gradSampleCount = 0
                var evaluatedCount = 0

                for (i in 0 until features.l1SampleCount step sampleStride) {
                    evaluatedCount++
                    val px = x + features.l1Dx[i]
                    val py = y + features.l1Dy[i]
                    val sIdx = rowOffset + (features.l1Dy[i] * sw) + px

                    if (!isShapeDriven && sIdx in sPixels.indices) {
                        val sc = sPixels[sIdx]
                        if (isDeltaEMode || features.isSmartColorFromMeta) {
                            val dE = if (features.l1LabL.isNotEmpty()) {
                                ColorDetector.computeDeltaEFast(sc, features.l1LabL[i], features.l1LabA[i], features.l1LabB[i])
                            } else {
                                val expColor = (features.l1R[i] shl 16) or (features.l1G[i] shl 8) or features.l1B[i]
                                ColorDetector.computeDeltaE(sc, expColor).toFloat()
                            }
                            colorScoreSum += when {
                                dE <= 10.0f -> 1.0f
                                dE <= 30.0f -> (1.0f - ((dE - 10.0f) / 20.0f))
                                else -> 0.0f
                            }
                        } else {
                            val totalDiff = abs(((sc ushr 16) and 0xFF) - features.l1R[i]) +
                                    abs(((sc ushr 8) and 0xFF) - features.l1G[i]) +
                                    abs((sc and 0xFF) - features.l1B[i])
                            colorScoreSum += if (totalDiff <= 25) 1.0f else (1.0f - (totalDiff - 25) / 125.0f).coerceIn(0.0f, 1.0f)
                        }
                    }

                    if (features.l1HasGrad[i]) {
                        gradSampleCount++
                        var isHit = false
                        for (d in 0..4) {
                            val nx = px + NEIGHBOR_DX[d]
                            val ny = py + NEIGHBOR_DY[d]
                            if (nx in 1 until sw - 1 && ny in 1 until sh - 1) {
                                val nIdx = ny * sw + nx
                                val pL = sPixels[nIdx - 1]
                                val pR = sPixels[nIdx + 1]
                                val pT = sPixels[nIdx - sw]
                                val pB = sPixels[nIdx + sw]
                                val lumaL = (((pL ushr 16) and 0xFF) * 77 + ((pL ushr 8) and 0xFF) * 150 + (pL and 0xFF) * 29) ushr 8
                                val lumaR = (((pR ushr 16) and 0xFF) * 77 + ((pR ushr 8) and 0xFF) * 150 + (pR and 0xFF) * 29) ushr 8
                                val lumaT = (((pT ushr 16) and 0xFF) * 77 + ((pT ushr 8) and 0xFF) * 150 + (pT and 0xFF) * 29) ushr 8
                                val lumaB = (((pB ushr 16) and 0xFF) * 77 + ((pB ushr 8) and 0xFF) * 150 + (pB and 0xFF) * 29) ushr 8

                                val sgx = (lumaR - lumaL).toFloat()
                                val sgy = (lumaB - lumaT).toFloat()
                                val smag = abs(sgx) + abs(sgy)
                                if (smag > 12f) {
                                    val dot = abs(sgx * features.l1Gx[i] + sgy * features.l1Gy[i]) / smag
                                    if (dot > 0.50f) {
                                        isHit = true
                                        break
                                    }
                                }
                            }
                        }
                        if (isHit) edgeEnergyHits++
                    }
                }

                val colorScore = if (!isShapeDriven && evaluatedCount > 0) colorScoreSum / evaluatedCount.toFloat() else 0f
                val edgeEnergyRatio = if (gradSampleCount > 0) edgeEnergyHits.toFloat() / gradSampleCount.toFloat() else 0.5f
                val coarseScore = if (isShapeDriven) edgeEnergyRatio else (colorScore * (1.0f - features.analysis.suggestedSobelWeight) + edgeEnergyRatio * features.analysis.suggestedSobelWeight)

                if (coarseScore >= safeCoarseThreshold) {
                    val sectorKey = ((y / sectorBinSize).toLong() shl 32) or ((x / sectorBinSize).toLong() and 0xFFFFFFFFL)
                    val prev = spatialSectors[sectorKey]
                    if (prev == null || coarseScore > prev.third) {
                        spatialSectors[sectorKey] = Triple(x, y, coarseScore)
                    }
                }
            }

            statL1Sectors += spatialSectors.size
            val sectorLimit = if (findAllMatches) 48 else maxOf(16, templates.size * 3)
            val sectorsToScan = if (selectedRoiZones.isNotEmpty() || selectedAnchors.isNotEmpty()) {
                spatialSectors.values.sortedByDescending { spot ->
                    val sX = spot.first + features.centroidX
                    val sY = spot.second + features.centroidY
                    var minDist = Float.MAX_VALUE
                    var inside = false
                    for (z in selectedRoiZones) {
                        if (z.contains(sX, sY)) {
                            inside = true
                            minDist = 0f
                            break
                        }
                        val d = hypot((sX - z.centerX()).toDouble(), (sY - z.centerY()).toDouble()).toFloat()
                        if (d < minDist) minDist = d
                    }
                    for (a in selectedAnchors) {
                        val d = hypot((sX - a.x).toDouble(), (sY - a.y).toDouble()).toFloat()
                        if (d < minDist) minDist = d
                        if (d < 30f) inside = true
                    }
                    val distWeight = if (inside) 1.25f else (1.0f - (minDist / (sw * 0.75f)).coerceIn(0f, 0.40f))
                    spot.third * distWeight
                }.take(sectorLimit)
            } else {
                spatialSectors.values.sortedByDescending { it.third }.take(sectorLimit)
            }

            val searchRadius = minOf(coarseStep, 8)
            for (spot in sectorsToScan) {
                if (isCancelled()) break
                var bestScore = 0f
                var bestX = spot.first
                var bestY = spot.second

                val fineStep = if (searchRadius >= 6) 2 else 1
                val startX = (spot.first - searchRadius).coerceIn(roiMinX, scanLimitX)
                val endX = (spot.first + searchRadius).coerceIn(startX, scanLimitX)
                val startY = (spot.second - searchRadius).coerceIn(roiMinY, scanLimitY)
                val endY = (spot.second + searchRadius).coerceIn(startY, scanLimitY)

                for (fy in startY..endY step fineStep) {
                    for (fx in startX..endX step fineStep) {
                        statL2Evals++
                        val score = evaluateCandidateScore(sPixels, sw, sh, fx, fy, features, isShapeDriven, useDeltaE = isDeltaEMode, selectedZones = selectedRoiZones, selectedAnchors = selectedAnchors)
                        if (score > bestScore) {
                            bestScore = score
                            bestX = fx
                            bestY = fy
                        }
                    }
                }

                if (fineStep > 1 && bestScore >= (templateThreshold * 0.70f)) {
                    val peakX = bestX
                    val peakY = bestY
                    val fineStartX = (peakX - 2).coerceIn(roiMinX, scanLimitX)
                    val fineEndX = (peakX + 2).coerceIn(fineStartX, scanLimitX)
                    val fineStartY = (peakY - 2).coerceIn(roiMinY, scanLimitY)
                    val fineEndY = (peakY + 2).coerceIn(fineStartY, scanLimitY)

                    for (fy in fineStartY..fineEndY) {
                        for (fx in fineStartX..fineEndX) {
                            statL2Evals++
                            val score = evaluateCandidateScore(sPixels, sw, sh, fx, fy, features, isShapeDriven, useDeltaE = isDeltaEMode, selectedZones = selectedRoiZones, selectedAnchors = selectedAnchors)
                            if (score > bestScore) {
                                bestScore = score
                                bestX = fx
                                bestY = fy
                            }
                        }
                    }
                }

                if (bestScore >= templateThreshold) {
                    val candidate = MatchCandidate(
                        bestX + features.centroidX, bestY + features.centroidY,
                        bestX, bestY, bestX + features.tw, bestY + features.th,
                        bestScore, tPath,
                        if (features.useCustomOffsetFromMeta) features.clickOffsetXFromMeta else 0f,
                        if (features.useCustomOffsetFromMeta) features.clickOffsetYFromMeta else 0f,
                        isShapeDriven
                    )
                    allCandidates.add(candidate)
                    recordFoundLocation(tPath, candidate.clickX, candidate.clickY)
                    if (!findAllMatches && bestScore >= maxOf(0.92f, templateThreshold)) {
                        break
                    }
                } else if (bestScore >= 0.35f) {
                    val rejectCand = MatchCandidate(
                        bestX + features.centroidX, bestY + features.centroidY,
                        bestX, bestY, bestX + features.tw, bestY + features.th,
                        bestScore, tPath,
                        if (features.useCustomOffsetFromMeta) features.clickOffsetXFromMeta else 0f,
                        if (features.useCustomOffsetFromMeta) features.clickOffsetYFromMeta else 0f,
                        isShapeDriven
                    )
                    val reason = if (isDeltaEMode && !isShapeDriven) "Отличие цвета (ΔE)" else "Ниже порога (${(bestScore * 100).toInt()}% < ${(templateThreshold * 100).toInt()}%)"
                    rejectedCandidatesList.add(Pair(rejectCand, reason))
                }
            }
        }

        val finalMatches = nmsFilter(allCandidates.toSet())
        val topWinner = finalMatches.firstOrNull()

        val elapsed = System.currentTimeMillis() - perfStartTime
        val searchRoi = if (hasRoi) Rect(roiMinX, roiMinY, roiMaxX, roiMaxY) else null
        val downscaleTag = if (lastUsedDownscale > 1) " [Сжатие: ${lastUsedDownscale}x]" else " [Сжатие: 1:1]"
        lastDiagnostics = SearchDiagnostics(
            roi = searchRoi,
            bestCandidate = topWinner,
            rejectedCandidates = rejectedCandidatesList.sortedByDescending { it.first.score }.take(3),
            thresholdPct = defaultSim,
            methodName = ((if (isDeltaEMode) "ГИБРИД + УМНЫЙ ЦВЕТ (ΔE)" else if (actionOverride?.isShapeOnlyMode == true) "ФОРМА / КОНТУР" else "ГИБРИД CV") + downscaleTag),
            downscaleFactor = lastUsedDownscale,
            elapsedMs = elapsed
        )
        AppLogger.log(
            null,
            "CV_PROFILER",
            String.format(
                java.util.Locale.US,
                "Мультипоиск [%d масок]: %dms [Дескрипторы=%dms, L0=%d, L1=%d, L2=%d] | Результат: %d целей (%s)",
                templates.size,
                elapsed,
                featExtractMs,
                statL0Hits,
                statL1Sectors,
                statL2Evals,
                finalMatches.size,
                if (topWinner != null) String.format(java.util.Locale.US, "Победитель: %s - %.1f%%", topWinner.templateName, topWinner.score * 100f) else "Не найдено"
            )
        )

        finalMatches
        } catch (t: Throwable) {
            AppLogger.logError(null, "CV_SEARCH", t)
            emptyList()
        }
    }

    private fun nmsFilter(candidates: Set<MatchCandidate>): List<MatchCandidate> {
        val sortedList = candidates.sortedByDescending { it.score }
        val nmsList = ArrayList<MatchCandidate>()
        for (cand in sortedList) {
            if (nmsList.none { it.rectLeft < cand.rectRight && it.rectRight > cand.rectLeft && it.rectTop < cand.rectBottom && it.rectBottom > cand.rectTop }) {
                nmsList.add(cand)
            }
        }
        return nmsList.take(32)
    }

    fun evaluateCandidateScore(
        sPixels: IntArray, sw: Int, sh: Int, fx: Int, fy: Int,
        features: TemplateFeatures, isShapeOnly: Boolean = false,
        useDeltaE: Boolean = false,
        selectedZones: List<Rect> = emptyList(),
        selectedAnchors: List<com.example.autotap.domain.model.Point2D> = emptyList()
    ): Float {
        if (fx < 0 || fy < 0 || fx + features.tw > sw || fy + features.th > sh) return 0f

        // [V200.0] Быстрый векторный предпросмотр кластера ключевых пикселей (раннее отсечение 92% неверных кандидатов)
        if (features.keyClusterIndices.isNotEmpty() && !isShapeOnly) {
            var clusterDiff = 0
            val cSize = features.keyClusterIndices.size
            for (idx in features.keyClusterIndices) {
                val px = fx + features.shapeDx[idx]
                val py = fy + features.shapeDy[idx]
                val sColor = sPixels[py * sw + px]
                val dr = abs(((sColor ushr 16) and 0xFF) - features.shapeR[idx])
                val dg = abs(((sColor ushr 8) and 0xFF) - features.shapeG[idx])
                val db = abs((sColor and 0xFF) - features.shapeB[idx])
                clusterDiff += dr + dg + db
            }
            if (clusterDiff / cSize > 200) return 0f
        }

        var colorScoreSum = 0f
        var colorWeightSum = 0f
        var edgeScoreSum = 0f
        var edgeWeightSum = 0f
        var hitQ0 = 0
        var hitQ1 = 0
        var hitQ2 = 0
        var hitQ3 = 0
        var screenTotalEdges = 0
        val evalStride = if (features.isMicroIcon || features.isThinLine || features.tw <= 36) 1 else max(1, features.shapeCount / 90)
        val isDeltaEActive = useDeltaE || features.isSmartColorFromMeta

        for (k in 0 until features.shapeCount step evalStride) {
            val px = fx + features.shapeDx[k]
            val py = fy + features.shapeDy[k]
            val sIdx = py * sw + px
            val sColor = sPixels[sIdx]

            if (!isShapeOnly) {
                if (isDeltaEActive) {
                    val dESq = if (features.shapeLabL.isNotEmpty()) {
                        ColorDetector.computeDeltaESquaredFast(sColor, features.shapeLabL[k], features.shapeLabA[k], features.shapeLabB[k])
                    } else {
                        val expColor = (features.shapeR[k] shl 16) or (features.shapeG[k] shl 8) or features.shapeB[k]
                        val de = ColorDetector.computeDeltaE(sColor, expColor).toFloat()
                        de * de
                    }
                    val ptColor = when {
                        dESq <= 64.0f -> 1.0f
                        dESq <= 784.0f -> (1.0f - ((sqrt(dESq) - 8.0f) / 20.0f))
                        else -> 0.0f
                    }
                    val spatialWeight = if (features.spatialWeights.size > k) features.spatialWeights[k] else 1.0f
                    colorScoreSum += ptColor * spatialWeight
                    colorWeightSum += spatialWeight
                } else {
                    val dr = abs(((sColor ushr 16) and 0xFF) - features.shapeR[k])
                    val dg = abs(((sColor ushr 8) and 0xFF) - features.shapeG[k])
                    val db = abs((sColor and 0xFF) - features.shapeB[k])
                    colorScoreSum += when {
                        (dr + dg + db) <= 25 -> 1.0f
                        (dr + dg + db) <= 150 -> 1.0f - ((dr + dg + db) - 25) / 125.0f
                        else -> 0.0f
                    }
                    colorWeightSum += 1.0f
                }
            }

            if (px in 1 until sw - 1 && py in 1 until sh - 1) {
                val c00 = sPixels[(py - 1) * sw + (px - 1)]
                val p00 = (((c00 ushr 16) and 0xFF) * 77 + ((c00 ushr 8) and 0xFF) * 150 + (c00 and 0xFF) * 29) shr 8
                val c01 = sPixels[(py - 1) * sw + px]
                val p01 = (((c01 ushr 16) and 0xFF) * 77 + ((c01 ushr 8) and 0xFF) * 150 + (c01 and 0xFF) * 29) shr 8
                val c02 = sPixels[(py - 1) * sw + (px + 1)]
                val p02 = (((c02 ushr 16) and 0xFF) * 77 + ((c02 ushr 8) and 0xFF) * 150 + (c02 and 0xFF) * 29) shr 8

                val c10 = sPixels[py * sw + (px - 1)]
                val p10 = (((c10 ushr 16) and 0xFF) * 77 + ((c10 ushr 8) and 0xFF) * 150 + (c10 and 0xFF) * 29) shr 8
                val c12 = sPixels[py * sw + (px + 1)]
                val p12 = (((c12 ushr 16) and 0xFF) * 77 + ((c12 ushr 8) and 0xFF) * 150 + (c12 and 0xFF) * 29) shr 8

                val c20 = sPixels[(py + 1) * sw + (px - 1)]
                val p20 = (((c20 ushr 16) and 0xFF) * 77 + ((c20 ushr 8) and 0xFF) * 150 + (c20 and 0xFF) * 29) shr 8
                val c21 = sPixels[(py + 1) * sw + px]
                val p21 = (((c21 ushr 16) and 0xFF) * 77 + ((c21 ushr 8) and 0xFF) * 150 + (c21 and 0xFF) * 29) shr 8
                val c22 = sPixels[(py + 1) * sw + (px + 1)]
                val p22 = (((c22 ushr 16) and 0xFF) * 77 + ((c22 ushr 8) and 0xFF) * 150 + (c22 and 0xFF) * 29) shr 8

                val sgx = (p02 + 2 * p12 + p22 - (p00 + 2 * p10 + p20)).toFloat()
                val sgy = (p20 + 2 * p21 + p22 - (p00 + 2 * p01 + p02)).toFloat()
                val smag = sqrt((sgx * sgx + sgy * sgy).toDouble()).toFloat()

                if (smag > 8.0f) screenTotalEdges++

                if (features.shapeGradMag[k] > 5.0f) {
                    val conf = (smag / 20f).coerceIn(0.25f, 1.0f)
                    edgeWeightSum += conf

                    if (smag > 6.0f) {
                        val rawCos = (abs(sgx * features.shapeGradGx[k] + sgy * features.shapeGradGy[k]) / smag).coerceIn(0f, 1f)
                        val shapeAlignment = if (rawCos >= 0.35f) {
                            ((rawCos - 0.35f) / 0.65f).coerceIn(0f, 1f)
                        } else 0f

                        if (shapeAlignment > 0.20f) {
                            edgeScoreSum += shapeAlignment * conf
                            when (features.shapeQuadrant[k]) {
                                0 -> hitQ0++
                                1 -> hitQ1++
                                2 -> hitQ2++
                                3 -> hitQ3++
                            }
                        }
                    }
                } else if (smag > 10.0f) {
                    edgeWeightSum += (smag / 24f).coerceIn(0.2f, 1.0f) * 0.5f
                }
            }

            if (k >= features.shapeCount / 3) {
                val evalCount = (k / evalStride) + 1
                val currentNormColor = if (colorWeightSum > 0f) (colorScoreSum / colorWeightSum) else 0f
                val currentEdgeRatio = if (edgeWeightSum > 0f) (edgeScoreSum / edgeWeightSum) else 0f
                val minColorThresh = if (features.isThinLine || features.isMicroIcon) 0.12f else 0.22f
                if (!isShapeOnly && evalCount >= 8 && currentNormColor < minColorThresh && currentEdgeRatio < 0.40f) {
                    return 0f
                }
                val minEdgeThresh = if (features.isThinLine || features.isMicroIcon) 0.10f else 0.18f
                if ((isShapeOnly || currentNormColor < 0.55f) && edgeWeightSum > 3f && currentEdgeRatio < minEdgeThresh) {
                    return 0f
                }
            }
        }

        var missingQuadrants = 0
        if (features.hasQuadrant0 && hitQ0 == 0) missingQuadrants++
        if (features.hasQuadrant1 && hitQ1 == 0) missingQuadrants++
        if (features.hasQuadrant2 && hitQ2 == 0) missingQuadrants++
        if (features.hasQuadrant3 && hitQ3 == 0) missingQuadrants++

        val edScore = if (edgeWeightSum > 0f) (edgeScoreSum / edgeWeightSum).coerceIn(0f, 1f) else 0.5f

        val normalizedScreenEdges = screenTotalEdges * evalStride
        val isMicroOrThin = features.isMicroIcon || features.isThinLine || min(features.tw, features.th) <= 32
        val clutterThreshold = if (isMicroOrThin) {
            max(features.shapeEdgeCount * 5.0f, features.shapeCount * 1.2f)
        } else {
            max(features.shapeEdgeCount * 3.5f, features.shapeCount * 0.75f)
        }
        val clutterPenalty = if (normalizedScreenEdges > clutterThreshold) {
            val minFloor = if (isMicroOrThin) 0.55f else 0.35f
            (clutterThreshold / normalizedScreenEdges.toFloat()).coerceIn(minFloor, 1.0f)
        } else 1.0f

        val colScore = if (colorWeightSum > 0f) (colorScoreSum / colorWeightSum) else edScore

        if (isShapeOnly) {
            val completeness = when {
                isMicroOrThin && missingQuadrants <= 1 -> 1.0f
                isMicroOrThin && missingQuadrants == 2 -> 0.92f
                missingQuadrants >= 3 -> 0.65f
                missingQuadrants == 2 -> 0.88f
                else -> 1.0f
            }
            return (edScore * completeness * clutterPenalty).coerceIn(0f, 1f)
        }

        val effectiveEdgeWeight = when {
            isMicroOrThin && edScore >= 0.70f -> 0.55f
            isMicroOrThin && edScore >= 0.55f -> 0.45f
            features.isThinLine -> 0.45f
            features.shapeEdgeCount >= 6 && edScore >= 0.60f -> 0.40f
            edgeWeightSum > 0f -> 0.25f
            else -> 0.05f
        }
        val completenessFactor = when {
            isMicroOrThin && missingQuadrants <= 1 -> 1.0f
            isMicroOrThin && missingQuadrants == 2 -> 0.94f
            missingQuadrants >= 3 -> 0.80f
            missingQuadrants == 2 -> 0.90f
            else -> 1.0f
        }

        val bonus = if (isDeltaEActive) 1.08f else 1.05f
        val maxPenalty = 0.10f
        val distanceScale = 0.85f
        val spatialFactor = if (selectedZones.isNotEmpty() || selectedAnchors.isNotEmpty()) {
            val candCenterX = fx + features.centroidX
            val candCenterY = fy + features.centroidY
            var minDistance = Float.MAX_VALUE
            var isInsideSelected = false

            for (zone in selectedZones) {
                if (zone.contains(candCenterX, candCenterY)) {
                    isInsideSelected = true
                    minDistance = 0f
                    break
                }
                val zX = zone.centerX().toFloat()
                val zY = zone.centerY().toFloat()
                val d = hypot((candCenterX - zX).toDouble(), (candCenterY - zY).toDouble()).toFloat()
                if (d < minDistance) minDistance = d
            }

            for (pt in selectedAnchors) {
                val d = hypot((candCenterX - pt.x).toDouble(), (candCenterY - pt.y).toDouble()).toFloat()
                if (d < minDistance) minDistance = d
                if (d < 30f) isInsideSelected = true
            }

            if (isInsideSelected) bonus else (1.0f - (minDistance / (sw * distanceScale)).coerceIn(0f, maxPenalty))
        } else 1.0f

        val rawMatchScore = (colScore * (1f - effectiveEdgeWeight) + edScore * effectiveEdgeWeight) * completenessFactor
        val effectiveClutter = if (rawMatchScore >= 0.55f) 1.0f else clutterPenalty
        val combinedScore = rawMatchScore * effectiveClutter * spatialFactor
        return combinedScore.coerceIn(0f, 1f)
    }
}