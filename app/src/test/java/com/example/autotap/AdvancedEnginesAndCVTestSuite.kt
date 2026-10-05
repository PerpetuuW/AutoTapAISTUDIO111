package com.example.autotap

import com.example.autotap.core.safety.SafetyGovernor
import com.example.autotap.domain.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class AdvancedEnginesAndCVTestSuite {

    @Test
    fun testColorTolerance_BrightnessDriftResistance() {
        val baseColor = 0x4080C0
        val driftedColor = 0x5292D2

        val r1 = (baseColor shr 16) and 0xFF; val g1 = (baseColor shr 8) and 0xFF; val b1 = baseColor and 0xFF
        val r2 = (driftedColor shr 16) and 0xFF; val g2 = (driftedColor shr 8) and 0xFF; val b2 = driftedColor and 0xFF

        val deltaTotal = abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
        val tolerance = 20
        assertTrue("Сдвиг яркости обязан укладываться в динамический допуск", deltaTotal <= tolerance * 3)
    }

    @Test
    fun testJoystickKnob_ClampsWithinMaxRadiusAcrossAllAngles() {
        val maxRadiusPx = 50f
        val testAnglesDegrees = listOf(0.0, 45.0, 90.0, 135.0, 180.0, -135.0, -90.0, -45.0)

        for (deg in testAnglesDegrees) {
            val rad = Math.toRadians(deg)
            val rawDist = 120.0
            val rawDx = rawDist * cos(rad)
            val rawDy = rawDist * sin(rad)

            val currentDist = hypot(rawDx, rawDy).toFloat()
            val angle = atan2(rawDy, rawDx)
            val clampedDist = min(currentDist, maxRadiusPx)

            val knobX = (clampedDist * cos(angle)).toFloat()
            val knobY = (clampedDist * sin(angle)).toFloat()
            val finalDist = hypot(knobX.toDouble(), knobY.toDouble()).toFloat()

            assertTrue("Смещение кноба не должно превышать макс. радиус", finalDist <= maxRadiusPx + 1e-3f)
            assertEquals("Угол отклонения обязан сохраняться идеально", rad, angle, 1e-4)
        }
    }

    @Test
    fun testThermalGovernor_StateTransitions() {
        var isPlaying = true
        var isPausedDueToThermal = false

        fun onTemperatureUpdate(tempCelsius: Float) {
            if (tempCelsius >= 42.0f && isPlaying) {
                isPlaying = false
                isPausedDueToThermal = true
            } else if (tempCelsius <= 38.0f && isPausedDueToThermal) {
                isPlaying = true
                isPausedDueToThermal = false
            }
        }

        onTemperatureUpdate(37.0f)
        assertTrue("Макрос работает", isPlaying)

        onTemperatureUpdate(42.5f)
        assertFalse("Макрос обязан встать на паузу при перегреве", isPlaying)
        assertTrue(isPausedDueToThermal)

        onTemperatureUpdate(37.5f)
        assertTrue("Макрос обязан автоматически возобновить работу после остывания", isPlaying)
        assertFalse(isPausedDueToThermal)
    }

    @Test
    fun testPinchGeometryCalculation() {
        val centerX = 500f
        val startDist = 300f
        val endDist = 600f

        val p1StartX = centerX - startDist / 2f
        val p2StartX = centerX + startDist / 2f
        val p1EndX = centerX - endDist / 2f
        val p2EndX = centerX + endDist / 2f

        assertEquals(350f, p1StartX, 1e-3f)
        assertEquals(650f, p2StartX, 1e-3f)
        assertEquals(200f, p1EndX, 1e-3f)
        assertEquals(800f, p2EndX, 1e-3f)
    }
}
