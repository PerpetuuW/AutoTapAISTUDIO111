package com.example.autotap.infrastructure.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.core.math.PathCompressionEngine
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.PlayMode
import com.example.autotap.domain.model.Point2D
import com.example.autotap.infrastructure.accessibility.AutoTapAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

interface VirtualStick {
    fun setDirection(dx: Float, dy: Float, strength: Float)
    fun release()
}

interface PathPlayer {
    suspend fun play(action: MacroAction)
}

object PathTimelineNormalizer {
    /**
     * Нормализует временную шкалу точек траектории под целевую длительность holdDurationMs.
     * Если у точек нет таймстампов (все 0L) или они нарушены после ручного перетаскивания,
     * время распределяется пропорционально физическому расстоянию между точками.
     */
    fun normalizeAndSmooth(action: MacroAction, screenW: Int, screenH: Int): List<Point2D> {
        val maxX = (screenW - 1).coerceAtLeast(1).toFloat()
        val maxY = (screenH - 1).coerceAtLeast(1).toFloat()
        val targetDuration = action.holdDurationMs.coerceIn(40L, 60000L)

        val basePoints = if (action.pathPoints.size >= 2) {
            action.pathPoints
        } else {
            val endX = action.endX ?: (action.posX + 100f)
            val endY = action.endY ?: (action.posY + 100f)
            listOf(
                Point2D(action.posX, action.posY, 0L),
                Point2D(endX, endY, targetDuration)
            )
        }

        // Проверяем, монотонны ли существующие таймстампы
        var hasValidMonotonicTime = basePoints.last().timeOffsetMs > basePoints.first().timeOffsetMs
        if (hasValidMonotonicTime) {
            for (i in 1 until basePoints.size) {
                if (basePoints[i].timeOffsetMs < basePoints[i - 1].timeOffsetMs) {
                    hasValidMonotonicTime = false
                    break
                }
            }
        }

        val timedBasePoints = if (hasValidMonotonicTime) {
            val startT = basePoints.first().timeOffsetMs
            val origTotal = (basePoints.last().timeOffsetMs - startT).coerceAtLeast(1L)
            // Масштабируем записанный ритм под актуальный action.holdDurationMs
            val scale = targetDuration.toDouble() / origTotal.toDouble()
            basePoints.map { pt ->
                Point2D(
                    x = pt.x.coerceIn(0f, maxX),
                    y = pt.y.coerceIn(0f, maxY),
                    timeOffsetMs = ((pt.timeOffsetMs - startT) * scale).toLong().coerceIn(0L, targetDuration)
                )
            }
        } else {
            // Распределяем время пропорционально длине сегментов
            val cumDist = FloatArray(basePoints.size)
            var totalDist = 0f
            for (i in 1 until basePoints.size) {
                val dx = basePoints[i].x - basePoints[i - 1].x
                val dy = basePoints[i].y - basePoints[i - 1].y
                totalDist += hypot(dx.toDouble(), dy.toDouble()).toFloat()
                cumDist[i] = totalDist
            }
            val safeTotalDist = totalDist.coerceAtLeast(1f)
            basePoints.mapIndexed { idx, pt ->
                val t = if (idx == basePoints.lastIndex) targetDuration
                else ((cumDist[idx] / safeTotalDist) * targetDuration).toLong()
                Point2D(
                    x = pt.x.coerceIn(0f, maxX),
                    y = pt.y.coerceIn(0f, maxY),
                    timeOffsetMs = t
                )
            }
        }

        val smoothed = if (timedBasePoints.size >= 3) {
            PathCompressionEngine.generateSmoothSpline(timedBasePoints, pointsPerSegment = 6)
        } else {
            timedBasePoints
        }

        // Гарантируем, что дуги сплайна не вышли за границы экрана (защита от краша StrokeDescription)
        return smoothed.map { pt ->
            Point2D(
                x = pt.x.coerceIn(0f, maxX),
                y = pt.y.coerceIn(0f, maxY),
                timeOffsetMs = pt.timeOffsetMs.coerceIn(0L, targetDuration)
            )
        }
    }
}

