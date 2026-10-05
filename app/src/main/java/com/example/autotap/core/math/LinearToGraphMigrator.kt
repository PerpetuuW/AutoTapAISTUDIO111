package com.example.autotap.core.math

import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.MacroScenario
import com.example.autotap.domain.model.Point2D
import com.example.autotap.domain.model.graph.EvaluationPolicy
import com.example.autotap.domain.model.graph.GraphMacroScenario
import com.example.autotap.domain.model.graph.NodeActionSpec
import com.example.autotap.domain.model.graph.NodeTriggerSpec
import com.example.autotap.domain.model.graph.ScenarioEdge
import com.example.autotap.domain.model.graph.ScenarioNode
import java.io.File
import java.util.UUID

object LinearToGraphMigrator {

    fun linearToGraph(scenario: MacroScenario): GraphMacroScenario {
        if (scenario.actions.isEmpty()) {
            val startNode = ScenarioNode(
                id = "node_start",
                title = "СТАРТ",
                canvasX = 60f,
                canvasY = 200f,
                standardPorts = listOf("out_default")
            )
            return GraphMacroScenario(
                id = UUID.randomUUID().toString(),
                name = scenario.name,
                version = 2,
                deviceSpecs = scenario.deviceSpecs,
                entryNodeId = startNode.id,
                nodes = mapOf(startNode.id to startNode),
                edges = emptyList(),
                globalClickDurationMs = scenario.globalClickDurationMs,
                globalSwipeDurationMs = scenario.globalSwipeDurationMs,
                createdAt = scenario.createdAt,
                modifiedAt = System.currentTimeMillis()
            )
        }

        val nodesMap = LinkedHashMap<String, ScenarioNode>()
        val edgesList = ArrayList<ScenarioEdge>()
        val totalActions = scenario.actions.size

        // [V110.0] Постоянный неделимый стартовый узел (Unreal Engine Blueprint Root Node)
        // Гарантирует наглядную точку старта: пользователь просто тянет провод от СТАРТ к первому действию
        val startNode = ScenarioNode(
            id = "node_start",
            title = "СТАРТ",
            canvasX = 60f,
            canvasY = 200f,
            standardPorts = listOf("out_default")
        )
        nodesMap[startNode.id] = startNode

        val nodeSpacingX = 400f
        val startCanvasX = 520f // Размещение первого шага правее стартового узла
        val startCanvasY = 200f

        if (totalActions > 0) {
            // Соединяем постоянный стартовый узел с первым действием сценария
            edgesList.add(
                ScenarioEdge(
                    id = "edge_start_to_first",
                    fromNodeId = startNode.id,
                    fromPortId = "out_default",
                    toNodeId = "node_${scenario.actions[0].id}",
                    toPortId = "in_entry"
                )
            )
        }

        // [Топологическая трассировка сверху вниз с учетом времени]
        var currentCanvasY = startCanvasY
        for (index in 0 until totalActions) {
            val action = scenario.actions[index]
            val nodeId = "node_${action.id}"
            val nextDefaultNodeId = if (index + 1 < totalActions) "node_${scenario.actions[index + 1].id}" else "node_${scenario.actions[0].id}"
            val timeDelaySpacing = (action.delayMs + action.holdDurationMs).coerceIn(120L, 800L) / 4f
            val posX = startCanvasX
            val posY = currentCanvasY + timeDelaySpacing
            currentCanvasY = posY + 60f
            val isDetectionType = action.type == ActionType.TRIGGER ||
                    action.type == ActionType.OCR ||
                    action.type == ActionType.COLOR_CHECK

            if (isDetectionType) {
                val isColorCheck = action.type == ActionType.COLOR_CHECK
                val isOcr = action.type == ActionType.OCR
                val isTrigger = action.type == ActionType.TRIGGER

                // [V151.0] Таймаут с гарантированным запасом (Safety Margin) для защиты от ложных таймаутов
                val safeTimeoutSeconds = when {
                    action.isWaitUntilMode -> 0
                    action.aiTimeoutSeconds > 0 -> action.aiTimeoutSeconds + 4
                    else -> 10 // 10 секунд гарантированный безопасный таймаут с запасом
                }

                val matchTargetNodeId = if (action.jumpToStepOnMatch != null && action.jumpToStepOnMatch in 1..totalActions) {
                    "node_${scenario.actions[action.jumpToStepOnMatch - 1].id}"
                } else {
                    nextDefaultNodeId
                }

                val timeoutTargetNodeId = if (action.jumpToStepOnTimeout != null && action.jumpToStepOnTimeout in 1..totalActions) {
                    "node_${scenario.actions[action.jumpToStepOnTimeout - 1].id}"
                } else {
                    nextDefaultNodeId
                }

                val allTemplatePaths = if (isTrigger) {
                    (listOf(action.templatePath) + action.multiTemplatePaths).filter { it.isNotBlank() }.distinct()
                } else emptyList()

                val isMultiTemplateAction = isTrigger && allTemplatePaths.size > 1

                if (isMultiTemplateAction) {
                    // [Мульти-поиск шаблонов в отдельных упорядоченных узлах графа]
                    val triggerSpecs = ArrayList<NodeTriggerSpec>()
                    val branchNodes = ArrayList<ScenarioNode>()

                    for ((idx, path) in allTemplatePaths.withIndex()) {
                        val templateName = java.io.File(path).nameWithoutExtension.removePrefix("mask_").take(14).ifEmpty { "цель_${idx + 1}" }
                        val trigId = "trig_${action.id}_${idx + 1}"
                        val matchPortId = "out_match_$trigId"

                        val trigSpec = NodeTriggerSpec(
                            id = trigId,
                            name = "Шаблон: $templateName #${idx + 1}",
                            type = ActionType.TRIGGER,
                            templatePath = path,
                            multiTemplatePaths = listOf(path),
                            similarityThreshold = action.similarityPercent,
                            targetQueryOrColor = action.targetScriptOrQuery,
                            colorTolerance = action.colorTolerance,
                            isContourMode = action.isContourMode,
                            isShapeOnlyMode = action.isShapeOnlyMode,
                            isMultiScaleMode = action.isMultiScaleMode,
                            colorDeltaEMode = action.colorDeltaEMode,
                            shapeExpansion = action.shapeExpansion,
                            paddingOffsetPx = action.paddingOffsetPx,
                            isCircleShape = action.isCircleShape,
                            roiLeft = action.roiLeft,
                            roiTop = action.roiTop,
                            roiRight = action.roiRight,
                            roiBottom = action.roiBottom,
                            autoClickTarget = false,
                            useCustomClick = action.useCustomClickOffset,
                            clickOffset = Point2D(action.clickOffsetX, action.clickOffsetY),
                            targetPortId = matchPortId
                        )
                        triggerSpecs.add(trigSpec)

                        val branchNodeId = "node_${action.id}_branch_${idx + 1}"
                        val branchActionSpec = if (action.clickAiTarget) {
                            NodeActionSpec(
                                id = "act_${action.id}_${idx + 1}",
                                type = ActionType.CLICK,
                                x = action.posX,
                                y = action.posY,
                                holdDurationMs = action.holdDurationMs.coerceAtLeast(30L),
                                delayAfterMs = action.delayMs
                            )
                        } else {
                            NodeActionSpec(
                                id = "act_${action.id}_${idx + 1}",
                                type = ActionType.DELAY,
                                x = 0f,
                                y = 0f,
                                delayAfterMs = action.delayMs
                            )
                        }

                        val branchCanvasY = posY + (idx * 130f) - ((allTemplatePaths.size - 1) * 65f)
                        val branchNode = ScenarioNode(
                            id = branchNodeId,
                            title = "Шаблон #${idx + 1}: $templateName",
                            canvasX = posX + 380f,
                            canvasY = branchCanvasY,
                            entryActions = listOf(branchActionSpec),
                            standardPorts = listOf("out_default")
                        )
                        nodesMap[branchNodeId] = branchNode

                        // Связь: Детектор -> Узел действия шаблона
                        edgesList.add(
                            ScenarioEdge(
                                id = "edge_${action.id}_match_branch_${idx + 1}",
                                fromNodeId = nodeId,
                                fromPortId = matchPortId,
                                toNodeId = branchNodeId,
                                toPortId = "in_entry"
                            )
                        )

                        // Связь: Узел действия шаблона -> Следующий узел по сценарию
                        edgesList.add(
                            ScenarioEdge(
                                id = "edge_${action.id}_branch_${idx + 1}_to_next",
                                fromNodeId = branchNodeId,
                                fromPortId = "out_default",
                                toNodeId = matchTargetNodeId,
                                toPortId = "in_entry"
                            )
                        )
                    }

                    // Основной узел мульти-селектора
                    val decisionNode = ScenarioNode(
                        id = nodeId,
                        title = "Мультипоиск #${action.id} (${allTemplatePaths.size} шабл.)",
                        canvasX = posX,
                        canvasY = posY,
                        evaluationPolicy = EvaluationPolicy.FIRST_MATCH_WINS,
                        triggers = triggerSpecs,
                        timeoutSeconds = safeTimeoutSeconds,
                        isInfiniteWait = action.isWaitUntilMode,
                        pollIntervalMs = action.checkIntervalMs.coerceAtLeast(100L),
                        entryActions = emptyList(),
                        standardPorts = listOf("out_timeout")
                    )
                    nodesMap[nodeId] = decisionNode

                    // Связь по таймауту
                    edgesList.add(
                        ScenarioEdge(
                            id = "edge_${action.id}_timeout",
                            fromNodeId = nodeId,
                            fromPortId = "out_timeout",
                            toNodeId = timeoutTargetNodeId,
                            toPortId = "in_entry"
                        )
                    )
                } else {
                    // Одиночный поиск (Trigger, OCR, ColorCheck)
                    val triggerId = "trig_${action.id}"
                    val targetPort = "out_match_$triggerId"

                    val triggerName = when (action.type) {
                        ActionType.TRIGGER -> "ИИ Поиск #${action.id}"
                        ActionType.OCR -> "OCR Текст #${action.id}"
                        ActionType.COLOR_CHECK -> "Цвет #${action.id}"
                        else -> "Действие #${action.id}"
                    }

                    val triggerSpec = NodeTriggerSpec(
                        id = triggerId,
                        name = triggerName,
                        type = action.type,
                        templatePath = action.templatePath,
                        multiTemplatePaths = if (action.multiTemplatePaths.isNotEmpty()) action.multiTemplatePaths else emptyList(),
                        similarityThreshold = action.similarityPercent,
                        targetQueryOrColor = if (isColorCheck) action.targetColorHex else action.targetScriptOrQuery,
                        colorTolerance = action.colorTolerance,
                        isContourMode = action.isContourMode,
                        isShapeOnlyMode = action.isShapeOnlyMode,
                        isMultiScaleMode = action.isMultiScaleMode,
                        colorDeltaEMode = action.colorDeltaEMode,
                        shapeExpansion = action.shapeExpansion,
                        paddingOffsetPx = action.paddingOffsetPx,
                        isCircleShape = action.isCircleShape,
                        roiLeft = action.roiLeft,
                        roiTop = action.roiTop,
                        roiRight = action.roiRight,
                        roiBottom = action.roiBottom,
                        autoClickTarget = action.clickAiTarget,
                        useCustomClick = if (isColorCheck) true else action.useCustomClickOffset,
                        clickOffset = if (isColorCheck) Point2D(action.posX, action.posY) else Point2D(action.clickOffsetX, action.clickOffsetY),
                        targetPortId = targetPort
                    )

                    val templateName = if (action.templatePath.isNotEmpty()) {
                        java.io.File(action.templatePath).nameWithoutExtension.removePrefix("mask_").take(12)
                    } else if (isOcr) {
                        action.targetScriptOrQuery.take(12).ifEmpty { "Текст" }
                    } else "Цель"

                    val node = ScenarioNode(
                        id = nodeId,
                        title = "${if (isOcr) "OCR" else "Поиск"}: $templateName #${action.id}",
                        canvasX = posX,
                        canvasY = posY,
                        evaluationPolicy = EvaluationPolicy.FIRST_MATCH_WINS,
                        triggers = listOf(triggerSpec),
                        timeoutSeconds = safeTimeoutSeconds,
                        isInfiniteWait = action.isWaitUntilMode,
                        pollIntervalMs = action.checkIntervalMs.coerceAtLeast(100L),
                        entryActions = emptyList(),
                        standardPorts = listOf("out_timeout")
                    )
                    nodesMap[nodeId] = node

                    if (action.clickAiTarget && action.jumpToStepOnMatch == null) {
                        val actNodeId = "node_${action.id}_act"
                        val actionSpec = NodeActionSpec(
                            id = "act_${action.id}",
                            type = ActionType.DELAY,
                            x = 0f,
                            y = 0f,
                            holdDurationMs = 0L,
                            delayAfterMs = action.delayMs
                        )
                        val actionNode = ScenarioNode(
                            id = actNodeId,
                            title = "Клик и пауза: ${action.delayMs}мс",
                            canvasX = posX + 360f,
                            canvasY = posY,
                            entryActions = listOf(actionSpec),
                            standardPorts = listOf("out_default")
                        )
                        nodesMap[actNodeId] = actionNode

                        edgesList.add(
                            ScenarioEdge(
                                id = "edge_${action.id}_match_to_act",
                                fromNodeId = nodeId,
                                fromPortId = targetPort,
                                toNodeId = actNodeId,
                                toPortId = "in_entry"
                            )
                        )
                        edgesList.add(
                            ScenarioEdge(
                                id = "edge_${action.id}_act_to_next",
                                fromNodeId = actNodeId,
                                fromPortId = "out_default",
                                toNodeId = matchTargetNodeId,
                                toPortId = "in_entry"
                            )
                        )
                    } else {
                        edgesList.add(
                            ScenarioEdge(
                                id = "edge_${action.id}_match",
                                fromNodeId = nodeId,
                                fromPortId = targetPort,
                                toNodeId = matchTargetNodeId,
                                toPortId = "in_entry"
                            )
                        )
                    }

                    edgesList.add(
                        ScenarioEdge(
                            id = "edge_${action.id}_timeout",
                            fromNodeId = nodeId,
                            fromPortId = "out_timeout",
                            toNodeId = timeoutTargetNodeId,
                            toPortId = "in_entry"
                        )
                    )
                }
            } else {
                val resolvedSubroutine = action.subroutineTarget.ifEmpty {
                    action.targetScriptOrQuery.ifEmpty { action.subroutineTag }
                }

                val actionSpec = NodeActionSpec(
                    id = "act_${action.id}",
                    type = action.type,
                    x = action.posX,
                    y = action.posY,
                    endX = action.endX,
                    endY = action.endY,
                    holdDurationMs = action.holdDurationMs,
                    delayAfterMs = action.delayMs,
                    repeatCount = action.repeatCount,
                    randomRadiusPx = action.randomRadiusPx,
                    pinchStartDistance = action.pinchStartDistance,
                    pinchEndDistance = action.pinchEndDistance,
                    pathPoints = action.pathPoints,
                    subroutineTarget = resolvedSubroutine
                )

                val node = ScenarioNode(
                    id = nodeId,
                    title = "Фаза #${action.id} [${action.type.name}]",
                    canvasX = posX,
                    canvasY = posY,
                    evaluationPolicy = EvaluationPolicy.FIRST_MATCH_WINS,
                    triggers = emptyList(),
                    timeoutSeconds = 0,
                    isInfiniteWait = false,
                    pollIntervalMs = 0L,
                    entryActions = listOf(actionSpec),
                    standardPorts = listOf("out_default")
                )
                nodesMap[nodeId] = node

                edgesList.add(
                    ScenarioEdge(
                        id = "edge_${action.id}_default",
                        fromNodeId = nodeId,
                        fromPortId = "out_default",
                        toNodeId = nextDefaultNodeId
                    )
                )
            }
        }

        val firstNodeId = "node_${scenario.actions[0].id}"
        return GraphMacroScenario(
            id = UUID.randomUUID().toString(),
            name = scenario.name,
            version = 2,
            deviceSpecs = scenario.deviceSpecs,
            entryNodeId = firstNodeId,
            nodes = nodesMap,
            edges = edgesList,
            globalClickDurationMs = scenario.globalClickDurationMs,
            globalSwipeDurationMs = scenario.globalSwipeDurationMs,
            createdAt = scenario.createdAt,
            modifiedAt = System.currentTimeMillis()
        )
    }

