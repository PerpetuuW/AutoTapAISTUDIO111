package com.example.autotap.infrastructure.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.domain.repository.ITemplateRepository
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class TemplateRepositoryImpl(private val context: Context) : ITemplateRepository {

    private val baseTemplatesDir: File
        get() = File(context.filesDir, "templates").apply { mkdirs() }

    private val trashDir: File
        get() = File(context.filesDir, "trash_templates").apply { mkdirs() }

    // Потокобезопасный LruCache с лимитом 16 МБ (размер считается в килобайтах)
    private val maxCacheSizeKb = 16 * 1024
    private val memoryCache = object : LruCache<String, Bitmap>(maxCacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    private fun getCachedBitmap(path: String): Bitmap? {
        val bmp = memoryCache.get(path)
        return if (bmp != null && !bmp.isRecycled) bmp else {
            if (bmp != null) memoryCache.remove(path)
            null
        }
    }

    override fun loadAllTemplates(): List<Pair<String, Bitmap>> {
        val result = mutableListOf<Pair<String, Bitmap>>()
        baseTemplatesDir.walkTopDown().filter {
            it.isFile && it.name.endsWith(".png", ignoreCase = true) &&
                    !it.name.startsWith("raw_", ignoreCase = true) &&
                    !it.name.startsWith("screen_", ignoreCase = true)
        }.forEach { file ->
            val path = file.absolutePath
            val bmp = getCachedBitmap(path) ?: BitmapFactory.decodeFile(path)?.also {
                memoryCache.put(path, it)
            }
            if (bmp != null && !bmp.isRecycled) {
                result.add(path to bmp)
            }
        }
        return result
    }

    override fun getTemplate(path: String): Bitmap? {
        if (path.isBlank()) return null
        getCachedBitmap(path)?.let { return it }

        val file = File(path)
        val targetFile = if (file.exists()) file else {
            baseTemplatesDir.walkTopDown().firstOrNull { it.isFile && it.name == file.name }
        }

        if (targetFile == null || !targetFile.exists()) return null
        return try {
            BitmapFactory.decodeFile(targetFile.absolutePath)?.also {
                memoryCache.put(path, it)
                if (targetFile.absolutePath != path) {
                    memoryCache.put(targetFile.absolutePath, it)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun getTemplateMetadata(path: String): Map<String, Any>? {
        return try {
            val file = File(path)
            val parent = file.parentFile ?: baseTemplatesDir
            val metaFile = File(parent, "${file.nameWithoutExtension}.json")
            val targetMeta = if (metaFile.exists()) metaFile else {
                baseTemplatesDir.walkTopDown().firstOrNull { it.isFile && it.name == "${file.nameWithoutExtension}.json" }
            }

            if (targetMeta == null || !targetMeta.exists()) return null
            val json = JSONObject(targetMeta.readText())
            val map = mutableMapOf<String, Any>()
            json.keys().forEach { map[it] = json.get(it) }

            if (json.has("additionalSearchLocations")) {
                val locArray = json.optJSONArray("additionalSearchLocations")
                if (locArray != null) {
                    for (i in 0 until locArray.length()) {
                        val obj = locArray.optJSONObject(i)
                        if (obj != null) {
                            val x = obj.optInt("x")
                            val y = obj.optInt("y")
                            com.example.autotap.infrastructure.vision.TemplateMatchingEngine.registerDiscoveredLocation(path, x, y)
                        }
                    }
                }
            }
            map
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Вспомогательный метод для синхронного перемещения/переименования всех сопутствующих файлов шаблона
     * (raw_, screen_.png / screen_.jpg и .json).
     */
    private fun moveRelatedFiles(oldMaskFile: File, newMaskFile: File) {
        val oldDir = oldMaskFile.parentFile ?: baseTemplatesDir
        val newDir = newMaskFile.parentFile ?: baseTemplatesDir

        val oldBase = oldMaskFile.nameWithoutExtension.removePrefix("mask_")
        val newBase = newMaskFile.nameWithoutExtension.removePrefix("mask_")

        val oldRaw = File(oldDir, "raw_$oldBase.png")
        val newRaw = File(newDir, "raw_$newBase.png")
        if (oldRaw.exists()) {
            if (newRaw.exists() && newRaw != oldRaw) newRaw.delete()
            oldRaw.renameTo(newRaw)
        }

        // Проверяем как .png (старый формат), так и .jpg
        val oldScreenPng = File(oldDir, "screen_$oldBase.png")
        val oldScreenJpg = File(oldDir, "screen_$oldBase.jpg")
        when {
            oldScreenPng.exists() -> {
                val newScreenPng = File(newDir, "screen_$newBase.png")
                if (newScreenPng.exists() && newScreenPng != oldScreenPng) newScreenPng.delete()
                oldScreenPng.renameTo(newScreenPng)
            }
            oldScreenJpg.exists() -> {
                val newScreenJpg = File(newDir, "screen_$newBase.jpg")
                if (newScreenJpg.exists() && newScreenJpg != oldScreenJpg) newScreenJpg.delete()
                oldScreenJpg.renameTo(newScreenJpg)
            }
        }

        val oldMeta = File(oldDir, "${oldMaskFile.nameWithoutExtension}.json")
        val newMeta = File(newDir, "${newMaskFile.nameWithoutExtension}.json")
        if (oldMeta.exists()) {
            if (newMeta.exists() && newMeta != oldMeta) newMeta.delete()
            oldMeta.renameTo(newMeta)
        }
    }

    private fun deleteRelatedFiles(maskFile: File) {
        val dir = maskFile.parentFile ?: return
        val base = maskFile.nameWithoutExtension.removePrefix("mask_")
        File(dir, "raw_$base.png").takeIf { it.exists() }?.delete()
        File(dir, "screen_$base.png").takeIf { it.exists() }?.delete()
        File(dir, "screen_$base.jpg").takeIf { it.exists() }?.delete()
        File(dir, "${maskFile.nameWithoutExtension}.json").takeIf { it.exists() }?.delete()
        maskFile.takeIf { it.exists() }?.delete()
    }

    // [V12.0] Перегрузка метода для поддержки полноэкранного скриншота
    fun saveTemplateWithScreen(
        folderName: String,
        existingPath: String?,
        maskBmp: Bitmap,
        rawBmp: Bitmap?,
        screenBmp: Bitmap?,
        metadata: JSONObject,
        customFileName: String? = null
    ): String {
        val targetDir = File(baseTemplatesDir, folderName).apply { mkdirs() }
        val cleanName = customFileName?.takeIf { it.isNotBlank() }?.let {
            val safe = it.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val name = if (safe.startsWith("mask_")) safe else "mask_$safe"
            if (name.endsWith(".png")) name else "$name.png"
        }

        val tPath = if (existingPath.isNullOrBlank()) {
            var targetFile = File(targetDir, cleanName ?: "mask_${System.currentTimeMillis()}.png")
            var suffixCounter = 1
            while (targetFile.exists()) {
                val base = targetFile.nameWithoutExtension
                targetFile = File(targetDir, "${base}_v${suffixCounter}.png")
                suffixCounter++
            }
            targetFile.absolutePath
        } else {
            val oldFile = File(existingPath)
            if (cleanName != null && (oldFile.name != cleanName || oldFile.parentFile != targetDir)) {
                val newFile = File(targetDir, cleanName)
                if (oldFile.exists()) {
                    moveRelatedFiles(oldFile, newFile)
                    if (newFile.exists() && newFile != oldFile) newFile.delete()
                    oldFile.renameTo(newFile)
                }
                newFile.absolutePath
            } else {
                existingPath
            }
        }

        val maskFile = File(tPath)
        val parentDir = maskFile.parentFile ?: targetDir
        val baseWithoutPrefix = maskFile.nameWithoutExtension.removePrefix("mask_")

        val rawFile = File(parentDir, "raw_$baseWithoutPrefix.png")
        // Сохраняем имя screen_<base>.png для полной совместимости с остальными частями проекта
        val screenFile = File(parentDir, "screen_$baseWithoutPrefix.png")
        val metaFile = File(parentDir, "${maskFile.nameWithoutExtension}.json")

        try {
            FileOutputStream(maskFile).use { out -> maskBmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            rawBmp?.let { bmp ->
                FileOutputStream(rawFile).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            }
            screenBmp?.let { bmp ->
                FileOutputStream(screenFile).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            }
            val learnedLocations = com.example.autotap.infrastructure.vision.TemplateMatchingEngine.additionalSearchLocations[tPath]
            if (learnedLocations != null && learnedLocations.isNotEmpty()) {
                val locArray = org.json.JSONArray()
                synchronized(learnedLocations) {
                    for (loc in learnedLocations) {
                        locArray.put(JSONObject().apply {
                            put("x", loc.first)
                            put("y", loc.second)
                        })
                    }
                }
                metadata.put("additionalSearchLocations", locArray)
            }

            FileOutputStream(metaFile).use { out -> out.write(metadata.toString().toByteArray()) }

            memoryCache.remove(tPath)
            if (existingPath != null && existingPath != tPath) {
                memoryCache.remove(existingPath)
            }
            memoryCache.put(tPath, maskBmp.copy(Bitmap.Config.ARGB_8888, false))
        } catch (e: Exception) {
            AppLogger.logError(context, "STORAGE", e)
        }
        return tPath
    }

    override fun saveTemplate(
        folderName: String,
        existingPath: String?,
        maskBmp: Bitmap,
        rawBmp: Bitmap?,
        metadata: JSONObject,
        screenBmp: Bitmap?,
        customFileName: String?
    ): String {
        return saveTemplateWithScreen(folderName, existingPath, maskBmp, rawBmp, screenBmp, metadata, customFileName)
    }

    fun listFolders(): List<String> {
        val dirs = baseTemplatesDir.listFiles { file -> file.isDirectory }
        val names = dirs?.map { it.name }?.toMutableList() ?: mutableListOf()
        if (!names.contains("default")) names.add(0, "default")
        return names.distinct().sorted()
    }

    fun deleteFolder(folderName: String, deleteTemplates: Boolean): Boolean {
        if (folderName == "default") return false
        return try {
            val folder = File(baseTemplatesDir, folderName)
            if (!folder.exists() || !folder.isDirectory) return false
            val files = folder.listFiles() ?: emptyArray()
            if (files.isEmpty()) {
                return folder.delete()
            }
            if (deleteTemplates) {
                for (file in files) {
                    memoryCache.remove(file.absolutePath)
                    file.delete()
                }
                folder.delete()
            } else {
                val defaultDir = File(baseTemplatesDir, "default").apply { mkdirs() }
                for (file in files) {
                    memoryCache.remove(file.absolutePath)
                    val target = File(defaultDir, file.name)
                    if (target.exists()) target.delete()
                    file.renameTo(target)
                }
                folder.delete()
            }
        } catch (e: Exception) {
            AppLogger.logError(context, "STORAGE", e)
            false
        }
    }

    fun renameFolder(oldName: String, newName: String): Boolean {
        if (oldName == "default" || newName == "default" || oldName.isBlank() || newName.isBlank()) return false
        return try {
            val oldFolder = File(baseTemplatesDir, oldName)
            val newFolder = File(baseTemplatesDir, newName)
            if (!oldFolder.exists() || newFolder.exists()) return false
            val success = oldFolder.renameTo(newFolder)
            if (success) {
                memoryCache.evictAll() // Сбрасываем кэш, так как пути изменились
            }
            success
        } catch (e: Exception) {
            AppLogger.logError(context, "STORAGE", e)
            false
        }
    }

    override fun moveToTrash(templatePath: String): Boolean {
        return try {
            val maskFile = File(templatePath)
            if (!maskFile.exists()) return false

            memoryCache.remove(templatePath)
            val baseName = maskFile.nameWithoutExtension
            val targetTrashMask = File(trashDir, maskFile.name)

            // Перемещаем raw_, screen_ и .json вместе с маской
            moveRelatedFiles(maskFile, targetTrashMask)
            if (targetTrashMask.exists()) targetTrashMask.delete()
            val success = maskFile.renameTo(targetTrashMask)

            AppLogger.log(context, "STORAGE", "Шаблон '$baseName' перемещен в корзину")
            success
        } catch (e: Exception) {
            AppLogger.logError(context, "STORAGE", e)
            false
        }
    }

    override fun restoreFromTrash(trashMaskFile: File): Boolean {
        return try {
            if (!trashMaskFile.exists()) return false
            val defaultDir = File(baseTemplatesDir, "default").apply { mkdirs() }
            val baseName = trashMaskFile.nameWithoutExtension
            val targetMask = File(defaultDir, trashMaskFile.name)

            // Восстанавливаем raw_, screen_ и .json вместе с маской
            moveRelatedFiles(trashMaskFile, targetMask)
            if (targetMask.exists()) targetMask.delete()
            val success = trashMaskFile.renameTo(targetMask)

            AppLogger.log(context, "STORAGE", "Шаблон '$baseName' восстановлен из корзины")
            success
        } catch (e: Exception) {
            AppLogger.logError(context, "STORAGE", e)
            false
        }
    }

    override fun purgeExpiredTrash(maxAgeDays: Int) {
        try {
            val cutoff = System.currentTimeMillis() - (maxAgeDays.toLong() * 24 * 60 * 60 * 1000)
            val files = trashDir.listFiles() ?: return
            var purgedCount = 0
            for (f in files) {
                if (f.lastModified() < cutoff) {
                    if (f.name.startsWith("mask_") && f.name.endsWith(".png")) {
                        deleteRelatedFiles(f)
                        purgedCount++
                    } else if (f.delete()) {
                        purgedCount++
                    }
                }
            }
            if (purgedCount > 0) {
                AppLogger.log(context, "STORAGE", "Очищено $purgedCount устаревших объектов из корзины")
            }
        } catch (e: Exception) {
            AppLogger.logError(context, "STORAGE", e)
        }
    }

    override fun listTrash(): List<File> {
        return trashDir.listFiles()?.filter {
            it.isFile && it.name.startsWith("mask_") && it.name.endsWith(".png")
        } ?: emptyList()
    }

    override fun renameTemplate(oldPath: String, newName: String): String? {
        return try {
            val maskFile = File(oldPath)
            if (!maskFile.exists()) return null

            val parentDir = maskFile.parentFile ?: baseTemplatesDir
            val cleanNewName = newName.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").removePrefix("mask_")
            if (cleanNewName.isEmpty()) return null

            val oldBase = maskFile.nameWithoutExtension.removePrefix("mask_")
            val targetMask = File(parentDir, "mask_$cleanNewName.png")

            if (targetMask.absolutePath == maskFile.absolutePath) return maskFile.absolutePath

            moveRelatedFiles(maskFile, targetMask)
            if (targetMask.exists()) targetMask.delete()
            val renamed = maskFile.renameTo(targetMask)
            if (!renamed) return null

            memoryCache.remove(oldPath)
            AppLogger.log(context, "STORAGE", "Шаблон переименован: $oldBase -> $cleanNewName")
            targetMask.absolutePath
        } catch (e: Exception) {
            AppLogger.logError(context, "STORAGE", e)
            null
        }
    }

    override fun clearCache() {
        memoryCache.evictAll()
    }
}