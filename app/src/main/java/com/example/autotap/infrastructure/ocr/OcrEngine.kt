package com.example.autotap.infrastructure.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.domain.model.OcrMatchResult
import java.nio.FloatBuffer
import java.util.Collections
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Высокоточный промышленный OCR-движок для интерфейсов и мобильных игр на базе Pure ONNX.
 *
 * Архитектура:
 *  1. Deep Neural Text Detector (PaddleOCR v4 DBNet ONNX `ch_PP-OCRv4_det.onnx`):
 *     Сегментация и точная локализация строк текста без ложных срабатываний на текстурах/игровом фоне.
 *  2. Game-Font Preprocessor:
 *     Адаптивная нормализация контраста, удаление артефактов обводки и безопасный паддинг глифов.
 *  3. Pure ONNX PP-OCRv4 SVTR Recognition (`cyrillic_rec.onnx` + `cyrillic_dict.txt`):
 *     Инференс кириллицы (RU) и латиницы (EN) в RGB NCHW с CTC декодером.
 *  4. Intelligent Homoglyph & Fuzzy Matcher:
 *     Устойчивость к стилизованным игровым шрифтам, переносам строк и опечаткам.
 */
object OcrEngine {
    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    @Volatile
    var explicitContext: Context? = null

    private val sessionLock = Any()
    @Volatile
    private var cachedRecSession: OrtSession? = null
    @Volatile
    private var cachedDetSession: OrtSession? = null
    @Volatile
    private var cachedDictionary: List<String>? = null

    val imagePreprocessor: ImagePreprocessing by lazy { ImagePreprocessing.instance }

    private fun getContext(): Context? {
        return explicitContext
            ?: AppLogger.appContext
            ?: com.example.autotap.infrastructure.accessibility.AutoTapAccessibilityService.instance
    }

