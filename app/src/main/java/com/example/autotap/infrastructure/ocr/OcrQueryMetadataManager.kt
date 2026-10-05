package com.example.autotap.infrastructure.ocr

import android.content.Context
import android.graphics.Rect
import com.example.autotap.core.logger.AppLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер сбора персистентных метаданных поиска OCR для шаблонов и запросов.
 *
 * Особенности:
 *  1. Учет последовательных неудачных попыток OCR для каждого запроса/шаблона.
 *  2. Порог автоматической активации ручного выбора области (3 подряд ошибки).
 *  3. Персистентное сохранение координат (ROI) и геометрических параметров (высоты строки) в SharedPreferences.
 */
object OcrQueryMetadataManager {

    private const val PREFS_NAME = "autotap_ocr_query_metadata"
    private const val FAILURE_THRESHOLD = 3

    // Счетчик ошибок OCR в текущей сессии для каждого запроса
    private val failureCounters = ConcurrentHashMap<String, Int>()

    /**
     * Регистрирует сбой OCR поиска для запроса [query].
     * Возвращает true, если превышен порог [FAILURE_THRESHOLD] (3 подряд ошибки) и требуется запуск ручного выбора области.
     */
    fun registerOcrFailure(query: String): Boolean {
        if (query.isBlank()) return false
        val cleanKey = query.trim().lowercase()
        val count = (failureCounters[cleanKey] ?: 0) + 1
        failureCounters[cleanKey] = count
        AppLogger.log(null, "OCR_METADATA", "Сбой OCR для '$query': попытка $count/$FAILURE_THRESHOLD")
        return count >= FAILURE_THRESHOLD
    }

    /**
     * Сбрасывает счетчик ошибок при успешном совпадении OCR.
     */
    fun registerOcrSuccess(query: String) {
        if (query.isBlank()) return
        val cleanKey = query.trim().lowercase()
        failureCounters.remove(cleanKey)
    }

    /**
     * Сохраняет персистентные метаданные ROI (координаты и высоту строки) для конкретного запроса/шаблона.
     */
    fun savePersistentRoi(context: Context, query: String, roi: Rect) {
        if (query.isBlank()) return
        val cleanKey = query.trim().lowercase()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("${cleanKey}_left", roi.left)
            .putInt("${cleanKey}_top", roi.top)
            .putInt("${cleanKey}_right", roi.right)
            .putInt("${cleanKey}_bottom", roi.bottom)
            .putInt("${cleanKey}_height", roi.height())
            .putLong("${cleanKey}_timestamp", System.currentTimeMillis())
            .apply()

        failureCounters.remove(cleanKey)
        AppLogger.log(context, "OCR_METADATA", "Сохранен персистентный ROI для '$query': $roi (высота: ${roi.height()}px)")
    }

    /**
     * Возвращает ранее сохраненный персистентный ROI для запроса [query], если он существует.
     */
    fun getPersistentRoi(context: Context, query: String): Rect? {
        if (query.isBlank()) return null
        val cleanKey = query.trim().lowercase()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains("${cleanKey}_left")) return null

        val left = prefs.getInt("${cleanKey}_left", 0)
        val top = prefs.getInt("${cleanKey}_top", 0)
        val right = prefs.getInt("${cleanKey}_right", 0)
        val bottom = prefs.getInt("${cleanKey}_bottom", 0)

        if (right > left && bottom > top) {
            return Rect(left, top, right, bottom)
        }
        return null
    }

    /**
     * Очищает персистентный ROI для запроса.
     */
    fun clearPersistentRoi(context: Context, query: String) {
        if (query.isBlank()) return
        val cleanKey = query.trim().lowercase()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("${cleanKey}_left")
            .remove("${cleanKey}_top")
            .remove("${cleanKey}_right")
            .remove("${cleanKey}_bottom")
            .remove("${cleanKey}_height")
            .remove("${cleanKey}_timestamp")
            .apply()
        failureCounters.remove(cleanKey)
    }
}
