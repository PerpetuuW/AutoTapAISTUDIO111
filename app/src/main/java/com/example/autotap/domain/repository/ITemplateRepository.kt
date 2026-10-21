package com.example.autotap.domain.repository

import android.graphics.Bitmap
import java.io.File
import org.json.JSONObject

interface ITemplateRepository {
    fun loadAllTemplates(): List<Pair<String, Bitmap>>
    fun getTemplate(path: String): Bitmap?
    fun getTemplateMetadata(path: String): Map<String, Any>?
    fun saveTemplate(folderName: String, existingPath: String?, maskBmp: Bitmap, rawBmp: Bitmap?, metadata: JSONObject, screenBmp: Bitmap? = null, customFileName: String? = null): String
    fun moveToTrash(templatePath: String): Boolean
    fun restoreFromTrash(trashMaskFile: File): Boolean
    fun purgeExpiredTrash(maxAgeDays: Int = 7)
    fun listTrash(): List<File>
    fun renameTemplate(oldPath: String, newName: String): String?
    fun resolveTemplatePath(path: String): String = path
    fun clearCache()
}
