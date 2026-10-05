package com.example.autotap.infrastructure.projection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.autotap.R
import com.example.autotap.core.logger.AppLogger
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

class MediaProjectionService : Service() {

    companion object {
        const val ACTION_START = "com.example.autotap.START_PROJECTION"
        const val ACTION_STOP = "com.example.autotap.STOP_PROJECTION"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val NOTIFICATION_ID = 9901
        private const val CHANNEL_ID = "autotap_screen_stream_channel"

        @Volatile
        var isStreaming: Boolean = false
            private set

        private val frameLock = ReentrantLock()
        private var latestBuffer: IntArray? = null
        private var frameWidth = 0
        private var frameHeight = 0

        fun copyLatestFrame(outPixels: IntArray, width: Int, height: Int): Boolean {
            if (!isStreaming) return false
            frameLock.withLock {
                val buf = latestBuffer ?: return false
                if (frameWidth != width || frameHeight != height || buf.size < width * height) return false
                System.arraycopy(buf, 0, outPixels, 0, width * height)
                return true
            }
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var cachedRowBytes: ByteArray? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val thread = HandlerThread("AutoTapStreamThread").apply { start() }
        captureThread = thread
        captureHandler = Handler(thread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (resultCode != 0 && resultData != null) {
                    startStreaming(resultCode, resultData)
                }
            }
            ACTION_STOP -> {
                stopStreaming()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startStreaming(resultCode: Int, resultData: Intent) {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                } else 0
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopStreaming()
                    }
                }, captureHandler)
            }

            setupVirtualDisplay()
            isStreaming = true
            AppLogger.log(this, "PROJECTION", "60 FPS захват экрана (MediaProjection) успешно активирован")
        } catch (e: Exception) {
            AppLogger.logError(this, "PROJECTION", e)
            stopStreaming()
        }
    }


    private fun setupVirtualDisplay() {
    val wm = getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
    val realMetrics = android.util.DisplayMetrics()
    @Suppress("DEPRECATION")
    wm?.defaultDisplay?.getRealMetrics(realMetrics)
    val width = if (realMetrics.widthPixels > 0) realMetrics.widthPixels else resources.displayMetrics.widthPixels
    val height = if (realMetrics.heightPixels > 0) realMetrics.heightPixels else resources.displayMetrics.heightPixels

    val dpi = if (realMetrics.densityDpi > 0) realMetrics.densityDpi else resources.displayMetrics.densityDpi

    imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    imageReader?.setOnImageAvailableListener({ reader ->
            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    val plane = image.planes[0]
                    val buffer: ByteBuffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride

                    frameLock.withLock {
                        if (latestBuffer == null || latestBuffer?.size != width * height) {
                            latestBuffer = IntArray(width * height)
                        }
                        if (cachedRowBytes == null || cachedRowBytes?.size != rowStride) {
                            cachedRowBytes = ByteArray(rowStride)
                        }

                        val dest = latestBuffer ?: return@withLock
                        val rowBuf = cachedRowBytes ?: return@withLock
                        frameWidth = width
                        frameHeight = height

                        var offset = 0
                        var rowOffset = 0
                        for (y in 0 until height) {
                            buffer.position(rowOffset)
                            val bytesToRead = min(rowStride, buffer.remaining())
                            buffer.get(rowBuf, 0, bytesToRead)

                            var byteIdx = 0
                            for (x in 0 until width) {
                                val r = rowBuf[byteIdx].toInt() and 0xFF
                                val g = rowBuf[byteIdx + 1].toInt() and 0xFF
                                val b = rowBuf[byteIdx + 2].toInt() and 0xFF
                                val a = if (pixelStride >= 4) rowBuf[byteIdx + 3].toInt() and 0xFF else 0xFF
                                dest[offset++] = (a shl 24) or (r shl 16) or (g shl 8) or b
                                byteIdx += pixelStride
                            }
                            rowOffset += rowStride
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    // ИНВАРИАНТ 4: Явное освобождение HardwareBuffer для MediaTek / Android 14+
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        image?.hardwareBuffer?.close()
                    }
                } catch (_: Throwable) {}
                try {
                    image?.close()
                } catch (_: Throwable) {}
            }
        }, captureHandler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AutoTapVirtualDisplay",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, captureHandler
        )
    }

    private fun stopStreaming() {
        isStreaming = false
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        AppLogger.log(this, "PROJECTION", "60 FPS захват экрана остановлен")
    }

    override fun onDestroy() {
        stopStreaming()
        captureThread?.quitSafely()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoTap 60 FPS Screen Stream",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Используется для прямого низколатентного захвата экрана"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoTap Ultra 60 FPS")
            .setContentText("Прямой аппаратный захват экрана активен")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
