package com.example.autotap.infrastructure.vision

import java.util.concurrent.ConcurrentLinkedQueue

object PixelBufferPool {
    private val pool = ConcurrentLinkedQueue<IntArray>()
    private const val MAX_BUFFERS = 8 // ИНВАРИАНТ 9: Пул увеличен до 8 для стабильности на Android 14+
    private const val MIN_RETAIN_SIZE = 64 * 1024 // 64K элементов (256 КБ)

    /**
     * Возвращает переиспользуемый буфер пикселей из пула.
     * Предотвращает фрагментацию хипа и вызовы сборщика мусора при 24/7 фарме.
     */
    fun obtain(minSize: Int): IntArray {
        var buffer = pool.poll()
        if (buffer == null || buffer.size < minSize) {
            buffer = IntArray(minSize)
        }
        return buffer
    }

    fun release(buffer: IntArray?) {
        if (buffer == null) return
        if (buffer.size >= MIN_RETAIN_SIZE && pool.size < MAX_BUFFERS) {
            pool.offer(buffer)
        }
    }

    fun clear() {
        pool.clear()
    }
}
