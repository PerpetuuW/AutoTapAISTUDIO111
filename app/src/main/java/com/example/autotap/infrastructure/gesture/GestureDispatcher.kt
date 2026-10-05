package com.example.autotap.infrastructure.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.domain.gateway.IGestureGateway
import com.example.autotap.domain.model.Point2D
import com.example.autotap.infrastructure.visualizer.GestureVisualizerManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GestureDispatcher(
    private val serviceProvider: () -> AccessibilityService?,
    private val visualizerManager: GestureVisualizerManager
) : IGestureGateway {

    private val mainHandler = Handler(Looper.getMainLooper())
    var onGesturePassthroughToggle: ((Boolean) -> Unit)? = null

    private fun safeTogglePassthrough(enabled: Boolean) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onGesturePassthroughToggle?.invoke(enabled)
        } else {
            mainHandler.post { onGesturePassthroughToggle?.invoke(enabled) }
        }
    }

    override fun performClick(x: Float, y: Float, durationMs: Long): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            performClickAsync(x, y, durationMs, null)
            return true
        }
        return performClickSync(x, y, durationMs, 2000L)
    }

    override fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            performSwipeAsync(startX, startY, endX, endY, durationMs, null)
            return true
        }
        return performSwipeSync(startX, startY, endX, endY, durationMs, 2000L)
    }

    override fun performPath(points: List<Point2D>, durationMs: Long): Boolean {
        if (points.isEmpty()) return false
        if (Looper.myLooper() == Looper.getMainLooper()) {
            performPathAsync(points, durationMs, null)
            return true
        }
        return performPathSync(points, durationMs, 3000L)
    }

    private var activeLiveStroke: android.accessibilityservice.GestureDescription.StrokeDescription? = null

    fun sendLivePathStart(startX: Float, startY: Float): Boolean {
        val service = serviceProvider() ?: return false
        val path = android.graphics.Path().apply {
            moveTo(startX, startY)
            lineTo(startX, startY)
        }
        val stroke = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 35L, true)
        } else {
            android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 35L)
        }
        activeLiveStroke = stroke
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
        return service.dispatchGesture(gesture, null, null)
    }

    fun sendLivePathUpdate(fromX: Float, fromY: Float, toX: Float, toY: Float): Boolean {
        val service = serviceProvider() ?: return false
        val path = android.graphics.Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val prev = activeLiveStroke
        val stroke = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && prev != null) {
            prev.continueStroke(path, 0L, 35L, true)
        } else {
            android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 35L, true)
        }
        activeLiveStroke = stroke
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
        return service.dispatchGesture(gesture, null, null)
    }

    fun sendLivePathFinish(lastX: Float, lastY: Float): Boolean {
        val service = serviceProvider() ?: return false
        val path = android.graphics.Path().apply {
            moveTo(lastX, lastY)
            lineTo(lastX, lastY)
        }
        val prev = activeLiveStroke
        val stroke = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && prev != null) {
            prev.continueStroke(path, 0L, 35L, false)
        } else {
            android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 35L, false)
        }
        activeLiveStroke = null
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
        return service.dispatchGesture(gesture, null, null)
    }

    override fun performPinch(centerX: Float, centerY: Float, startDistance: Float, endDistance: Float, durationMs: Long): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            performPinchAsync(centerX, centerY, startDistance, endDistance, durationMs, null)
            return true
        }
        return performPinchSync(centerX, centerY, startDistance, endDistance, durationMs, 2500L)
    }

    override fun vibrateFeedback(durationMs: Long) {
        val service = serviceProvider() ?: return
        try {
            val vibrator = service.getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
        }

        private fun getRealDisplayMetrics(service: AccessibilityService): DisplayMetrics {
        val wm = service.getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager
        val realMetrics = DisplayMetrics()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && wm != null) {
            val bounds = wm.maximumWindowMetrics.bounds
            realMetrics.widthPixels = bounds.width()
            realMetrics.heightPixels = bounds.height()
            realMetrics.density = service.resources.displayMetrics.density
            realMetrics.densityDpi = service.resources.displayMetrics.densityDpi
            return realMetrics
        }
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(realMetrics)
        if (realMetrics.widthPixels > 0 && realMetrics.heightPixels > 0) {
            return realMetrics
        }
        return service.resources.displayMetrics
        }

        fun performClickAsync(x: Float, y: Float, durationMs: Long, onComplete: ((Boolean) -> Unit)?) {
        val service = serviceProvider() ?: run {
            onComplete?.invoke(false)
            return
        }
        val dm: DisplayMetrics = getRealDisplayMetrics(service)
        val safeX = x.coerceIn(0f, (dm.widthPixels - 1).toFloat())
        val safeY = y.coerceIn(0f, (dm.heightPixels - 1).toFloat())

        visualizerManager.showClickVisualizer(safeX, safeY)
        AppLogger.log(service, "GESTURE", "Точечный клик в ($safeX, $safeY), длительность ${durationMs}мс")

        val path = Path().apply {
            moveTo(safeX, safeY)
            lineTo(safeX, safeY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(10L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val isDone = AtomicBoolean(false)
        val timeoutRunnable = Runnable {
            if (isDone.compareAndSet(false, true)) {
                safeTogglePassthrough(false)
                AppLogger.log(service, "GESTURE", "Таймаут клика в ($safeX, $safeY)")
                onComplete?.invoke(false)
            }
        }

                safeTogglePassthrough(true)
        val renderDelay = 80L
        mainHandler.postDelayed(timeoutRunnable, durationMs + 250L + renderDelay)

        mainHandler.postDelayed({
            if (isDone.get()) return@postDelayed
            try {
                val dispatched = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        safeTogglePassthrough(false)
                        if (isDone.compareAndSet(false, true)) onComplete?.invoke(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        safeTogglePassthrough(false)
                        if (isDone.compareAndSet(false, true)) onComplete?.invoke(false)
                    }
                }, null)

                if (!dispatched) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    safeTogglePassthrough(false)
                    if (isDone.compareAndSet(false, true)) onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                AppLogger.logError(service, "GESTURE_CRASH", e)
                mainHandler.removeCallbacks(timeoutRunnable)
                safeTogglePassthrough(false)
                if (isDone.compareAndSet(false, true)) onComplete?.invoke(false)
            }
        }, renderDelay)
    }

    fun performClickSync(x: Float, y: Float, durationMs: Long, timeoutMs: Long): Boolean {
        val latch = CountDownLatch(1)
        var result = false
        performClickAsync(x, y, durationMs) { success ->
            result = success
            latch.countDown()
        }
        try {
            latch.await(durationMs + timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return result
    }

    fun performSwipeAsync(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long, onComplete: ((Boolean) -> Unit)?) {
        val service = serviceProvider() ?: run {
            onComplete?.invoke(false)
            return
        }
        val dm: DisplayMetrics = getRealDisplayMetrics(service)
        visualizerManager.showSwipeVisualizer(startX, startY, endX, endY, durationMs)
        AppLogger.log(service, "GESTURE", "Свайп ($startX, $startY) -> ($endX, $endY), длительность ${durationMs}мс")

        val humanGesture = HumanGestureEngine.buildHumanSwipeGesture(startX, startY, endX, endY, durationMs, dm.widthPixels, dm.heightPixels)
        val isDone = AtomicBoolean(false)
        val timeoutRunnable = Runnable {
            if (isDone.compareAndSet(false, true)) {
                safeTogglePassthrough(false)
                onComplete?.invoke(false)
            }
        }

                safeTogglePassthrough(true)
        val renderDelay = 80L
        mainHandler.postDelayed(timeoutRunnable, durationMs + 300L + renderDelay)

        mainHandler.postDelayed({
            if (isDone.get()) return@postDelayed
            try {
                val dispatched = service.dispatchGesture(humanGesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        safeTogglePassthrough(false)
                        if (isDone.compareAndSet(false, true)) onComplete?.invoke(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        safeTogglePassthrough(false)
                        if (isDone.compareAndSet(false, true)) onComplete?.invoke(false)
                    }
                }, null)

                if (!dispatched) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    safeTogglePassthrough(false)
                    if (isDone.compareAndSet(false, true)) onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                AppLogger.logError(service, "GESTURE_CRASH", e)
                mainHandler.removeCallbacks(timeoutRunnable)
                safeTogglePassthrough(false)
                if (isDone.compareAndSet(false, true)) onComplete?.invoke(false)
            }
        }, renderDelay)
    }

    fun performSwipeSync(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long, timeoutMs: Long): Boolean {
        val latch = CountDownLatch(1)
        var result = false
        performSwipeAsync(startX, startY, endX, endY, durationMs) { success ->
            result = success
            latch.countDown()
        }
        try {
            latch.await(durationMs + timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return result
    }

    fun performPathAsync(points: List<Point2D>, durationMs: Long, onComplete: ((Boolean) -> Unit)?) {
        if (points.isEmpty()) {
            onComplete?.invoke(false)
            return
        }
        val service = serviceProvider() ?: run {
            onComplete?.invoke(false)
            return
        }
        val dm: DisplayMetrics = getRealDisplayMetrics(service)
        val maxW = (dm.widthPixels - 1).toFloat()
        val maxH = (dm.heightPixels - 1).toFloat()

        val path = Path().apply {
            val startX = points[0].x.coerceIn(0f, maxW)
            val startY = points[0].y.coerceIn(0f, maxH)
            moveTo(startX, startY)
            if (points.size == 1) {
                lineTo(startX, startY)
            } else {
                for (i in 1 until points.size) {
                    lineTo(points[i].x.coerceIn(0f, maxW), points[i].y.coerceIn(0f, maxH))
                }
            }
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(50L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val isDone = AtomicBoolean(false)
        val timeoutRunnable = Runnable {
            if (isDone.compareAndSet(false, true)) {
                safeTogglePassthrough(false)
                onComplete?.invoke(false)
            }
        }

        safeTogglePassthrough(true)
        val renderDelay = 45L
        mainHandler.postDelayed(timeoutRunnable, durationMs + 350L + renderDelay)

        mainHandler.postDelayed({
            if (isDone.get()) return@postDelayed
            try {
                val dispatched = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        safeTogglePassthrough(false)
                        if (isDone.compareAndSet(false, true)) onComplete?.invoke(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        safeTogglePassthrough(false)
                        if (isDone.compareAndSet(false, true)) onComplete?.invoke(false)
                    }
                }, null)

                if (!dispatched) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    safeTogglePassthrough(false)
                    if (isDone.compareAndSet(false, true)) onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                AppLogger.logError(service, "GESTURE_CRASH", e)
                mainHandler.removeCallbacks(timeoutRunnable)
                safeTogglePassthrough(false)
                if (isDone.compareAndSet(false, true)) onComplete?.invoke(false)
            }
        }, renderDelay)
    }

    fun performPathSync(points: List<Point2D>, durationMs: Long, timeoutMs: Long): Boolean {
        val latch = CountDownLatch(1)
        var result = false
        performPathAsync(points, durationMs) { success ->
            result = success
            latch.countDown()
        }
        try {
            latch.await(durationMs + timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return result
    }

    fun performPinchAsync(centerX: Float, centerY: Float, startDistance: Float, endDistance: Float, durationMs: Long, onComplete: ((Boolean) -> Unit)?) {
        val service = serviceProvider() ?: run {
            onComplete?.invoke(false)
            return
        }
        val dm: DisplayMetrics = getRealDisplayMetrics(service)
        val maxW = (dm.widthPixels - 1).toFloat()
        val maxH = (dm.heightPixels - 1).toFloat()

        val halfStart = startDistance / 2f
        val halfEnd = endDistance / 2f

        val path1 = Path().apply {
            moveTo((centerX - halfStart).coerceIn(0f, maxW), centerY.coerceIn(0f, maxH))
            lineTo((centerX - halfEnd).coerceIn(0f, maxW), centerY.coerceIn(0f, maxH))
        }

        val path2 = Path().apply {
            moveTo((centerX + halfStart).coerceIn(0f, maxW), centerY.coerceIn(0f, maxH))
            lineTo((centerX + halfEnd).coerceIn(0f, maxW), centerY.coerceIn(0f, maxH))
        }

        val safeDuration = durationMs.coerceIn(100L, 2000L)
        val stroke1 = GestureDescription.StrokeDescription(path1, 0L, safeDuration)
        val stroke2 = GestureDescription.StrokeDescription(path2, 0L, safeDuration)

        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()

        val isDone = AtomicBoolean(false)
        val timeoutRunnable = Runnable {
            if (isDone.compareAndSet(false, true)) {
                safeTogglePassthrough(false)
                onComplete?.invoke(false)
            }
        }

        safeTogglePassthrough(true)
        val renderDelay = 45L
        mainHandler.postDelayed(timeoutRunnable, safeDuration + 300L + renderDelay)

        mainHandler.postDelayed({
            if (isDone.get()) return@postDelayed
            try {
                val dispatched = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        safeTogglePassthrough(false)
                        if (isDone.compareAndSet(false, true)) onComplete?.invoke(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        safeTogglePassthrough(false)
                        if (isDone.compareAndSet(false, true)) onComplete?.invoke(false)
                    }
                }, null)

                if (!dispatched) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    safeTogglePassthrough(false)
                    if (isDone.compareAndSet(false, true)) onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                AppLogger.logError(service, "GESTURE_CRASH", e)
                mainHandler.removeCallbacks(timeoutRunnable)
                safeTogglePassthrough(false)
                if (isDone.compareAndSet(false, true)) onComplete?.invoke(false)
            }
        }, renderDelay)
    }

    fun performPinchSync(centerX: Float, centerY: Float, startDistance: Float, endDistance: Float, durationMs: Long, timeoutMs: Long): Boolean {
        val latch = CountDownLatch(1)
        var result = false
        performPinchAsync(centerX, centerY, startDistance, endDistance, durationMs) { success ->
            result = success
            latch.countDown()
        }
        try {
            latch.await(durationMs + timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return result
    }
}