class GesturePathPlayer(
    private val serviceProvider: () -> AccessibilityService?
) : PathPlayer {

    override suspend fun play(action: MacroAction) {
        val service = serviceProvider() ?: return
        val dm = service.resources.displayMetrics
        val sw = dm.widthPixels.takeIf { it > 0 } ?: 1080
        val sh = dm.heightPixels.takeIf { it > 0 } ?: 2400

        val effectivePoints = PathTimelineNormalizer.normalizeAndSmooth(action, sw, sh)
        if (effectivePoints.size < 2) return

        val dispatcher = (service as? AutoTapAccessibilityService)?.gestureDispatcher
        val minX = effectivePoints.minOf { it.x }
        val minY = effectivePoints.minOf { it.y }
        val maxX = effectivePoints.maxOf { it.x }
        val maxY = effectivePoints.maxOf { it.y }
        val boundsRect = RectF(minX, minY, maxX, maxY)

        // [Чанкинг путей]: разбиваем длинный путь на окна по ~250 мс для защиты от переполнения буфера Binder
        val chunks = mutableListOf<List<Point2D>>()
        var currentChunk = mutableListOf<Point2D>()
        currentChunk.add(effectivePoints[0])
        var chunkStartTime = effectivePoints[0].timeOffsetMs

        for (i in 1 until effectivePoints.size) {
            val pt = effectivePoints[i]
            currentChunk.add(pt)
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

        dispatcher?.onGesturePassthroughToggle?.invoke(true, boundsRect)
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && chunks.size > 1) {
                    playChunksChained(service, chunks, 0, null, cont)
                } else {
                    playLegacySingleStroke(service, effectivePoints, cont)
                }
            }
        } finally {
            dispatcher?.onGesturePassthroughToggle?.invoke(false, null)
        }
    }

    private fun playChunksChained(
        service: AccessibilityService,
        chunks: List<List<Point2D>>,
        chunkIndex: Int,
        previousStroke: GestureDescription.StrokeDescription?,
        cont: kotlinx.coroutines.CancellableContinuation<Unit>
    ) {
        if (!cont.isActive || chunkIndex >= chunks.size) {
            if (cont.isActive) cont.resume(Unit)
            return
        }

        val chunk = chunks[chunkIndex]
        val isLast = (chunkIndex == chunks.size - 1)
        val startTimeOffset = chunk.first().timeOffsetMs
        val duration = (chunk.last().timeOffsetMs - startTimeOffset).coerceIn(15L, 10000L)

        val androidPath = Path().apply {
            moveTo(chunk[0].x, chunk[0].y)
            for (i in 1 until chunk.size) {
                lineTo(chunk[i].x, chunk[i].y)
            }
        }

        try {
            val stroke = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && previousStroke != null) {
                previousStroke.continueStroke(androidPath, 0L, duration, !isLast)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                GestureDescription.StrokeDescription(androidPath, 0L, duration, !isLast)
            } else {
                GestureDescription.StrokeDescription(androidPath, 0L, duration)
            }

            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = service.dispatchGesture(
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
                        if (cont.isActive) cont.resume(Unit)
                    }
                },
                null
            )
            if (!dispatched && cont.isActive) {
                cont.resume(Unit)
            }
        } catch (t: Throwable) {
            AppLogger.logError(service, "PATH_PLAYER_CHUNK", t)
            if (cont.isActive) cont.resume(Unit)
        }
    }

    private fun playLegacySingleStroke(
        service: AccessibilityService,
        effectivePoints: List<Point2D>,
        cont: kotlinx.coroutines.CancellableContinuation<Unit>
    ) {
        try {
            val androidPath = Path().apply {
                moveTo(effectivePoints[0].x, effectivePoints[0].y)
                for (i in 1 until effectivePoints.size) {
                    lineTo(effectivePoints[i].x, effectivePoints[i].y)
                }
            }
            val duration = effectivePoints.last().timeOffsetMs.coerceIn(30L, 60000L)
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
        } catch (t: Throwable) {
            AppLogger.logError(service, "PATH_PLAYER_SINGLE", t)
            if (cont.isActive) cont.resume(Unit)
        }
    }
}

class VirtualStickPathPlayer(
    private val stick: VirtualStick
) : PathPlayer {
    override suspend fun play(action: MacroAction) {
        val points = PathTimelineNormalizer.normalizeAndSmooth(action, 1080, 2400)
        if (points.size < 2) return

        try {
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]

                val dx = curr.x - prev.x
                val dy = curr.y - prev.y
                val len = hypot(dx, dy)

                if (len > 0f) {
                    stick.setDirection(dx / len, dy / len, 1f)
                }

                val delayMs = (curr.timeOffsetMs - prev.timeOffsetMs).coerceAtLeast(4L)
                delay(delayMs)
            }
        } finally {
            stick.release()
        }
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