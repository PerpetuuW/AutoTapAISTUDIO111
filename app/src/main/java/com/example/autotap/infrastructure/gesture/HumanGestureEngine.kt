package com.example.autotap.infrastructure.gesture

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

object HumanGestureEngine {
    fun buildHumanSwipeGesture(
        startX: Float, startY: Float, endX: Float, endY: Float,
        durationMs: Long, screenW: Int, screenH: Int
    ): GestureDescription {
        val maxW = (screenW - 1).toFloat().coerceAtLeast(0f)
        val maxH = (screenH - 1).toFloat().coerceAtLeast(0f)
        val safeStartX = startX.coerceIn(0f, maxW)
        val safeStartY = startY.coerceIn(0f, maxH)
        val safeEndX = endX.coerceIn(0f, maxW)
        val safeEndY = endY.coerceIn(0f, maxH)

        val distance = hypot((safeEndX - safeStartX).toDouble(), (safeEndY - safeStartY).toDouble()).toFloat()
        if (distance < 5f) {
            val fallbackPath = Path().apply {
                moveTo(safeStartX, safeStartY)
                lineTo(safeStartX, safeStartY)
            }
            val stroke = GestureDescription.StrokeDescription(fallbackPath, 0L, durationMs.coerceAtLeast(60L))
            return GestureDescription.Builder().addStroke(stroke).build()
        }

        val curvature = (distance * 0.12f).coerceIn(10f, 60f)
        val angle = atan2((safeEndY - safeStartY).toDouble(), (safeEndX - safeStartX).toDouble())
        val normalAngle = angle + (if (Random.nextBoolean()) Math.PI / 2 else -Math.PI / 2)

        val mid1X = safeStartX + (safeEndX - safeStartX) * 0.33f + (curvature * cos(normalAngle)).toFloat() * Random.nextFloat()
        val mid1Y = safeStartY + (safeEndY - safeStartY) * 0.33f + (curvature * sin(normalAngle)).toFloat() * Random.nextFloat()
        val mid2X = safeStartX + (safeEndX - safeStartX) * 0.66f + (curvature * cos(normalAngle)).toFloat() * (Random.nextFloat() * 0.7f)
        val mid2Y = safeStartY + (safeEndY - safeStartY) * 0.66f + (curvature * sin(normalAngle)).toFloat() * (Random.nextFloat() * 0.7f)

        val path = Path()
        path.moveTo(safeStartX, safeStartY)

        val steps = (durationMs / 14).toInt().coerceIn(16, 75)
        for (i in 1..steps) {
            val t = i.toFloat() / steps.toFloat()
            val easedT = when {
                t < 0.35f -> (t / 0.35f).pow(2) * 0.45f
                t < 0.80f -> 0.45f + ((t - 0.35f) / 0.45f) * 0.45f
                else -> 0.90f + (1f - (1f - (t - 0.80f) / 0.20f).pow(2)) * 0.10f
            }

            // ИНВАРИАНТ 8: Гауссовский джиттер (Box-Muller) для защиты от античитов
            val u1 = Random.nextDouble().coerceAtLeast(1e-6)
            val u2 = Random.nextDouble().coerceAtLeast(1e-6)
            val mag = kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * 1.5
            val angle = 2.0 * Math.PI * u2
            
            val jitterX = if (i < steps) (mag * kotlin.math.cos(angle)).toFloat() else 0f
            val jitterY = if (i < steps) (mag * kotlin.math.sin(angle)).toFloat() else 0f

            val x = (1 - easedT).pow(3) * safeStartX +
                    3 * (1 - easedT).pow(2) * easedT * mid1X +
                    3 * (1 - easedT) * easedT.pow(2) * mid2X +
                    easedT.pow(3) * safeEndX + jitterX

            val y = (1 - easedT).pow(3) * safeStartY +
                    3 * (1 - easedT).pow(2) * easedT * mid1Y +
                    3 * (1 - easedT) * easedT.pow(2) * mid2Y +
                    easedT.pow(3) * safeEndY + jitterY

            path.lineTo(x.coerceIn(0f, maxW), y.coerceIn(0f, maxH))
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(60L))
        return GestureDescription.Builder().addStroke(stroke).build()
    }
}
