📄 FILE: app/src/main/java/com/example/autotap/infrastructure/vision/PixelBufferPool.kt
<<<< SEARCH
object PixelBufferPool {
    private val pool = ConcurrentLinkedQueue<IntArray>()
    private const val MAX_BUFFERS = 4
    private const val MIN_RETAIN_SIZE = 64 * 1024 // 64K элементов (256 КБ)
====
object PixelBufferPool {
    private val pool = ConcurrentLinkedQueue<IntArray>()
    private const val MAX_BUFFERS = 8 // ИНВАРИАНТ 9: Пул увеличен до 8 для стабильности на Android 14+
    private const val MIN_RETAIN_SIZE = 64 * 1024 // 64K элементов (256 КБ)
>>>> REPLACE

📄 FILE: app/src/main/java/com/example/autotap/infrastructure/projection/MediaProjectionService.kt
<<<< SEARCH
            } catch (_: Exception) {
            } finally {
                image?.close()
            }
        }, captureHandler)
====
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
>>>> REPLACE

📄 FILE: app/src/main/java/com/example/autotap/infrastructure/ocr/OcrEngine.kt
<<<< SEARCH
    fun findTextOnScreen(
        bitmap: Bitmap,
        targetQuery: String,
        timeoutMs: Long = 2000L,
        roi: Rect? = null,
        outVariables: MutableMap<String, String>? = null
    ): List<OcrMatchResult> {
        val perfStart = System.currentTimeMillis()
        if (bitmap.isRecycled) return emptyList()
====
    private fun isValidForOcr(bmp: Bitmap?): Boolean {
        if (bmp == null || bmp.isRecycled) return false
        // ИНВАРИАНТ 7: Защита от FLAG_SECURE (скриншот может быть черным или 1x1)
        return bmp.width > 1 && bmp.height > 1
    }

    fun findTextOnScreen(
        bitmap: Bitmap,
        targetQuery: String,
        timeoutMs: Long = 2000L,
        roi: Rect? = null,
        outVariables: MutableMap<String, String>? = null
    ): List<OcrMatchResult> {
        val perfStart = System.currentTimeMillis()
        if (!isValidForOcr(bitmap)) {
            AppLogger.log(null, "OCR", "Скриншот пуст или FLAG_SECURE — пропускаем OCR")
            return emptyList()
        }
>>>> REPLACE

📄 FILE: app/src/main/java/com/example/autotap/infrastructure/ocr/OcrEngine.kt
<<<< SEARCH
    fun findAndExtractRegex(bitmap: Bitmap, regexPattern: String, timeoutMs: Long = 2000L, roi: Rect? = null): String? {
        if (regexPattern.isBlank() || bitmap.isRecycled) return null
====
    fun findAndExtractRegex(bitmap: Bitmap, regexPattern: String, timeoutMs: Long = 2000L, roi: Rect? = null): String? {
        if (regexPattern.isBlank() || !isValidForOcr(bitmap)) {
            AppLogger.log(null, "OCR", "Скриншот пуст или FLAG_SECURE — пропускаем Regex OCR")
            return null
        }
>>>> REPLACE

📄 FILE: app/src/main/java/com/example/autotap/infrastructure/overlay/OverlayWindowManager.kt
<<<< SEARCH
    @Suppress("DEPRECATION")
    fun createDialogLayoutParams(
        width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        gravity: Int = Gravity.CENTER
    ): WindowManager.LayoutParams {
        val target = getActiveWindowManager(requiresKeyboard = true)
        val windowType = target?.second ?: WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        // Для TYPE_ACCESSIBILITY_OVERLAY обязателен FLAG_NOT_FOCUSABLE, иначе Android AOSP блокирует тач-события кнопок!
        val flags = if (windowType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        return WindowManager.LayoutParams(width, height, windowType, flags, PixelFormat.TRANSLUCENT).apply {
====
    @Suppress("DEPRECATION")
    fun createDialogLayoutParams(
        width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        gravity: Int = Gravity.CENTER
    ): WindowManager.LayoutParams {
        // ИНВАРИАНТ 1: Модальные окна/Диалоги строго TYPE_APPLICATION_OVERLAY.
        // Ввод текста и клики в TYPE_ACCESSIBILITY_OVERLAY заблокированы на новых версиях Android.
        val windowType = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        return WindowManager.LayoutParams(width, height, windowType, flags, android.graphics.PixelFormat.TRANSLUCENT).apply {
>>>> REPLACE

📄 FILE: app/src/main/java/com/example/autotap/infrastructure/gesture/HumanGestureEngine.kt
<<<< SEARCH
            val jitterX = if (i < steps) (Random.nextFloat() - 0.5f) * 1.5f else 0f
            val jitterY = if (i < steps) (Random.nextFloat() - 0.5f) * 1.5f else 0f

            val x = (1 - easedT).pow(3) * safeStartX +
====
            // ИНВАРИАНТ 8: Гауссовский джиттер (Box-Muller) для защиты от античитов
            val u1 = Random.nextDouble().coerceAtLeast(1e-6)
            val u2 = Random.nextDouble().coerceAtLeast(1e-6)
            val mag = kotlin.math.sqrt(-2.0 * kotlin.math.log(u1)) * 1.5
            val angle = 2.0 * Math.PI * u2
            
            val jitterX = if (i < steps) (mag * kotlin.math.cos(angle)).toFloat() else 0f
            val jitterY = if (i < steps) (mag * kotlin.math.sin(angle)).toFloat() else 0f

            val x = (1 - easedT).pow(3) * safeStartX +
>>>> REPLACE