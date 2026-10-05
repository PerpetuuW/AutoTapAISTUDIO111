package com.example.autotap.domain.model

data class OcrMatchResult(
    val matchedText: String,
    val clickX: Int,
    val clickY: Int,
    val rectLeft: Int,
    val rectTop: Int,
    val rectRight: Int,
    val rectBottom: Int,
    val confidence: Float = 1.0f
)
