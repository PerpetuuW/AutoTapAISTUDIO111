package com.example.autotap.domain.model

import java.io.File

data class MatchCandidate(
    val clickX: Int,
    val clickY: Int,
    val rectLeft: Int,
    val rectTop: Int,
    val rectRight: Int,
    val rectBottom: Int,
    val score: Float,
    val templatePath: String = "",
    val clickOffsetX: Float = 0f,
    val clickOffsetY: Float = 0f,
    val isShapeOnly: Boolean = false
) {
    val templateName: String get() = if (templatePath.isNotEmpty()) File(templatePath).nameWithoutExtension else ""
    val width: Int get() = (rectRight - rectLeft).coerceAtLeast(1)
    val height: Int get() = (rectBottom - rectTop).coerceAtLeast(1)
}