    fun generateMultiTemplateBranchingGraph(
        scenario: MacroScenario,
        targetAction: MacroAction,
        templatePaths: List<String>
    ): GraphMacroScenario {
        val baseGraph = linearToGraph(scenario)
        val validPaths = templatePaths.filter { it.isNotBlank() }.distinct()
        if (validPaths.isEmpty()) return baseGraph

        val nodesMap = baseGraph.nodes.toMutableMap()
        val edgesList = baseGraph.edges.toMutableList()

        val mainNodeId = "node_${targetAction.id}"
        val existingMainNode = nodesMap[mainNodeId] ?: ScenarioNode(
            id = mainNodeId,
            title = "ПОИСК ИИ #${targetAction.id}",
            canvasX = 80f,
            canvasY = 300f
        )

        val triggerSpecs = ArrayList<NodeTriggerSpec>()
        val startCanvasX = existingMainNode.canvasX
        val startCanvasY = existingMainNode.canvasY

        edgesList.removeAll { it.fromNodeId == mainNodeId }

        val targetIndex = scenario.actions.indexOfFirst { it.id == targetAction.id }
        val nextTargetNodeId = if (targetIndex != -1 && targetIndex + 1 < scenario.actions.size) {
            "node_${scenario.actions[targetIndex + 1].id}"
        } else if (scenario.actions.isNotEmpty()) {
            "node_${scenario.actions[0].id}"
        } else "node_1"

        // Сдвигаем следующий узел вниз, чтобы он не слипался с горизонтальными ветвями мультипоиска
        nodesMap[nextTargetNodeId]?.let { nextNode ->
            nodesMap[nextTargetNodeId] = nextNode.copy(canvasX = startCanvasX, canvasY = startCanvasY + 280f)
        }

        for ((idx, path) in validPaths.withIndex()) {
            val templateName = File(path).nameWithoutExtension.removePrefix("mask_").take(14).ifEmpty { "цель_${idx + 1}" }
            val triggerId = "trig_${targetAction.id}_${idx + 1}"
            val matchPortId = "out_match_$triggerId"

            val trig = NodeTriggerSpec(
                id = triggerId,
                name = "Поиск: $templateName",
                type = ActionType.TRIGGER,
                templatePath = path,
                multiTemplatePaths = listOf(path),
                similarityThreshold = targetAction.similarityPercent,
                isContourMode = targetAction.isContourMode,
                isShapeOnlyMode = targetAction.isShapeOnlyMode,
                isMultiScaleMode = targetAction.isMultiScaleMode,
                shapeExpansion = targetAction.shapeExpansion,
                paddingOffsetPx = targetAction.paddingOffsetPx,
                isCircleShape = targetAction.isCircleShape,
                autoClickTarget = false,
                targetPortId = matchPortId
            )
            triggerSpecs.add(trig)

            val branchNodeId = "node_${targetAction.id}_branch_${idx + 1}"
            val branchActionSpec = NodeActionSpec(
                id = "act_branch_${idx + 1}",
                type = ActionType.CLICK,
                x = targetAction.posX,
                y = targetAction.posY,
                holdDurationMs = targetAction.holdDurationMs,
                delayAfterMs = targetAction.delayMs
            )

            val branchNode = ScenarioNode(
                id = branchNodeId,
                title = "ШАБЛОН: $templateName",
                canvasX = startCanvasX + 400f,
                canvasY = startCanvasY + (idx * 140f) - ((validPaths.size - 1) * 70f),
                entryActions = listOf(branchActionSpec),
                standardPorts = listOf("out_default")
            )
            nodesMap[branchNodeId] = branchNode

            edgesList.add(
                ScenarioEdge(
                    id = "edge_multi_${targetAction.id}_$idx",
                    fromNodeId = mainNodeId,
                    fromPortId = matchPortId,
                    toNodeId = branchNodeId,
                    toPortId = "in_entry"
                )
            )

            edgesList.add(
                ScenarioEdge(
                    id = "edge_return_${targetAction.id}_$idx",
                    fromNodeId = branchNodeId,
                    fromPortId = "out_default",
                    toNodeId = nextTargetNodeId,
                    toPortId = "in_entry"
                )
            )
        }

        val timeoutNodeId = "node_${targetAction.id}_timeout"
        val timeoutActionNode = ScenarioNode(
            id = timeoutNodeId,
            title = "ТАЙМАУТ / НЕ НАЙДЕНО",
            canvasX = startCanvasX + 400f,
            canvasY = startCanvasY + (validPaths.size * 140f) - ((validPaths.size - 1) * 70f),
            entryActions = listOf(
                NodeActionSpec(
                    id = "act_timeout_${targetAction.id}",
                    type = ActionType.CLICK,
                    x = targetAction.posX,
                    y = targetAction.posY,
                    delayAfterMs = 500L
                )
            ),
            standardPorts = listOf("out_default")
        )
        nodesMap[timeoutNodeId] = timeoutActionNode

        edgesList.add(
            ScenarioEdge(
                id = "edge_timeout_${targetAction.id}",
                fromNodeId = mainNodeId,
                fromPortId = "out_timeout",
                toNodeId = timeoutNodeId,
                toPortId = "in_entry"
            )
        )

        val updatedDecisionNode = existingMainNode.copy(
            title = "Мульти-селектор #${targetAction.id} (${validPaths.size} цели)",
            evaluationPolicy = EvaluationPolicy.FIRST_MATCH_WINS,
            triggers = triggerSpecs,
            timeoutSeconds = if (targetAction.aiTimeoutSeconds > 0) targetAction.aiTimeoutSeconds else 5,
            isInfiniteWait = targetAction.isWaitUntilMode,
            pollIntervalMs = targetAction.checkIntervalMs,
            entryActions = emptyList(),
            standardPorts = listOf("out_timeout")
        )
        nodesMap[mainNodeId] = updatedDecisionNode

        return baseGraph.copy(
            name = "${scenario.name}_граф",
            nodes = nodesMap,
            edges = edgesList,
            modifiedAt = System.currentTimeMillis()
        )
    }

