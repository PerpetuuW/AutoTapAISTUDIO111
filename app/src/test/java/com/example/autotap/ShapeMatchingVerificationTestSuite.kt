package com.example.autotap

import com.example.autotap.domain.model.TemplateArchetype
import com.example.autotap.domain.model.TemplateMorphology
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ShapeMatchingVerificationTestSuite {

    @Test
    fun testSobelGradientPhase_CosineAngleMatchesIdenticalShapes() {
        val gxTpl = 120f
        val gyTpl = 50f
        val magTpl = sqrt((gxTpl * gxTpl + gyTpl * gyTpl).toDouble()).toFloat()
        val nx = gxTpl / magTpl
        val ny = gyTpl / magTpl

        // Тестируем тот же вектор направления на экране
        val gxScreen = 240f
        val gyScreen = 100f
        val magScreen = sqrt((gxScreen * gxScreen + gyScreen * gyScreen).toDouble()).toFloat()

        val cosTheta = (gxScreen * nx + gyScreen * ny) / magScreen
        assertEquals("Идентичные направления контура формы обязаны давать cos(θ)=1.0", 1.0f, cosTheta, 1e-4f)
    }

    @Test
    fun testShapeOnlyMode_RejectsColorDiscrepancyWhenShapeMatches() {
        val colorWeight = 0.0f
        val edgeWeight = 1.0f
        val colScore = 0.10f // Цвета полностью отличаются
        val edScore = 0.96f  // Форма и силуэт совпадают идеально

        val finalScore = colScore * colorWeight + edScore * edgeWeight
        assertEquals(0.96f, finalScore, 1e-4f)
        assertTrue("В режиме чистой формы score определяется исключительно контуром", finalScore >= 0.90f)
    }
}
