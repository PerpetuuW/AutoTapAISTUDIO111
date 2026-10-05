package com.example.autotap.infrastructure.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

/**
 * Препроцессор изображений для конвейера OCR (Game & UI OCR).
 *
 * Применяет:
 *  1. Высокоскоростное преобразование в оттенки серого (Grayscale Conversion BT.601).
 *  2. Адаптивную бинаризацию (Adaptive Thresholding с Integral Image) для компенсации
 *     бликов, градиентов и неравномерной подсветки игровых кнопок.
 *  3. Морфологическое размыкание (Morphological Opening = Erosion -> Dilation)
 *     для устранения мелких изолированных шумов, бликов и артефактов сжатия.
 */
class ImagePreprocessing {

    /**
     * Выполняет полный цикл предобработки изображения перед передачей в OCR-движок.
     */
    fun process(
        bitmap: Bitmap,
        windowSize: Int = 15,
        cValue: Int = 8,
        enableAdaptiveThreshold: Boolean = true,
        enableMorphology: Boolean = true
    ): Bitmap {
        if (bitmap.isRecycled || bitmap.width < 2 || bitmap.height < 2) {
            return bitmap
        }

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 1. Извлечение градаций серого (Grayscale)
        val gray = toGrayscale(pixels)

        // 2. Адаптивная бинаризация (Integral Image Adaptive Thresholding)
        val binarized = if (enableAdaptiveThreshold) {
            adaptiveThreshold(gray, w, h, windowSize, cValue)
        } else {
            gray
        }

        // 3. Морфологическое размыкание (Erosion -> Dilation)
        val cleaned = if (enableMorphology) {
            morphologicalOpening(binarized, w, h, kernelSize = 3)
        } else {
            binarized
        }

        // 4. Восстановление ARGB_8888 Bitmap
        val resultBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(w * h)
        for (i in cleaned.indices) {
            val v = cleaned[i].coerceIn(0, 255)
            outPixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        resultBitmap.setPixels(outPixels, 0, w, 0, 0, w, h)
        return resultBitmap
    }

    /**
     * Конвертация ARGB пикселей в полутоновое значение (0..255) по BT.601.
     */
    fun toGrayscale(pixels: IntArray): IntArray {
        val gray = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        return gray
    }

    /**
     * Адаптивная бинаризация через интегральное изображение (Summed-Area Table).
     * Вычисляется за O(1) на пиксель, эффективно устраняя локальные блики UI.
     */
    fun adaptiveThreshold(
        gray: IntArray,
        w: Int,
        h: Int,
        windowSize: Int = 15,
        cValue: Int = 8
    ): IntArray {
        val result = IntArray(w * h)
        val r = windowSize / 2

        // Построение интегрального изображения O(W*H)
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            val rowOff = y * w
            val intRowOff = (y + 1) * (w + 1)
            val prevIntRowOff = y * (w + 1)

            for (x in 0 until w) {
                rowSum += gray[rowOff + x]
                integral[intRowOff + x + 1] = integral[prevIntRowOff + x + 1] + rowSum
            }
        }

        // Адаптивное пороговое вычисление для каждого пикселя
        for (y in 0 until h) {
            val y1 = max(0, y - r)
            val y2 = min(h - 1, y + r)
            val rowOff = y * w

            for (x in 0 until w) {
                val x1 = max(0, x - r)
                val x2 = min(w - 1, x + r)

                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                val sum = integral[(y2 + 1) * (w + 1) + (x2 + 1)] -
                          integral[y1 * (w + 1) + (x2 + 1)] -
                          integral[(y2 + 1) * (w + 1) + x1] +
                          integral[y1 * (w + 1) + x1]

                val mean = (sum / count).toInt()
                val pixel = gray[rowOff + x]

                // Если пиксель светлее локального среднего за вычетом C — относим к переднему плану (текст)
                result[rowOff + x] = if (pixel >= mean - cValue) 255 else 0
            }
        }
        return result
    }

    /**
     * Морфологическое размыкание (Opening): Эрозия -> Дилатация.
     * Удаляет изолированные шумовые пиксели и мелкие бликовые точки.
     */
    fun morphologicalOpening(
        binaryPixels: IntArray,
        w: Int,
        h: Int,
        kernelSize: Int = 3
    ): IntArray {
        val eroded = erode(binaryPixels, w, h, kernelSize)
        return dilate(eroded, w, h, kernelSize)
    }

    /**
     * Операция морфологической эрозии (Erosion).
     */
    fun erode(src: IntArray, w: Int, h: Int, kernelSize: Int = 3): IntArray {
        val dst = IntArray(w * h)
        val r = kernelSize / 2

        for (y in 0 until h) {
            val yMin = max(0, y - r)
            val yMax = min(h - 1, y + r)
            val rowOff = y * w

            for (x in 0 until w) {
                val xMin = max(0, x - r)
                val xMax = min(w - 1, x + r)

                var minVal = 255
                for (ky in yMin..yMax) {
                    val kRowOff = ky * w
                    for (kx in xMin..xMax) {
                        val v = src[kRowOff + kx]
                        if (v < minVal) {
                            minVal = v
                            if (minVal == 0) break
                        }
                    }
                    if (minVal == 0) break
                }
                dst[rowOff + x] = minVal
            }
        }
        return dst
    }

    /**
     * Операция морфологической дилатации (Dilation).
     */
    fun dilate(src: IntArray, w: Int, h: Int, kernelSize: Int = 3): IntArray {
        val dst = IntArray(w * h)
        val r = kernelSize / 2

        for (y in 0 until h) {
            val yMin = max(0, y - r)
            val yMax = min(h - 1, y + r)
            val rowOff = y * w

            for (x in 0 until w) {
                val xMin = max(0, x - r)
                val xMax = min(w - 1, x + r)

                var maxVal = 0
                for (ky in yMin..yMax) {
                    val kRowOff = ky * w
                    for (kx in xMin..xMax) {
                        val v = src[kRowOff + kx]
                        if (v > maxVal) {
                            maxVal = v
                            if (maxVal == 255) break
                        }
                    }
                    if (maxVal == 255) break
                }
                dst[rowOff + x] = maxVal
            }
        }
        return dst
    }

    companion object {
        @JvmStatic
        val instance: ImagePreprocessing by lazy { ImagePreprocessing() }
    }
}
