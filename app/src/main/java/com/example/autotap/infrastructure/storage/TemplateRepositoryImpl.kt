package com.example.autotap.infrastructure.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.domain.repository.ITemplateRepository
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

class TemplateRepositoryImpl(private val context: Context) : ITemplateRepository {

    private val baseTemplatesDir: File
        get() = File(context.filesDir, "templates").apply { mkdirs() }

    private val trashDir: File
        get() = File(context.filesDir, "trash_templates").apply { mkdirs() }

    private val memoryCache = android.util.LruCache<String, Bitmap>(32)

    override fun loadAllTemplates(): List<Pair<String, Bitmap>> {
        val result = mutableListOf<Pair<String, Bitmap>>()
        baseTemplatesDir.walkTopDown().filter {
            it.isFile && it.name.endsWith(".png", ignoreCase = true) &&
            !it.name.startsWith("raw_", ignoreCase = true) &&
            !it.name.startsWith("screen_", ignoreCase = true)
        }.forEach { file ->
            val path = file.absolutePath
            val cached = memoryCache.get(path)
            val bmp = if (cached != null && !cached.isRecycled) {
                cached
            } else {
                BitmapFactory.decodeFile(path)?.also { memoryCache.put(path, it) }
            }
            if (bmp != null && !bmp.isRecycled) {
                result.add(path to bmp)
            }
        }
        return result
    }

