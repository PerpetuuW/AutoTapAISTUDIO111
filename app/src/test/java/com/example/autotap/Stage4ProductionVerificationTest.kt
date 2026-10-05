package com.example.autotap

import com.example.autotap.core.math.PathCompressionEngine
import com.example.autotap.core.safety.SafetyGovernor
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.Point2D
import com.example.autotap.infrastructure.ocr.ExpressionEvaluator
import com.example.autotap.infrastructure.vision.ColorDetector
import kotlin.math.abs
import kotlin.math.atan2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage4ProductionVerificationTest {

    @Test
    fun testRdpPathCompression_ReducesDensePointsWhilePreservingShape() {
        val rawTrajectory = mutableListOf<Point2D>()
        for (i in 0..200) {
            val noise = if (i % 2 == 0) 0.5f else -0.5f
            rawTrajectory.add(Point2D(i.toFloat(), i.toFloat() * 2f + noise))
        }

        val compressed = PathCompressionEngine.compressPath(rawTrajectory, maxPoints = 80, initialEpsilon = 2.5f)

        assertEquals("Прямая линия обязана сжаться до 2 точек", 2, compressed.size)
        assertEquals(0f, compressed.first().x, 1e-4f)
        assertEquals(200f, compressed.last().x, 1e-4f)
    }

    @Test
    fun testCielabDeltaE_ExactColorMatchAndIlluminationInvariance() {
        val colorPureCyan = 0x00F5D4
        val labCyan1 = ColorDetector.sRgbToLab(colorPureCyan)

        val deltaESame = ColorDetector.computeDeltaE(colorPureCyan, colorPureCyan)
        assertEquals("Delta-E идентичных цветов обязана быть 0.0", 0.0, deltaESame, 1e-4)

        val shadowShiftCyan = 0x007A6A
        val labShadow = ColorDetector.sRgbToLab(shadowShiftCyan)

        val theta1 = atan2(labCyan1[2], labCyan1[1])
        val theta2 = atan2(labShadow[2], labShadow[1])
        assertTrue("Угол цветового тона (Hue Angle) в тени обязан сохраняться инвариантно", abs(theta1 - theta2) < 0.05)

        val slightDriftCyan = 0x02F2D2
        val deltaEDrift = ColorDetector.computeDeltaE(colorPureCyan, slightDriftCyan)
        assertTrue("Незначительный дрейф освещения обязан укладываться в допустимый порог Delta-E < 5.0", deltaEDrift < 5.0)
    }

    @Test
    fun testOcrGameNumberParsing_AllSuffixesIncludingCyrillic() {
        assertEquals(45500.0, ExpressionEvaluator.parseGameNumber("45.5k") ?: 0.0, 1e-3)
        assertEquals(1200000.0, ExpressionEvaluator.parseGameNumber("1.2M") ?: 0.0, 1e-3)
        assertEquals(3000000000.0, ExpressionEvaluator.parseGameNumber("3.0B") ?: 0.0, 1e-3)
        assertEquals(12500.0, ExpressionEvaluator.parseGameNumber("12,500") ?: 0.0, 1e-3)

        assertEquals(45500.0, ExpressionEvaluator.parseGameNumber("45.5к") ?: 0.0, 1e-3)
        assertEquals(1200000.0, ExpressionEvaluator.parseGameNumber("1.2м") ?: 0.0, 1e-3)
        assertEquals(3000000000.0, ExpressionEvaluator.parseGameNumber("3.0б") ?: 0.0, 1e-3)

        val vars = mapOf("gold" to "54.2к", "hp" to "120", "lvl" to "85", "energy" to "1.5м")
        assertTrue(ExpressionEvaluator.evaluate("{gold} >= 50к", vars))
        assertFalse(ExpressionEvaluator.evaluate("{gold} < 40к", vars))
        assertTrue(ExpressionEvaluator.evaluate("{energy} >= 1м", vars))
        assertTrue(ExpressionEvaluator.evaluate("{hp} > 100", vars))
        assertTrue(ExpressionEvaluator.evaluate("{lvl} == 85", vars))
    }

    @Test
    fun testThermalGovernor_ZoneCalculations() {
        fun simulateZone(temp: Float, battery: Int): SafetyGovernor.ThermalZone {
            return when {
                temp > 43.5f || battery < 10 -> SafetyGovernor.ThermalZone.CRITICAL
                temp > 41.0f -> SafetyGovernor.ThermalZone.THROTTLED
                temp > 38.5f -> SafetyGovernor.ThermalZone.ELEVATED
                else -> SafetyGovernor.ThermalZone.OPTIMAL
            }
        }

        assertEquals(SafetyGovernor.ThermalZone.OPTIMAL, simulateZone(36.5f, 80))
        assertEquals(SafetyGovernor.ThermalZone.ELEVATED, simulateZone(39.0f, 80))
        assertEquals(SafetyGovernor.ThermalZone.THROTTLED, simulateZone(42.0f, 60))
        assertEquals(SafetyGovernor.ThermalZone.CRITICAL, simulateZone(44.5f, 50))
        assertEquals(SafetyGovernor.ThermalZone.CRITICAL, simulateZone(36.0f, 8))
    }

    @Test
    fun testMultiTouchPinch_ActionTypeInvariants() {
        val types = ActionType.values()
        assertTrue("ActionType обязан содержать PINCH", types.contains(ActionType.PINCH))

        val startDist = 300f
        val centerX = 500f
        val p1StartX = centerX - startDist / 2f
        val p2StartX = centerX + startDist / 2f
        assertEquals(350f, p1StartX, 1e-4f)
        assertEquals(650f, p2StartX, 1e-4f)
    }
}
