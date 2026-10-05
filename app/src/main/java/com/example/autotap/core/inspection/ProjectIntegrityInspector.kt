package com.example.autotap.core.inspection

import android.content.Context
import com.example.autotap.core.logger.AppLogger
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object ProjectIntegrityInspector {

    data class InspectionReport(
        val totalScripts: Int,
        val totalTemplates: Int,
        val totalTrashItems: Int,
        val orphanedMetaFiles: List<String>,
        val corruptedScriptFiles: List<String>,
        val duplicateScriptNames: List<String>,
        val isHealthy: Boolean
    )

    fun inspectProjectStorage(context: Context): InspectionReport {
        val filesDir = context.filesDir
        val scriptsDir = File(filesDir, "scripts").apply { mkdirs() }
        val templatesDir = File(filesDir, "templates").apply { mkdirs() }
        val trashDir = File(filesDir, "trash_templates").apply { mkdirs() }

        val scriptFiles = scriptsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") } ?: emptyList()
        val corruptedScripts = mutableListOf<String>()
        val seenScriptKeys = mutableSetOf<String>()
        val duplicateScripts = mutableListOf<String>()

        for (sf in scriptFiles) {
            val baseName = if (sf.name.endsWith(".graph.json")) {
                sf.name.removeSuffix(".graph.json").lowercase()
            } else {
                sf.nameWithoutExtension.lowercase()
            }
            val uniqueKey = if (sf.name.endsWith(".graph.json")) "${baseName}_graph" else "${baseName}_linear"
            if (!seenScriptKeys.add(uniqueKey)) {
                duplicateScripts.add(sf.name)
            }

            try {
                val content = sf.readText(Charsets.UTF_8).trim()
                if (content.startsWith("{")) {
                    val jsonObj = JSONObject(content)
                    if (!jsonObj.has("name") && !jsonObj.has("nodes")) {
                        corruptedScripts.add(sf.name)
                    }
                } else if (content.startsWith("[")) {
                    val jsonArr = JSONArray(content)
                    if (jsonArr.length() == 0) {
                        corruptedScripts.add(sf.name)
                    }
                } else {
                    corruptedScripts.add(sf.name)
                }
            } catch (t: Throwable) {
                corruptedScripts.add(sf.name)
                AppLogger.logError(context, "INSPECTOR", t)
            }
        }

        val allMaskFiles = templatesDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("mask_") && it.name.endsWith(".png") }
            .toList()

        val allMetaFiles = templatesDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("mask_") && it.name.endsWith(".json") }
            .toList()

        val orphanedMeta = mutableListOf<String>()
        for (meta in allMetaFiles) {
            val maskPngName = "${meta.nameWithoutExtension}.png"
            val expectedPng = File(meta.parentFile, maskPngName)
            if (!expectedPng.exists()) {
                orphanedMeta.add(meta.absolutePath)
            }
        }

        val trashItems = trashDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("mask_") && it.name.endsWith(".png") }
            .toList()

        val healthy = corruptedScripts.isEmpty() && orphanedMeta.isEmpty() && duplicateScripts.isEmpty()
        val report = InspectionReport(
            totalScripts = scriptFiles.size,
            totalTemplates = allMaskFiles.size,
            totalTrashItems = trashItems.size,
            orphanedMetaFiles = orphanedMeta,
            corruptedScriptFiles = corruptedScripts,
            duplicateScriptNames = duplicateScripts,
            isHealthy = healthy
        )

        if (!healthy) {
            AppLogger.log(
                context,
                "INSPECTOR",
                "Обнаружены отклонения хранилища: Дубликатов=${report.duplicateScriptNames.size}, Ошибок=${report.corruptedScriptFiles.size + report.orphanedMetaFiles.size}"
            )
        }

        if (orphanedMeta.isNotEmpty()) {
            pruneOrphanedMetadata(context)
        }

        return report
    }

    fun pruneOrphanedMetadata(context: Context): Int {
        val templatesDir = File(context.filesDir, "templates").apply { mkdirs() }
        val allMetaFiles = templatesDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("mask_") && it.name.endsWith(".json") }
            .toList()

        val allRawFiles = templatesDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("raw_") && it.name.endsWith(".png") }
            .toList()

        var pruned = 0
        for (meta in allMetaFiles) {
            val expectedPng = File(meta.parentFile, "${meta.nameWithoutExtension}.png")
            if (!expectedPng.exists() && meta.delete()) {
                pruned++
            }
        }

        for (raw in allRawFiles) {
            val expectedMask = File(raw.parentFile, "mask_" + raw.name.removePrefix("raw_"))
            if (!expectedMask.exists() && raw.delete()) {
                pruned++
            }
        }

        return pruned
    }
}
