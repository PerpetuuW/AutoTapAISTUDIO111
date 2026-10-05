package com.example.autotap.infrastructure.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.example.autotap.domain.model.TemplateArchetype
import com.example.autotap.domain.model.TemplateMorphology
import java.util.ArrayDeque
import java.util.BitSet
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object SmartMaskEngine {

    data class MaskResult(
        val bitmap: Bitmap,
        val cropOffsetX: Int = 0,
        val cropOffsetY: Int = 0,
        val cropW: Int = 0,
        val cropH: Int = 0,
        val keptPixels: Int = 0
    )

    data class AutoOptimizationResult(
        val bestMask: Bitmap,
        val cropOffsetX: Int,
        val cropOffsetY: Int,
        val bestSimilarity: Int,
        val peakMeasuredScore: Float,
        val optimalExpansion: Int,
        val optimalPadding: Int,
        val useContourMode: Boolean,
        val isShapeOnlyMode: Boolean,
        val isCircleShape: Boolean,
        val strategyExplanation: String
    )

    fun generateContourMaskWithCrop(
        src: Bitmap,
        shapeExpansion: Int = 45,
        paddingOffsetPx: Int = 2,
        isCircle: Boolean = false,
        isRawMode: Boolean = false,
        isShapeOnly: Boolean = false
    ): MaskResult {
        if (src.isRecycled || src.width <= 0 || src.height <= 0) {
            return MaskResult(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888), 0, 0, 16, 16, 256)
        }

        val w = src.width
        val h = src.height
        val totalPixels = w * h
        val pixels = IntArray(totalPixels)
        val origPixels = IntArray(totalPixels)

        try {
            src.getPixels(pixels, 0, w, 0, 0, w, h)
            System.arraycopy(pixels, 0, origPixels, 0, totalPixels)
        } catch (_: Exception) {
            return MaskResult(src.copy(Bitmap.Config.ARGB_8888, true), 0, 0, w, h, totalPixels)
        }

        if (isRawMode) {
            if (isCircle) applyCircleMask(pixels, w, h)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.setPixels(pixels, 0, w, 0, 0, w, h)
            return MaskResult(out, 0, 0, w, h, totalPixels)
        }

        val lum = IntArray(totalPixels)
        val histogram = IntArray(256)
        for (i in 0 until totalPixels) {
            val c = pixels[i]
            val l = (((c ushr 16) and 0xFF) * 299 + ((c ushr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            lum[i] = l
            histogram[l]++
        }

        var sumTotal = 0.0
        for (i in 0..255) sumTotal += i * histogram[i]

        var sumBg = 0.0
        var weightBg = 0
        var maxVariance = 0.0
        var otsuThreshold = 128

        for (t in 0..255) {
            weightBg += histogram[t]
            if (weightBg == 0) continue
            val weightFg = totalPixels - weightBg
            if (weightFg == 0) break

            sumBg += t * histogram[t]
            val meanBg = sumBg / weightBg
            val meanFg = (sumTotal - sumBg) / weightFg

            val varianceBetween = weightBg.toDouble() * weightFg.toDouble() * (meanBg - meanFg) * (meanBg - meanFg)
            if (varianceBetween > maxVariance) {
                maxVariance = varianceBetween
                otsuThreshold = t
            }
        }

        val cx = w / 2
        val cy = h / 2
        var centerLumSum = 0L
        var centerCount = 0
        val sampleR = max(2, min(w, h) / 6)
        for (dy in -sampleR..sampleR) {
            val py = cy + dy
            if (py in 0 until h) {
                for (dx in -sampleR..sampleR) {
                    val px = cx + dx
                    if (px in 0 until w) {
                        centerLumSum += lum[py * w + px]
                        centerCount++
                    }
                }
            }
        }
        val avgCenterLum = (centerLumSum / max(1, centerCount)).toInt()

        var bgPerimeterSum = 0L
        var bgPerimeterCount = 0
        for (x in 0 until w) {
            bgPerimeterSum += lum[x] + lum[(h - 1) * w + x]
            bgPerimeterCount += 2
        }
        for (y in 1 until h - 1) {
            bgPerimeterSum += lum[y * w] + lum[y * w + (w - 1)]
            bgPerimeterCount += 2
        }
        val avgPerimeterLum = (bgPerimeterSum / max(1, bgPerimeterCount)).toInt()
        val isCenterBrighter = avgCenterLum > avgPerimeterLum

        val binaryFg = BitSet(totalPixels)
        for (i in 0 until totalPixels) {
            val isFg = if (isCenterBrighter) lum[i] >= otsuThreshold else lum[i] <= otsuThreshold
            if (isFg) binaryFg.set(i)
        }

        val backgroundMask = BitSet(totalPixels)
        val bgQueue = ArrayDeque<Int>()

        for (x in 0 until w) {
            if (!binaryFg.get(x)) { bgQueue.push(x); backgroundMask.set(x) }
            val bIdx = (h - 1) * w + x
            if (!binaryFg.get(bIdx)) { bgQueue.push(bIdx); backgroundMask.set(bIdx) }
        }
        for (y in 1 until h - 1) {
            val lIdx = y * w
            if (!binaryFg.get(lIdx)) { bgQueue.push(lIdx); backgroundMask.set(lIdx) }
            val rIdx = y * w + (w - 1)
            if (!binaryFg.get(rIdx)) { bgQueue.push(rIdx); backgroundMask.set(rIdx) }
        }

        val dxArr = intArrayOf(-1, 1, 0, 0)
        val dyArr = intArrayOf(0, 0, -1, 1)

        while (bgQueue.isNotEmpty()) {
            val curr = bgQueue.pop()
            val qx = curr % w
            val qy = curr / w
            for (i in 0..3) {
                val nx = qx + dxArr[i]
                val ny = qy + dyArr[i]
                if (nx in 0 until w && ny in 0 until h) {
                    val nIdx = ny * w + nx
                    if (!binaryFg.get(nIdx) && !backgroundMask.get(nIdx)) {
                        backgroundMask.set(nIdx)
                        bgQueue.push(nIdx)
                    }
                }
            }
        }

        val dilationRadius = when {
            shapeExpansion <= 8 -> -1
            shapeExpansion <= 20 -> 0
            shapeExpansion <= 35 -> 1
            shapeExpansion <= 48 -> 2
            shapeExpansion <= 58 -> 3
            else -> 4
        }

        if (dilationRadius > 0) {
            val dilatedMask = backgroundMask.clone() as BitSet
            val borderQueue = ArrayDeque<Int>()

            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val idx = row + x
                    if (!backgroundMask.get(idx)) {
                        for (d in 0..3) {
                            val nx = x + dxArr[d]
                            val ny = y + dyArr[d]
                            if (nx in 0 until w && ny in 0 until h && backgroundMask.get(ny * w + nx)) {
                                borderQueue.push((y shl 16) or x)
                                break
                            }
                        }
                    }
                }
            }

            var currentDist = 0
            var levelSize = borderQueue.size
            while (borderQueue.isNotEmpty() && currentDist < dilationRadius) {
                val packed = borderQueue.pop()
                val qy = packed ushr 16
                val qx = packed and 0xFFFF
                for (d in 0..3) {
                    val nx = qx + dxArr[d]
                    val ny = qy + dyArr[d]
                    if (nx in 0 until w && ny in 0 until h) {
                        val nIdx = ny * w + nx
                        if (dilatedMask.get(nIdx)) {
                            dilatedMask.clear(nIdx)
                            borderQueue.addLast((ny shl 16) or nx)
                        }
                    }
                }
                levelSize--
                if (levelSize == 0) {
                    currentDist++
                    levelSize = borderQueue.size
                }
            }
            backgroundMask.and(dilatedMask)
        } else if (dilationRadius < 0) {
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val idx = row + x
                    if (!backgroundMask.get(idx)) {
                        for (d in 0..3) {
                            val nx = x + dxArr[d]
                            val ny = y + dyArr[d]
                            if (nx in 0 until w && ny in 0 until h && backgroundMask.get(ny * w + nx)) {
                                pixels[idx] = Color.TRANSPARENT
                                break
                            }
                        }
                    }
                }
            }
        }

        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        var keptCount = 0

        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val idx = row + x
                if (!backgroundMask.get(idx) && pixels[idx] != Color.TRANSPARENT) {
                    pixels[idx] = origPixels[idx]
                    keptCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                } else {
                    pixels[idx] = Color.TRANSPARENT
                }
            }
        }

        if (keptCount < 4) {
            return MaskResult(src.copy(Bitmap.Config.ARGB_8888, true), 0, 0, w, h, totalPixels)
        }


        if (isCircle) applyCircleMask(pixels, w, h)

        if (isShapeOnly) {
            // [Adaptive Grayscale Contrast Normalization]
            // Перевод переднего плана маски в контрастный ч/б градиент с эквализацией гистограммы
            var minL = 255
            var maxL = 0
            val fgHist = IntArray(256)
            var fgCount = 0
            for (i in 0 until totalPixels) {
                val a = (pixels[i] ushr 24) and 0xFF
                if (a > 25) {
                    val l = lum[i]
                    if (l < minL) minL = l
                    if (l > maxL) maxL = l
                    fgHist[l]++
                    fgCount++
                }
            }
            val lumRange = maxL - minL
            if (lumRange in 10..254 && fgCount > 0) {
                val cdf = IntArray(256)
                var cdfSum = 0
                var cdfMin = -1
                for (v in 0..255) {
                    cdfSum += fgHist[v]
                    cdf[v] = cdfSum
                    if (fgHist[v] > 0 && cdfMin == -1) cdfMin = cdfSum
                }
                val cdfDenom = (fgCount - (cdfMin.coerceAtLeast(0))).coerceAtLeast(1)
                for (i in 0 until totalPixels) {
                    val a = (pixels[i] ushr 24) and 0xFF
                    if (a > 25) {
                        val rawL = lum[i]
                        val normL = ((rawL - minL) * 255 / lumRange).coerceIn(0, 255)
                        val eqL = if (cdf[rawL] >= (cdfMin.coerceAtLeast(0))) {
                            (((cdf[rawL] - cdfMin).toDouble() / cdfDenom.toDouble()) * 255.0).toInt().coerceIn(0, 255)
                        } else normL
                        val finalL = (normL * 0.4f + eqL * 0.6f).toInt().coerceIn(0, 255)
                        pixels[i] = (a shl 24) or (finalL shl 16) or (finalL shl 8) or finalL
                    }
                }
            } else {
                for (i in 0 until totalPixels) {
                    val a = (pixels[i] ushr 24) and 0xFF
                    if (a > 25) {
                        val l = lum[i]
                        pixels[i] = (a shl 24) or (l shl 16) or (l shl 8) or l
                    }
                }
            }
        }

        val fullMask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        fullMask.setPixels(pixels, 0, w, 0, 0, w, h)


        val pad = paddingOffsetPx.coerceIn(-10, 15)
        val cropLeft = (minX - pad).coerceIn(0, maxX)
        val cropTop = (minY - pad).coerceIn(0, maxY)
        val cropRight = (maxX + 1 + pad).coerceIn(cropLeft + 1, w)
        val cropBottom = (maxY + 1 + pad).coerceIn(cropTop + 1, h)

        val cropW = (cropRight - cropLeft).coerceAtLeast(2)
        val cropH = (cropBottom - cropTop).coerceAtLeast(2)

        if (cropW >= w && cropH >= h) {
            return MaskResult(fullMask, 0, 0, w, h, keptCount)
        }

        val tightMask = Bitmap.createBitmap(fullMask, cropLeft, cropTop, cropW, cropH)
        if (tightMask != fullMask) {
            fullMask.recycle()
        }

        return MaskResult(tightMask, cropLeft, cropTop, cropW, cropH, keptCount)
    }

    fun autoOptimizeGlyphSegmentationFast(
        rawPixels: IntArray,
        tw: Int,
        th: Int,
        screenshotPixels: IntArray,
        sw: Int,
        sh: Int,
        anchorX: Int,
        anchorY: Int
    ): Pair<Int, Float> {
        val features = TemplateMatchingEngine.extractFeatures(rawPixels, tw, th, "opt_raw_${tw}x${th}", 80, "", false)
        val score = if (features != null) {
            TemplateMatchingEngine.evaluateCandidateScore(screenshotPixels, sw, sh, anchorX, anchorY, features, false)
        } else 0f
        val topScorePct = (score * 100).toInt().coerceIn(40, 100)
        val operationalSim = (topScorePct - 5).coerceIn(65, 95)
        return Pair(operationalSim, score)
    }

    fun autoOptimizeGlyphSegmentationFast(
        rawTemplate: Bitmap,
        screenshotPixels: IntArray,
        sw: Int,
        sh: Int,
        anchorX: Int,
        anchorY: Int,
        isCircle: Boolean,
        isShapeOnly: Boolean = false
    ): AutoOptimizationResult {
        val analysis = TemplateMorphologyClassifier.analyze(rawTemplate)
        val isCircularShape = isCircle || analysis.archetype == TemplateArchetype.CIRCULAR_BADGE

        var bestScore = 0f
        var bestExpansion = 35
        var bestPadding = 2
        var bestCropOffsetX = 0
        var bestCropOffsetY = 0
        var bestMaskBitmap: Bitmap? = null

        // [V12.0] Смягченные пороги расширения для текста и тонких иконок
        val testNodes = intArrayOf(5, 15, 30)

        for (exp in testNodes) {
            val res = generateContourMaskWithCrop(
                src = rawTemplate,
                shapeExpansion = exp,
                paddingOffsetPx = 2,
                isCircle = isCircularShape,
                isRawMode = false
            )

            val originX = res.cropOffsetX.coerceIn(0, (sw - res.bitmap.width).coerceAtLeast(0))
            val originY = res.cropOffsetY.coerceIn(0, (sh - res.bitmap.height).coerceAtLeast(0))

            val tw = res.bitmap.width
            val th = res.bitmap.height
            val tPixels = IntArray(tw * th)
            res.bitmap.getPixels(tPixels, 0, tw, 0, 0, tw, th)

            val features = TemplateMatchingEngine.extractFeatures(tPixels, tw, th, "opt_${exp}", 80, "", false)
            val score = if (features != null) {
                TemplateMatchingEngine.evaluateCandidateScore(screenshotPixels, sw, sh, originX, originY, features, isShapeOnly)
            } else 0f

            if (score > bestScore) {
                bestScore = score
                bestExpansion = exp
                bestPadding = 2
                bestCropOffsetX = res.cropOffsetX
                bestCropOffsetY = res.cropOffsetY
                bestMaskBitmap?.let { if (it != rawTemplate && !it.isRecycled) it.recycle() }
                bestMaskBitmap = res.bitmap
            } else {
                if (res.bitmap != rawTemplate && !res.bitmap.isRecycled) {
                    res.bitmap.recycle()
                }
            }
        }

        // [V16.0] Гарантированная альфа-маска: если оптимизатор не улучшил скор, генерируем маску без фона вместо сырого растра
        val finalMask = bestMaskBitmap ?: generateContourMaskWithCrop(
            src = rawTemplate,
            shapeExpansion = 25,
            paddingOffsetPx = 2,
            isCircle = isCircularShape,
            isRawMode = false
        ).bitmap
        val topScorePct = (bestScore * 100).toInt().coerceIn(60, 100)
        val operationalSim = (topScorePct - 5).coerceIn(80, 95)

        return AutoOptimizationResult(
            bestMask = finalMask,
            cropOffsetX = bestCropOffsetX,
            cropOffsetY = bestCropOffsetY,
            bestSimilarity = operationalSim,
            peakMeasuredScore = bestScore,
            optimalExpansion = bestExpansion,
            optimalPadding = bestPadding,
            useContourMode = true,
            isShapeOnlyMode = isShapeOnly,
            isCircleShape = isCircularShape,
            strategyExplanation = "ПИК: $topScorePct% (Порог $operationalSim%) • Охват $bestExpansion%"
        )
    }

    fun autoExtractGlyph(src: Bitmap): Bitmap {
        return generateContourMaskWithCrop(src, shapeExpansion = 35, paddingOffsetPx = 2).bitmap
    }

    fun invertMask(maskBmp: Bitmap, origBmp: Bitmap): Bitmap {
        if (maskBmp.isRecycled || origBmp.isRecycled) return maskBmp
        val w = maskBmp.width
        val h = maskBmp.height
        val pixels = IntArray(w * h)
        val origPixels = IntArray(w * h)
        maskBmp.getPixels(pixels, 0, w, 0, 0, w, h)
        origBmp.getPixels(origPixels, 0, w, 0, 0, w, h)

        for (i in 0 until w * h) {
            val alpha = (pixels[i] ushr 24) and 0xFF
            pixels[i] = if (alpha > 30) Color.TRANSPARENT else origPixels[i]
        }

        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(pixels, 0, w, 0, 0, w, h)
        return res
    }

    fun magicWandEraseAt(maskBmp: Bitmap, startX: Int, startY: Int, tolerance: Int): Bitmap {
        if (maskBmp.isRecycled || startX !in 0 until maskBmp.width || startY !in 0 until maskBmp.height) return maskBmp
        val w = maskBmp.width
        val h = maskBmp.height
        val pixels = IntArray(w * h)
        maskBmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val targetIdx = startY * w + startX
        val targetColor = pixels[targetIdx]
        if ((targetColor ushr 24) and 0xFF <= 20) return maskBmp

        val tr = (targetColor ushr 16) and 0xFF
        val tg = (targetColor ushr 8) and 0xFF
        val tb = targetColor and 0xFF
        val tolDist = tolerance * 3

        val visited = BitSet(w * h)
        val queue = ArrayDeque<Int>()
        queue.push(targetIdx)
        visited.set(targetIdx)

        val dx = intArrayOf(-1, 1, 0, 0)
        val dy = intArrayOf(0, 0, -1, 1)

        while (queue.isNotEmpty()) {
            val curr = queue.pop()
            pixels[curr] = Color.TRANSPARENT
            val qx = curr % w
            val qy = curr / w

            for (i in 0..3) {
                val nx = qx + dx[i]
                val ny = qy + dy[i]
                if (nx in 0 until w && ny in 0 until h) {
                    val nIdx = ny * w + nx
                    if (!visited.get(nIdx)) {
                        visited.set(nIdx)
                        val nc = pixels[nIdx]
                        if ((nc ushr 24) and 0xFF > 20) {
                            val nr = (nc ushr 16) and 0xFF
                            val ng = (nc ushr 8) and 0xFF
                            val nb = nc and 0xFF
                            if (abs(nr - tr) + abs(ng - tg) + abs(nb - tb) <= tolDist) {
                                queue.push(nIdx)
                            }
                        }
                    }
                }
            }
        }

        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(pixels, 0, w, 0, 0, w, h)
        return res
    }

    fun paintBrushAt(maskBmp: Bitmap, origBmp: Bitmap?, x: Int, y: Int, radiusPx: Int, isEraser: Boolean): Bitmap {
        if (maskBmp.isRecycled) return maskBmp
        val w = maskBmp.width
        val h = maskBmp.height
        val pixels = IntArray(w * h)
        val origPixels = if (origBmp != null && !origBmp.isRecycled) IntArray(w * h).also { origBmp.getPixels(it, 0, w, 0, 0, w, h) } else null
        maskBmp.getPixels(pixels, 0, w, 0, 0, w, h)

        for (dyVal in -radiusPx..radiusPx) {
            val py = y + dyVal
            if (py in 0 until h) {
                val row = py * w
                for (dxVal in -radiusPx..radiusPx) {
                    val px = x + dxVal
                    if (px in 0 until w && (dxVal * dxVal + dyVal * dyVal <= radiusPx * radiusPx)) {
                        val idx = row + px
                        pixels[idx] = if (isEraser) Color.TRANSPARENT else (origPixels?.get(idx) ?: pixels[idx])
                    }
                }
            }
        }
        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(pixels, 0, w, 0, 0, w, h)
        return res
    }

    private fun applyCircleMask(pixels: IntArray, w: Int, h: Int) {
        val cx = w / 2.0
        val cy = h / 2.0
        val halfW = (w * 0.5).coerceAtLeast(0.5)
        val halfH = (h * 0.5).coerceAtLeast(0.5)

        for (y in 0 until h) {
            val row = y * w
            val nyNorm = (y + 0.5 - cy) / halfH
            for (x in 0 until w) {
                val nxNorm = (x + 0.5 - cx) / halfW
                if (nxNorm * nxNorm + nyNorm * nyNorm > 1.0) {
                    pixels[row + x] = Color.TRANSPARENT
                }
            }
        }
    }
}
