package com.example.autotap

import com.example.autotap.core.math.CoordinateNormalizer
import com.example.autotap.domain.engine.SubroutineManager
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.DeviceDisplaySpecs
import com.example.autotap.domain.model.ExecutionState
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.MacroScenario
import com.example.autotap.domain.model.MatchCandidate
import com.example.autotap.domain.model.Point2D
import com.example.autotap.infrastructure.ocr.ExpressionEvaluator
import com.example.autotap.infrastructure.vision.ScreenStabilityDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenfieldArchitectureTestSuite {

    @Test
    fun testMacroAction_ImmutabilityAndDefaults() {
        val action = MacroAction(
            id = 1,
            type = ActionType.CLICK,
            posX = 500f,
            posY = 800f
        )

        assertEquals(1, action.id)
        assertEquals(ActionType.CLICK, action.type)
        assertEquals(1000L, action.delayMs)
        assertEquals(120L, action.holdDurationMs)
        assertEquals("#00F5D4", action.targetColorHex)

        val updated = action.copy(type = ActionType.TRIGGER, similarityPercent = 85)
        assertEquals(ActionType.TRIGGER, updated.type)
        assertEquals(85, updated.similarityPercent)
        assertEquals(ActionType.CLICK, action.type)
    }

    @Test
    fun testExecutionState_RunningLiveTimerState() {
        val now = System.currentTimeMillis()
        val state = ExecutionState.Running(
            currentStepIndex = 1,
            totalSteps = 3,
            currentRepeat = 1,
            totalRepeats = 1,
            isSearching = true,
            stepType = ActionType.TRIGGER,
            targetTagOrName = "ИИ #1",
            searchStartTime = now,
            searchTimeoutMs = 5000L,
            isInfiniteSearch = false
        )
        assertTrue(state.isSearching)
        assertEquals(now, state.searchStartTime)
        assertEquals(5000L, state.searchTimeoutMs)
        assertEquals("ИИ #1", state.targetTagOrName)
    }

    @Test
    fun testMatchCandidate_TemplateNameResolution() {
        val candidate = MatchCandidate(
            clickX = 100,
            clickY = 200,
            rectLeft = 80,
            rectTop = 180,
            rectRight = 120,
            rectBottom = 220,
            score = 0.95f,
            templatePath = "/data/user/0/com.example.autotap/files/templates/default/mask_btn_play.png"
        )
        assertEquals("mask_btn_play", candidate.templateName)
        assertEquals(40, candidate.width)
        assertEquals(40, candidate.height)
    }

    @Test
    fun testCoordinateNormalizer_IsotropicScalingAndOrientation() {
        val srcPhonePortrait = DeviceDisplaySpecs(1080, 2400, 480, 2.75f, isLandscape = false)
        val dstPhoneLandscape = DeviceDisplaySpecs(2400, 1080, 480, 2.75f, isLandscape = true)

        val transform = CoordinateNormalizer.calculateTransform(srcPhonePortrait, dstPhoneLandscape)
        assertEquals(1.0f, transform.scale, 1e-4f)

        val origPoint = Point2D(540f, 540f)
        val mappedPoint = CoordinateNormalizer.mapPoint(origPoint, transform, 2400, 1080)
        assertEquals(540f, mappedPoint.x, 1e-3f)
        assertEquals(540f, mappedPoint.y, 1e-3f)
    }

    @Test
    fun testSubroutineManager_CallStackDepthAndOverflowGuard() {
        val manager = SubroutineManager(maxDepth = 50)
        assertEquals(0, manager.currentDepth)

        val actions = listOf(MacroAction(1, ActionType.CLICK, 100f, 100f))
        assertTrue(manager.pushFrame("Main", actions, 3))
        assertTrue(manager.pushFrame("SubA", actions, 14))
        assertEquals(2, manager.currentDepth)

        val topFrame = manager.popFrame()
        assertEquals(14, topFrame?.returnIndex)
        assertEquals("SubA", topFrame?.scenarioName)

        val rootFrame = manager.popFrame()
        assertEquals(3, rootFrame?.returnIndex)
        assertEquals("Main", rootFrame?.scenarioName)

        assertNull(manager.popFrame())

        for (i in 1..50) {
            assertTrue(manager.pushFrame("Scen$i", actions, i))
        }
        assertEquals(50, manager.currentDepth)
        assertFalse("51-й вызов обязан быть заблокирован", manager.pushFrame("Overflow", actions, 51))
    }

    @Test
    fun testExpressionEvaluator_NumericAndStringComparisons() {
        val vars = mapOf(
            "gold" to "4500",
            "hp" to "98.5",
            "status" to "VICTORY",
            "hero" to " Paladin "
        )

        assertTrue(ExpressionEvaluator.evaluate("{gold} >= 4000", vars))
        assertTrue(ExpressionEvaluator.evaluate("{gold} < 5000", vars))
        assertTrue(ExpressionEvaluator.evaluate("{hp} > 50.0", vars))
        assertTrue(ExpressionEvaluator.evaluate("{status} == 'VICTORY'", vars))
        assertTrue(ExpressionEvaluator.evaluate("{hero} == 'Paladin'", vars))
        assertFalse(ExpressionEvaluator.evaluate("{gold} > 10000", vars))
    }

    @Test
    fun testScreenStabilityDetector_HashDeterminism() {
        val frameA = IntArray(200 * 200) { 0xFF112233.toInt() }
        val frameB = IntArray(200 * 200) { 0xFF112233.toInt() }
        val frameC = IntArray(200 * 200) { 0xFF445566.toInt() }

        val hashA = ScreenStabilityDetector.computeFastHash(frameA, 200, 200)
        val hashB = ScreenStabilityDetector.computeFastHash(frameB, 200, 200)
        val hashC = ScreenStabilityDetector.computeFastHash(frameC, 200, 200)

        assertEquals("Одинаковые кадры обязаны давать идентичный хэш", hashA, hashB)
        assertNotEquals("Изменившийся экран обязан давать другой хэш", hashA, hashC)
    }

    @Test
    fun testMacroScenario_Integrity() {
        val specs = DeviceDisplaySpecs(1080, 2400, 480, 2.75f, false)
        val actions = listOf(
            MacroAction(id = 1, type = ActionType.CLICK, posX = 100f, posY = 200f),
            MacroAction(id = 2, type = ActionType.TRIGGER, posX = 300f, posY = 400f, jumpToStepOnMatch = 1)
        )
        val scenario = MacroScenario(
            name = "TestFarmScenario",
            version = 1,
            deviceSpecs = specs,
            actions = actions
        )

        assertEquals("TestFarmScenario", scenario.name)
        assertEquals(2, scenario.actions.size)
        assertEquals(1, scenario.actions[1].jumpToStepOnMatch)
    }
}
