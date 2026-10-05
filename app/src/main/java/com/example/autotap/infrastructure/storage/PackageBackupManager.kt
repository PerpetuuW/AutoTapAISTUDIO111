package com.example.autotap.infrastructure.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.autotap.core.logger.AppLogger
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

class PackageBackupManager(private val context: Context) {

    private fun resolveTemplateFile(rawPath: String): File? {
        if (rawPath.isBlank()) return null
        val direct = File(rawPath)
        if (direct.exists()) return direct

        val templatesDir = File(context.filesDir, "templates")
        val fileName = direct.name
        return templatesDir.walkTopDown().firstOrNull { it.isFile && it.name == fileName }
    }

    fun exportSingleScriptZip(scriptName: String): File? {
        return try {
            val scriptsDir = File(context.filesDir, "scripts")
            val linearFile = File(scriptsDir, "$scriptName.json")
            val graphFile = File(scriptsDir, "$scriptName.graph.json")

            if (!linearFile.exists() && !graphFile.exists()) return null

            val zipFile = File(context.externalCacheDir ?: context.cacheDir, "$scriptName.zip")
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                val templatesToZip = HashSet<String>()

                val allAvailableTemplates = try {
                    TemplateRepositoryImpl(context).loadAllTemplates()
                } catch (_: Exception) {
                    emptyList()
                }

                if (linearFile.exists()) {
                    zos.putNextEntry(ZipEntry("scripts/$scriptName.json"))
                    zos.write(linearFile.readBytes())
                    zos.closeEntry()

                    try {
                        val jsonArray = JSONArray(linearFile.readText())
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.optJSONObject(i) ?: continue
                            val path = obj.optString("templatePath", "")
                            resolveTemplateFile(path)?.let { templatesToZip.add(it.absolutePath) }

                            val mArr = obj.optJSONArray("multiTemplateIndices")
                            if (mArr != null) {
                                for (k in 0 until mArr.length()) {
                                    val idx = mArr.optInt(k, -1)
                                    if (idx in allAvailableTemplates.indices) {
                                        templatesToZip.add(allAvailableTemplates[idx].first)
                                    }
                                }
                            }
                            val mPaths = obj.optJSONArray("multiTemplatePaths")
                            if (mPaths != null) {
                                for (k in 0 until mPaths.length()) {
                                    val pStr = mPaths.optString(k, "")
                                    resolveTemplateFile(pStr)?.let { templatesToZip.add(it.absolutePath) }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (graphFile.exists()) {
                    zos.putNextEntry(ZipEntry("scripts/$scriptName.graph.json"))
                    zos.write(graphFile.readBytes())
                    zos.closeEntry()

                    try {
                        val rootObj = JSONObject(graphFile.readText())
                        val nodesField = rootObj.opt("nodes")
                        val trigList = mutableListOf<JSONObject>()

                        if (nodesField is JSONObject) {
                            val keys = nodesField.keys()
                            while (keys.hasNext()) {
                                val nObj = nodesField.optJSONObject(keys.next()) ?: continue
                                val tArr = nObj.optJSONArray("triggers") ?: continue
                                for (j in 0 until tArr.length()) tArr.optJSONObject(j)?.let { trigList.add(it) }
                            }
                        } else if (nodesField is JSONArray) {
                            for (i in 0 until nodesField.length()) {
                                val nObj = nodesField.optJSONObject(i) ?: continue
                                val tArr = nObj.optJSONArray("triggers") ?: continue
                                for (j in 0 until tArr.length()) tArr.optJSONObject(j)?.let { trigList.add(it) }
                            }
                        }

                        for (tObj in trigList) {
                            val tPath = tObj.optString("templatePath", "")
                            resolveTemplateFile(tPath)?.let { templatesToZip.add(it.absolutePath) }
                            val mpArr = tObj.optJSONArray("multiTemplatePaths") ?: JSONArray()
                            for (k in 0 until mpArr.length()) {
                                val pStr = mpArr.optString(k, "")
                                resolveTemplateFile(pStr)?.let { templatesToZip.add(it.absolutePath) }
                            }
                        }
                    } catch (_: Exception) {}
                }

                for (path in templatesToZip) {
                    val maskFile = File(path)
                    if (!maskFile.exists()) continue
                    val dateFolder = maskFile.parentFile?.name ?: "default"

                    zos.putNextEntry(ZipEntry("templates/$dateFolder/${maskFile.name}"))
                    zos.write(maskFile.readBytes())
                    zos.closeEntry()

                    val metaFile = File(maskFile.parentFile, "${maskFile.nameWithoutExtension}.json")
                    if (metaFile.exists()) {
                        zos.putNextEntry(ZipEntry("templates/$dateFolder/${metaFile.name}"))
                        zos.write(metaFile.readBytes())
                        zos.closeEntry()
                    }

                    val rawFile = File(maskFile.parentFile, "raw_" + maskFile.name.removePrefix("mask_"))
                    if (rawFile.exists()) {
                        zos.putNextEntry(ZipEntry("templates/$dateFolder/${rawFile.name}"))
                        zos.write(rawFile.readBytes())
                        zos.closeEntry()
                    }
                }
            }
            AppLogger.log(context, "BACKUP", "Сценарий '$scriptName' успешно экспортирован (${zipFile.length()} байт)")
            zipFile
        } catch (e: Exception) {
            AppLogger.logError(context, "BACKUP", e)
            null
        }
    }


    fun exportAllTemplatesZip(): File? {
    return try {
    val templatesDir = File(context.filesDir, "templates")
    val hasTemplates = templatesDir.exists() && templatesDir.walkTopDown().any { it.isFile && it.name.endsWith(".png") }
    if (!hasTemplates) {
    AppLogger.log(context, "BACKUP", "Экспорт отменен: шаблоны отсутствуют")
    return null
    }
    val zipFile = File(context.externalCacheDir ?: context.cacheDir, "autotap_templates_backup.zip")
    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
    zipDirectory(context.filesDir, templatesDir, zos)
    }
    if (zipFile.length() <= 22L) {
    zipFile.delete()
    return null
    }
    AppLogger.log(context, "BACKUP", "Все шаблоны успешно экспортированы (${zipFile.length()} байт)")
    zipFile
    } catch (e: Exception) {
    AppLogger.logError(context, "BACKUP", e)
    null
    }
    }

    fun importZipArchive(zipFile: File): Boolean {
        return try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                val baseDir = context.filesDir
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && (name.startsWith("scripts/") || name.startsWith("templates/"))) {
                        val outFile = File(baseDir, name)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            AppLogger.log(context, "BACKUP", "Импорт архива '${zipFile.name}' выполнен успешно")
            true
        } catch (e: Exception) {
            AppLogger.logError(context, "BACKUP", e)
            false
        }
    }


    fun exportFullBackupZip(): File? {
    return try {
    val scriptsDir = File(context.filesDir, "scripts")
    val templatesDir = File(context.filesDir, "templates")
    val hasScripts = scriptsDir.exists() && scriptsDir.walkTopDown().any { it.isFile && it.name.endsWith(".json") }
    val hasTemplates = templatesDir.exists() && templatesDir.walkTopDown().any { it.isFile && it.name.endsWith(".png") }
    if (!hasScripts && !hasTemplates) {
    AppLogger.log(context, "BACKUP", "Экспорт отменен: нет сценариев или шаблонов для создания бэкапа")
    return null
    }
    val zipFile = File(context.externalCacheDir ?: context.cacheDir, "autotap_full_backup.zip")
    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
    if (scriptsDir.exists()) zipDirectory(context.filesDir, scriptsDir, zos)
    if (templatesDir.exists()) zipDirectory(context.filesDir, templatesDir, zos)
    }
    if (zipFile.length() <= 22L) {
    zipFile.delete()
    return null
    }
    AppLogger.log(context, "BACKUP", "Полный бэкап успешно создан (${zipFile.length()} байт)")
    zipFile
    } catch (e: Exception) {
    AppLogger.logError(context, "BACKUP", e)
    null
    }
    }

