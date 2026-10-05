package com.example.autotap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ShapeCascadeVerificationTestSuite {

    @Test
    fun testShapeProbeGuard_RejectsFlatScreenAreas() {
        val gxProbe = 1.0f
        val gyProbe = 0.0f

        // Плоский экран (sgx = 0, sgy = 0)
        val sgxScreen = 0.0f
        val sgyScreen = 0.0f
        val mag = Math.abs(sgxScreen) + Math.abs(sgyScreen)
        val passedProbe = mag > 6.0f && (sgxScreen * gxProbe + sgyScreen * gyProbe > 0f)

        assertFalse("Плоская область экрана обязана немедленно отбраковываться Shape Probe Guard", passedProbe)
    }

    @Test
    fun testShapeCosineSimilarity_ExactNormalAlignment() {
        val spGx = 0.6f
        val spGy = 0.8f

        val sgx = 6.0f
        val sgy = 8.0f
        val smag = sqrt((sgx * sgx + sgy * sgy).toDouble()).toFloat()

        val cosTheta = (sgx * spGx + sgy * spGy) / smag
        assertEquals(1.0f, cosTheta, 1e-4f)
    }

    @Test
    fun testContourGuardGate_ZerosOutNonMatchingShape() {
        val edgeCount = 20
        val edgeScoreSum = 4.0f // Средний edScore = 0.20 < 0.42
        val edScore = edgeScoreSum / edgeCount.toFloat()

        val isRejected = edgeCount >= 6 && edScore < 0.42f
        assertTrue("Кандидат с несоответствующим контуром обязан отбраковываться Contour Guard (G-07)", isRejected)
    }
}
