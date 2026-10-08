package com.example.autotap

import com.example.autotap.core.math.PathCompressionEngine
import com.example.autotap.domain.model.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathCompressionRadicalDirectionTestSuite {

    @Test
    fun testStraightLineWithTremor_CompressesToExactStartAndEnd() {
        val points = mutableListOf<Point2D>()
        for (i in 0..100) {
            // Прямая линия с легким высокочастотным тремором руки (+- 2px)
            val wobble = if (i % 2 == 0) 1.5f else -1.5f
            points.add(Point2D(i * 5f, i * 3f + wobble))
        }

        val compressed = PathCompressionEngine.compressPath(points)

        assertEquals("Прямая линия с тремором обязана сжаться ровно до 2 точек (старт и финиш)", 2, compressed.size)
        assertEquals(points.first().x, compressed.first().x, 1e-3f)
        assertEquals(points.last().x, compressed.last().x, 1e-3f)
    }

    @Test
    fun testLShapedGesture_CreatesPointOnlyAtRadicalTurnCorner() {
        val points = mutableListOf<Point2D>()
        // Сегмент 1: Движение строго вниз от (100, 100) до (100, 400)
        for (y in 100..400 step 5) {
            points.add(Point2D(100f, y.toFloat()))
        }
        // Сегмент 2: Радикальный поворот на 90 градусов вправо от (100, 400) до (500, 400)
        for (x in 105..500 step 5) {
            points.add(Point2D(x.toFloat(), 400f))
        }

        val compressed = PathCompressionEngine.compressPath(points)

        assertEquals("L-образный жест обязан содержать ровно 3 точки: старт, вершина угла 90° и финиш", 3, compressed.size)
        // Проверяем, что промежуточная точка находится ровно в вершине угла
        val corner = compressed[1]
        assertEquals(100f, corner.x, 5f)
        assertEquals(400f, corner.y, 5f)
    }

    @Test
    fun testZShapedGesture_CreatesPointsOnlyAtTwoTurningVertices() {
        val points = mutableListOf<Point2D>()
        // 1. Вправо: (50, 100) -> (300, 100)
        for (x in 50..300 step 5) points.add(Point2D(x.toFloat(), 100f))
        // 2. Диагональ вниз-влево: (300, 100) -> (50, 400)
        for (step in 1..50) {
            val t = step / 50f
            points.add(Point2D(300f - 250f * t, 100f + 300f * t))
        }
        // 3. Вправо: (50, 400) -> (350, 400)
        for (x in 55..350 step 5) points.add(Point2D(x.toFloat(), 400f))

        val compressed = PathCompressionEngine.compressPath(points)

        assertEquals("Z-образный жест с двумя изломами обязан содержать ровно 4 точки", 4, compressed.size)
    }

    @Test
    fun testUShapedGesture_ExtractsKeyTurningCorners() {
        val points = mutableListOf<Point2D>()
        // Вниз: (100, 100) -> (100, 500)
        for (y in 100..500 step 5) points.add(Point2D(100f, y.toFloat()))
        // Вправо: (100, 500) -> (400, 500)
        for (x in 105..400 step 5) points.add(Point2D(x.toFloat(), 500f))
        // Вверх: (400, 500) -> (400, 100)
        for (y in 495 downTo 100 step 5) points.add(Point2D(400f, y.toFloat()))

        val compressed = PathCompressionEngine.compressPath(points)

        assertEquals("U-образная траектория с двумя углами поворота обязана содержать ровно 4 точки", 4, compressed.size)
    }

    @Test
    fun testCurvedArc_CompressesToSparseKeyControlPoints() {
        val points = mutableListOf<Point2D>()
        // Дуга четверти окружности радиусом 300px
        for (deg in 0..90 step 2) {
            val rad = Math.toRadians(deg.toDouble())
            points.add(Point2D((500 + kotlin.math.cos(rad) * 300).toFloat(), (500 + kotlin.math.sin(rad) * 300).toFloat()))
        }

        val compressed = PathCompressionEngine.compressPath(points)

        assertTrue("Плавная дуга не должна содержать избыточных точек для редактирования (<= 4 точек)", compressed.size in 2..4)
    }
}
