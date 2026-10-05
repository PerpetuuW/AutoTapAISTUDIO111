package com.example.autotap

import com.example.autotap.infrastructure.vision.TemplateMatchingEngine
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeDifferentiationTestSuite {

    @Test
    fun testShapeOnlyMode_DifferentiatesFromHybridScore() {
        val tw = 32
        val th = 32
        val tplPixels = IntArray(tw * th)

        for (y in 0 until th) {
            for (x in 0 until tw) {
                val idx = y * tw + x
                if (x in 8..24 && y in 8..24 && (x <= y && (th - y) >= (x - 8))) {
                    tplPixels[idx] = 0xFFFFFFFF.toInt()
                } else {
                    tplPixels[idx] = 0xFF000000.toInt()
                }
            }
        }

        val features = TemplateMatchingEngine.extractFeatures(tplPixels, tw, th, "diff_pure_test", 80)
        assertNotNull("Дескрипторы формы обязаны быть успешно извлечены", features)
        val feat = features ?: return

        val sw = 64
        val sh = 64
        val screenPixels = IntArray(sw * sh)

        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val tx = x - 16
                val ty = y - 16
                if (tx in 8..24 && ty in 8..24 && (tx <= ty && (th - ty) >= (tx - 8))) {
                    screenPixels[y * sw + x] = 0xFFFFFFFF.toInt()
                } else {
                    screenPixels[y * sw + x] = 0xFFFF0000.toInt()
                }
            }
        }

        val hybridScore = TemplateMatchingEngine.evaluateCandidateScore(screenPixels, sw, sh, 16, 16, feat, isShapeOnly = false)
        val shapeScore = TemplateMatchingEngine.evaluateCandidateScore(screenPixels, sw, sh, 16, 16, feat, isShapeOnly = true)

        assertTrue("Балл формы ($shapeScore) обязан быть выше гибридного ($hybridScore) при изменении фона", shapeScore > hybridScore)
        assertTrue("Балл формы обязан превышать 80% на идентичном контуре", shapeScore >= 0.80f)
    }
}
