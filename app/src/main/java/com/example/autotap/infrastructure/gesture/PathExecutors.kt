package com.example.autotap.infrastructure.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.PlayMode
import com.example.autotap.domain.model.Point2D
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.hypot

interface VirtualStick {
    fun setDirection(dx: Float, dy: Float, strength: Float)
    fun release()
}

interface PathPlayer {
    suspend fun play(action: MacroAction)
}

class GesturePathPlayer(
    private val serviceProvider: () -> AccessibilityService?
) : PathPlayer {
    override suspend fun play(action: MacroAction) {
        val service = serviceProvider() ?: return
        val points = action.pathPoints

        if (points.isEmpty()) return

        // Поддержка обычных свайпов, если pathPoints пусто, но есть координаты
        val rawPoints = if (points.size >= 2) points else listOf(
            Point2D(action.posX, action.posY, 0L),
            Point2D(action.endX ?: (action.posX + 100f), action.endY ?: (action.posY + 100f), action.holdDurationMs)
        )

        // Генерируем сглаженную траекторию сплайнами
        val effectivePoints = com.example.autotap.core.math.PathCompressionEngine.generateSmoothSpline(rawPoints, pointsPerSegment = 6)

        val androidPath = Path()
        effectivePoints.forEachIndexed { index, point ->
            if (index == 0) {
                androidPath.moveTo(point.x, point.y)
            } else {
                androidPath.lineTo(point.x, point.y)
            }
        }

        val lastTime = effectivePoints.last().timeOffsetMs
        val duration = if (lastTime > 0L) lastTime.coerceAtLeast(30L) else action.holdDurationMs.coerceAtLeast(30L)
        val stroke = GestureDescription.StrokeDescription(androidPath, 0L, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        suspendCancellableCoroutine<Unit> { cont ->
            val dispatched = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                },
                null
            )
            if (!dispatched && cont.isActive) {
                cont.resume(Unit)
            }
        }
    }
}

class VirtualStickPathPlayer(
    private val stick: VirtualStick
) : PathPlayer {
    override suspend fun play(action: MacroAction) {
        val rawPoints = action.pathPoints
        if (rawPoints.size < 2) return

        val points = com.example.autotap.core.math.PathCompressionEngine.generateSmoothSpline(rawPoints, pointsPerSegment = 6)

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]

            val dx = curr.x - prev.x
            val dy = curr.y - prev.y
            val len = hypot(dx, dy)

            if (len > 0f) {
                stick.setDirection(dx / len, dy / len, 1f)
            }

            val delayMs = (curr.timeOffsetMs - prev.timeOffsetMs).coerceAtLeast(1L)
            delay(delayMs)
        }

        stick.release()
    }
}

class ActionExecutor(
    private val gesturePlayer: GesturePathPlayer,
    private val stickPlayer: VirtualStickPathPlayer
) {
    suspend fun execute(action: MacroAction) {
        when (action.playMode) {
            PlayMode.GESTURE -> gesturePlayer.play(action)
            PlayMode.VIRTUAL_STICK -> stickPlayer.play(action)
        }
    }
}
