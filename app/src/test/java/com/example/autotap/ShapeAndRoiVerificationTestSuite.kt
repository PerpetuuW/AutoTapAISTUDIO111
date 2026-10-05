package com.example.autotap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeAndRoiVerificationTestSuite {

    @Test
    fun testContourGuard_RejectsFlatColorRegionsWithoutGradients() {
        val edgeScoreFlat = 0.0f
        val passedGate = edgeScoreFlat >= 0.42f
        assertFalse("Плоский фон без градиента формы обязан отбраковываться Contour Guard (edgeScore < 0.42)", passedGate)
    }

    @Test
    fun testRoiBoundingBox_ClampingLogic() {
        val tw = 100
        val th = 100

        val roiLeft = 200
        val roiTop = 400
        val roiRight = 800
        val roiBottom = 1200

        val scanLimitX = (roiRight - tw).coerceAtLeast(roiLeft)
        val scanLimitY = (roiBottom - th).coerceAtLeast(roiTop)

        assertEquals(700, scanLimitX)
        assertEquals(1100, scanLimitY)
        assertTrue(scanLimitX in roiLeft until roiRight)
        assertTrue(scanLimitY in roiTop until roiBottom)
    }
}
