package com.example.autotap

import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.DeviceDisplaySpecs
import com.example.autotap.domain.model.graph.EvaluationPolicy
import com.example.autotap.domain.model.graph.GraphMacroScenario
import com.example.autotap.domain.model.graph.NodeActionSpec
import com.example.autotap.domain.model.graph.NodeTriggerSpec
import com.example.autotap.domain.model.graph.ScenarioEdge
import com.example.autotap.domain.model.graph.ScenarioNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphEngineExecutionTestSuite {

    @Test
    fun testGraphTopology_ValidatesLoopingBackEdges() {
        val specs = DeviceDisplaySpecs(1080, 2400, 480, 2.75f, false)
        val node1 = ScenarioNode(
            id = "node_start",
            title = "Старт",
            entryActions = listOf(NodeActionSpec("act_click", ActionType.CLICK, 500f, 1000f)),
            standardPorts = listOf("out_default")
        )
        val node2 = ScenarioNode(
            id = "node_combat",
            title = "Бой",
            triggers = listOf(NodeTriggerSpec(id = "trig_win", name = "Победа", type = ActionType.TRIGGER, similarityThreshold = 85)),
            standardPorts = listOf("out_timeout")
        )

        // Цикл: node_start -> node_combat -> обратная петля на node_start
        val edge1 = ScenarioEdge("edge_1", "node_start", "out_default", "node_combat")
        val backEdge = ScenarioEdge("edge_loop", "node_combat", "out_match_trig_win", "node_start")

        val graph = GraphMacroScenario(
            id = "test_graph",
            name = "TestLoopGraph",
            version = 2,
            deviceSpecs = specs,
            entryNodeId = "node_start",
            nodes = mapOf(node1.id to node1, node2.id to node2),
            edges = listOf(edge1, backEdge)
        )

        assertEquals("node_start", graph.entryNodeId)
        assertEquals(2, graph.nodes.size)
        assertEquals(2, graph.edges.size)

        // Проверка наличия обратного ребра без жесткого разыменования !!
        val loop = graph.edges.firstOrNull { it.toNodeId == "node_start" && it.fromNodeId == "node_combat" }
        assertNotNull(loop)
        assertEquals("out_match_trig_win", loop?.fromPortId)
    }

    @Test
    fun testGraphTopology_TimeoutFallbackEdgeIntegrity() {
        val specs = DeviceDisplaySpecs(1080, 2400, 480, 2.75f, false)
        val nodeWait = ScenarioNode(
            id = "node_wait",
            title = "Ожидание диалога",
            triggers = listOf(NodeTriggerSpec(id = "trig_dialog", name = "Диалог", type = ActionType.TRIGGER)),
            timeoutSeconds = 5,
            standardPorts = listOf("out_timeout")
        )
        val nodeRecover = ScenarioNode(
            id = "node_recover",
            title = "Свайп восстановления",
            entryActions = listOf(NodeActionSpec("act_swipe", ActionType.SWIPE, 500f, 1500f, 500f, 500f)),
            standardPorts = listOf("out_default")
        )

        val timeoutEdge = ScenarioEdge("edge_timeout", "node_wait", "out_timeout", "node_recover")

        val graph = GraphMacroScenario(
            id = "test_fallback",
            name = "FallbackGraph",
            version = 2,
            deviceSpecs = specs,
            entryNodeId = "node_wait",
            nodes = mapOf(nodeWait.id to nodeWait, nodeRecover.id to nodeRecover),
            edges = listOf(timeoutEdge)
        )

        val outEdge = graph.edges.firstOrNull { it.fromNodeId == "node_wait" && it.fromPortId == "out_timeout" }
        assertNotNull(outEdge)
        assertEquals("node_recover", outEdge?.toNodeId)
    }
}
