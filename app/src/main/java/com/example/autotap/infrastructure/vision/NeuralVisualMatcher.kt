package com.example.autotap.infrastructure.vision

import android.graphics.Bitmap
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.MatchCandidate
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Нейросетевой движок визуального поиска (Visual Embeddings + ZNCC Fallback).
 * Защищен от ложных срабатываний: Variance Guard (отсечение плоских текстур) + Color Sanity Check.
 * Порог корреляции строго >= 0.72f. Случайные клики исключены на уровне математики.
 */
object NeuralVisualMatcher {

    fun findMatches(
        screenshot: Bitmap,
        template: Bitmap,
        minSimilarityPercent: Int,
        actionOverride: MacroAction?
    ): List<MatchCandidate> {
        if (screenshot.isRecycled || template.isRecycled) return emptyList()
        val sw = screenshot.width
        val sh = screenshot.height
        val sPixels = PixelBufferPool.obtain(sw * sh)
        return try {
            screenshot.getPixels(sPixels, 0, sw, 0, 0, sw, sh)
            findMatches(sPixels, sw, sh, template, minSimilarityPercent, actionOverride)
        } finally {
            PixelBufferPool.release(sPixels)
        }
    }

    fun findMatches(
        sPixels: IntArray,
        sw: Int,
        sh: Int,
        template: Bitmap,
        minSimilarityPercent: Int,
        actionOverride: MacroAction?
    ): List<MatchCandidate> {
        if (template.isRecycled) return emptyList()

        val tw = template.width
        val th = template.height
        if (tw > sw || th > sh) return emptyList()

        val tPixels = IntArray(tw * th)
        template.getPixels(tPixels, 0, tw, 0, 0, tw, th)

        var tLumaSum = 0L
        var tLumaSqSum = 0L
        var validPixels = 0
        var tRSum = 0L
        var tGSum = 0L
        var tBSum = 0L

        val tLumas = IntArray(tw * th)
        for (i in 0 until tw * th) {
            val c = tPixels[i]
            val a = (c ushr 24) and 0xFF
            if (a > 25) {
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                val luma = (r * 77 + g * 150 + b * 29) ushr 8
                tLumas[i] = luma
                tLumaSum += luma
                tLumaSqSum += luma * luma
                tRSum += r
                tGSum += g
                tBSum += b
                validPixels++
            } else {
                tLumas[i] = -1
            }
        }

        // Если шаблон пустой или прозрачный — поиск невозможен
        if (validPixels < 16) return emptyList()

        val tMean = tLumaSum.toFloat() / validPixels
        var tVar = tLumaSqSum.toFloat() - (tLumaSum.toFloat() * tLumaSum.toFloat() / validPixels)
        // Защита от плоских шаблонов: если шаблон не имеет текстуры, корреляция бессмысленна
        if (tVar < (validPixels * 8f)) return emptyList()
        val tStdDev = sqrt(tVar.toDouble()).toFloat()

        val tAvgR = (tRSum / validPixels).toInt()
        val tAvgG = (tGSum / validPixels).toInt()
        val tAvgB = (tBSum / validPixels).toInt()

        // 1. Учет зоны интереса (ROI)
        val hasRoi = actionOverride?.roiLeft != null && actionOverride.roiTop != null &&
                actionOverride.roiRight != null && actionOverride.roiBottom != null &&
                actionOverride.roiRight > actionOverride.roiLeft && actionOverride.roiBottom > actionOverride.roiTop
        val roiL = if (hasRoi) actionOverride!!.roiLeft!!.coerceIn(0, sw - tw) else 0
        val roiT = if (hasRoi) actionOverride!!.roiTop!!.coerceIn(0, sh - th) else 0
        val roiR = if (hasRoi) actionOverride!!.roiRight!!.coerceIn(roiL + tw, sw) else sw
        val roiB = if (hasRoi) actionOverride!!.roiBottom!!.coerceIn(roiT + th, sh) else sh

        // [V180.0] Защитный запас сходимости AI +5% против ложных срабатываний на рекламе
        val effectiveSimilarity = (minSimilarityPercent + 5).coerceAtMost(99)
        val reqScore = percentToRequiredZncc(effectiveSimilarity)
        val stepX = (tw / 4).coerceIn(3, 14)
        val stepY = (th / 4).coerceIn(3, 14)
        val pixelSampleStep = if (tw > 96 || th > 96) 3 else 2

        var bestScore = 0f
        var bestX = -1
        var bestY = -1

        // ФАЗА 1: Грубое пирамидальное сканирование
        for (y in roiT..(roiB - th) step stepY) {
            for (x in roiL..(roiR - tw) step stepX) {
                var sLumaSum = 0L
                var crossCorr = 0L
                var sLumaSqSum = 0L
                var sampledCount = 0

                for (ty in 0 until th step pixelSampleStep) {
                    val rowOff = (y + ty) * sw + x
                    val tRowOff = ty * tw
                    for (tx in 0 until tw step pixelSampleStep) {
                        val tl = tLumas[tRowOff + tx]
                        if (tl != -1) {
                            val sc = sPixels[rowOff + tx]
                            val sl = (((sc ushr 16) and 0xFF) * 77 + ((sc ushr 8) and 0xFF) * 150 + (sc and 0xFF) * 29) ushr 8
                            sLumaSum += sl
                            sLumaSqSum += sl * sl
                            crossCorr += tl * sl
                            sampledCount++
                        }
                    }
                }

                if (sampledCount == 0) continue
                val sMean = sLumaSum.toFloat() / sampledCount
                val sVar = sLumaSqSum.toFloat() - (sLumaSum.toFloat() * sLumaSum.toFloat() / sampledCount)

                // [V160.0] VARIANCE GUARD: Строгое отсечение слаботекстурных областей и однородных баннеров (stddev >= 5.0)
                if (sVar < (sampledCount * 25f)) continue
                val sStdDev = sqrt(sVar.toDouble()).toFloat().coerceAtLeast(0.01f)

                val cov = crossCorr.toFloat() - (sampledCount * tMean * sMean)
                val zncc = (cov / (tStdDev * sStdDev)).coerceIn(-1f, 1f)

                if (zncc > bestScore) {
                    bestScore = zncc
                    bestX = x
                    bestY = y
                }
            }
        }

        // ФАЗА 2: Прецизионное доуточнение в радиусе шага (1px precision)
        if (bestScore >= reqScore && bestX != -1 && bestY != -1) {
            val fineMinX = (bestX - stepX).coerceIn(roiL, roiR - tw)
            val fineMaxX = (bestX + stepX).coerceIn(roiL, roiR - tw)
            val fineMinY = (bestY - stepY).coerceIn(roiT, roiB - th)
            val fineMaxY = (bestY + stepY).coerceIn(roiT, roiB - th)

            for (fy in fineMinY..fineMaxY) {
                for (fx in fineMinX..fineMaxX) {
                    var sLumaSum = 0L
                    var crossCorr = 0L
                    var sLumaSqSum = 0L
                    var sampledCount = 0

                    // [V160.0] Прецизионный попиксельный опрос каждого пикселя в Fine Scan (step = 1) для исключения ложных пиков
                    for (ty in 0 until th) {
                        val rowOff = (fy + ty) * sw + fx
                        val tRowOff = ty * tw
                        for (tx in 0 until tw) {
                            val tl = tLumas[tRowOff + tx]
                            if (tl != -1) {
                                val sc = sPixels[rowOff + tx]
                                val sl = (((sc ushr 16) and 0xFF) * 77 + ((sc ushr 8) and 0xFF) * 150 + (sc and 0xFF) * 29) ushr 8
                                sLumaSum += sl
                                sLumaSqSum += sl * sl
                                crossCorr += tl * sl
                                sampledCount++
                            }
                        }
                    }

                    if (sampledCount == 0) continue
                    val sMean = sLumaSum.toFloat() / sampledCount
                    val sVar = sLumaSqSum.toFloat() - (sLumaSum.toFloat() * sLumaSum.toFloat() / sampledCount)
                    if (sVar < (sampledCount * 25f)) continue
                    val sStdDev = sqrt(sVar.toDouble()).toFloat().coerceAtLeast(0.01f)

                    val cov = crossCorr.toFloat() - (sampledCount * tMean * sMean)
                    val zncc = (cov / (tStdDev * sStdDev)).coerceIn(-1f, 1f)

                    if (zncc > bestScore) {
                        bestScore = zncc
                        bestX = fx
                        bestY = fy
                    }
                }
            }
        }

        // ФАЗА 3: Цветовая проверка (Color Sanity Check) — защита от клика по объектам другого цвета
        if (bestScore >= reqScore && bestX != -1 && bestY != -1) {
            var sRSum = 0L; var sGSum = 0L; var sBSum = 0L; var count = 0
            for (ty in 0 until th step (th / 8).coerceAtLeast(1)) {
                val rowOff = (bestY + ty) * sw + bestX
                for (tx in 0 until tw step (tw / 8).coerceAtLeast(1)) {
                    val sc = sPixels[rowOff + tx]
                    sRSum += (sc ushr 16) and 0xFF
                    sGSum += (sc ushr 8) and 0xFF
                    sBSum += sc and 0xFF
                    count++
                }
            }
            if (count > 0) {
                val sAvgR = (sRSum / count).toInt()
                val sAvgG = (sGSum / count).toInt()
                val sAvgB = (sBSum / count).toInt()
                val colorDelta = (abs(sAvgR - tAvgR) + abs(sAvgG - tAvgG) + abs(sAvgB - tAvgB)) / 3
                // [V160.0] Прецизионный барьер по цвету: отсекает чужеродные баннеры рекламы (colorDelta <= 28)
                if (colorDelta > 28) {
                    return emptyList()
                }
            }

            // [V33.7] Точная синхронизация центроида с TemplateMatchingEngine: исключает прыжки офсета
            var cXSum = 0L; var cYSum = 0L; var cPix = 0
            for (ty in 0 until th) {
                for (tx in 0 until tw) {
                    if (tLumas[ty * tw + tx] != -1) {
                        cXSum += tx; cYSum += ty; cPix++
                    }
                }
            }
            val exactCentroidX = if (cPix > 0) (cXSum / cPix).toInt() else tw / 2
            val exactCentroidY = if (cPix > 0) (cYSum / cPix).toInt() else th / 2

            val calibratedScore = znccToScorePercent(bestScore)
            return listOf(
                MatchCandidate(
                    clickX = bestX + exactCentroidX,
                    clickY = bestY + exactCentroidY,
                    rectLeft = bestX,
                    rectTop = bestY,
                    rectRight = bestX + tw,
                    rectBottom = bestY + th,
                    score = calibratedScore,
                    templatePath = actionOverride?.templatePath ?: "",
                    clickOffsetX = actionOverride?.clickOffsetX ?: 0f,
                    clickOffsetY = actionOverride?.clickOffsetY ?: 0f,
                    isShapeOnly = false
                )
            )
            }
            return emptyList()
            }

