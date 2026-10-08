package com.example.autotap.core.math

import com.example.autotap.domain.model.Point2D
import kotlin.math.abs
import kotlin.math.hypot

object PathCompressionEngine {

    /**
     * Сжимает последовательность точек жеста, создавая опорные вершины
     * СТРОГО в местах радикального изменения направления (углы, резкие повороты, изломы траектории).
     *
     * Гарантирует минимальное количество точек для чистого и удобного ручного
     * редактирования в оверлеях (TargetOverlayManager) и соблюдение лимитов AOSP.
     */
    fun compressPath(
        points: List<Point2D>,
        maxPoints: Int = 80,
        initialEpsilon: Float = 3.5f
    ): List<Point2D> {
        if (points.size <= 2) return points

        // Шаг 1: Первичная RDP-фильтрация сенсорного высокочастотного шума
        var eps = initialEpsilon
        var compressed = rdpRecursive(points, eps)

        var iterations = 0
        while (compressed.size > maxPoints && iterations < 8) {
            eps *= 1.45f
            compressed = rdpRecursive(points, eps)
            iterations++
        }

        // Шаг 2: Фильтрация узлов строго по местам радикального изменения направления
        val radicalOnly = filterRadicalDirectionChanges(compressed, minAngleDeg = 25f, minDistancePx = 24f)

        val finalResult = if (radicalOnly.size > maxPoints) {
            downsampleFixed(radicalOnly, maxPoints)
        } else {
            radicalOnly
        }

        return if (finalResult.size >= 2) finalResult else listOf(points.first(), points.last())
    }

    /**
     * Фильтрует вершины траектории, выделяя точки СТРОГО там, где происходит
     * радикальное изменение вектора движения (углы поворота >= minAngleDeg).
     *
     * Устраняет промежуточные паразитные точки на прямых отрезках и при незначительных микроколебаниях пальца.
     */
    fun filterRadicalDirectionChanges(
        points: List<Point2D>,
        minAngleDeg: Float = 25f,
        minDistancePx: Float = 24f
    ): List<Point2D> {
        if (points.size <= 2) return points

        var totalLen = 0f
        for (i in 0 until points.size - 1) {
            totalLen += hypot((points[i + 1].x - points[i].x).toDouble(), (points[i + 1].y - points[i].y).toDouble()).toFloat()
        }

        if (totalLen < 15f) {
            return listOf(points.first(), points.last())
        }

        // Адаптивный порог минимального расстояния для коротких жестов
        val effectiveMinDist = minOf(minDistancePx, maxOf(10f, totalLen * 0.08f))

        val result = ArrayList<Point2D>()
        val startPt = points.first()
        val endPt = points.last()
        result.add(startPt)

        var anchor = startPt
        var i = 1
        while (i < points.size - 1) {
            val candidate = points[i]
            val next = points[i + 1]

            val inDx = candidate.x - anchor.x
            val inDy = candidate.y - anchor.y
            val inLen = hypot(inDx.toDouble(), inDy.toDouble()).toFloat()

            val outDx = next.x - candidate.x
            val outDy = next.y - candidate.y
            val outLen = hypot(outDx.toDouble(), outDy.toDouble()).toFloat()

            // Если кандидат слишком близок к предыдущему опорному узлу — пропускаем
            if (inLen < effectiveMinDist) {
                i++
                continue
            }

            // Вычисляем угол поворота между направлением движения к кандидату и направлением от кандидата
            val dot = inDx * outDx + inDy * outDy
            val cosTheta = if (inLen > 1e-4f && outLen > 1e-4f) {
                (dot / (inLen * outLen)).toDouble().coerceIn(-1.0, 1.0)
            } else 1.0

            val angleRad = kotlin.math.acos(cosTheta)
            val angleDeg = Math.toDegrees(angleRad).toFloat()

            // Отклонение точки от прямой линии между anchor и next
            val perpDist = perpendicularDistance(candidate, anchor, next)

            // Радикальное изменение: резкий угловой перелом (>= minAngleDeg)
            // либо выраженный изгиб относительно хорды
            val isRadical = (angleDeg >= minAngleDeg && inLen >= effectiveMinDist && outLen >= 6f) ||
                    (perpDist >= effectiveMinDist && angleDeg >= 18f)

            if (isRadical) {
                result.add(candidate)
                anchor = candidate
            }
            i++
        }

        val lastKept = result.last()
        val distToEnd = hypot((endPt.x - lastKept.x).toDouble(), (endPt.y - lastKept.y).toDouble()).toFloat()

        if (distToEnd < effectiveMinDist * 0.65f && result.size > 1) {
            result[result.size - 1] = endPt
        } else {
            result.add(endPt)
        }

        return result
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
