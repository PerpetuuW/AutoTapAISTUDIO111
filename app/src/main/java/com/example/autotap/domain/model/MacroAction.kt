package com.example.autotap.domain.model

data class MacroAction(
    val id: Int,
    val type: ActionType = ActionType.CLICK,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val endX: Float? = null,
    val endY: Float? = null,
    val delayMs: Long = 1000L,
    val holdDurationMs: Long = 120L,
    val checkIntervalMs: Long = 300L,
    val repeatCount: Int = 1,
    val randomRadiusPx: Int = 0,

    val pinchStartDistance: Float = 300f,
    val pinchEndDistance: Float = 600f,

    val selectedTemplateIndex: Int = -1,
    val multiTemplateIndices: List<Int> = emptyList(),
    val multiTemplatePaths: List<String> = emptyList(),
    val templatePath: String = "",
    val similarityPercent: Int = 80,
    val aiTimeoutSeconds: Int = 5,
    val clickAiTarget: Boolean = true,
    val isWaitUntilMode: Boolean = false,
    val scanStepPreset: Int = 2,
    val isContourMode: Boolean = true,
    val isFastMode: Boolean = true,
    val isMultiScaleMode: Boolean = false,
    val isShapeOnlyMode: Boolean = false,
    val isNeuralEngine: Boolean = false,

    val useCustomClickOffset: Boolean = false,
    val clickOffsetX: Float = 0f,
    val clickOffsetY: Float = 0f,

    val primaryAnchorPoints: List<Point2D> = emptyList(),

    val shapeExpansion: Int = 45,
    val paddingOffsetPx: Int = 2,
    val isCircleShape: Boolean = false,
    val roiLeft: Int? = null,
    val roiTop: Int? = null,
    val roiRight: Int? = null,
    val roiBottom: Int? = null,

    val jumpToStepOnMatch: Int? = null,
    val jumpToStepOnTimeout: Int? = null,
    val pathPoints: List<Point2D> = emptyList(),
    val targetScriptOrQuery: String = "",
    val subroutineTag: String = "",
    val subroutineTarget: String = "",

    val targetColorHex: String = "#00F5D4",
    val colorTolerance: Int = 15,
    val colorDeltaEMode: Boolean = false,
    val notifyOnMatch: Boolean = false
    )