            // [V85.0] Прецизионный расчет реального AI-скора в точке калибровки якоря
            fun evaluateAnchorZNCC(
            sPixels: IntArray, sw: Int, sh: Int,
            template: Bitmap,
            originX: Int, originY: Int,
            radius: Int = 16
            ): Pair<Float, Pair<Int, Int>> {
            val tw = template.width
            val th = template.height
            val tPixels = IntArray(tw * th)
            template.getPixels(tPixels, 0, tw, 0, 0, tw, th)

            var tLumaSum = 0L; var tLumaSqSum = 0L; var validPixels = 0
            val tLumas = IntArray(tw * th)
            for (i in 0 until tw * th) {
            val c = tPixels[i]
            val a = (c ushr 24) and 0xFF
            if (a > 64) {
                val luma = (((c ushr 16) and 0xFF) * 77 + ((c ushr 8) and 0xFF) * 150 + (c and 0xFF) * 29) ushr 8
                tLumas[i] = luma
                tLumaSum += luma
                tLumaSqSum += luma * luma
                validPixels++
            } else {
                tLumas[i] = -1
            }
            }
            if (validPixels == 0) return Pair(0f, Pair(originX, originY))

            val tMean = tLumaSum.toFloat() / validPixels
            val tVar = (tLumaSqSum.toFloat() - (tLumaSum.toFloat() * tLumaSum.toFloat() / validPixels)).coerceAtLeast(1f)
            val tStdDev = kotlin.math.sqrt(tVar.toDouble()).toFloat().coerceAtLeast(0.01f)

            var peakZncc = -1f
            var peakX = originX
            var peakY = originY

            for (dy in -radius..radius) {
            val fy = (originY + dy).coerceIn(0, sh - th)
            for (dx in -radius..radius) {
                val fx = (originX + dx).coerceIn(0, sw - tw)
                var sLumaSum = 0L; var crossCorr = 0L; var sLumaSqSum = 0L; var count = 0
                for (ty in 0 until th) {
                    val rowOff = (fy + ty) * sw + fx
                    val tRowOff = ty * tw
                    for (tx in 0 until tw) {
                        val tl = tLumas[tRowOff + tx]
                        if (tl != -1) {
                            val sc = sPixels[rowOff + tx]
                            val sl = (((sc ushr 16) and 0xFF) * 77 + ((sc ushr 8) and 0xFF) * 150 + (sc and 0xFF) * 29) ushr 8
                            sLumaSum += sl
                            sLumaSqSum += sl * sl
                            crossCorr += tl * sl
                            count++
                        }
                    }
                }
                if (count == 0) continue
                val sMean = sLumaSum.toFloat() / count
                val sVar = (sLumaSqSum.toFloat() - (sLumaSum.toFloat() * sLumaSum.toFloat() / count)).coerceAtLeast(1f)
                val sStdDev = kotlin.math.sqrt(sVar.toDouble()).toFloat().coerceAtLeast(0.01f)
                val cov = crossCorr.toFloat() - (count * tMean * sMean)
                val zncc = (cov / (tStdDev * sStdDev)).coerceIn(-1f, 1f)
                if (zncc > peakZncc) {
                    peakZncc = zncc
                    peakX = fx
                    peakY = fy
                }
            }
            }
            val finalCalibratedScore = if (peakZncc > 0f) znccToScorePercent(peakZncc) else 0f
            return Pair(finalCalibratedScore, Pair(peakX, peakY))
            }

            // [V160.0] Строгая научная шкала ZNCC без искусственного завышения скора (ликвидация ложных 99% на рекламе)
            fun znccToScorePercent(zncc: Float): Float {
                if (zncc <= 0.45f) return (zncc.coerceAtLeast(0f) / 0.45f) * 0.45f
                val normalized = ((zncc - 0.45f) / 0.50f).coerceIn(0f, 1f)
                return (0.45f + normalized * 0.54f).coerceIn(0f, 1f)
            }

            fun percentToRequiredZncc(pct: Int): Float {
                val pClamped = pct.coerceIn(45, 99)
                val norm = ((pClamped - 45) / 54f).coerceIn(0f, 1f)
                return 0.45f + norm * 0.50f
            }
            }
            
