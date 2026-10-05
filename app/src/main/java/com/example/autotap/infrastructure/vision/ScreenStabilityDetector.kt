package com.example.autotap.infrastructure.vision

import android.graphics.Bitmap

object ScreenStabilityDetector {

    fun computeFastHash(pixels: IntArray, width: Int, height: Int): Long {
        if (pixels.isEmpty() || width <= 0 || height <= 0) return 0L
        var hash = 1125899906842597L
        val stepX = (width / 16).coerceAtLeast(1)
        val stepY = (height / 16).coerceAtLeast(1)

        for (y in 0 until height step stepY) {
            val rowOffset = y * width
            for (x in 0 until width step stepX) {
                val idx = rowOffset + x
                if (idx < pixels.size) {
                    val color = pixels[idx]
                    val lum = (((color ushr 16) and 0xFF) * 299 + ((color ushr 8) and 0xFF) * 587 + (color and 0xFF) * 114) / 1000
                    hash = 31L * hash + lum
                }
            }
        }
        return hash
    }

    fun computeFastHash(bitmap: Bitmap): Long {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return 0L
        val w = bitmap.width
        val h = bitmap.height
        val pixels = PixelBufferPool.obtain(w * h)
        try {
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            return computeFastHash(pixels, w, h)
        } catch (_: Exception) {
            return 0L
        } finally {
            PixelBufferPool.release(pixels)
        }
    }
}