    private fun getDetSession(): OrtSession? {
        cachedDetSession?.let { return it }
        synchronized(sessionLock) {
            cachedDetSession?.let { return it }
            val ctx = getContext() ?: return null
            return try {
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                }
                val modelBytes = ctx.assets.open("models/ch_PP-OCRv4_det.onnx").use { it.readBytes() }
                val session = ortEnv.createSession(modelBytes, opts)
                cachedDetSession = session
                AppLogger.log(null, "ONNX_DET", "Нейросетевой DBNet детектор текста инициализирован")
                session
            } catch (e: Throwable) {
                AppLogger.logError(null, "ONNX_DET_INIT", e)
                null
            }
        }
    }

    private fun getRecSession(): OrtSession? {
        cachedRecSession?.let { return it }
        synchronized(sessionLock) {
            cachedRecSession?.let { return it }
            val ctx = getContext() ?: return null
            return try {
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                }
                val modelBytes = ctx.assets.open("models/cyrillic_rec.onnx").use { it.readBytes() }
                val session = ortEnv.createSession(modelBytes, opts)
                cachedRecSession = session
                session
            } catch (e: Throwable) {
                AppLogger.logError(null, "ONNX_REC_INIT", e)
                null
            }
        }
    }

    private fun getDictionary(): List<String> {
        cachedDictionary?.let { return it }
        synchronized(sessionLock) {
            cachedDictionary?.let { return it }
            val ctx = getContext() ?: return emptyList()
            return try {
                val list = mutableListOf<String>()
                list.add("blank") // CTC blank символ на позиции 0
                ctx.assets.open("models/cyrillic_dict.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        list.add(line.replace("\r", "").replace("\n", ""))
                    }
                }
                if (!list.contains(" ")) {
                    list.add(" ")
                }
                cachedDictionary = list
                list
            } catch (e: Throwable) {
                AppLogger.logError(null, "OCR_DICT_INIT", e)
                emptyList()
            }
        }
    }

    private val KEY_VALUE_REGEX = Regex("""(?i)([a-zа-яё0-9_-]+)[:\s=]+([\d.,]+[kmbKMBкКмМбБ]?)""")

    private fun isValidForOcr(bmp: Bitmap?): Boolean {
        if (bmp == null || bmp.isRecycled) return false
        return bmp.width > 1 && bmp.height > 1
    }

    /**
     * Объединение близлежащих боксов слов на одной горизонтальной строке для распознавания составных фраз.
     */
    private fun mergeAdjacentLineBoxes(boxes: List<Rect>): List<Rect> {
        if (boxes.size <= 1) return boxes
        val sorted = boxes.sortedWith(compareBy({ it.top / 12 }, { it.left }))
        val merged = mutableListOf<Rect>()

        var current = Rect(sorted[0])
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            val vOverlapMin = maxOf(current.top, next.top)
            val vOverlapMax = minOf(current.bottom, next.bottom)
            val vOverlap = vOverlapMax - vOverlapMin
            val minH = minOf(current.height(), next.height()).coerceAtLeast(1)

            val isSameLine = vOverlap >= minH * 0.45f
            val hGap = next.left - current.right

            if (isSameLine && hGap in -10..32) {
                // Объединяем близкие боксы в единую текстовую строку
                current.left = minOf(current.left, next.left)
                current.top = minOf(current.top, next.top)
                current.right = maxOf(current.right, next.right)
                current.bottom = maxOf(current.bottom, next.bottom)
            } else {
                merged.add(Rect(current))
                current = Rect(next)
            }
        }
        merged.add(Rect(current))
        return merged
    }

    /**
     * Предобработка игрового шрифта:
     * Добавление безопасного паддинга, апскейлинг мелких шрифтов, динамическое растяжение контраста и инверсия фона.
     */
    private fun enhanceGameFontCrop(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return bitmap

        // Масштабирование микро-шрифтов HUD (8..20px) для высокой точности распознавания
        val targetSrc = if (h in 8..22) {
            Bitmap.createScaledBitmap(bitmap, w * 2, h * 2, true)
        } else {
            bitmap
        }

        val sw = targetSrc.width
        val sh = targetSrc.height

        // 1. Добавление защитного паддинга (6px по краям), чтобы крайние штрихи букв и мягкие знаки не срезались
        val padX = 6
        val padY = 3
        val padded = Bitmap.createBitmap(sw + padX * 2, sh + padY * 2, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(padded)
        canvas.drawBitmap(targetSrc, padX.toFloat(), padY.toFloat(), null)

        val pw = padded.width
        val ph = padded.height
        val pixels = IntArray(pw * ph)
        padded.getPixels(pixels, 0, pw, 0, 0, pw, ph)

        var minLum = 255
        var maxLum = 0
        for (i in pixels.indices) {
            val p = pixels[i]
            val lum = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
        }

        if (maxLum - minLum in 15..250) {
            val range = max(1, maxLum - minLum)
            for (i in pixels.indices) {
                val p = pixels[i]
                val a = (p shr 24) and 0xFF
                val r = ((((p shr 16) and 0xFF) - minLum) * 255 / range).coerceIn(0, 255)
                val g = ((((p shr 8) and 0xFF) - minLum) * 255 / range).coerceIn(0, 255)
                val b = (((p and 0xFF) - minLum) * 255 / range).coerceIn(0, 255)
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            padded.setPixels(pixels, 0, pw, 0, 0, pw, ph)
        }
        if (targetSrc != bitmap && !targetSrc.isRecycled) targetSrc.recycle()
        return padded
    }

    /**
     * Инференс нейросетевого детектора текста DBNet (Pure ONNX `ch_PP-OCRv4_det.onnx`).
     * Выделяет точные боксы строк текста и отфильтровывает фон и текстуры.
     */
    fun detectTextBoundingBoxesWithDbNet(bmp: Bitmap): List<Rect> {
        val session = getDetSession() ?: return fallbackTextBoundingBoxes(bmp)
        val origW = bmp.width
        val origH = bmp.height
        if (origW < 8 || origH < 8) return listOf(Rect(0, 0, origW, origH))

        // Высокое разрешение для полноэкранного детектирования мелких шрифтов HUD/иконок (до 1280px)
        val maxSide = maxOf(960, minOf(1280, max(origW, origH)))
        val ratio = maxSide.toFloat() / max(origW, origH).toFloat()
        var targetW = (origW * ratio).toInt()
        var targetH = (origH * ratio).toInt()
        targetW = maxOf(32, ((targetW + 31) / 32) * 32)
        targetH = maxOf(32, ((targetH + 31) / 32) * 32)

        val scaleW = targetW.toFloat() / origW.toFloat()
        val scaleH = targetH.toFloat() / origH.toFloat()

        val scaledBmp = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
        val pixels = IntArray(targetW * targetH)
        scaledBmp.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        if (scaledBmp != bmp && !scaledBmp.isRecycled) {
            scaledBmp.recycle()
        }

        val floatBuffer = FloatBuffer.allocate(1 * 3 * targetH * targetW)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        for (c in 0 until 3) {
            val m = mean[c]
            val s = std[c]
            for (y in 0 until targetH) {
                val rowOff = y * targetW
                for (x in 0 until targetW) {
                    val p = pixels[rowOff + x]
                    val raw = when (c) {
                        0 -> (p shr 16) and 0xFF // R
                        1 -> (p shr 8) and 0xFF  // G
                        else -> p and 0xFF       // B
                    }
                    val norm = (raw / 255.0f - m) / s
                    floatBuffer.put(norm)
                }
            }
        }
        floatBuffer.flip()

        var tensor: OnnxTensor? = null
        var results: OrtSession.Result? = null
        try {
            tensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, targetH.toLong(), targetW.toLong()))
            val inputName = session.inputNames.iterator().next()
            results = session.run(Collections.singletonMap(inputName, tensor))

            val outputTensor = results[0] as? OnnxTensor ?: return fallbackTextBoundingBoxes(bmp)
            val fb = outputTensor.floatBuffer
            val probMap = FloatArray(targetH * targetW)
            fb.get(probMap)

            // Чувствительный порог для гарантированного захвата мелких подписей под иконками (12-18px)
            val thresh = 0.20f
            val boxThresh = 0.35f
            val minSize = 3
            val unclipRatio = 1.6f

            val visited = BooleanArray(targetH * targetW)
            val detectedBoxes = mutableListOf<Rect>()

            val queue = IntArray(targetH * targetW)

            for (y in 0 until targetH) {
                val rowOff = y * targetW
                for (x in 0 until targetW) {
                    val idx = rowOff + x
                    if (probMap[idx] >= thresh && !visited[idx]) {
                        var minX = x; var maxX = x
                        var minY = y; var maxY = y
                        var sumScore = 0.0f
                        var count = 0

                        var head = 0; var tail = 0
                        queue[tail++] = idx
                        visited[idx] = true

                        while (head < tail) {
                            val currIdx = queue[head++]
                            val cy = currIdx / targetW
                            val cx = currIdx % targetW
                            count++
                            sumScore += probMap[currIdx]

                            if (cx < minX) minX = cx
                            if (cx > maxX) maxX = cx
                            if (cy < minY) minY = cy
                            if (cy > maxY) maxY = cy

                            // 4-связное соседство
                            if (cx > 0) {
                                val n = currIdx - 1
                                if (!visited[n] && probMap[n] >= thresh) { visited[n] = true; queue[tail++] = n }
                            }
                            if (cx < targetW - 1) {
                                val n = currIdx + 1
                                if (!visited[n] && probMap[n] >= thresh) { visited[n] = true; queue[tail++] = n }
                            }
                            if (cy > 0) {
                                val n = currIdx - targetW
                                if (!visited[n] && probMap[n] >= thresh) { visited[n] = true; queue[tail++] = n }
                            }
                            if (cy < targetH - 1) {
                                val n = currIdx + targetW
                                if (!visited[n] && probMap[n] >= thresh) { visited[n] = true; queue[tail++] = n }
                            }
                        }

                        val avgScore = sumScore / max(1, count)
                        val bw = maxX - minX + 1
                        val bh = maxY - minY + 1

                        if (avgScore >= boxThresh && bw >= minSize && bh >= minSize) {
                            val area = bw * bh
                            val perimeter = 2 * (bw + bh)
                            val distance = (area * unclipRatio) / max(1, perimeter)

                            val unclipL = max(0, (minX - distance).toInt())
                            val unclipT = max(0, (minY - distance).toInt())
                            val unclipR = min(targetW - 1, (maxX + distance).toInt())
                            val unclipB = min(targetH - 1, (maxY + distance).toInt())

                            val origL = (unclipL / scaleW).toInt().coerceIn(0, origW - 1)
                            val origT = (unclipT / scaleH).toInt().coerceIn(0, origH - 1)
                            val origR = (unclipR / scaleW).toInt().coerceIn(origL + 1, origW)
                            val origB = (unclipB / scaleH).toInt().coerceIn(origT + 1, origH)

                            detectedBoxes.add(Rect(origL, origT, origR, origB))
                        }
                    }
                }
            }

            if (detectedBoxes.isEmpty()) {
                return fallbackTextBoundingBoxes(bmp)
            }

            // Подавление перекрывающихся дубликатов (NMS)
            val mergedBoxes = mutableListOf<Rect>()
            detectedBoxes.sortByDescending { it.width() * it.height() }

            for (box in detectedBoxes) {
                var isDuplicate = false
                for (m in mergedBoxes) {
                    val inter = Rect()
                    if (inter.setIntersect(m, box)) {
                        val interArea = inter.width() * inter.height()
                        val boxArea = box.width() * box.height()
                        if (interArea >= boxArea * 0.75f) {
                            isDuplicate = true
                            break
                        }
                    }
                }
                if (!isDuplicate) {
                    mergedBoxes.add(box)
                }
            }

            // Сортировка сверху-вниз, слева-направо
            mergedBoxes.sortBy { it.top * 10000 + it.left }
            return mergeAdjacentLineBoxes(mergedBoxes)
        } catch (e: Throwable) {
            AppLogger.logError(null, "ONNX_DET_RUN", e)
            return fallbackTextBoundingBoxes(bmp)
        } finally {
            try { tensor?.close() } catch (_: Throwable) {}
            try { results?.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Эвристический запасной детектор боксов (если нейросетевой DBNet недоступен).
     */
    private fun fallbackTextBoundingBoxes(bmp: Bitmap): List<Rect> {
        val w = bmp.width
        val h = bmp.height
        if (w < 16 || h < 16) return listOf(Rect(0, 0, w, h))

        val scale = if (max(w, h) > 720) 720f / max(w, h).toFloat() else 1f
        val sw = (w * scale).toInt().coerceAtLeast(16)
        val sh = (h * scale).toInt().coerceAtLeast(16)

        val scaledBmp = if (scale < 1f) Bitmap.createScaledBitmap(bmp, sw, sh, false) else bmp
        val pixels = IntArray(sw * sh)
        scaledBmp.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        if (scaledBmp != bmp && !scaledBmp.isRecycled) scaledBmp.recycle()

        val lums = IntArray(sw * sh)
        for (i in pixels.indices) {
            val p = pixels[i]
            lums[i] = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }

        val edges = BooleanArray(sw * sh)
        for (y in 1 until sh - 1) {
            val rowOff = y * sw
            for (x in 1 until sw - 1) {
                val gx = abs(lums[rowOff + x + 1] - lums[rowOff + x - 1])
                val gy = abs(lums[(y + 1) * sw + x] - lums[(y - 1) * sw + x])
                if (gx + gy > 42) {
                    edges[rowOff + x] = true
                }
            }
        }

        val dilated = BooleanArray(sw * sh)
        val kRadius = (6 * scale).toInt().coerceIn(3, 8)
        for (y in 0 until sh) {
            val rowOff = y * sw
            var count = 0
            for (x in 0 until min(sw, kRadius * 2)) {
                if (edges[rowOff + x]) count++
            }
            for (x in 0 until sw) {
                if (count > 0) dilated[rowOff + x] = true
                val removeX = x - kRadius
                if (removeX >= 0 && edges[rowOff + removeX]) count--
                val addX = x + kRadius + 1
                if (addX < sw && edges[rowOff + addX]) count++
            }
        }

        val visited = BooleanArray(sw * sh)
        val rawBoxes = mutableListOf<Rect>()
        val invScale = 1f / scale

        for (y in 0 until sh step 2) {
            val rowOff = y * sw
            for (x in 0 until sw step 2) {
                val idx = rowOff + x
                if (dilated[idx] && !visited[idx]) {
                    var minX = x; var maxX = x
                    var minY = y; var maxY = y
                    var compPixels = 0

                    val queueX = IntArray(2048)
                    val queueY = IntArray(2048)
                    var head = 0; var tail = 0

                    queueX[tail] = x; queueY[tail] = y; tail++
                    visited[idx] = true

                    while (head < tail && tail < 2040) {
                        val cx = queueX[head]; val cy = queueY[head]; head++
                        compPixels++
                        if (cx < minX) minX = cx
                        if (cx > maxX) maxX = cx
                        if (cy < minY) minY = cy
                        if (cy > maxY) maxY = cy

                        val neighbors = intArrayOf(cx - 2, cy, cx + 2, cy, cx, cy - 2, cx, cy + 2)
                        for (ni in 0 until 8 step 2) {
                            val nx = neighbors[ni]; val ny = neighbors[ni + 1]
                            if (nx in 0 until sw && ny in 0 until sh) {
                                val nIdx = ny * sw + nx
                                if (dilated[nIdx] && !visited[nIdx]) {
                                    visited[nIdx] = true
                                    queueX[tail] = nx; queueY[tail] = ny; tail++
                                }
                            }
                        }
                    }

                    val bw = maxX - minX + 1
                    val bh = maxY - minY + 1
                    if (bw in 8..600 && bh in 6..120 && compPixels >= 6) {
                        val origL = ((minX - 4) * invScale).toInt().coerceIn(0, w - 1)
                        val origT = ((minY - 3) * invScale).toInt().coerceIn(0, h - 1)
                        val origR = ((maxX + 5) * invScale).toInt().coerceIn(origL + 1, w)
                        val origB = ((maxY + 4) * invScale).toInt().coerceIn(origT + 1, h)
                        rawBoxes.add(Rect(origL, origT, origR, origB))
                    }
                }
            }
        }

        rawBoxes.sortBy { it.top * 10000 + it.left }
        return if (rawBoxes.isNotEmpty()) rawBoxes else listOf(Rect(0, 0, w, h))
    }

    /**
     * Инференс нейросети PP-OCRv4 (Pure ONNX) в строгом RGB формате.
     */
    fun recognizeTextWithOnnx(bitmap: Bitmap): String? {
        val session = getRecSession() ?: return null
        val dict = getDictionary()
        if (dict.isEmpty()) return null

        val processedBmp = enhanceGameFontCrop(bitmap)
        val targetH = 48
        val scale = targetH.toFloat() / processedBmp.height.toFloat().coerceAtLeast(1f)
        val resizedW = ((processedBmp.width * scale).toInt()).coerceIn(32, 960)
        val targetW = maxOf(64, ((resizedW + 31) / 32) * 32)

        val scaled = Bitmap.createScaledBitmap(processedBmp, resizedW, targetH, true)
        val pixels = IntArray(resizedW * targetH)
        scaled.getPixels(pixels, 0, resizedW, 0, 0, resizedW, targetH)

        val floatBuffer = FloatBuffer.allocate(1 * 3 * targetH * targetW)
        for (c in 0 until 3) {
            for (y in 0 until targetH) {
                for (x in 0 until targetW) {
                    if (x < resizedW) {
                        val pixel = pixels[y * resizedW + x]
                        val rawChannel = when (c) {
                            0 -> (pixel shr 16) and 0xFF // R
                            1 -> (pixel shr 8) and 0xFF  // G
                            else -> pixel and 0xFF       // B
                        }
                        floatBuffer.put((rawChannel / 255.0f - 0.5f) / 0.5f)
                    } else {
                        floatBuffer.put(0.0f)
                    }
                }
            }
        }
        floatBuffer.flip()

        var tensor: OnnxTensor? = null
        var results: OrtSession.Result? = null
        try {
            tensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, targetH.toLong(), targetW.toLong()))
            val inputName = session.inputNames.iterator().next()
            results = session.run(Collections.singletonMap(inputName, tensor))

            val outputTensor = results[0] as? OnnxTensor ?: return null
            @Suppress("UNCHECKED_CAST")
            val output = outputTensor.value as? Array<Array<FloatArray>> ?: return null

            val sb = StringBuilder()
            var lastIndex = -1
            val timeSteps = output[0]
            val validSteps = ((timeSteps.size.toFloat() * resizedW) / targetW).toInt().coerceIn(1, timeSteps.size)

            for (s in 0 until validSteps) {
                val step = timeSteps[s]
                var maxIdx = 0
                var maxProb = step[0]
                for (i in 1 until step.size) {
                    if (step[i] > maxProb) {
                        maxProb = step[i]
                        maxIdx = i
                    }
                }
                if (maxIdx != 0 && maxIdx != lastIndex && maxIdx < dict.size) {
                    sb.append(dict[maxIdx])
                }
                lastIndex = maxIdx
            }
            val rawDecoded = sb.toString().trim()
            return postProcessRussianText(rawDecoded)
        } catch (e: Throwable) {
            AppLogger.logError(null, "ONNX_INFER", e)
            return null
        } finally {
            try { tensor?.close() } catch (_: Throwable) {}
            try { results?.close() } catch (_: Throwable) {}
            if (scaled != processedBmp && !scaled.isRecycled) {
                try { scaled.recycle() } catch (_: Throwable) {}
            }
            if (processedBmp != bitmap && !processedBmp.isRecycled) {
                try { processedBmp.recycle() } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Постобработка: конвертация латинских гомоглифов и цифр-ошибок в русскую кириллицу
     * с удержанием регистра (заглавные/строчные).
     */
    private fun postProcessRussianText(text: String): String {
        if (text.isBlank()) return text

        val sb = StringBuilder()
        for (ch in text) {
            val converted = when (ch) {
                'A' -> 'А'
                'B' -> 'В'
                'E' -> 'Е'
                'K' -> 'К'
                'M' -> 'М'
                'H' -> 'Н'
                'O' -> 'О'
                'P' -> 'Р'
                'C' -> 'С'
                'T' -> 'Т'
                'X' -> 'Х'
                'Y' -> 'У'
                'a' -> 'а'
                'e' -> 'е'
                'o' -> 'о'
                'p' -> 'р'
                'c' -> 'с'
                'x' -> 'х'
                'y' -> 'у'
                'u' -> 'и'
                'r' -> 'г'
                'n' -> 'п'
                'd' -> 'д'
                'b' -> 'ь'
                else -> ch
            }
            sb.append(converted)
        }
        return sb.toString()
    }

    /**
     * Нормализация строки для нечеткого поиска:
     * Очистка от спецсимволов и свод кириллических/латинских гомоглифов и близких цифровых двойников.
     */
    private fun normalizeString(s: String): String {
        val clean = s.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\p{N}]+"""), "") // Оставляем только буквы и цифры
            .replace('ё', 'е')
            .replace('a', 'а')
            .replace('o', 'о')
            .replace('e', 'е')
            .replace('p', 'р')
            .replace('c', 'с')
            .replace('x', 'х')
            .replace('t', 'т')
            .replace('k', 'к')
            .replace('m', 'м')
            .replace('h', 'н')
            .replace('y', 'у')
            .replace('u', 'и')
            .replace('r', 'г')
            .replace('n', 'п')
            .replace('d', 'д')
            .replace('b', 'б')
            .replace('v', 'в')
            .replace('w', 'в')
            .replace('3', 'з')
            .replace('0', 'о')
            .replace('6', 'б')
            .replace('4', 'ч')
        return clean
    }

    /**
     * Вычисление классического расстояния Левенштейна (Dynamic Programming Edit Distance).
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // эрозия / удаление
                    dp[i][j - 1] + 1,       // вставка
                    dp[i - 1][j - 1] + cost  // замена
                )
            }
        }
        return dp[len1][len2]
    }

    /**
     * Интеллектуальный Fuzzy Matcher с поддержкой редакционного расстояния Левенштейна:
     * 1. Защита от ложных совпадений коротких боксов (например, одиночные буквы 'з', 'а' не совпадают с 'альянс').
     * 2. Нормализация мягких знаков ('алянс' <-> 'альянс').
     * 3. Допуск пропусков/замен символов через расстояние Левенштейна.
     */
    private fun isFuzzyMatch(candidate: String, query: String): Boolean {
        val normC = normalizeString(candidate)
        val normQ = normalizeString(query)
        if (normQ.isEmpty()) return true
        if (normC.isEmpty()) return false

        // Защита от ложных совпадений: короткие кандидаты (1-2 буквы) не могут совпадать с длинным запросом через contains()
        if (normC.length <= 2 && normQ.length > 2) {
            return normC == normQ
        }

        // 1. Точное или подстрочное совпадение
        if (normC == normQ || normC.contains(normQ) || (normC.length >= 3 && normQ.contains(normC))) return true

        // 2. Нормализация основ (игнорирование пропущенных или размытых мягких/твердых знаков 'ь' и 'ъ')
        val stemC = normC.replace("ь", "").replace("ъ", "")
        val stemQ = normQ.replace("ь", "").replace("ъ", "")
        if (stemC == stemQ || stemC.contains(stemQ) || (stemC.length >= 3 && stemQ.contains(stemC))) return true

        // 3. Каноническая нормализация путаемых кириллических глифов в мелких шрифтах
        val canonC = stemC.replace('в', 'б').replace('и', 'н').replace('м', 'н').replace('з', 'с').replace('г', 'р')
        val canonQ = stemQ.replace('в', 'б').replace('и', 'н').replace('м', 'н').replace('з', 'с').replace('г', 'р')
        if (canonC == canonQ || canonC.contains(canonQ) || (canonC.length >= 3 && canonQ.contains(canonC))) return true

        val qLen = normQ.length
        val maxDist = maxOf(1, qLen / 3)

        // 4. Оценка честного расстояния Левенштейна
        if (abs(canonC.length - canonQ.length) <= maxDist) {
            val dist = levenshteinDistance(canonC, canonQ)
            if (dist <= maxDist) return true
        }

        // Выравнивание по подстрокам через расстояние Левенштейна
        for (i in 0..maxOf(0, canonC.length - qLen)) {
            val sub = canonC.substring(i, minOf(canonC.length, i + qLen))
            val subDist = levenshteinDistance(sub, canonQ)
            if (subDist <= maxDist) return true
        }

        return false
    }

    fun findTextOnScreen(
        bitmap: Bitmap,
        targetQuery: String,
        timeoutMs: Long = 3000L,
        roi: Rect? = null,
        outVariables: MutableMap<String, String>? = null
    ): List<OcrMatchResult> {
        val perfStart = System.currentTimeMillis()
        if (!isValidForOcr(bitmap)) {
            AppLogger.log(null, "OCR", "OCR отменен: передан невалидный bitmap")
            return emptyList()
        }

        AppLogger.log(null, "OCR", "Старт Game OCR. Поиск: '$targetQuery', Размер: ${bitmap.width}x${bitmap.height}, ROI: ${roi?.toShortString() ?: "весь экран"}")

        // 1. Мгновенная проверка системного UI через Accessibility Service (1-3 мс)
        val service = com.example.autotap.infrastructure.accessibility.AutoTapAccessibilityService.instance
        if (service != null && targetQuery.isNotBlank() && !targetQuery.startsWith("regex:")) {
            val nativeResults = service.findTextInActiveWindow(targetQuery, roi)
            if (nativeResults.isNotEmpty()) {
                val elapsed = System.currentTimeMillis() - perfStart
                AppLogger.log(null, "OCR", "OCR (Accessibility Service): '${nativeResults.first().matchedText}' в (${nativeResults.first().clickX}, ${nativeResults.first().clickY}) за ${elapsed}мс")
                return nativeResults
            }
        }

        val isHardware = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE
        val srcBmp = if (isHardware || !bitmap.isMutable) {
            val sw = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(sw)
            c.drawBitmap(bitmap, 0f, 0f, null)
            sw
        } else {
            bitmap
        }

        val effectiveRoi = roi ?: service?.let { OcrQueryMetadataManager.getPersistentRoi(it.applicationContext, targetQuery) }

        val localBmp = if (effectiveRoi != null) {
            val safeLeft = effectiveRoi.left.coerceIn(0, srcBmp.width - 1)
            val safeTop = effectiveRoi.top.coerceIn(0, srcBmp.height - 1)
            val safeWidth = effectiveRoi.width().coerceIn(1, srcBmp.width - safeLeft)
            val safeHeight = effectiveRoi.height().coerceIn(1, srcBmp.height - safeTop)
            Bitmap.createBitmap(srcBmp, safeLeft, safeTop, safeWidth, safeHeight)
        } else {
            srcBmp
        }

        val cleanQuery = targetQuery.trim()
        val stripH = 48
        val gOffsetX = effectiveRoi?.left?.coerceIn(0, srcBmp.width - 1) ?: 0
        val gOffsetY = effectiveRoi?.top?.coerceIn(0, srcBmp.height - 1) ?: 0

        // 2. Прямое распознавание выделенной области ROI целиком (если ROI задан компактным окном)
        if (effectiveRoi != null && localBmp.width in 16..400 && localBmp.height in 12..200) {
            val directRec = recognizeTextWithOnnx(localBmp)
            if (!directRec.isNullOrBlank()) {
                AppLogger.log(null, "OCR_ROI_DIRECT", "Прямое распознавание ROI: '$directRec'")
                KEY_VALUE_REGEX.findAll(directRec).forEach { match ->
                    val key = match.groupValues[1].lowercase(Locale.ROOT)
                    val value = match.groupValues[2]
                    outVariables?.put(key, value)
                }
                if (cleanQuery.isBlank() || isFuzzyMatch(directRec, cleanQuery)) {
                    val matchResult = OcrMatchResult(
                        matchedText = directRec,
                        clickX = gOffsetX + localBmp.width / 2,
                        clickY = gOffsetY + localBmp.height / 2,
                        rectLeft = gOffsetX,
                        rectTop = gOffsetY,
                        rectRight = gOffsetX + localBmp.width,
                        rectBottom = gOffsetY + localBmp.height,
                        confidence = 1.0f
                    )
                    val elapsed = System.currentTimeMillis() - perfStart
                    AppLogger.log(null, "OCR", "OCR (Прямой ROI Успех): '$directRec' за ${elapsed}мс")
                    OcrQueryMetadataManager.registerOcrSuccess(cleanQuery)
                    if (localBmp != srcBmp && !localBmp.isRecycled) localBmp.recycle()
                    if (srcBmp != bitmap && !srcBmp.isRecycled) srcBmp.recycle()
                    return listOf(matchResult)
                }
            }
        }

        // 3. Нейросетевой детектор текстовых линий DBNet
        val boxes = detectTextBoundingBoxesWithDbNet(localBmp)
        AppLogger.log(null, "OCR", "Нейросетевой детектор DBNet: выделено ${boxes.size} точных боксов")

        val matches = mutableListOf<OcrMatchResult>()
        val filteredBoxes = boxes.filter { it.width() >= 6 && it.height() >= 6 }

        if (filteredBoxes.isNotEmpty()) {
            // Быстрое параллельное распознавание через пул потоков
            val numThreads = minOf(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(2))
            val threadPool = java.util.concurrent.Executors.newFixedThreadPool(numThreads)
            val futures = mutableListOf<java.util.concurrent.Future<Pair<Rect, String?>>>()

            for (box in filteredBoxes) {
                val f = threadPool.submit(java.util.concurrent.Callable {
                    val bH = box.height()
                    // Добавляем горизонтальный и вертикальный паддинг (25% высоты строки), чтобы предотвратить "проглатывание" первых и последних букв
                    val padX = (bH * 0.25f).toInt().coerceAtLeast(8)
                    val padY = (bH * 0.15f).toInt().coerceAtLeast(4)

                    val cropL = (box.left - padX).coerceIn(0, localBmp.width - 1)
                    val cropT = (box.top - padY).coerceIn(0, localBmp.height - 1)
                    val cropR = (box.right + padX).coerceIn(cropL + 1, localBmp.width)
                    val cropB = (box.bottom + padY).coerceIn(cropT + 1, localBmp.height)

                    val crop = Bitmap.createBitmap(localBmp, cropL, cropT, cropR - cropL, cropB - cropT)
                    val rec = recognizeTextWithOnnx(crop)
                    if (crop != localBmp && !crop.isRecycled) crop.recycle()
                    Pair(box, rec)
                })
                futures.add(f)
            }

            threadPool.shutdown()

            for (future in futures) {
                val (box, rec) = try { future.get() } catch (_: Throwable) { continue }
                if (!rec.isNullOrBlank()) {
                    AppLogger.log(null, "OCR_SCAN", "Бокс [${box.left},${box.top}..${box.right},${box.bottom}]: '$rec'")
                    KEY_VALUE_REGEX.findAll(rec).forEach { match ->
                        val key = match.groupValues[1].lowercase(Locale.ROOT)
                        val value = match.groupValues[2]
                        outVariables?.put(key, value)
                    }

                    val isMatch = cleanQuery.isBlank() || isFuzzyMatch(rec, cleanQuery)
                    if (isMatch) {
                        val bW = box.width()
                        val bH = box.height()
                        val gX = gOffsetX + box.left
                        val gY = gOffsetY + box.top
                        val matchResult = OcrMatchResult(
                            matchedText = rec,
                            clickX = gX + bW / 2,
                            clickY = gY + bH / 2,
                            rectLeft = gX,
                            rectTop = gY,
                            rectRight = gX + bW,
                            rectBottom = gY + bH,
                            confidence = 1.0f
                        )
                        matches.add(matchResult)
                        if (cleanQuery.isNotBlank()) {
                            AppLogger.log(null, "OCR", "Game OCR Найдено совпадение: '$rec' (запрос '$targetQuery') в (${matchResult.clickX}, ${matchResult.clickY})")
                            OcrQueryMetadataManager.registerOcrSuccess(cleanQuery)
                        }
                    }
                }
            }
        }

        if (matches.isNotEmpty()) {
            matches.sortBy { it.rectTop * 10000 + it.rectLeft }
            val elapsed = System.currentTimeMillis() - perfStart
            AppLogger.log(null, "OCR", "Game OCR Завершен: найдено ${matches.size} вариантов для '$targetQuery' за ${elapsed}мс")
            if (localBmp != srcBmp && !localBmp.isRecycled) localBmp.recycle()
            if (srcBmp != bitmap && !srcBmp.isRecycled) srcBmp.recycle()
            return matches
        }

        if (localBmp != srcBmp && !localBmp.isRecycled) localBmp.recycle()
        if (srcBmp != bitmap && !srcBmp.isRecycled) srcBmp.recycle()

        val elapsed = System.currentTimeMillis() - perfStart
        if (targetQuery.isNotBlank()) {
            AppLogger.log(null, "OCR", "Game OCR: совпадений для '$targetQuery' не обнаружено (время: ${elapsed}мс)")
        }
        return emptyList()
    }

    fun findAndExtractRegex(bitmap: Bitmap, regexPattern: String, timeoutMs: Long = 3000L, roi: Rect? = null): String? {
        if (regexPattern.isBlank() || !isValidForOcr(bitmap)) return null
        val pattern = try { Regex(regexPattern) } catch (_: Exception) { return null }

        val results = findTextOnScreen(bitmap, "", timeoutMs, roi)
        for (res in results) {
            val match = pattern.find(res.matchedText)
            if (match != null) {
                val extracted = match.groupValues.getOrNull(1) ?: match.value
                AppLogger.log(null, "OCR", "ONNX Regex: '$extracted'")
                return extracted
            }
        }
        return null
    }
}

