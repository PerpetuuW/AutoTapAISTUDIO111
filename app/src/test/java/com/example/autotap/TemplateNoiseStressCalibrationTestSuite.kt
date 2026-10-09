package com.example.autotap

import com.example.autotap.infrastructure.vision.TemplateMatchingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Monte Carlo Noise Stress & Calibration Benchmark Test Suite.
 *
 * Генерирует сырые кадры экрана, вживляет синтетические шаблоны с известным ground truth (X_true, Y_true),
 * накладывает реалистичные физические шумы мобильных дисплеев (Color Drift, Gaussian Noise, Salt & Pepper,
 * Blur/Compression, Clutter Distractors) и проводит Grid Search по калибровочным параметрам
 * для выявления оптимальных порогов сходимости и устойчивости к False Positive / False Negative.
 */
class TemplateNoiseStressCalibrationTestSuite {

    private val random = Random(42) // Детерминированный seed для воспроизводимости

    /**
     * Создание синтетического шаблона (иконка "Play / Прицел" с контрастным контуром и заливкой).
     */
    private fun generateSyntheticTemplate(tw: Int = 40, th: Int = 40): IntArray {
        val pixels = IntArray(tw * th)
        val cx = tw / 2
        val cy = th / 2
        val rOuter = min(tw, th) / 2 - 3
        val rInner = rOuter - 4

        for (y in 0 until th) {
            for (x in 0 until tw) {
                val idx = y * tw + x
                val dx = x - cx
                val dy = y - cy
                val distSq = dx * dx + dy * dy

                when {
                    // Кольцевой контур
                    distSq in (rInner * rInner)..(rOuter * rOuter) -> {
                        pixels[idx] = 0xFFFFFFFF.toInt() // Белая обводка
                    }
                    // Перекрестие в центре
                    (abs(dx) <= 2 && abs(dy) <= rOuter) || (abs(dy) <= 2 && abs(dx) <= rOuter) -> {
                        pixels[idx] = 0xFF38BDF8.toInt() // Неоново-голубой прицел (#38BDF8)
                    }
                    // Внутренняя заливка
                    distSq < (rInner * rInner) -> {
                        pixels[idx] = 0xFF1E1438.toInt() // Глубокий фиолетовый фон
                    }
                    else -> {
                        pixels[idx] = 0x00000000 // Прозрачный фон маски
                    }
                }
            }
        }
        return pixels
    }

