package com.example.autotap

import com.example.autotap.core.math.CoordinateNormalizer
import com.example.autotap.core.math.LinearToGraphMigrator
import com.example.autotap.core.math.PathCompressionEngine
import com.example.autotap.domain.engine.SubroutineManager
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.DeviceDisplaySpecs
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.MacroScenario
import com.example.autotap.domain.model.Point2D
import com.example.autotap.infrastructure.vision.ColorDetector
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndToEndSystemVerificationTest {

    @Test
    fun testFullScenarioLifecycle_LinearToGraphAndRoundtrip() {
        val srcSpecs = DeviceDisplaySpecs(1080, 2400, 480, 2.75f, false)
        val actions = listOf(
            MacroAction(id = 1, type = ActionType.CLICK, posX = 200f, posY = 400f, delayMs = 300L),
            MacroAction(id = 2, type = ActionType.SWIPE, posX = 200f, posY = 1000f, endX = 200f, endY = 400f),
            MacroAction(
                id = 3,
                type = ActionType.TRIGGER,
                posX = 540f,
                posY = 1200f,
                similarityPercent = 85,
                jumpToStepOnMatch = 1,
                jumpToStepOnTimeout = 4
            ),
            MacroAction(
                id = 4,
                type = ActionType.PINCH,
                posX = 540f,
                posY = 1200f,
                pinchStartDistance = 250f,
                pinchEndDistance = 600f
            ),
            MacroAction(
                id = 5,
                type = ActionType.SUBROUTINE,
                targetScriptOrQuery = "SubFarm"
            )
        )

        val originalScenario = MacroScenario(
            name = "E2E_Combat_Routine",
            version = 1,
            deviceSpecs = srcSpecs,
            actions = actions,
            globalClickDurationMs = 100L,
            globalSwipeDurationMs = 280L
        )

        val graph = LinearToGraphMigrator.linearToGraph(originalScenario)
        assertEquals("E2E_Combat_Routine", graph.name)
        assertEquals("node_1", graph.entryNodeId)
        assertEquals(6, graph.nodes.size)

        val node3 = graph.nodes["node_3"]
        assertNotNull(node3)
        assertEquals(1, node3?.triggers?.size ?: 0)

        val roundtripScenario = LinearToGraphMigrator.graphToLinear(graph)
        assertEquals(5, roundtripScenario.actions.size)
        assertEquals("node_1", "node_${roundtripScenario.actions[0].id}")
        assertEquals(ActionType.CLICK, roundtripScenario.actions[0].type)
        assertEquals(ActionType.TRIGGER, roundtripScenario.actions[2].type)
        assertEquals(1, roundtripScenario.actions[2].jumpToStepOnMatch)
        assertEquals(4, roundtripScenario.actions[2].jumpToStepOnTimeout)
    }

    @Test
    fun testCoordinateNormalizer_ProportionalRectMapping() {
        val srcPhone = DeviceDisplaySpecs(1080, 2400, 480, 2.75f, false)
        val dstPhone = DeviceDisplaySpecs(720, 1600, 320, 2.0f, false)

        val transform = CoordinateNormalizer.calculateTransform(srcPhone, dstPhone)
        val mappedRoi = CoordinateNormalizer.mapRect(100, 200, 500, 800, transform, dstPhone.screenWidth, dstPhone.screenHeight)

        assertTrue("Масштабированный ROI обязан быть внутри экрана приемника", mappedRoi[0] >= 0)
        assertTrue("Масштабированный ROI обязан быть внутри экрана приемника", mappedRoi[2] <= dstPhone.screenWidth)
        assertTrue("Масштабированный ROI обязан быть внутри экрана приемника", mappedRoi[1] >= 0)
        assertTrue("Масштабированный ROI обязан быть внутри экрана приемника", mappedRoi[3] <= dstPhone.screenHeight)
        assertTrue("Ширина масштабированного ROI обязана быть положительной", (mappedRoi[2] - mappedRoi[0]) > 0)
        assertTrue("Высота масштабированного ROI обязана быть положительной", (mappedRoi[3] - mappedRoi[1]) > 0)
    }

    @Test
    fun testSubroutineManager_ConcurrentThreadSafety() {
        val manager = SubroutineManager(maxDepth = 50)
        val executor = Executors.newFixedThreadPool(4)
        val actions = listOf(MacroAction(1, ActionType.CLICK, 100f, 100f))

        for (i in 0 until 50) {
            executor.submit {
                manager.pushFrame("ThreadScenario_$i", actions, i + 1)
                manager.currentDepth
                manager.popFrame()
            }
        }

        executor.shutdown()
        assertTrue("Все параллельные задачи обязаны завершиться без сбоев", executor.awaitTermination(3, TimeUnit.SECONDS))
        manager.clear()
        assertEquals(0, manager.currentDepth)
    }

    @Test
    fun testColorDetector_ExactZeroToleranceAndDeltaE() {
        val purePixel = ColorDetector.parseHexColor("#00F5D4")
        val exactMatch = ColorDetector.isColorMatch(
            actualPixel = purePixel,
            expectedColorHex = "#00F5D4",
            tolerance = 0,
            useDeltaE = true
        )
        assertTrue("При допуске 0 точное совпадение обязано возвращать true", exactMatch)
    }

    @Test
    fun testPathCompression_StrictMaxPointsGuarantee() {
        val denseChaoticTrajectory = ArrayList<Point2D>()
        for (i in 0 until 400) {
            val angle = i * 0.25
            denseChaoticTrajectory.add(Point2D((i * 2 + kotlin.math.sin(angle) * 30).toFloat(), (i * 3 + kotlin.math.cos(angle) * 30).toFloat()))
        }

        val compressed = PathCompressionEngine.compressPath(denseChaoticTrajectory, maxPoints = 80)
        assertTrue("Итоговый сжатый путь обязан строго удовлетворять лимиту AOSP <= 80", compressed.size <= 80)
        assertTrue("Итоговый сжатый путь обязан сохранять минимум 2 точки", compressed.size >= 2)
    }
}
