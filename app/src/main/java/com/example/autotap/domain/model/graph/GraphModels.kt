package com.example.autotap.domain.model.graph

import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.DeviceDisplaySpecs
import com.example.autotap.domain.model.Point2D

enum class EvaluationPolicy {
    FIRST_MATCH_WINS, ALL_MUST_MATCH, SEQUENCE_MATCH
}

enum class TriggerBehavior {
    WAIT_APPEAR,       // Ожидание появления шаблона/текста
    WAIT_DISAPPEAR,    // Ожидание исчезновения шаблона (лоадеры, баннеры)
    CHECK_ONCE,        // Однократная проверка без задержки
    LOOP_WHILE         // Цикл, пока условие истинно
}

data class NodeTriggerSpec(
    val behavior: TriggerBehavior = TriggerBehavior.WAIT_APPEAR,
    val id: String,
    val name: String,
    val type: ActionType = ActionType.TRIGGER,
    val templatePath: String = "",
    val multiTemplatePaths: List<String> = emptyList(),
    val similarityThreshold: Int = 80,
    val targetQueryOrColor: String = "",
    val colorTolerance: Int = 15,
    val isContourMode: Boolean = true,
    val isShapeOnlyMode: Boolean = false,
    val isMultiScaleMode: Boolean = false,
    val colorDeltaEMode: Boolean = false,
    val shapeExpansion: Int = 45,
    val paddingOffsetPx: Int = 2,
    val isCircleShape: Boolean = false,
    val roiLeft: Int? = null,
    val roiTop: Int? = null,
    val roiRight: Int? = null,
    val roiBottom: Int? = null,
    val autoClickTarget: Boolean = true,
    val useCustomClick: Boolean = false,
    val clickOffset: Point2D = Point2D(0f, 0f),
    val targetPortId: String = "out_match_${id}",
    val additionalSearchLocations: MutableList<Point2D> = mutableListOf()
) {
    fun registerDiscoveredLocation(newLocation: Point2D, minDeltaPx: Float = 24f) {
        val minDeltaSq = minDeltaPx * minDeltaPx
        val alreadyExists = additionalSearchLocations.any { loc ->
            val dx = loc.x - newLocation.x
            val dy = loc.y - newLocation.y
            (dx * dx + dy * dy) < minDeltaSq
        }
        if (!alreadyExists) {
            if (additionalSearchLocations.size >= 8) {
                additionalSearchLocations.removeAt(0)
            }
            additionalSearchLocations.add(newLocation)
        }
    }

    fun getAllCandidateLocations(primaryLocation: Point2D?): List<Point2D> {
        val candidates = mutableListOf<Point2D>()
        primaryLocation?.let { candidates.add(it) }
        candidates.addAll(additionalSearchLocations)
        return candidates
    }

}

data class NodeActionSpec(
    val id: String,
    val type: ActionType = ActionType.CLICK,
    val x: Float = 0f,
    val y: Float = 0f,
    val endX: Float? = null,
    val endY: Float? = null,
    val holdDurationMs: Long = 120L,
    val delayAfterMs: Long = 200L,
    val repeatCount: Int = 1,
    val randomRadiusPx: Int = 0,
    val pinchStartDistance: Float = 300f,
    val pinchEndDistance: Float = 600f,
    val pathPoints: List<Point2D> = emptyList(),
    val subroutineTarget: String = ""
)

data class ScenarioEdge(
    val id: String,
    val fromNodeId: String,
    val fromPortId: String,
    val toNodeId: String,
    val toPortId: String = "in_entry"
)

data class ScenarioNode(
    val id: String,
    val title: String,
    val canvasX: Float = 0f,
    val canvasY: Float = 0f,
    val evaluationPolicy: EvaluationPolicy = EvaluationPolicy.FIRST_MATCH_WINS,
    val triggers: List<NodeTriggerSpec> = emptyList(),
    val timeoutSeconds: Int = 5,
    val isInfiniteWait: Boolean = false,
    val pollIntervalMs: Long = 250L,
    val entryActions: List<NodeActionSpec> = emptyList(),
    val standardPorts: List<String> = listOf("out_default", "out_timeout")
)

data class GraphMacroScenario(
    val id: String,
    val name: String,
    val version: Int = 2,
    val deviceSpecs: DeviceDisplaySpecs,
    val entryNodeId: String,
    val nodes: Map<String, ScenarioNode> = emptyMap(),
    val edges: List<ScenarioEdge> = emptyList(),
    val globalClickDurationMs: Long = 120L,
    val globalSwipeDurationMs: Long = 300L,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)
