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

        // Поддержка обычных свайпов, если pathPoints пусто, но заданы координаты
        val rawPoints = if (points.size >= 2) points else listOf(
            Point2D(action.posX, action.posY, 0L),
            Point2D(action.endX ?: (action.posX + 100f), action.endY ?: (action.posY + 100f), action.holdDurationMs)
        )

        // Генерируем сглаженную траекторию сплайнами с сохранением timeOffsetMs
        val effectivePoints = com.example.autotap.core.math.PathCompressionEngine.generateSmoothSpline(rawPoints, pointsPerSegment = 6)
        if (effectivePoints.size < 2) return

        // [Чанкинг путей]: разбиваем длинный путь на окна по ~250 мс для защиты от переполнения буфера Binder
        val chunks = mutableListOf<List<Point2D>>()
        var currentChunk = mutableListOf<Point2D>()
        currentChunk.add(effectivePoints[0])

        var chunkStartTime = effectivePoints[0].timeOffsetMs

        for (i in 1 until effectivePoints.size) {
            val pt = effectivePoints[i]
            currentChunk.add(pt)
            // Если накопленный интервал чанка превысил 250 мс или это последняя точка, фиксируем чанк
            if ((pt.timeOffsetMs - chunkStartTime >= 250L) || i == effectivePoints.size - 1) {
                if (currentChunk.size >= 2) {
                    chunks.add(currentChunk.toList())
                }
                currentChunk = mutableListOf(pt)
                chunkStartTime = pt.timeOffsetMs
            }
        }
        if (currentChunk.size >= 2 && !chunks.contains(currentChunk)) {
            chunks.add(currentChunk.toList())
        }
        if (chunks.isEmpty()) return

        // Выполняем чанки последовательно, склеивая их через continueStroke (доступно на Android O / API 26+)
        suspendCancellableCoroutine<Unit> { cont ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                playChunksChained(service, chunks, 0, null, cont)
            } else {
                // Фолбэк для старых версий (монолитный путь)
                playLegacySingleStroke(service, effectivePoints, cont)
            }
        }
    }

    private fun playChunksChained(
        service: AccessibilityService,
        chunks: List<List<Point2D>>,
        chunkIndex: Int,
        previousStroke: GestureDescription.StrokeDescription?,
        cont: kotlinx.coroutines.CancellableContinuation<Unit>
    ) {
        if (chunkIndex >= chunks.size) {
            if (cont.isActive) cont.resume(Unit)
            return
        }

        val chunk = chunks[chunkIndex]
        val isLast = (chunkIndex == chunks.size - 1)
        val startTimeOffset = chunk.first().timeOffsetMs
        val duration = (chunk.last().timeOffsetMs - startTimeOffset).coerceAtLeast(30L)

        val androidPath = Path().apply {
            moveTo(chunk[0].x, chunk[0].y)
            for (i in 1 until chunk.size) {
                lineTo(chunk[i].x, chunk[i].y)
            }
        }

        val stroke = if (previousStroke != null) {
            previousStroke.continueStroke(androidPath, 0L, duration, !isLast)
        } else {
            GestureDescription.StrokeDescription(androidPath, 0L, duration, !isLast)
        }

        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (isLast) {
                        if (cont.isActive) cont.resume(Unit)
                    } else {
                        playChunksChained(service, chunks, chunkIndex + 1, stroke, cont)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    // При отмене чанка прерываем цепочку, чтобы избежать зависаний системы
                    if (cont.isActive) cont.resume(Unit)
                }
            },
            null
        )
    }

    private fun playLegacySingleStroke(
        service: AccessibilityService,
        effectivePoints: List<Point2D>,
        cont: kotlinx.coroutines.CancellableContinuation<Unit>
    ) {
        val androidPath = Path().apply {
            moveTo(effectivePoints[0].x, effectivePoints[0].y)
            for (i in 1 until effectivePoints.size) {
                lineTo(effectivePoints[i].x, effectivePoints[i].y)
            }
        }
        val duration = effectivePoints.last().timeOffsetMs.coerceAtLeast(30L)
        val stroke = GestureDescription.StrokeDescription(androidPath, 0L, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

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