    fun importPackageZip(zipStream: ZipInputStream): Boolean {
        return try {
            val canonicalFilesDir = context.filesDir.canonicalPath
            var entry = zipStream.nextEntry

            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = entry.name
                    val targetFile = if (entryName.startsWith("scripts/")) {
                        File(File(context.filesDir, "scripts"), entryName.substringAfterLast("/"))
                    } else if (entryName.startsWith("templates/")) {
                        File(File(context.filesDir, "templates"), entryName.removePrefix("templates/"))
                    } else {
                        File(context.filesDir, entryName)
                    }

                    if (targetFile.canonicalPath.startsWith(canonicalFilesDir + File.separator)) {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { out -> zipStream.copyTo(out) }
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            TemplateRepositoryImpl(context).clearCache()
            AppLogger.log(context, "BACKUP", "Импорт архива успешно завершен в $canonicalFilesDir")
            true
        } catch (e: Exception) {
            AppLogger.logError(context, "BACKUP", e)
            false
        }
    }

    fun shareZipFile(zipFile: File, subject: String) {
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
        } catch (e: Exception) {
            AppLogger.logError(context, "BACKUP", e)
            Uri.fromFile(zipFile)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun zipDirectory(root: File, folder: File, zos: ZipOutputStream) {
        val files = folder.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                zipDirectory(root, file, zos)
            } else {
                val relPath = file.absolutePath.substring(root.absolutePath.length + 1)
                zos.putNextEntry(ZipEntry(relPath))
                BufferedInputStream(FileInputStream(file)).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }
}
