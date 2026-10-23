package com.example.autotap.core.math

import com.example.autotap.domain.model.Point2D
import kotlin.math.abs
import kotlin.math.hypot

object PathCompressionEngine {

    /**
     * Точное RDP-сжатие записанных траекторий жестов (дуги, джойстики, волны)
     * БЕЗ отсечения сглаженных изгибов. Сохраняет естественную плавно изогнутую геометрию.
     */
    fun compressRecordedPath(
        points: List<Point2D>,
        maxPoints: Int = 60,
        initialEpsilon: Float = 2.5f
    ): List<Point2D> {
        if (points.size <= 2) return points

        var eps = initialEpsilon
        var compressed = rdpRecursive(points, eps)

        var iterations = 0
        while (compressed.size > maxPoints && iterations < 8) {
            eps *= 1.35f
            compressed = rdpRecursive(points, eps)
            iterations++
        }

        val finalResult = if (compressed.size > maxPoints) {
            downsampleFixed(compressed, maxPoints)
        } else {
            compressed
        }

        return if (finalResult.size >= 2) finalResult else listOf(points.first(), points.last())
    }

    /**
     * Сжимает последовательность точек жеста, создавая опорные вершины
     * СТРОГО в местах радикального изменения направления (углы, резкие повороты, изломы траектории).
     */
    fun compressPath(
        points: List<Point2D>,
        maxPoints: Int = 80,
        initialEpsilon: Float = 3.5f
    ): List<Point2D> {
        if (points.size <= 2) return points

        var eps = initialEpsilon
        var compressed = rdpRecursive(points, eps)

        var iterations = 0
        while ((compressed.size > maxPoints) && (iterations < 8)) {
            eps *= 1.45f
            compressed = rdpRecursive(points, eps)
            iterations++
        }

        val radicalOnly = filterRadicalDirectionChanges(compressed, minAngleDeg = 25f, minDistancePx = 24f)

        val finalResult = if (radicalOnly.size > maxPoints) {
            downsampleFixed(radicalOnly, maxPoints)
        } else {
            radicalOnly
        }

        return if (finalResult.size >= 2) finalResult else listOf(points.first(), points.last())
    }

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

            if (inLen < effectiveMinDist) {
                i++
                continue
            }

            val dot = inDx * outDx + inDy * outDy
            val cosTheta = if (inLen > 1e-4f && outLen > 1e-4f) {
                (dot / (inLen * outLen)).toDouble().coerceIn(-1.0, 1.0)
            } else 1.0

            val angleRad = kotlin.math.acos(cosTheta)
            val angleDeg = Math.toDegrees(angleRad).toFloat()
            val perpDist = perpendicularDistance(candidate, anchor, next)

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

    /**
     * Генерирует плавную человекоподобную траекторию с использованием сглаживания Catmull-Rom Сплайном.
     * Если у точек не заданы монотоннно возрастающие timeOffsetMs (например, все 0L),
     * автоматически распределяет fallbackDurationMs пропорционально длине сегментов.
     */
    fun generateSmoothSpline(
        points: List<Point2D>,
        pointsPerSegment: Int = 8,
        fallbackDurationMs: Long = 1000L
    ): List<Point2D> {
        if (points.size < 2) return points

        var isMonotonicTime = points.last().timeOffsetMs > points.first().timeOffsetMs
        if (isMonotonicTime) {
            for (i in 1 until points.size) {
                if (points[i].timeOffsetMs < points[i - 1].timeOffsetMs) {
                    isMonotonicTime = false
                    break
                }
            }
        }

        val timedPoints = if (isMonotonicTime) {
            points
        } else {
            var totalLen = 0f
            val dists = FloatArray(points.size)
            for (i in 1 until points.size) {
                totalLen += hypot((points[i].x - points[i - 1].x).toDouble(), (points[i].y - points[i - 1].y).toDouble()).toFloat()
                dists[i] = totalLen
            }
            val safeTotal = totalLen.coerceAtLeast(1f)
            val dur = fallbackDurationMs.coerceAtLeast(100L)
            points.mapIndexed { idx, pt ->
                val tMs = if (idx == points.lastIndex) dur else ((dists[idx] / safeTotal) * dur).toLong()
                Point2D(pt.x, pt.y, tMs)
            }
        }

        if (timedPoints.size < 3) return timedPoints

        val result = ArrayList<Point2D>()
        val p = ArrayList<Point2D>()

        p.add(timedPoints.first())
        p.addAll(timedPoints)
        p.add(timedPoints.last())

        for (i in 1 until p.size - 2) {
            val p0 = p[i - 1]
            val p1 = p[i]
            val p2 = p[i + 1]
            val p3 = p[i + 2]

            for (step in 0 until pointsPerSegment) {
                val t = step.toFloat() / pointsPerSegment.toFloat()
                val t2 = t * t
                val t3 = t2 * t

                val x = 0.5f * ((2f * p1.x) +
                        (-p0.x + p2.x) * t +
                        (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
                        (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3)

                val y = 0.5f * ((2f * p1.y) +
                        (-p0.y + p2.y) * t +
                        (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
                        (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3)

                val timeMs = p1.timeOffsetMs + ((p2.timeOffsetMs - p1.timeOffsetMs) * t).toLong()

                result.add(Point2D(x, y, timeMs))
            }
        }
        result.add(timedPoints.last())
        return result
    }
}