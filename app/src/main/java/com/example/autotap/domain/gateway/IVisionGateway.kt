package com.example.autotap.domain.gateway

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.MatchCandidate
import com.example.autotap.domain.model.OcrMatchResult

interface IVisionGateway {
    fun findTemplateMatches(
        screenshot: Bitmap,
        template: Bitmap,
        minSimilarityPercent: Int = 80,
        actionOverride: MacroAction? = null,
        isCancelled: () -> Boolean = { false }
    ): List<MatchCandidate>

    fun findMultiTemplateMatches(
        screenshot: Bitmap,
        templates: List<Pair<String, Bitmap>>,
        minSimilarityPercent: Int = 80,
        actionOverride: MacroAction? = null,
        isCancelled: () -> Boolean = { false }
    ): List<MatchCandidate>

    fun findText(
        screenshot: Bitmap,
        targetQuery: String,
        timeoutMs: Long = 2000L,
        roi: Rect? = null
    ): List<OcrMatchResult>

    fun checkColor(
        screenshot: Bitmap,
        targetX: Int,
        targetY: Int,
        expectedHex: String,
        tolerance: Int,
        useDeltaE: Boolean = false
    ): Boolean

    fun computeScreenHash(screenshot: Bitmap): Long
}