    fun graphToLinear(graphScenario: GraphMacroScenario): MacroScenario {
        val resultActions = ArrayList<MacroAction>()

        val entryNode = graphScenario.nodes[graphScenario.entryNodeId]
        val otherNodes = graphScenario.nodes.values
            .filter { it.id != graphScenario.entryNodeId && it.id != "node_start" }
            .sortedWith(compareBy<ScenarioNode> { it.canvasY.toInt() / 200 }.thenBy { it.canvasX })

        val sortedNodes = if (entryNode != null && entryNode.id != "node_start") listOf(entryNode) + otherNodes else otherNodes

        val nodeIdToStepIdMap = HashMap<String, Int>()
        for (i in sortedNodes.indices) {
            nodeIdToStepIdMap[sortedNodes[i].id] = i + 1
        }

        for (node in sortedNodes) {
            val stepId = nodeIdToStepIdMap[node.id] ?: (resultActions.size + 1)

            if (node.triggers.isNotEmpty()) {
                val trig = node.triggers[0]
                val matchEdge = graphScenario.edges.firstOrNull { it.fromNodeId == node.id && it.fromPortId == trig.targetPortId }
                val timeoutEdge = graphScenario.edges.firstOrNull { it.fromNodeId == node.id && it.fromPortId == "out_timeout" }

                val jumpMatch = matchEdge?.toNodeId?.let { nodeIdToStepIdMap[it] }
                val jumpTimeout = timeoutEdge?.toNodeId?.let { nodeIdToStepIdMap[it] }
                val isColorCheck = trig.type == ActionType.COLOR_CHECK

                val posX = if (isColorCheck) trig.clickOffset.x else 0f
                val posY = if (isColorCheck) trig.clickOffset.y else 0f
                val clickOffsetX = if (!isColorCheck && trig.useCustomClick) trig.clickOffset.x else 0f
                val clickOffsetY = if (!isColorCheck && trig.useCustomClick) trig.clickOffset.y else 0f

                val action = MacroAction(
                    id = stepId,
                    type = trig.type,
                    posX = posX,
                    posY = posY,
                    delayMs = node.pollIntervalMs.coerceAtLeast(100L),
                    holdDurationMs = 120L,
                    checkIntervalMs = node.pollIntervalMs,
                    repeatCount = 1,
                    templatePath = trig.templatePath,
                    multiTemplatePaths = trig.multiTemplatePaths,
                    similarityPercent = trig.similarityThreshold,
                    aiTimeoutSeconds = node.timeoutSeconds,
                    clickAiTarget = trig.autoClickTarget,
                    isWaitUntilMode = node.isInfiniteWait,
                    isContourMode = trig.isContourMode,
                    isShapeOnlyMode = trig.isShapeOnlyMode,
                    isMultiScaleMode = trig.isMultiScaleMode,
                    colorDeltaEMode = trig.colorDeltaEMode,
                    shapeExpansion = trig.shapeExpansion,
                    paddingOffsetPx = trig.paddingOffsetPx,
                    isCircleShape = trig.isCircleShape,
                    useCustomClickOffset = if (isColorCheck) false else trig.useCustomClick,
                    clickOffsetX = clickOffsetX,
                    clickOffsetY = clickOffsetY,
                    jumpToStepOnMatch = jumpMatch,
                    jumpToStepOnTimeout = jumpTimeout,
                    targetScriptOrQuery = if (isColorCheck) "" else trig.targetQueryOrColor,
                    subroutineTag = "",
                    subroutineTarget = "",
                    targetColorHex = if (isColorCheck) trig.targetQueryOrColor else "#00F5D4",
                    colorTolerance = trig.colorTolerance,
                    roiLeft = trig.roiLeft,
                    roiTop = trig.roiTop,
                    roiRight = trig.roiRight,
                    roiBottom = trig.roiBottom
                )
                resultActions.add(action)
            } else if (node.entryActions.isNotEmpty()) {
                val act = node.entryActions[0]
                val defaultEdge = graphScenario.edges.firstOrNull { it.fromNodeId == node.id && it.fromPortId == "out_default" }
                val jumpTarget = defaultEdge?.toNodeId?.let { nodeIdToStepIdMap[it] }

                val action = MacroAction(
                    id = stepId,
                    type = act.type,
                    posX = act.x,
                    posY = act.y,
                    endX = act.endX,
                    endY = act.endY,
                    delayMs = act.delayAfterMs,
                    holdDurationMs = act.holdDurationMs,
                    repeatCount = act.repeatCount,
                    randomRadiusPx = act.randomRadiusPx,
                    pinchStartDistance = act.pinchStartDistance,
                    pinchEndDistance = act.pinchEndDistance,
                    pathPoints = act.pathPoints,
                    targetScriptOrQuery = act.subroutineTarget,
                    subroutineTag = act.subroutineTarget,
                    subroutineTarget = act.subroutineTarget,
                    jumpToStepOnMatch = jumpTarget
                )
                resultActions.add(action)
            }
        }

        return MacroScenario(
            name = graphScenario.name,
            version = graphScenario.version,
            deviceSpecs = graphScenario.deviceSpecs,
            actions = resultActions,
            globalClickDurationMs = graphScenario.globalClickDurationMs,
            globalSwipeDurationMs = graphScenario.globalSwipeDurationMs,
            createdAt = graphScenario.createdAt
        )
    }
}
