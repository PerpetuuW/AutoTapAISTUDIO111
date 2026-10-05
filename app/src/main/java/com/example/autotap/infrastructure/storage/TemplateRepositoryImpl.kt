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

    private val memoryCache = ConcurrentHashMap<String, Bitmap>()

    override fun loadAllTemplates(): List<Pair<String, Bitmap>> {
        val result = mutableListOf<Pair<String, Bitmap>>()
        baseTemplatesDir.walkTopDown().filter { it.isFile && it.name.startsWith("mask_") && it.name.endsWith(".png") }.forEach { file ->
            val path = file.absolutePath
            val cached = memoryCache[path]
            val bmp = if (cached != null && !cached.isRecycled) {
                cached
            } else {
                BitmapFactory.decodeFile(path)?.also { memoryCache[path] = it }
            }
            if (bmp != null && !bmp.isRecycled) {
                result.add(path to bmp)
            }
        }
        return result
    }

    override fun getTemplate(path: String): Bitmap? {
        if (path.isBlank()) return null
        val cached = memoryCache[path]
        if (cached != null && !cached.isRecycled) return cached

        val file = File(path)
        val targetFile = if (file.exists()) file else {
            baseTemplatesDir.walkTopDown().firstOrNull { it.isFile && it.name == file.name }
        }

        if (targetFile == null || !targetFile.exists()) return null
        return try {
            BitmapFactory.decodeFile(targetFile.absolutePath)?.also { memoryCache[path] = it }
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
    fun saveTemplateWithScreen(folderName: String, existingPath: String?, maskBmp: Bitmap, rawBmp: Bitmap?, screenBmp: Bitmap?, metadata: JSONObject): String {
        val targetDir = File(baseTemplatesDir, folderName).apply { mkdirs() }
        val tPath = if (existingPath.isNullOrBlank()) {
            File(targetDir, "mask_${System.currentTimeMillis()}.png").absolutePath
        } else existingPath

        val maskFile = File(tPath)
        val rawFile = File(maskFile.parentFile, "raw_" + maskFile.name.removePrefix("mask_"))
        val screenFile = File(maskFile.parentFile, "screen_" + maskFile.name.removePrefix("mask_"))
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
            memoryCache[tPath] = maskBmp.copy(Bitmap.Config.ARGB_8888, true)
        } catch (e: Exception) {
            AppLogger.logError(context, "STORAGE", e)
        }
        return tPath
    }

    override fun saveTemplate(folderName: String, existingPath: String?, maskBmp: android.graphics.Bitmap, rawBmp: android.graphics.Bitmap?, metadata: org.json.JSONObject): String {
        return saveTemplateWithScreen(folderName, existingPath, maskBmp, rawBmp, null, metadata)
    }

    fun listFolders(): List<String> {
        val dirs = baseTemplatesDir.listFiles { file -> file.isDirectory }
        val names = dirs?.map { it.name }?.toMutableList() ?: mutableListOf()
        if (!names.contains("default")) names.add(0, "default")
        return names.distinct().sorted()
    }

    override fun moveToTrash(templatePath: String): Boolean {
        return try {
            val maskFile = File(templatePath)
            if (!maskFile.exists()) return false

            memoryCache.remove(templatePath)
            val parentDir = maskFile.parentFile
            val baseName = maskFile.nameWithoutExtension

            val metaFile = File(parentDir, "$baseName.json")
            val rawFile = File(parentDir, "raw_" + maskFile.name.removePrefix("mask_"))

            val targetTrashMask = File(trashDir, maskFile.name)
            val success = maskFile.renameTo(targetTrashMask)

            if (metaFile.exists()) {
                metaFile.renameTo(File(trashDir, metaFile.name))
            }
            if (rawFile.exists()) {
                rawFile.renameTo(File(trashDir, rawFile.name))
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
            val rawFile = File(trashDir, "raw_" + trashMaskFile.name.removePrefix("mask_"))

            val targetMask = File(defaultDir, trashMaskFile.name)
            val success = trashMaskFile.renameTo(targetMask)

            if (metaFile.exists()) {
                metaFile.renameTo(File(defaultDir, metaFile.name))
            }
            if (rawFile.exists()) {
                rawFile.renameTo(File(defaultDir, rawFile.name))
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
        memoryCache.clear()
    }
}
