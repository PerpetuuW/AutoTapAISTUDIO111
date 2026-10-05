package com.example.autotap.infrastructure.vision

import android.graphics.Bitmap
import com.example.autotap.domain.model.TemplateAnalysisResult
import com.example.autotap.domain.model.TemplateArchetype
import com.example.autotap.domain.model.TemplateMorphology
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object TemplateMorphologyClassifier {

    fun analyze(template: Bitmap): TemplateAnalysisResult {
        if (template.isRecycled || template.width <= 0 || template.height <= 0) {
            return TemplateAnalysisResult(
                morphology = TemplateMorphology.MICRO,
                archetype = TemplateArchetype.MONOCHROME_SILHOUETTE,
                suggestedScanStep = 1,
                suggestedSobelWeight = 0.70f,
                isSubpixelRecommended = true,
                isShapeOnlyRecommended = true
            )
        }

        val w = template.width
        val h = template.height
        val total = w * h
        val pixels = PixelBufferPool.obtain(total)
        return try {
            template.getPixels(pixels, 0, w, 0, 0, w, h)
            analyze(pixels, w, h)
        } catch (_: Exception) {
            TemplateAnalysisResult(
                morphology = TemplateMorphology.MICRO,
                archetype = TemplateArchetype.MONOCHROME_SILHOUETTE,
                suggestedScanStep = 1,
                suggestedSobelWeight = 0.70f,
                isSubpixelRecommended = true,
                isShapeOnlyRecommended = true
            )
        } finally {
            PixelBufferPool.release(pixels)
        }
    }

    fun analyze(pixels: IntArray, w: Int, h: Int): TemplateAnalysisResult {
        if (pixels.isEmpty() || w <= 0 || h <= 0) {
            return TemplateAnalysisResult(
                morphology = TemplateMorphology.MICRO,
                archetype = TemplateArchetype.MONOCHROME_SILHOUETTE,
                suggestedScanStep = 1,
                suggestedSobelWeight = 0.70f,
                isSubpixelRecommended = true,
                isShapeOnlyRecommended = true
            )
        }

        val area = w * h
        val aspectRatio = if (h > 0) w.toFloat() / h.toFloat() else 1.0f

        val morphology = when {
            h in 1..16 && w >= 20 -> TemplateMorphology.THIN_HORIZONTAL
            w in 1..16 && h >= 20 -> TemplateMorphology.THIN_VERTICAL
            aspectRatio >= 2.6f -> TemplateMorphology.WIDE
            area <= 900 -> TemplateMorphology.MICRO
            area <= 3600 -> TemplateMorphology.SMALL
            area <= 16000 -> TemplateMorphology.MEDIUM
            area <= 45000 -> TemplateMorphology.LARGE
            else -> TemplateMorphology.HUGE
        }

        val isMonochrome = isMonochromaticSilhouette(pixels, w, h)
        val isCircular = abs(w - h) <= max(2, (min(w, h) * 0.14).toInt()) && !isMonochrome

        val archetype = when {
            isMonochrome || morphology == TemplateMorphology.MICRO -> TemplateArchetype.MONOCHROME_SILHOUETTE
            morphology == TemplateMorphology.WIDE -> TemplateArchetype.TEXT_BANNER
            isCircular -> TemplateArchetype.CIRCULAR_BADGE
            else -> TemplateArchetype.MEDIA_CARD
        }

        val sobelWeight = when (archetype) {
            TemplateArchetype.MONOCHROME_SILHOUETTE -> 0.70f
            TemplateArchetype.CIRCULAR_BADGE -> 0.60f
            TemplateArchetype.TEXT_BANNER -> 0.55f
            TemplateArchetype.MEDIA_CARD -> 0.50f
        }

        val step = when (morphology) {
            TemplateMorphology.MICRO,
            TemplateMorphology.THIN_HORIZONTAL,
            TemplateMorphology.THIN_VERTICAL -> 1
            TemplateMorphology.SMALL,
            TemplateMorphology.WIDE -> 2
            TemplateMorphology.MEDIUM -> 3
            TemplateMorphology.LARGE -> 4
            TemplateMorphology.HUGE -> 5
        }

        val isSubpixel = morphology == TemplateMorphology.MICRO ||
                morphology == TemplateMorphology.THIN_HORIZONTAL ||
                morphology == TemplateMorphology.THIN_VERTICAL

        return TemplateAnalysisResult(
            morphology = morphology,
            archetype = archetype,
            suggestedScanStep = step,
            suggestedSobelWeight = sobelWeight,
            isSubpixelRecommended = isSubpixel,
            isShapeOnlyRecommended = isMonochrome
        )
    }

    private fun isMonochromaticSilhouette(pixels: IntArray, w: Int, h: Int): Boolean {
        if (pixels.isEmpty() || w <= 0 || h <= 0) return false
        val total = w * h
        var satSum = 0L
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var opaqueCount = 0

        val stride = maxOf(1, total / 2000)
        for (i in 0 until total step stride) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            if (a > 30) {
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                val maxC = maxOf(r, maxOf(g, b))
                val minC = minOf(r, minOf(g, b))
                satSum += (maxC - minC)
                rSum += r
                gSum += g
                bSum += b
                opaqueCount++
            }
        }

        if (opaqueCount == 0) return false
        val avgSat = satSum / opaqueCount

        val meanR = rSum / opaqueCount
        val meanG = gSum / opaqueCount
        val meanB = bSum / opaqueCount
        var varianceSum = 0L
        for (i in 0 until total step stride) {
            val c = pixels[i]
            if (((c ushr 24) and 0xFF) > 30) {
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                varianceSum += abs(r - meanR) + abs(g - meanG) + abs(b - meanB)
            }
        }
        val avgVariance = varianceSum / opaqueCount

        return avgSat < 24 || avgVariance < 28
    }
}
