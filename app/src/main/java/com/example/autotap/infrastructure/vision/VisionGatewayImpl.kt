package com.example.autotap.infrastructure.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.domain.gateway.IVisionGateway
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.MatchCandidate
import com.example.autotap.domain.model.OcrMatchResult
import com.example.autotap.infrastructure.ocr.OcrEngine

class VisionGatewayImpl(private val context: Context? = null) : IVisionGateway {


    override fun findTemplateMatches(
    screenshot: Bitmap,
    template: Bitmap,
    minSimilarityPercent: Int,
    actionOverride: MacroAction?,
    isCancelled: () -> Boolean
    ): List<MatchCandidate> {
    if (screenshot.isRecycled || template.isRecycled) return emptyList()
    if (actionOverride?.isNeuralEngine == true) {
    val neuralMatches = NeuralVisualMatcher.findMatches(screenshot, template, minSimilarityPercent, actionOverride)
    if (neuralMatches.isNotEmpty()) return neuralMatches
    }

    val sw = screenshot.width
    val sh = screenshot.height
        val sPixels = PixelBufferPool.obtain(sw * sh)

        return try {
            screenshot.getPixels(sPixels, 0, sw, 0, 0, sw, sh)
            TemplateMatchingEngine.findTemplateFastCascade(
                sPixels = sPixels,
                sw = sw,
                sh = sh,
                template = template,
                minSimilarityPercent = minSimilarityPercent,
                templatePath = actionOverride?.templatePath ?: "",
                actionOverride = actionOverride,
                enableL0Cache = true
            )
        } catch (e: Exception) {
            AppLogger.logError(context, "VISION_GATEWAY", e)
            emptyList()
        } finally {
            PixelBufferPool.release(sPixels)
        }
    }


    override fun findMultiTemplateMatches(
    screenshot: Bitmap,
    templates: List<Pair<String, Bitmap>>,
    minSimilarityPercent: Int,
    actionOverride: MacroAction?,
    isCancelled: () -> Boolean
    ): List<MatchCandidate> {
    if (screenshot.isRecycled || templates.isEmpty()) return emptyList()
    if (actionOverride?.isNeuralEngine == true) {
    for (t in templates) {
    if (!t.second.isRecycled) {
    val nMatches = NeuralVisualMatcher.findMatches(screenshot, t.second, minSimilarityPercent, actionOverride)
    if (nMatches.isNotEmpty()) return nMatches
    }
    }
    }

    val sw = screenshot.width
        val sh = screenshot.height
        val sPixels = PixelBufferPool.obtain(sw * sh)

        return try {
            screenshot.getPixels(sPixels, 0, sw, 0, 0, sw, sh)
            TemplateMatchingEngine.findMultiTemplatesFastCascade(
                sPixels = sPixels,
                sw = sw,
                sh = sh,
                templates = templates,
                minSimilarityPercent = minSimilarityPercent,
                actionOverride = actionOverride,
                isCancelled = isCancelled
            )
        } catch (e: Exception) {
            AppLogger.logError(context, "VISION_GATEWAY", e)
            emptyList()
        } finally {
            PixelBufferPool.release(sPixels)
        }
    }

    override fun findText(
        screenshot: Bitmap,
        targetQuery: String,
        timeoutMs: Long,
        roi: Rect?
    ): List<OcrMatchResult> {
        if (screenshot.isRecycled) return emptyList()
        return try {
            OcrEngine.findTextOnScreen(screenshot, targetQuery, timeoutMs, roi)
        } catch (e: Exception) {
            AppLogger.logError(context, "VISION_GATEWAY", e)
            emptyList()
        }
    }

    override fun checkColor(
        screenshot: Bitmap,
        targetX: Int,
        targetY: Int,
        expectedHex: String,
        tolerance: Int,
        useDeltaE: Boolean
    ): Boolean {
        return try {
            ColorDetector.checkColorAt(screenshot, targetX, targetY, expectedHex, tolerance, useDeltaE)
        } catch (e: Exception) {
            AppLogger.logError(context, "VISION_GATEWAY", e)
            false
        }
    }

    override fun computeScreenHash(screenshot: Bitmap): Long {
        return try {
            ScreenStabilityDetector.computeFastHash(screenshot)
        } catch (e: Exception) {
            AppLogger.logError(context, "VISION_GATEWAY", e)
            0L
        }
    }
}