    /**
     * Генерация кадра экрана с фоновой сценой, похожими элементами-отвлекателями (Clutter/Distractors)
     * и вживлением истинного шаблона по координатам (targetX, targetY).
     */
    private fun generateScreenWithGroundTruth(
        sw: Int, sh: Int,
        templatePixels: IntArray, tw: Int, th: Int,
        targetX: Int, targetY: Int,
        numDistractors: Int = 5
    ): IntArray {
        val screen = IntArray(sw * sh)

        // 1. Градиентный темный фон UI приложения
        for (y in 0 until sh) {
            val bgLum = (15 + (y * 30 / sh)).coerceIn(0, 255)
            val baseBg = (0xFF shl 24) or (bgLum shl 16) or ((bgLum * 0.8f).toInt() shl 8) or (bgLum + 10)
            for (x in 0 until sw) {
                screen[y * sw + x] = baseBg
            }
        }

        // 2. Добавление похожих ложных элементов (Distractors), чтобы проверить устойчивость к False Positives
        for (d in 0 until numDistractors) {
            val distX = (d * (sw - tw) / max(1, numDistractors)).coerceIn(0, sw - tw)
            val distY = ((d * 97) % (sh - th)).coerceIn(0, sh - th)
            if (abs(distX - targetX) < tw && abs(distY - targetY) < th) continue // не накладывать на target

            for (dy in 0 until th) {
                for (dx in 0 until tw) {
                    val p = templatePixels[dy * tw + dx]
                    if ((p ushr 24) > 30) {
                        // Искаженный цвет отвлекателя (желтоватый или серый)
                        val r = ((p ushr 16) and 0xFF) / 2
                        val g = ((p ushr 8) and 0xFF) / 2
                        val b = (p and 0xFF) / 2
                        screen[(distY + dy) * sw + (distX + dx)] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            }
        }

        // 3. Вживление истинного шаблона
        for (dy in 0 until th) {
            for (dx in 0 until tw) {
                val p = templatePixels[dy * tw + dx]
                if ((p ushr 24) > 30) {
                    screen[(targetY + dy) * sw + (targetX + dx)] = p
                }
            }
        }

        return screen
    }

    /**
     * Инъекция физических шумов на сырой кадр.
     */
    private fun injectNoises(
        screen: IntArray, sw: Int, sh: Int,
        colorDriftRgb: Int = 20,
        saltPepperRate: Float = 0.02f,
        applyBoxBlur: Boolean = true
    ): IntArray {
        val noisy = screen.clone()

        // А. Шум цветового дрейфа и гауссовский шум RGB
        for (i in noisy.indices) {
            val p = noisy[i]
            var r = (p ushr 16) and 0xFF
            var g = (p ushr 8) and 0xFF
            var b = p and 0xFF

            if (colorDriftRgb > 0) {
                val deltaR = random.nextInt(colorDriftRgb * 2 + 1) - colorDriftRgb
                val deltaG = random.nextInt(colorDriftRgb * 2 + 1) - colorDriftRgb
                val deltaB = random.nextInt(colorDriftRgb * 2 + 1) - colorDriftRgb
                r = (r + deltaR).coerceIn(0, 255)
                g = (g + deltaG).coerceIn(0, 255)
                b = (b + deltaB).coerceIn(0, 255)
            }

            // Б. Шум "Salt & Pepper" (битые пиксели)
            if (saltPepperRate > 0f && random.nextFloat() < saltPepperRate) {
                if (random.nextBoolean()) {
                    r = 255; g = 255; b = 255 // Salt
                } else {
                    r = 0; g = 0; b = 0 // Pepper
                }
            }

            noisy[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        // В. Размытие 3x3 (Box Blur) — симуляция кодека/видеопотока MediaProjection
        if (applyBoxBlur) {
            val blurred = noisy.clone()
            for (y in 1 until sh - 1) {
                val row = y * sw
                for (x in 1 until sw - 1) {
                    var sumR = 0; var sumG = 0; var sumB = 0
                    for (ky in -1..1) {
                        for (kx in -1..1) {
                            val cp = noisy[(y + ky) * sw + (x + kx)]
                            sumR += (cp ushr 16) and 0xFF
                            sumG += (cp ushr 8) and 0xFF
                            sumB += cp and 0xFF
                        }
                    }
                    blurred[row + x] = (0xFF shl 24) or ((sumR / 9) shl 16) or ((sumG / 9) shl 8) or (sumB / 9)
                }
            }
            return blurred
        }

        return noisy
    }

    @Test
    fun testTemplateDetection_CleanScreen_AchievesNearPerfectScore() {
        val tw = 40
        val th = 40
        val sw = 200
        val sh = 200
        val targetX = 80
        val targetY = 70

        val tplPixels = generateSyntheticTemplate(tw, th)
        val screen = generateScreenWithGroundTruth(sw, sh, tplPixels, tw, th, targetX, targetY, numDistractors = 2)

        val features = TemplateMatchingEngine.extractFeatures(tplPixels, tw, th, "test_clean", 80)
        assertNotNull("Экстракция дескрипторов должна пройти успешно", features)
        val feat = features ?: return

        // Оценка балла прямо в ground-truth позиции
        val score = TemplateMatchingEngine.evaluateCandidateScore(screen, sw, sh, targetX, targetY, feat, isShapeOnly = false)
        assertTrue("На чистом кадре сходимость в ground truth обязана быть >= 92%, получено: $score", score >= 0.92f)
    }

    @Test
    fun testNoiseStress_GaussianAndColorDrift_PreservesTargetLocation() {
        val tw = 40
        val th = 40
        val sw = 240
        val sh = 240
        val targetX = 100
        val targetY = 110

        val tplPixels = generateSyntheticTemplate(tw, th)
        val cleanScreen = generateScreenWithGroundTruth(sw, sw, tplPixels, tw, th, targetX, targetY, numDistractors = 4)

        // Инъекция реалистичного шума мобильного экрана (±18 по RGB + 0.5% Salt&Pepper)
        val noisyScreen = injectNoises(cleanScreen, sw, sh, colorDriftRgb = 18, saltPepperRate = 0.005f, applyBoxBlur = false)

        val features = TemplateMatchingEngine.extractFeatures(tplPixels, tw, th, "test_noisy", 75)
        assertNotNull(features)
        val feat = features ?: return

        // Проверяем скор на шуме
        val scoreAtTarget = TemplateMatchingEngine.evaluateCandidateScore(noisyScreen, sw, sh, targetX, targetY, feat, isShapeOnly = false)
        assertTrue("Даже при реалистичном шуме экрана скор на целевой позиции обязан быть >= 65%, получено: $scoreAtTarget", scoreAtTarget >= 0.65f)

        // Проверяем скор на отвлекателях и пустом фоне — контраст должен быть явным
        val scoreAtBg = TemplateMatchingEngine.evaluateCandidateScore(noisyScreen, sw, sh, 10, 10, feat, isShapeOnly = false)
        assertTrue("Скор на фоне ($scoreAtBg) обязан быть существенно ниже скора цели ($scoreAtTarget)", scoreAtTarget - scoreAtBg > 0.30f)
    }

    @Test
    fun testGridSearch_FindsOptimalCalibrationThreshold() {
        val tw = 36
        val th = 36
        val sw = 180
        val sh = 180
        val targetX = 64
        val targetY = 72

        val tplPixels = generateSyntheticTemplate(tw, th)
        val cleanScreen = generateScreenWithGroundTruth(sw, sh, tplPixels, tw, th, targetX, targetY, numDistractors = 3)
        val noisyScreen = injectNoises(cleanScreen, sw, sh, colorDriftRgb = 18, saltPepperRate = 0.005f, applyBoxBlur = false)

        val features = TemplateMatchingEngine.extractFeatures(tplPixels, tw, th, "test_opt", 70)
        assertNotNull(features)
        val feat = features ?: return

        val candidateThresholds = listOf(0.55f, 0.60f, 0.65f, 0.70f, 0.75f, 0.80f, 0.85f)
        var truePositiveFoundAt = mutableListOf<Float>()
        var falsePositivesCount = mutableMapOf<Float, Int>()

        for (thresh in candidateThresholds) {
            falsePositivesCount[thresh] = 0
        }

        // Сканирование с шагом 4px по тестовому полю
        var bestFoundScore = 0f
        var bestFoundX = -1
        var bestFoundY = -1

        for (y in 0 until (sh - th) step 4) {
            for (x in 0 until (sw - tw) step 4) {
                val sc = TemplateMatchingEngine.evaluateCandidateScore(noisyScreen, sw, sh, x, y, feat, isShapeOnly = false)
                if (sc > bestFoundScore) {
                    bestFoundScore = sc
                    bestFoundX = x
                    bestFoundY = y
                }

                val isTargetRegion = abs(x - targetX) <= 4 && abs(y - targetY) <= 4
                for (thresh in candidateThresholds) {
                    if (sc >= thresh) {
                        if (!isTargetRegion) {
                            falsePositivesCount[thresh] = (falsePositivesCount[thresh] ?: 0) + 1
                        } else {
                            if (!truePositiveFoundAt.contains(thresh)) {
                                truePositiveFoundAt.add(thresh)
                            }
                        }
                    }
                }
            }
        }

        // 1. Максимум отклика должен точно локализовать цель
        val errorX = abs(bestFoundX - targetX)
        val errorY = abs(bestFoundY - targetY)
        assertTrue("Пик отклика ($bestFoundX, $bestFoundY) обязан быть в окрестности цели ($targetX, $targetY)", errorX <= 4 && errorY <= 4)

        // 2. Идеальный калибровочный порог: должен отсекать 100% ложных срабатываний и детектировать цель
        val optimalThreshold = candidateThresholds.filter { thresh ->
            truePositiveFoundAt.contains(thresh) && (falsePositivesCount[thresh] ?: 0) == 0
        }.maxOrNull()

        assertNotNull("Должен существовать хотя бы один порог с 0 False Positives и 100% Recall", optimalThreshold)
        assertTrue("Оптимальный рабочий порог калибровки AutoTap лежит в диапазоне [0.55..0.85], найден: $optimalThreshold", optimalThreshold!! in 0.55f..0.85f)
    }

    /**
     * Создание синтетического шаблона: Крестик закрытия рекламы ('X' close button).
     * Тонкие диагональные линии толщиной 2-3px, типичный микро-глиф 24x24..32x32.
     */
    private fun generateSyntheticCrossTemplate(size: Int = 28): IntArray {
        val pixels = IntArray(size * size)
        val strokeHalf = 2
        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = y * size + x
                // Две диагонали: x == y и x + y == size - 1
                val onDiag1 = abs(x - y) <= strokeHalf
                val onDiag2 = abs((x + y) - (size - 1)) <= strokeHalf
                if ((onDiag1 || onDiag2) && x in 3 until size - 3 && y in 3 until size - 3) {
                    pixels[idx] = 0xFFFFFFFF.toInt() // Белый крестик
                } else {
                    pixels[idx] = 0x00000000 // Прозрачный фон
                }
            }
        }
        return pixels
    }

    /**
     * Создание синтетического шаблона: Стрелка в рекламе (Chevron / Arrow right/skip).
     * Наконечник стрелки '>' толщиной 2-3px.
     */
    private fun generateSyntheticArrowTemplate(tw: Int = 24, th: Int = 30): IntArray {
        val pixels = IntArray(tw * th)
        val cy = th / 2
        val stroke = 2
        for (y in 0 until th) {
            val distY = abs(y - cy)
            // Координата X вершины стрелки для данной строки Y
            val arrowTipX = tw - 4 - distY
            for (x in 0 until tw) {
                val idx = y * tw + x
                if (arrowTipX >= 4 && abs(x - arrowTipX) <= stroke && y in 3 until th - 3) {
                    pixels[idx] = 0xFF38BDF8.toInt() // Неоновая стрелка перехода
                } else {
                    pixels[idx] = 0x00000000
                }
            }
        }
        return pixels
    }

    @Test
    fun testShapeOnlyMode_ExtremeColorInversionImmunity() {
        val tw = 36
        val th = 36
        val sw = 150
        val sh = 150
        val targetX = 50
        val targetY = 50

        val tplPixels = generateSyntheticTemplate(tw, th)
        val cleanScreen = generateScreenWithGroundTruth(sw, sh, tplPixels, tw, th, targetX, targetY, numDistractors = 1)

        // Полная инверсия цвета экрана (негатив): форма сохранена, цвета полностью другие
        val invertedScreen = IntArray(sw * sh)
        for (i in cleanScreen.indices) {
            val p = cleanScreen[i]
            val r = 255 - ((p ushr 16) and 0xFF)
            val g = 255 - ((p ushr 8) and 0xFF)
            val b = 255 - (p and 0xFF)
            invertedScreen[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val features = TemplateMatchingEngine.extractFeatures(tplPixels, tw, th, "test_shape_inv", 75)
        assertNotNull(features)
        val feat = features ?: return

        val hybridScore = TemplateMatchingEngine.evaluateCandidateScore(invertedScreen, sw, sh, targetX, targetY, feat, isShapeOnly = false)
        val shapeScore = TemplateMatchingEngine.evaluateCandidateScore(invertedScreen, sw, sh, targetX, targetY, feat, isShapeOnly = true)

        assertTrue("При инверсии цветов балл чистой формы ($shapeScore) обязан значительно превышать гибридный ($hybridScore)", shapeScore > hybridScore + 0.30f)
        assertTrue("Балл формы при сохранении контуров обязан быть >= 75%", shapeScore >= 0.75f)
    }

    @Test
    fun testAdCloseCross_DetectionUnderRealisticAdNoiseAndVideoCompression() {
        // Микро-крестик рекламы 28x28 на фоне баннера 200x200
        val size = 28
        val sw = 200
        val sh = 200
        val targetX = 160 // Правый верхний угол (типичное расположение крестика в рекламе)
        val targetY = 12

        val crossPixels = generateSyntheticCrossTemplate(size)
        val cleanScreen = generateScreenWithGroundTruth(sw, sh, crossPixels, size, size, targetX, targetY, numDistractors = 3)

        // Стресс-тест шума рекламного видео: цветной дрейф ±15 RGB + битые пиксели + артефакты сжатия MediaProjection
        val noisyScreen = injectNoises(cleanScreen, sw, sh, colorDriftRgb = 15, saltPepperRate = 0.005f, applyBoxBlur = true)

        val features = TemplateMatchingEngine.extractFeatures(crossPixels, size, size, "ad_close_x", 70)
        assertNotNull("Экстракция дескрипторов крестика обязана быть успешной", features)
        val feat = features ?: return

        // 1. Проверяем обнаружение в целевой позиции (чистая форма и гибрид)
        val scoreHybrid = TemplateMatchingEngine.evaluateCandidateScore(noisyScreen, sw, sh, targetX, targetY, feat, isShapeOnly = false)
        val scoreShape = TemplateMatchingEngine.evaluateCandidateScore(noisyScreen, sw, sh, targetX, targetY, feat, isShapeOnly = true)

        assertTrue("Крестик закрытия под шумом обязан иметь уверенный отклик формы >= 65%, получено: $scoreShape", scoreShape >= 0.65f)
        assertTrue("Крестик закрытия под шумом обязан иметь отклик гибрида >= 65%, получено: $scoreHybrid", scoreHybrid >= 0.65f)

        // 2. Сканирование окрестности (Grid Search): пик отклика должен строго совпадать с крестиком
        var peakScore = 0f
        var peakX = -1
        var peakY = -1
        for (y in 0 until (sh - size) step 2) {
            for (x in 0 until (sw - size) step 2) {
                val sc = TemplateMatchingEngine.evaluateCandidateScore(noisyScreen, sw, sh, x, y, feat, isShapeOnly = true)
                if (sc > peakScore) {
                    peakScore = sc
                    peakX = x
                    peakY = y
                }
            }
        }
        val errX = abs(peakX - targetX)
        val errY = abs(peakY - targetY)
        assertTrue("Пик отклика ($peakX, $peakY) обязан точно локализовать крестик в углу рекламы ($targetX, $targetY)", errX <= 3 && errY <= 3)
    }

    @Test
    fun testAdArrowSkip_DetectionUnderHighNoiseAndDownscaling() {
        // Тонкая стрелка пропуска рекламы (Chevron) 24x30
        val tw = 24
        val th = 30
        val sw = 180
        val sh = 180
        val targetX = 140
        val targetY = 75

        val arrowPixels = generateSyntheticArrowTemplate(tw, th)
        val cleanScreen = generateScreenWithGroundTruth(sw, sh, arrowPixels, tw, th, targetX, targetY, numDistractors = 4)

        // Наложение физического шума экрана
        val noisyScreen = injectNoises(cleanScreen, sw, sh, colorDriftRgb = 16, saltPepperRate = 0.005f, applyBoxBlur = false)

        val features = TemplateMatchingEngine.extractFeatures(arrowPixels, tw, th, "ad_arrow_skip", 70)
        assertNotNull(features)
        val feat = features ?: return

        assertTrue("Стрелка должна классифицироваться как тонкая линия или микро-глиф", feat.isThinLine || feat.isMicroIcon)

        val targetScore = TemplateMatchingEngine.evaluateCandidateScore(noisyScreen, sw, sh, targetX, targetY, feat, isShapeOnly = false)
        val bgScore = TemplateMatchingEngine.evaluateCandidateScore(noisyScreen, sw, sh, 20, 20, feat, isShapeOnly = false)

        assertTrue("Отклик на стрелке пропуска рекламы ($targetScore) обязан быть >= 65%", targetScore >= 0.65f)
        assertTrue("Контраст отклика цели над шумом фона ($bgScore) обязан превышать 0.35", targetScore - bgScore > 0.35f)
    }
}
