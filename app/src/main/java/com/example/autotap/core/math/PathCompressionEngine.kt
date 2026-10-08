package com.example.autotap.core.math

import com.example.autotap.domain.model.Point2D
import kotlin.math.abs
import kotlin.math.hypot

object PathCompressionEngine {

    /**
     * Сжимает последовательность точек жеста алгоритмом Рамера — Дугласа — Пекера (RDP).
     * Гарантирует строгое соблюдение лимита вершин AOSP (maxPoints <= 80).
     */
    fun compressPath(points: List<Point2D>, maxPoints: Int = 80, initialEpsilon: Float = 3.5f): List<Point2D> {
        if (points.size <= 2) return points
        var eps = initialEpsilon
        var compressed = rdpRecursive(points, eps)

        var iterations = 0
        while (compressed.size > maxPoints && iterations < 8) {
            eps *= 1.45f
            compressed = rdpRecursive(points, eps)
            iterations++
        }

        if (compressed.size > maxPoints) {
            compressed = downsampleFixed(compressed, maxPoints)
        }

        return compressed
    }

    private fun rdpRecursive(points: List<Point2D>, epsilon: Float): List<Point2D> {
        if (points.size <= 2) return points

        var maxDistance = 0f
        var maxIndex = 0

        val start = points.first()
        val end = points.last()

        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], start, end)
            if (dist > maxDistance) {
                maxDistance = dist
                maxIndex = i
            }
        }

        return if (maxDistance > epsilon) {
            val left = rdpRecursive(points.subList(0, maxIndex + 1), epsilon)
            val right = rdpRecursive(points.subList(maxIndex, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }

    private fun downsampleFixed(points: List<Point2D>, targetCount: Int): List<Point2D> {
        if (points.size <= targetCount || targetCount < 2) return points
        val result = ArrayList<Point2D>(targetCount)
        val step = (points.size - 1).toDouble() / (targetCount - 1).toDouble()
        for (i in 0 until targetCount - 1) {
            val idx = (i * step).toInt().coerceIn(0, points.size - 1)
            result.add(points[idx])
        }
        result.add(points.last())
        return result
    }

    private fun perpendicularDistance(pt: Point2D, lineStart: Point2D, lineEnd: Point2D): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val lineLength = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (lineLength < 1e-4f) {
            return hypot((pt.x - lineStart.x).toDouble(), (pt.y - lineStart.y).toDouble()).toFloat()
        }

        val numerator = abs(dy * pt.x - dx * pt.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        return numerator / lineLength
    }
}
