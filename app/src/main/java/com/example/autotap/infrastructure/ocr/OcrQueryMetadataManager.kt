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
        AppLogger.log(null, "OCR_METADATA", "Сбой OCR для '$query': попытка $count")
        return false // Отключено по запросу пользователя (без всплывающих окон)
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

    /**
     * Экспорт всех сохраненных персистентных метаданных OCR и истории в JSON.
     */
    fun exportOcrMetadataJson(context: Context): org.json.JSONObject {
        val root = org.json.JSONObject()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val allEntries = prefs.all
        val metadataObj = org.json.JSONObject()

        allEntries.forEach { (key, value) ->
            metadataObj.put(key, value)
        }
        root.put("ocr_metadata", metadataObj)

        val ocrPrefs = context.getSharedPreferences("autotap_recent_ocr", Context.MODE_PRIVATE)
        val rawHistory = ocrPrefs.getString("recent_queries", "") ?: ""
        root.put("recent_queries", rawHistory)
        root.put("export_timestamp", System.currentTimeMillis())
        return root
    }

    /**
     * Импорт метаданных OCR из JSON структуры. Возвращает количество импортированных ключей.
     */
    fun importOcrMetadataJson(context: Context, root: org.json.JSONObject): Int {
        var importedCount = 0
        try {
            val metadataObj = root.optJSONObject("ocr_metadata")
            if (metadataObj != null) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()
                val keys = metadataObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = metadataObj.get(k)
                    when (v) {
                        is Int -> editor.putInt(k, v)
                        is Long -> editor.putLong(k, v)
                        is Float -> editor.putFloat(k, v)
                        is String -> editor.putString(k, v)
                        is Boolean -> editor.putBoolean(k, v)
                    }
                    importedCount++
                }
                editor.apply()
            }

            val recentQueries = root.optString("recent_queries", "")
            if (recentQueries.isNotBlank()) {
                val ocrPrefs = context.getSharedPreferences("autotap_recent_ocr", Context.MODE_PRIVATE)
                val existing = ocrPrefs.getString("recent_queries", "") ?: ""
                val merged = (existing.split("|||") + recentQueries.split("|||"))
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(20)
                    .joinToString("|||")
                ocrPrefs.edit().putString("recent_queries", merged).apply()
            }
            AppLogger.log(context, "OCR_METADATA", "Успешно импортировано $importedCount элементов метаданных OCR")
        } catch (e: Exception) {
            AppLogger.logError(context, "OCR_METADATA_IMPORT", e)
        }
        return importedCount
    }
}
