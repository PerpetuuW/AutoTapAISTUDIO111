package com.example.autotap

import com.example.autotap.infrastructure.ocr.ImagePreprocessing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePreprocessingTest {

    private val preprocessor = ImagePreprocessing()

    @Test
    fun testToGrayscale_BT601Coefficients() {
        val red = 0xFFFF0000.toInt()
        val green = 0xFF00FF00.toInt()
        val blue = 0xFF0000FF.toInt()

        val pixels = intArrayOf(red, green, blue)
        val gray = preprocessor.toGrayscale(pixels)

        // BT.601: R*0.299, G*0.587, B*0.114
        assertEquals(76, gray[0])  // 255 * 0.299 = 76.245
        assertEquals(149, gray[1]) // 255 * 0.587 = 149.685
        assertEquals(29, gray[2])  // 255 * 0.114 = 29.07
    }

    @Test
    fun testAdaptiveThreshold_HandlesLocalGlare() {
        val w = 10
        val h = 10
        val gray = IntArray(w * h) { 100 } // Базовый серый фон

        // Задаем область текста (светлые пиксели) и сильный блик в углу
        gray[5 * w + 5] = 200 // Текст
        gray[0 * w + 0] = 255 // Блик

        val binarized = preprocessor.adaptiveThreshold(gray, w, h, windowSize = 5, cValue = 5)

        assertEquals(255, binarized[5 * w + 5]) // Текст локально выделяется
        assertTrue("Пиксель текста адаптирован под контекст", binarized[5 * w + 5] == 255)
    }

    @Test
    fun testMorphologicalOpening_RemovesIsolatedNoiseSpec() {
        val w = 5
        val h = 5
        val binary = IntArray(w * h) { 0 }

        // Изолированная шумящая точка
        binary[2 * w + 2] = 255

        val opened = preprocessor.morphologicalOpening(binary, w, h, kernelSize = 3)

        // Одиночный пиксель шума удаляется эрозией
        assertEquals(0, opened[2 * w + 2])
    }

    @Test
    fun testMorphologicalOpening_PreservesSolidBlock() {
        val w = 6
        val h = 6
        val binary = IntArray(w * h) { 0 }

        // Сплошной блок 3x3 (имитация фрагмента символа)
        for (y in 1..3) {
            for (x in 1..3) {
                binary[y * w + x] = 255
            }
        }

        val opened = preprocessor.morphologicalOpening(binary, w, h, kernelSize = 3)

        // Центр блока сохраняется после открытия
        assertEquals(255, opened[2 * w + 2])
    }

    @Test
    fun testErodeAndDilateSymmetry() {
        val w = 5
        val h = 5
        val src = IntArray(w * h) { 255 }

        val eroded = preprocessor.erode(src, w, h, kernelSize = 3)
        val dilated = preprocessor.dilate(eroded, w, h, kernelSize = 3)

        assertNotNull(eroded)
        assertNotNull(dilated)
        assertEquals(255, dilated[2 * w + 2])
    }
}

