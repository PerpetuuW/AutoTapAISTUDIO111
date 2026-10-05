package com.example.autotap

import com.example.autotap.core.math.LinearToGraphMigrator
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.DeviceDisplaySpecs
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.MacroScenario
import com.example.autotap.domain.model.graph.EvaluationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiTemplateGraphGenerationTestSuite {

    @Test
    fun testGenerateMultiTemplateBranchingGraph_CreatesDedicatedNodesAndConnections() {
        val specs = DeviceDisplaySpecs(1080, 2400, 480, 2.75f, false)
        val templatePaths = listOf(
            "/data/templates/mask_accept_button.png",
            "/data/templates/mask_decline_button.png",
            "/data/templates/mask_close_cross.png"
        )

        val triggerAction = MacroAction(
            id = 2,
            type = ActionType.TRIGGER,
            posX = 500f,
            posY = 900f,
            similarityPercent = 85,
            templatePath = templatePaths[0],
            multiTemplatePaths = templatePaths
        )

        val scenario = MacroScenario(
            name = "RaidCombat",
            version = 1,
            deviceSpecs = specs,
            actions = listOf(
                MacroAction(id = 1, type = ActionType.CLICK, posX = 100f, posY = 100f),
                triggerAction,
                MacroAction(id = 3, type = ActionType.CLICK, posX = 200f, posY = 200f)
            )
        )

        val graph = LinearToGraphMigrator.generateMultiTemplateBranchingGraph(
            scenario = scenario,
            targetAction = triggerAction,
            templatePaths = templatePaths
        )

        // 1. Проверка структуры селектора без операторов !!
        val decisionNode = graph.nodes["node_2"]
        assertNotNull("Нода мульти-селектора обязана существовать", decisionNode)
        assertEquals(3, decisionNode?.triggers?.size ?: 0)
        assertEquals(EvaluationPolicy.FIRST_MATCH_WINS, decisionNode?.evaluationPolicy)

        // 2. Проверка генерации дочерних нод под каждый шаблон
        val branch1 = graph.nodes["node_2_branch_1"]
        val branch2 = graph.nodes["node_2_branch_2"]
        val branch3 = graph.nodes["node_2_branch_3"]
        val timeoutNode = graph.nodes["node_2_timeout"]

        assertNotNull("Нода для шаблона 1 обязана быть сгенерирована", branch1)
        assertNotNull("Нода для шаблона 2 обязана быть сгенерирована", branch2)
        assertNotNull("Нода для шаблона 3 обязана быть сгенерирована", branch3)
        assertNotNull("Нода таймаута обязана быть сгенерирована", timeoutNode)

        // 3. Проверка корректности соединений без операторов !!
        val edge1 = graph.edges.firstOrNull { it.fromNodeId == "node_2" && it.toNodeId == "node_2_branch_1" }
        assertNotNull("Связь от селектора к ноде 1 обязана существовать", edge1)
        assertEquals("out_match_trig_2_1", edge1?.fromPortId)

        val edgeTimeout = graph.edges.firstOrNull { it.fromNodeId == "node_2" && it.toNodeId == "node_2_timeout" }
        assertNotNull("Связь таймаута обязана существовать", edgeTimeout)
        assertEquals("out_timeout", edgeTimeout?.fromPortId)

        // 4. Проверка возврата в основной цикл
        val returnEdge = graph.edges.firstOrNull { it.fromNodeId == "node_2_branch_1" && it.toNodeId == "node_3" }
        assertNotNull("Связь возврата в следующий шаг макроса обязана существовать", returnEdge)
    }
}
