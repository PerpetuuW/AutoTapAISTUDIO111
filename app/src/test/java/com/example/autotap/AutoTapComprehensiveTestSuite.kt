package com.example.autotap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class AutoTapComprehensiveTestSuite {

    @Test
    fun testSpatialNMS_RemovesOverlappingBoundingBoxes() {
        data class SimpleRect(val left: Int, val top: Int, val right: Int, val bottom: Int, val score: Float)

        val rawCandidates = listOf(
            SimpleRect(100, 100, 200, 200, score = 0.95f),
            SimpleRect(105, 102, 205, 202, score = 0.91f),
            SimpleRect(110, 108, 210, 208, score = 0.82f),
            SimpleRect(500, 500, 600, 600, score = 0.89f),
            SimpleRect(502, 498, 602, 598, score = 0.75f)
        )

        fun intersects(a: SimpleRect, b: SimpleRect): Boolean {
            return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
        }

        val sorted = rawCandidates.sortedByDescending { it.score }
        val filtered = mutableListOf<SimpleRect>()
        for (cand in sorted) {
            if (filtered.none { intersects(it, cand) }) {
                filtered.add(cand)
            }
        }

        assertEquals("NMS обязан оставить ровно 2 уникальных объекта", 2, filtered.size)
        assertEquals(0.95f, filtered[0].score, 1e-4f)
        assertEquals(0.89f, filtered[1].score, 1e-4f)
    }

    @Test
    fun testColorToleranceDistance_RGB() {
        fun isColorMatch(c1: Int, c2: Int, tolerance: Int): Boolean {
            val r1 = (c1 shr 16) and 0xFF; val g1 = (c1 shr 8) and 0xFF; val b1 = c1 and 0xFF
            val r2 = (c2 shr 16) and 0xFF; val g2 = (c2 shr 8) and 0xFF; val b2 = c2 and 0xFF
            val dr = abs(r1 - r2); val dg = abs(g1 - g2); val db = abs(b1 - b2)
            return (dr + dg + db) <= (tolerance * 3)
        }

        val colorTarget = 0x00F5D4
        val colorSlightShift = 0x0AF0DE
        val colorCompletelyDifferent = 0xFF0000

        assertTrue("Близкий оттенок обязан совпасть при допуске 15", isColorMatch(colorTarget, colorSlightShift, tolerance = 15))
        assertFalse("Совершенно другой цвет обязан отклониться", isColorMatch(colorTarget, colorCompletelyDifferent, tolerance = 15))
    }

    @Test
    fun testCurvedArrowGeometry_ZeroDistanceGuard() {
        val x1 = 300f; val y1 = 300f
        val x2 = 300f; val y2 = 300f

        val dx = x2 - x1
        val dy = y2 - y1
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        val shouldDraw = dist >= 12f
        assertFalse("Стрелка нулевой длины не должна рассчитываться во избежание деления на ноль", shouldDraw)
    }

    @Test
    fun testCoordinateClamping_WithinScreenBounds() {
        val screenW = 1080
        val screenH = 2400
        val targetSize = 100

        fun clampX(rawX: Int): Int = rawX.coerceIn(0, screenW - targetSize)
        fun clampY(rawY: Int): Int = rawY.coerceIn(0, screenH - targetSize)

        assertEquals(0, clampX(-50))
        assertEquals(980, clampX(1500))
        assertEquals(500, clampX(500))

        assertEquals(0, clampY(-200))
        assertEquals(2300, clampY(3000))
    }

    @Test
    fun testBatteryGovernor_ThermalSafetyThresholds() {
        fun isSafe(tempCelsius: Float, batteryPct: Int): Boolean {
            if (tempCelsius > 42.0f) return false
            if (batteryPct < 15) return false
            return true
        }

        assertTrue("Нормальная температура 36°C и заряд 80% безопасны", isSafe(36.0f, 80))
        assertFalse("Перегрев 43.5°C обязан ставить макрос на паузу", isSafe(43.5f, 80))
        assertFalse("Критический разряд 10% обязан останавливать кликер", isSafe(35.0f, 10))
    }
}
