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

class GraphScenarioMigrationTestSuite {

    @Test
    fun testLinearToGraph_PreservesSequenceAndParameters() {
        val specs = DeviceDisplaySpecs(1080, 2400, 480, 2.75f, false)
        val actions = listOf(
            MacroAction(id = 1, type = ActionType.CLICK, posX = 150f, posY = 250f, delayMs = 500L),
            MacroAction(
                id = 2,
                type = ActionType.TRIGGER,
                posX = 500f,
                posY = 800f,
                similarityPercent = 88,
                jumpToStepOnMatch = 1,
                jumpToStepOnTimeout = 3
            ),
            MacroAction(id = 3, type = ActionType.SWIPE, posX = 200f, posY = 400f, endX = 200f, endY = 800f)
        )
        val linear = MacroScenario("CombatScenario", 1, specs, actions)

        val graph = LinearToGraphMigrator.linearToGraph(linear)

        assertEquals("CombatScenario", graph.name)
        assertEquals(4, graph.nodes.size)
        assertEquals("node_1", graph.entryNodeId)

        val node2 = graph.nodes["node_2"]
        assertNotNull(node2)
        assertEquals(1, node2?.triggers?.size ?: 0)
        assertEquals(88, node2?.triggers?.getOrNull(0)?.similarityThreshold)
        assertEquals(EvaluationPolicy.FIRST_MATCH_WINS, node2?.evaluationPolicy)

        val trigId = node2?.triggers?.getOrNull(0)?.targetPortId ?: ""
        val matchEdge = graph.edges.firstOrNull { it.fromNodeId == "node_2" && it.fromPortId == trigId }
        assertNotNull(matchEdge)
        assertEquals("node_1", matchEdge?.toNodeId)

        val timeoutEdge = graph.edges.firstOrNull { it.fromNodeId == "node_2" && it.fromPortId == "out_timeout" }
        assertNotNull(timeoutEdge)
        assertEquals("node_3", timeoutEdge?.toNodeId)
    }

    @Test
    fun testGraphToLinear_RoundTripFidelity() {
        val specs = DeviceDisplaySpecs(1080, 2400, 480, 2.75f, false)
        val actions = listOf(
            MacroAction(id = 1, type = ActionType.CLICK, posX = 100f, posY = 150f, delayMs = 300L),
            MacroAction(
                id = 2,
                type = ActionType.TRIGGER,
                similarityPercent = 85,
                jumpToStepOnMatch = 1,
                jumpToStepOnTimeout = 2
            )
        )
        val original = MacroScenario("LoopScenario", 1, specs, actions)

        val graph = LinearToGraphMigrator.linearToGraph(original)
        val roundTrip = LinearToGraphMigrator.graphToLinear(graph)

        assertEquals(original.actions.size, roundTrip.actions.size)
        assertEquals(original.actions[0].type, roundTrip.actions[0].type)
        assertEquals(original.actions[0].posX, roundTrip.actions[0].posX, 1e-3f)
        assertEquals(original.actions[1].type, roundTrip.actions[1].type)
        assertEquals(original.actions[1].similarityPercent, roundTrip.actions[1].similarityPercent)
        assertEquals(1, roundTrip.actions[1].jumpToStepOnMatch)
        assertEquals(2, roundTrip.actions[1].jumpToStepOnTimeout)
    }
}