    override fun getTemplate(path: String): Bitmap? {
        if (path.isBlank()) return null
        val cached = memoryCache.get(path)
        if (cached != null && !cached.isRecycled) return cached

        val file = File(path)
        val targetFile = if (file.exists()) file else {
            baseTemplatesDir.walkTopDown().firstOrNull { it.isFile && it.name == file.name }
        }

        if (targetFile == null || !targetFile.exists()) return null
        return try {
            BitmapFactory.decodeFile(targetFile.absolutePath)?.also { memoryCache.put(path, it) }
        } catch (_: Exception) { null }
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
            map
        } catch (_: Exception) { null }
    }

    // [V12.0] Перегрузка метода для поддержки полноэкранного скриншота
    fun saveTemplateWithScreen(folderName: String, existingPath: String?, maskBmp: Bitmap, rawBmp: Bitmap?, screenBmp: Bitmap?, metadata: JSONObject, customFileName: String? = null): String {
        val targetDir = File(baseTemplatesDir, folderName).apply { mkdirs() }
        val cleanName = customFileName?.takeIf { it.isNotBlank() }?.let {
            val name = if (it.startsWith("mask_")) it else "mask_$it"
            if (name.endsWith(".png")) name else "$name.png"
        }
        
        val tPath = if (existingPath.isNullOrBlank()) {
            var targetFile = File(targetDir, cleanName ?: "mask_${System.currentTimeMillis()}.png")
            // [V29.0] Гарантированная уникальность при создании новых шаблонов (предотвращение перезаписи)
            var suffixCounter = 1
            while (targetFile.exists()) {
                val base = targetFile.nameWithoutExtension
                targetFile = File(targetDir, "${base}_v${suffixCounter}.png")
                suffixCounter++
            }
            targetFile.absolutePath
        } else {
            if (cleanName != null && File(existingPath).name != cleanName) {
                val oldFile = File(existingPath)
                val newFile = File(targetDir, if (cleanName.endsWith(".png")) cleanName else "$cleanName.png")
                
                // Переименовываем все связанные файлы
                val parent = oldFile.parentFile
                val oldBase = oldFile.nameWithoutExtension.removePrefix("mask_")
                val newBase = newFile.nameWithoutExtension.removePrefix("mask_")
                
                File(parent, "raw_$oldBase.png").let { if (it.exists()) it.renameTo(File(targetDir, "raw_$newBase.png")) }
                File(parent, "screen_$oldBase.jpg").let { if (it.exists()) it.renameTo(File(targetDir, "screen_$newBase.jpg")) }
                File(parent, "mask_$oldBase.json").let { if (it.exists()) it.renameTo(File(targetDir, "mask_$newBase.json")) }
                
                oldFile.renameTo(newFile)
                newFile.absolutePath
            } else existingPath
        }

        val maskFile = File(tPath)
        val baseNameNoPrefix = maskFile.nameWithoutExtension.removePrefix("mask_")
        val rawFile = File(maskFile.parentFile, "raw_$baseNameNoPrefix.png")
        val screenFile = File(maskFile.parentFile, "screen_$baseNameNoPrefix.jpg")
        val metaFile = File(maskFile.parentFile, "${maskFile.nameWithoutExtension}.json")

        try {
            FileOutputStream(maskFile).use { out -> maskBmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            rawBmp?.let { bmp ->
                FileOutputStream(rawFile).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            }
            screenBmp?.let { bmp ->
                FileOutputStream(screenFile).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            }
            FileOutputStream(metaFile).use { out -> out.write(metadata.toString().toByteArray()) }

            memoryCache.remove(tPath)
            if (existingPath != null && existingPath != tPath) {
                memoryCache.remove(existingPath)
            }
            maskBmp.copy(Bitmap.Config.ARGB_8888, true)?.let { memoryCache.put(tPath, it) }
        } catch (e: Exception) {
            AppLogger.logError(context, "STORAGE", e)
        }
        return tPath
    }

    override fun saveTemplate(folderName: String, existingPath: String?, maskBmp: android.graphics.Bitmap, rawBmp: android.graphics.Bitmap?, metadata: org.json.JSONObject, screenBmp: android.graphics.Bitmap?, customFileName: String?): String {
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
                    file.delete()
                }
                folder.delete()
            } else {
                val defaultDir = File(baseTemplatesDir, "default").apply { mkdirs() }
                for (file in files) {
                    file.renameTo(File(defaultDir, file.name))
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
                memoryCache.evictAll() // Cache paths are invalidated
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
            val parentDir = maskFile.parentFile
            val baseName = maskFile.nameWithoutExtension

            val metaFile = File(parentDir, "$baseName.json")
            val baseNameNoPrefix = maskFile.nameWithoutExtension.removePrefix("mask_")
            val rawFile = File(parentDir, "raw_$baseNameNoPrefix.png")
            val screenFile = File(parentDir, "screen_$baseNameNoPrefix.jpg")

            val targetTrashMask = File(trashDir, maskFile.name)
            val success = maskFile.renameTo(targetTrashMask)

            if (metaFile.exists()) {
                metaFile.renameTo(File(trashDir, metaFile.name))
            }
            if (rawFile.exists()) {
                rawFile.renameTo(File(trashDir, rawFile.name))
            }
            if (screenFile.exists()) {
                screenFile.renameTo(File(trashDir, screenFile.name))
            }

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

            val metaFile = File(trashDir, "$baseName.json")
            val baseNameNoPrefix = trashMaskFile.nameWithoutExtension.removePrefix("mask_")
            val rawFile = File(trashDir, "raw_$baseNameNoPrefix.png")
            val screenFile = File(trashDir, "screen_$baseNameNoPrefix.jpg")

            val targetMask = File(defaultDir, trashMaskFile.name)
            val success = trashMaskFile.renameTo(targetMask)

            if (metaFile.exists()) {
                metaFile.renameTo(File(defaultDir, metaFile.name))
            }
            if (rawFile.exists()) {
                rawFile.renameTo(File(defaultDir, rawFile.name))
            }
            if (screenFile.exists()) {
                screenFile.renameTo(File(defaultDir, screenFile.name))
            }

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
                    if (f.delete()) purgedCount++
                }
            }
            if (purgedCount > 0) {
                AppLogger.log(context, "STORAGE", "Очищено $purgedCount устаревших файлов из корзины")
            }
        } catch (e: Exception) {
            AppLogger.logError(context, "STORAGE", e)
        }
    }

    override fun listTrash(): List<File> {
        return trashDir.listFiles()?.filter { it.isFile && it.name.startsWith("mask_") && it.name.endsWith(".png") } ?: emptyList()
    }

    override fun renameTemplate(oldPath: String, newName: String): String? {
        return try {
            val maskFile = File(oldPath)
            if (!maskFile.exists()) return null

            val parentDir = maskFile.parentFile ?: baseTemplatesDir
            val cleanNewName = newName.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
            if (cleanNewName.isEmpty()) return null

            val targetMask = File(parentDir, "mask_$cleanNewName.png")
            val targetRaw = File(parentDir, "raw_$cleanNewName.png")
            val targetScreen = File(parentDir, "screen_$cleanNewName.png")
            val targetMeta = File(parentDir, "mask_$cleanNewName.json")

            val oldBase = maskFile.nameWithoutExtension.removePrefix("mask_")
            val oldRaw = File(parentDir, "raw_$oldBase.png")
            val oldScreen = File(parentDir, "screen_$oldBase.png")
            val oldMeta = File(parentDir, "${maskFile.nameWithoutExtension}.json")

            maskFile.renameTo(targetMask)
            if (oldRaw.exists()) oldRaw.renameTo(targetRaw)
            if (oldScreen.exists()) oldScreen.renameTo(targetScreen)
            if (oldMeta.exists()) oldMeta.renameTo(targetMeta)

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
