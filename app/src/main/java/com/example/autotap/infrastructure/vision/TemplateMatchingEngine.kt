package com.example.autotap.infrastructure.vision

import android.graphics.Bitmap
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.MatchCandidate
import com.example.autotap.domain.model.TemplateMorphology
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt


object TemplateMatchingEngine {

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
        val clickOffsetXFromMeta: Float = 0f,
        val clickOffsetYFromMeta: Float = 0f,
        val useCustomOffsetFromMeta: Boolean = false,
        val calibratedX: Int? = null,
        val calibratedY: Int? = null
    )

    val lastMatchedPositions = ConcurrentHashMap<String, Pair<Int, Int>>()
    val additionalSearchLocations = ConcurrentHashMap<String, MutableSet<Pair<Int, Int>>>()
    private val templateFeatureCache = ConcurrentHashMap<String, TemplateFeatures>()
    fun registerDiscoveredLocation(tPath: String, x: Int, y: Int) {
        if (tPath.isEmpty()) return
        val set = additionalSearchLocations.getOrPut(tPath) { java.util.Collections.synchronizedSet(mutableSetOf()) }
        synchronized(set) {
            if (set.none { Math.abs(it.first - x) < 24 && Math.abs(it.second - y) < 24 }) {
                if (set.size >= 8) {
                    val first = set.firstOrNull()
                    if (first != null) set.remove(first)
                }
                set.add(Pair(x, y))
            }
        }
    }
    fun clearTemplateCache() {
        lastMatchedPositions.clear()
        additionalSearchLocations.clear()
        templateFeatureCache.clear()
        AppLogger.log(null, "VISION", "Кэши дескрипторов шаблонов очищены")
    }

    // Перегрузка 1: Вызов с Bitmap (основная)
    fun extractFeatures(template: Bitmap, cacheKey: String, tPath: String = "", defaultSim: Int = 80): TemplateFeatures? {
        if (template.isRecycled || template.width <= 0 || template.height <= 0) return null

        val tw = template.width
        val th = template.height
        val tPixels = IntArray(tw * th)
        template.getPixels(tPixels, 0, tw, 0, 0, tw, th)

                val metaLastMod = if (tPath.isNotEmpty()) {
            try {
                val mf = File(tPath)
                val jf = File(mf.parentFile, "${mf.nameWithoutExtension}.json")
                if (jf.exists()) jf.lastModified() else 0L
            } catch (_: Exception) { 0L }
        } else 0L

        val dynamicKey = if (cacheKey.isNotEmpty()) "${cacheKey}_${tw}x${th}_${template.generationId}_$metaLastMod"
                         else "dyn_${tw}x${th}_${template.generationId}_$metaLastMod"

        return extractFeatures(tPixels, tw, th, dynamicKey, defaultSim, tPath, false)
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
        if (tPixels.isEmpty() || tw <= 0 || th <= 0) return null

        val dynamicKey = if (cacheKey.isNotEmpty()) "${cacheKey}_${tw}x${th}_$defaultSim"
                         else "dyn_${tw}x${th}_$defaultSim"


        val cached = templateFeatureCache[dynamicKey]
        if (cached != null && cached.tw == tw && cached.th == th) return cached

        var templateSim = defaultSim
        var metaShapeOnly = false
        var metaClickX = 0f
        var metaClickY = 0f
        var metaUseOffset = false
        var metaCalibX: Int? = null
        var metaCalibY: Int? = null

        if (tPath.isNotEmpty()) {
            try {
                val maskFile = File(tPath)
                val metaFile = File(maskFile.parentFile, "${maskFile.nameWithoutExtension}.json")
                if (metaFile.exists()) {
                    val meta = JSONObject(metaFile.readText())
                    val rawSim = meta.optInt("similarityPercent", defaultSim)
                    templateSim = rawSim.coerceIn(60, 98)
                    metaShapeOnly = meta.optBoolean("isShapeOnlyMode", false)
                    metaClickX = meta.optDouble("clickOffsetX", 0.0).toFloat()
                    metaClickY = meta.optDouble("clickOffsetY", 0.0).toFloat()
                    metaUseOffset = meta.optBoolean("useCustomClickOffset", false)
                    if (meta.has("calibratedX")) metaCalibX = meta.getInt("calibratedX")
                    if (meta.has("calibratedY")) metaCalibY = meta.getInt("calibratedY")
                }
            } catch (_: Exception) {}
        }

                val area = tw * th
        val isMicro = area <= 1600 || tw <= 36 || th <= 36
        val isThin = (th in 1..20 && tw >= 20) || (tw in 1..20 && th >= 20)

        // [V12.0] Загрузка истинных RGB цветов из raw_*.png вместо бинарной маски
        var colorSourcePixels = tPixels
        if (tPath.isNotEmpty()) {
            try {
                val maskFile = java.io.File(tPath)
                if (maskFile.exists()) {
                    val rawFile = java.io.File(maskFile.parentFile, "raw_" + maskFile.name.removePrefix("mask_"))
                    if (rawFile.exists()) {
                        val rawBmp = android.graphics.BitmapFactory.decodeFile(rawFile.absolutePath)
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
            } catch (_: Exception) {}
        }

        val maxPoints = tw * th
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

        val lum = IntArray(tw * th)
        val mask = BooleanArray(tw * th)
        for (i in 0 until tw * th) {
            val c = tPixels[i]
            val a = (c ushr 24) and 0xFF
            if (a <= 25) transparentCount++
            mask[i] = a > 25
            lum[i] = if (a > 25) {
                (((c ushr 16) and 0xFF) * 299 + ((c ushr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            } else 0
        }

        val isRawImage = isRawPixelsParam || (transparentCount < (tw * th * 0.05))

        // [Adaptive Grayscale Contrast Normalization]
        // Линейное растяжение динамического диапазона яркости [min, max] -> [0, 255] и эквализация гистограммы (CDF) для масок и стрелок
        if (!isRawImage && transparentCount > 0) {
            var minL = 255
            var maxL = 0
            val fgHist = IntArray(256)
            var fgCount = 0
            for (i in 0 until tw * th) {
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
                for (i in 0 until tw * th) {
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


        val cxEstimate = tw / 2
        val cyEstimate = th / 2
        var q0EdgeCount = 0; var q1EdgeCount = 0; var q2EdgeCount = 0; var q3EdgeCount = 0

        for (ty in 0 until th) {
            val row = ty * tw
            for (tx in 0 until tw) {
                val idx = row + tx
                if (mask[idx]) {
                    val c = colorSourcePixels[idx]
                    shapeDx[shapeCount] = tx
                    shapeDy[shapeCount] = ty
                    shapeR[shapeCount] = (c ushr 16) and 0xFF
                    shapeG[shapeCount] = (c ushr 8) and 0xFF
                    shapeB[shapeCount] = c and 0xFF
                    sumX += tx
                    sumY += ty

                    val q = (if (tx >= cxEstimate) 1 else 0) or (if (ty >= cyEstimate) 2 else 0)
                    shapeQuadrant[shapeCount] = q

                    var gx = 0f
                    var gy = 0f

                    val isBorder = (tx == 0 || tx == tw - 1 || ty == 0 || ty == th - 1) ||
                            (!mask[idx - 1] || !mask[idx + 1] || !mask[idx - tw] || !mask[idx + tw])

                    if (!isRawImage && isBorder) {
                        val m00 = if (tx > 0 && ty > 0 && mask[idx - tw - 1]) 255 else 0
                        val m01 = if (ty > 0 && mask[idx - tw]) 255 else 0
                        val m02 = if (tx < tw - 1 && ty > 0 && mask[idx - tw + 1]) 255 else 0

                        val m10 = if (tx > 0 && mask[idx - 1]) 255 else 0
                        val m12 = if (tx < tw - 1 && mask[idx + 1]) 255 else 0

                        val m20 = if (tx > 0 && ty < th - 1 && mask[idx + tw - 1]) 255 else 0
                        val m21 = if (ty < th - 1 && mask[idx + tw]) 255 else 0
                        val m22 = if (tx < tw - 1 && ty < th - 1 && mask[idx + tw + 1]) 255 else 0

                        gx = (m02 + 2 * m12 + m22 - (m00 + 2 * m10 + m20)).toFloat()
                        gy = (m20 + 2 * m21 + m22 - (m00 + 2 * m01 + m02)).toFloat()
                    } else {
                        val l00 = if (tx > 0 && ty > 0) lum[idx - tw - 1] else lum[idx]
                        val l01 = if (ty > 0) lum[idx - tw] else lum[idx]
                        val l02 = if (tx < tw - 1 && ty > 0) lum[idx - tw + 1] else lum[idx]

                        val l10 = if (tx > 0) lum[idx - 1] else lum[idx]
                        val l12 = if (tx < tw - 1) lum[idx + 1] else lum[idx]

                        val l20 = if (tx > 0 && ty < th - 1) lum[idx + tw - 1] else lum[idx]
                        val l21 = if (ty < th - 1 && lum.size > idx + tw) lum[idx + tw] else lum[idx]
                        val l22 = if (tx < tw - 1 && ty < th - 1 && lum.size > idx + tw + 1) lum[idx + tw + 1] else lum[idx]

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
            clickOffsetXFromMeta = metaClickX,
            clickOffsetYFromMeta = metaClickY,
            useCustomOffsetFromMeta = metaUseOffset,
            calibratedX = metaCalibX,
            calibratedY = metaCalibY
        )
        templateFeatureCache[dynamicKey] = features
        return features
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

        private fun halton(index: Int, base: Int): Float {
        var result = 0f
        var f = 1f / base
        var i = index
        while (i > 0) {
            result += f * (i % base)
            i /= base
            f /= base
        }
        return result
    }

    fun findMultiTemplatesFastCascade(
        sPixels: IntArray, sw: Int, sh: Int, templates: List<Pair<String, Bitmap>>,
        minSimilarityPercent: Int = 30, actionOverride: MacroAction? = null,
        enableL0Cache: Boolean = true, findAllMatches: Boolean = false,
        isCancelled: () -> Boolean = { false }
    ): List<MatchCandidate> {
        if (templates.isEmpty() || sPixels.isEmpty() || sw <= 0 || sh <= 0) return emptyList()

        val perfStartTime = System.currentTimeMillis()
        var statL0Hits = 0
        var statL1Sectors = 0
        var statL2Evals = 0

        val allCandidates = ConcurrentHashMap.newKeySet<MatchCandidate>()
        val defaultSim = actionOverride?.similarityPercent ?: minSimilarityPercent

        val hasRoi = actionOverride?.roiLeft != null && actionOverride.roiTop != null &&
            actionOverride.roiRight != null && actionOverride.roiBottom != null
        val roiMinX = if (hasRoi) (actionOverride?.roiLeft ?: 0).coerceIn(0, sw - 1) else 0
        val roiMinY = if (hasRoi) (actionOverride?.roiTop ?: 0).coerceIn(0, sh - 1) else 0
        val roiMaxX = if (hasRoi) (actionOverride?.roiRight ?: sw).coerceIn(roiMinX + 1, sw) else sw
        val roiMaxY = if (hasRoi) (actionOverride?.roiBottom ?: sh).coerceIn(roiMinY + 1, sh) else sh

        val primaryPath = actionOverride?.templatePath ?: ""

        for ((tPath, tBmp) in templates) {
            if (isCancelled() || tBmp.isRecycled) continue

// [V12.2] Полная изоляция: каждый шаблон использует строго свои метаданные из JSON
            val features = extractFeatures(tBmp, tPath, tPath, defaultSim) ?: continue
            val templateThreshold = features.individualThreshold.coerceIn(0.60f, 0.98f)
            val isShapeDriven = features.isShapeOnlyFromMeta

            val scanLimitX = (roiMaxX - features.tw).coerceAtLeast(roiMinX)
            val scanLimitY = (roiMaxY - features.th).coerceAtLeast(roiMinY)

            var tier0Found = false
            val anchorProbes = mutableListOf<com.example.autotap.domain.model.Point2D>()

            // 1. БАЗОВОЕ МЕСТО ПО УМОЛЧАНИЮ: координаты создания шаблона (posX, posY / calibratedX, calibratedY)
            if (actionOverride != null && (actionOverride.posX > 1f || actionOverride.posY > 1f)) {
                anchorProbes.add(com.example.autotap.domain.model.Point2D(actionOverride.posX, actionOverride.posY))
            } else if (features.calibratedX != null && features.calibratedY != null) {
                anchorProbes.add(com.example.autotap.domain.model.Point2D(features.calibratedX.toFloat(), features.calibratedY.toFloat()))
            }

            // 2. Дополнительные динамические позиции (L0 Cache и последние обнаруженные точки)
            if (enableL0Cache && tPath.isNotEmpty()) {
                lastMatchedPositions[tPath]?.let {
                    val pt = com.example.autotap.domain.model.Point2D(it.first.toFloat(), it.second.toFloat())
                    if (anchorProbes.none { p -> abs(p.x - pt.x) < 16f && abs(p.y - pt.y) < 16f }) {
                        anchorProbes.add(pt)
                    }
                }
                additionalSearchLocations[tPath]?.forEach { loc ->
                    val pt = com.example.autotap.domain.model.Point2D(loc.first.toFloat(), loc.second.toFloat())
                    if (anchorProbes.none { p -> abs(p.x - pt.x) < 16f && abs(p.y - pt.y) < 16f }) {
                        anchorProbes.add(pt)
                    }
                }
            }

            val confidentThreshold = (templateThreshold * 0.85f).coerceAtLeast(0.65f)

            // TIER 0: Точечная проверка в окрестности опорной точки (+-12 px, < 1 мс)
            for (probe in anchorProbes) {
                val expLeft = (probe.x - features.centroidX).toInt().coerceIn(roiMinX, scanLimitX)
                val expTop = (probe.y - features.centroidY).toInt().coerceIn(roiMinY, scanLimitY)

                var bestMicroScore = 0f
                var bestMicroX = expLeft
                var bestMicroY = expTop

                for (dy in -12..12) {
                    val qy = (expTop + dy).coerceIn(roiMinY, scanLimitY)
                    for (dx in -12..12) {
                        val qx = (expLeft + dx).coerceIn(roiMinX, scanLimitX)
                        val score = evaluateCandidateScore(sPixels, sw, sh, qx, qy, features, isShapeDriven)
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
                    if (tPath.isNotEmpty()) {
                        lastMatchedPositions[tPath] = Pair(candidate.clickX, candidate.clickY)
                        val locs = additionalSearchLocations.getOrPut(tPath) { java.util.Collections.newSetFromMap(ConcurrentHashMap()) }
                        if (locs.none { abs(it.first - candidate.clickX) < 20 && abs(it.second - candidate.clickY) < 20 }) {
                            if (locs.size >= 8) locs.clear()
                            locs.add(Pair(candidate.clickX, candidate.clickY))
                        }
                    }
                                        tier0Found = true
                    statL0Hits++
                    if (!findAllMatches && templates.size == 1 && candidate.score >= 0.95f) {
                        return listOf(candidate)
                    }
                    if (!findAllMatches) break
                }
            }
            if (tier0Found && !findAllMatches) continue

            // TIER 1: Окрестность UI (+-48 px вокруг опорной точки, 3-5 мс)
            var tier1Found = false
            for (probe in anchorProbes) {
                val expLeft = (probe.x - features.centroidX).toInt().coerceIn(roiMinX, scanLimitX)
                val expTop = (probe.y - features.centroidY).toInt().coerceIn(roiMinY, scanLimitY)

                var bestLocalScore = 0f
                var bestLocalX = expLeft
                var bestLocalY = expTop
                val localRange = 48

                for (dy in -localRange..localRange step 2) {
                    val qy = (expTop + dy).coerceIn(roiMinY, scanLimitY)
                    for (dx in -localRange..localRange step 2) {
                        val qx = (expLeft + dx).coerceIn(roiMinX, scanLimitX)
                        val score = evaluateCandidateScore(sPixels, sw, sh, qx, qy, features, isShapeDriven)
                        if (score > bestLocalScore) {
                            bestLocalScore = score
                            bestLocalX = qx
                            bestLocalY = qy
                        }
                    }
                }

                val peakX = bestLocalX
                val peakY = bestLocalY
                for (fdy in -1..1) {
                    val fqy = (peakY + fdy).coerceIn(roiMinY, scanLimitY)
                    for (fdx in -1..1) {
                        val fqx = (peakX + fdx).coerceIn(roiMinX, scanLimitX)
                        val score = evaluateCandidateScore(sPixels, sw, sh, fqx, fqy, features, isShapeDriven)
                        if (score > bestLocalScore) {
                            bestLocalScore = score
                            bestLocalX = fqx
                            bestLocalY = fqy
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
                    if (tPath.isNotEmpty()) {
                        lastMatchedPositions[tPath] = Pair(candidate.clickX, candidate.clickY)
                        val locs = additionalSearchLocations.getOrPut(tPath) { java.util.Collections.newSetFromMap(ConcurrentHashMap()) }
                        if (locs.none { abs(it.first - candidate.clickX) < 20 && abs(it.second - candidate.clickY) < 20 }) {
                            if (locs.size >= 8) locs.clear()
                            locs.add(Pair(candidate.clickX, candidate.clickY))
                        }
                    }
                    tier1Found = true
                    if (!findAllMatches) break
                }
            }
            if (tier1Found && !findAllMatches) continue

                    // TIER 2: Адаптивное регулярное сеточное сканирование (Grid Scan) с ранним выходом
            val isSmall = features.isMicroIcon || features.isThinLine
            val safeCoarseThreshold = if (isSmall) {
                (templateThreshold * 0.20f).coerceIn(0.12f, 0.26f)
            } else {
                (templateThreshold * 0.30f).coerceIn(0.18f, 0.35f)
            }
                        // [V14.0] Адаптивный шаг сетки: для микроиконок и тонких линий (стрелки, крестики) шаг 3..6 px для исключения пропусков штрихов
            val gridStepX = if (isSmall) (features.tw / 4).coerceIn(3, 6) else (features.tw / 3).coerceIn(8, 24)
            val gridStepY = if (isSmall) (features.th / 4).coerceIn(3, 6) else (features.th / 3).coerceIn(8, 24)
            val spatialSectors = HashMap<Long, Triple<Int, Int, Float>>()
            val sectorBinSize = if (isSmall) 16 else 36
            var earlyExitFound = false

            val scanPoints = mutableListOf<Pair<Int, Int>>()
            // Формируем плотную регулярную сетку по области поиска
            for (gy in roiMinY..scanLimitY step gridStepY) {
                for (gx in roiMinX..scanLimitX step gridStepX) {
                    scanPoints.add(Pair(gx, gy))
                }
            }
            val coarseStep = maxOf(gridStepX, gridStepY)

            for (pt in scanPoints) {
                if (isCancelled()) break
                val x = pt.first
                val y = pt.second
                val rowOffset = y * sw

                var colorScoreSum = 0f
                var edgeEnergyHits = 0
                var gradSampleCount = 0

                for (i in 0 until features.l1SampleCount) {
                    val px = x + features.l1Dx[i]
                    val py = y + features.l1Dy[i]
                    val sIdx = rowOffset + (features.l1Dy[i] * sw) + px

                    if (!isShapeDriven && sIdx in sPixels.indices) {
                        val sc = sPixels[sIdx]
                        val totalDiff = abs(((sc ushr 16) and 0xFF) - features.l1R[i]) +
                            abs(((sc ushr 8) and 0xFF) - features.l1G[i]) +
                            abs((sc and 0xFF) - features.l1B[i])
                        colorScoreSum += if (totalDiff <= 25) 1.0f else (1.0f - (totalDiff - 25) / 125.0f).coerceIn(0.0f, 1.0f)
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
                                if (smag > 8f) {
                                    val dot = abs(sgx * features.l1Gx[i] + sgy * features.l1Gy[i]) / smag
                                    if (dot > 0.35f) {
                                        isHit = true
                                        break
                                    }
                                }
                            }
                        }
                        if (isHit) edgeEnergyHits++
                    }
                }

                val colorScore = if (!isShapeDriven) colorScoreSum / features.l1SampleCount.toFloat() else 0f
                val edgeEnergyRatio = if (gradSampleCount > 0) edgeEnergyHits.toFloat() / gradSampleCount.toFloat() else 0.5f
                val coarseScore = if (isShapeDriven) edgeEnergyRatio else (colorScore * (1.0f - features.analysis.suggestedSobelWeight) + edgeEnergyRatio * features.analysis.suggestedSobelWeight)

                if (coarseScore >= safeCoarseThreshold) {
                    val sectorKey = ((y / sectorBinSize).toLong() shl 32) or ((x / sectorBinSize).toLong() and 0xFFFFFFFFL)
                    val prev = spatialSectors[sectorKey]
                    if (prev == null || coarseScore > prev.third) {
                        spatialSectors[sectorKey] = Triple(x, y, coarseScore)
                    }

                    // Быстрая локальная доводка при высоком coarse скоре
                    if (coarseScore >= 0.70f) {
                        val fineScore = evaluateCandidateScore(sPixels, sw, sh, x, y, features, isShapeDriven)
                        if (fineScore >= 0.88f) {
                            val candidate = MatchCandidate(
                                x + features.centroidX, y + features.centroidY,
                                x, y, x + features.tw, y + features.th,
                                fineScore, tPath,
                                if (features.useCustomOffsetFromMeta) features.clickOffsetXFromMeta else 0f,
                                if (features.useCustomOffsetFromMeta) features.clickOffsetYFromMeta else 0f,
                                isShapeDriven
                            )
                            allCandidates.add(candidate)
                            if (tPath.isNotEmpty()) {
                                lastMatchedPositions[tPath] = Pair(candidate.clickX, candidate.clickY)
                            }
                            earlyExitFound = true
                            break
                        }
                    }
                }
            }


            if (!findAllMatches && earlyExitFound) continue

            statL1Sectors += spatialSectors.size
            val sectorsToScan = if (findAllMatches) {
                spatialSectors.values.sortedByDescending { it.third }.take(24)
            } else {
                spatialSectors.values.sortedByDescending { it.third }.take(10)
            }
            for (spot in sectorsToScan) {
                if (isCancelled()) break
                var bestScore = 0f
                var bestX = spot.first
                var bestY = spot.second

                for (fy in max(roiMinY, spot.second - coarseStep)..min(scanLimitY, spot.second + coarseStep)) {
                    for (fx in max(roiMinX, spot.first - coarseStep)..min(scanLimitX, spot.first + coarseStep)) {
                        statL2Evals++
                        val score = evaluateCandidateScore(sPixels, sw, sh, fx, fy, features, isShapeDriven)
                        if (score > bestScore) {
                            bestScore = score
                            bestX = fx
                            bestY = fy
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
                    if (tPath.isNotEmpty()) {
                        lastMatchedPositions[tPath] = Pair(candidate.clickX, candidate.clickY)
                    }
                    if (!findAllMatches && bestScore >= maxOf(0.92f, templateThreshold)) {
                        break
                    }
                }
            }
        }

        val finalMatches = nmsFilter(allCandidates.toSet())
        val topWinner = finalMatches.firstOrNull()
        if (topWinner != null && topWinner.templatePath.isNotEmpty() && topWinner.score >= 0.88f) {
            lastMatchedPositions[topWinner.templatePath] = Pair(topWinner.clickX, topWinner.clickY)
        }

        val elapsed = System.currentTimeMillis() - perfStartTime
        AppLogger.log(null, "CV_PROFILER", "Мультипоиск [${templates.size} масок]: ${elapsed}ms | L0=$statL0Hits, L1=$statL1Sectors, L2=$statL2Evals | Результат: ${finalMatches.size} целей")

        return finalMatches
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

        fun evaluateCandidateScore(sPixels: IntArray, sw: Int, sh: Int, fx: Int, fy: Int, features: TemplateFeatures, isShapeOnly: Boolean = false): Float {
    if (fx < 0 || fy < 0 || fx + features.tw > sw || fy + features.th > sh) return 0f

    var colorScoreSum = 0f; var edgeScoreSum = 0f; var edgeWeightSum = 0f
    var hitQ0 = 0; var hitQ1 = 0; var hitQ2 = 0; var hitQ3 = 0; var screenTotalEdges = 0
    val evalStride = if (features.isMicroIcon || features.isThinLine || features.tw <= 36) 1 else max(1, features.shapeCount / 90)

    for (k in 0 until features.shapeCount step evalStride) {
    val px = fx + features.shapeDx[k]
    val py = fy + features.shapeDy[k]
    val sIdx = py * sw + px
    val sColor = sPixels[sIdx]

    if (!isShapeOnly) {
    val dr = abs(((sColor ushr 16) and 0xFF) - features.shapeR[k])
    val dg = abs(((sColor ushr 8) and 0xFF) - features.shapeG[k])
    val db = abs((sColor and 0xFF) - features.shapeB[k])
    colorScoreSum += when {
    (dr + dg + db) <= 25 -> 1.0f
    (dr + dg + db) <= 150 -> 1.0f - ((dr + dg + db) - 25) / 125.0f
    else -> 0.0f
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

    if (features.shapeGradMag[k] > 6.0f) {
    var bestDot = if (smag > 8.0f) {
    val rawCos = (abs(sgx * features.shapeGradGx[k] + sgy * features.shapeGradGy[k]) / smag).coerceIn(0f, 1f)
    if (rawCos > 0.60f) {
    val normVal = (rawCos - 0.60f) / 0.40f
    normVal * normVal
    } else 0f
    } else 0f

    val conf = (smag / 24f).coerceIn(0.2f, 1.0f)
    edgeWeightSum += conf

    if (bestDot > 0.40f) {
    edgeScoreSum += bestDot.coerceIn(0.0f, 1.0f) * conf
    when (features.shapeQuadrant[k]) { 0 -> hitQ0++; 1 -> hitQ1++; 2 -> hitQ2++; 3 -> hitQ3++ }
    }
    } else if (smag > 10.0f) {
    edgeWeightSum += (smag / 24f).coerceIn(0.2f, 1.0f) * 0.6f
    }
    }


    if (k >= features.shapeCount / 3) {
    val evalCount = (k / evalStride) + 1
    if (!isShapeOnly && evalCount >= 8 && (colorScoreSum / evalCount.toFloat()) < 0.22f) {
    return 0f
    }
    if (edgeWeightSum > 3f && (edgeScoreSum / edgeWeightSum) < 0.20f) {
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
    val clutterThreshold = max(features.shapeEdgeCount * 3.5f, features.shapeCount * 0.75f)
    val clutterPenalty = if (normalizedScreenEdges > clutterThreshold) {
        (clutterThreshold / normalizedScreenEdges.toFloat()).coerceIn(0.35f, 1.0f)
    } else 1.0f

    val totalChecks = features.shapeCount / evalStride
    val colScore = if (totalChecks > 0) colorScoreSum / totalChecks.toFloat() else edScore

    if (isShapeOnly) {
        val completeness = if (missingQuadrants >= 3) 0.55f else if (missingQuadrants == 2) 0.85f else 1.0f
        return (edScore * completeness * clutterPenalty).coerceIn(0f, 1f)
    }

    // Мягкая комбинация: если совпадение по цвету высокое (>0.60), не отсекаем из-за размытых границ
    val effectiveEdgeWeight = if (features.shapeEdgeCount >= 6 && edScore >= 0.60f) 0.40f else if (edgeWeightSum > 0f) 0.25f else 0.05f
    val completenessFactor = if (missingQuadrants >= 3) 0.80f else if (missingQuadrants == 2) 0.90f else 1.0f

    val combinedScore = (colScore * (1f - effectiveEdgeWeight) + edScore * effectiveEdgeWeight) * completenessFactor * clutterPenalty
    return combinedScore.coerceIn(0f, 1f)
    }
}
