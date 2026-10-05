package com.example.autotap

import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.Point2D
import com.example.autotap.infrastructure.vision.TemplateMatchingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CascadeAndAnchorVerificationTestSuite {

    @Test
    fun testTier0_ProbesAnchorAndCreationLocationFirst() {
        val tw = 32
        val th = 32
        val tPixels = IntArray(tw * th) { 0 }

        // Рисуем белый крестик внутри шаблона
        for (y in 0 until th) {
            for (x in 0 until tw) {
                if (x in 14..17 || y in 14..17) {
                    tPixels[y * tw + x] = 0xFFFFFFFF.toInt()
                }
            }
        }

        val sw = 200
        val sh = 200
        val sPixels = IntArray(sw * sh) { 0xFF10141E.toInt() }

        // Помещаем крестик на экране строго в месте создания (posX=50, posY=50)
        val targetLeft = 50 - 16
        val targetTop = 50 - 16
        for (y in 0 until th) {
            for (x in 0 until tw) {
                if (x in 14..17 || y in 14..17) {
                    sPixels[(targetTop + y) * sw + (targetLeft + x)] = 0xFFFFFFFF.toInt()
                }
            }
        }

        val action = MacroAction(
            id = 1,
            type = ActionType.TRIGGER,
            posX = 50f,
            posY = 50f,
            similarityPercent = 85
        )

        val features = TemplateMatchingEngine.extractFeatures(tPixels, tw, th, "test_tier0", 85)
        assertNotNull("Дескрипторы шаблона обязаны быть успешно извлечены", features)

        val safeFeatures = requireNotNull(features) { "Features cannot be null" }
        val scoreAtAnchor = TemplateMatchingEngine.evaluateCandidateScore(sPixels, sw, sh, targetLeft, targetTop, safeFeatures, false)
        assertTrue("Скор в месте создания обязан превышать 90%, получено: $scoreAtAnchor", scoreAtAnchor >= 0.90f)
    }

    @Test
    fun testBackgroundVariation_PreservesHighMatchingScoreOnDynamicBackground() {
        val tw = 32
        val th = 32
        val tPixels = IntArray(tw * th) { 0 } // Прозрачный фон вокруг иконки

        // Рисуем белую стрелку на прозрачном фоне
        for (y in 0 until th) {
            for (x in 0 until tw) {
                if (x in 10..22 && y in 10..22 && x >= y) {
                    tPixels[y * tw + x] = 0xFFFFFFFF.toInt()
                }
            }
        }

        val sw = 100
        val sh = 100
        // Экран 1: темный фон
        val sPixelsDark = IntArray(sw * sh) { 0xFF05070A.toInt() }
        // Экран 2: яркий цветной динамический фон (зеленый/красный)
        val sPixelsVibrant = IntArray(sw * sh) { 0xFF008833.toInt() }

        for (y in 0 until th) {
            for (x in 0 until tw) {
                if (x in 10..22 && y in 10..22 && x >= y) {
                    sPixelsDark[(20 + y) * sw + (20 + x)] = 0xFFFFFFFF.toInt()
                    sPixelsVibrant[(20 + y) * sw + (20 + x)] = 0xFFFFFFFF.toInt()
                }
            }
        }

        val features = TemplateMatchingEngine.extractFeatures(tPixels, tw, th, "test_bg_vary", 80)
        assertNotNull(features)

        val safeFeatures = requireNotNull(features) { "Features cannot be null" }
        val scoreDark = TemplateMatchingEngine.evaluateCandidateScore(sPixelsDark, sw, sh, 20, 20, safeFeatures, false)
        val scoreVibrant = TemplateMatchingEngine.evaluateCandidateScore(sPixelsVibrant, sw, sh, 20, 20, safeFeatures, false)

        assertTrue("На темном фоне скор обязан быть >= 80%, получено: $scoreDark", scoreDark >= 0.80f)
        assertTrue("При полной смене окружающего фона скор обязан сохраняться >= 80%, получено: $scoreVibrant", scoreVibrant >= 0.80f)
    }
}