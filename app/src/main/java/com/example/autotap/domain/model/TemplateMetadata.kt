package com.example.autotap.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject

@Serializable
data class RoiZone(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

@Serializable
data class TemplateMetadata(
    val similarityPercent: Int = 80,
    val shapeExpansion: Int = 45,
    val paddingOffsetPx: Int = 2,
    val isContourMode: Boolean = true,
    val isShapeOnlyMode: Boolean = false,
    val isCircleShape: Boolean = false,
    val colorDeltaEMode: Boolean = true,
    val isSmartColorMode: Boolean = true,
    val optimalDownscaleFactor: Int = 1,
    val downscaleFactor: Int = 1,
    val useCustomClickOffset: Boolean = false,
    val clickOffsetX: Double = 0.0,
    val clickOffsetY: Double = 0.0,
    val calibratedX: Int? = null,
    val calibratedY: Int? = null,
    val optimalGridStep: Int = 0,
    val cropLeft: Int? = null,
    val cropTop: Int? = null,
    val cropWidth: Int? = null,
    val cropHeight: Int? = null,
    val cropX: Int? = null,
    val cropY: Int? = null,
    val cropSize: Int? = null,
    val updatedAt: Long? = null,
    val createdAt: Long? = null,
    val coVerificationTemplates: List<String> = emptyList(),
    val roiZones: List<RoiZone> = emptyList(),
    val isMultiScaleMode: Boolean = false,
    val isNeuralEngine: Boolean = false
) {
    fun toJsonString(): String {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        return json.encodeToString(serializer(), this)
    }

    companion object {
        fun fromJsonString(jsonStr: String): TemplateMetadata? {
            return try {
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString(serializer(), jsonStr)
            } catch (e: Exception) {
                // Fallback for org.json legacy
                try {
                    val orgJson = JSONObject(jsonStr)
                    TemplateMetadata(
                        similarityPercent = orgJson.optInt("similarityPercent", orgJson.optInt("similarity", 80)),
                        shapeExpansion = orgJson.optInt("shapeExpansion", 45),
                        paddingOffsetPx = orgJson.optInt("paddingOffsetPx", 2),
                        isContourMode = orgJson.optBoolean("isContourMode", true),
                        isShapeOnlyMode = orgJson.optBoolean("isShapeOnlyMode", false),
                        isCircleShape = orgJson.optBoolean("isCircleShape", false),
                        colorDeltaEMode = orgJson.optBoolean("colorDeltaEMode", true),
                        isSmartColorMode = orgJson.optBoolean("isSmartColorMode", true),
                        optimalDownscaleFactor = orgJson.optInt("optimalDownscaleFactor", 1),
                        downscaleFactor = orgJson.optInt("downscaleFactor", 1),
                        useCustomClickOffset = orgJson.optBoolean("useCustomClickOffset", false),
                        clickOffsetX = orgJson.optDouble("clickOffsetX", 0.0),
                        clickOffsetY = orgJson.optDouble("clickOffsetY", 0.0),
                        calibratedX = if (orgJson.has("calibratedX")) orgJson.getInt("calibratedX") else null,
                        calibratedY = if (orgJson.has("calibratedY")) orgJson.getInt("calibratedY") else null,
                        optimalGridStep = orgJson.optInt("optimalGridStep", 0),
                        cropLeft = if (orgJson.has("cropLeft")) orgJson.getInt("cropLeft") else null,
                        cropTop = if (orgJson.has("cropTop")) orgJson.getInt("cropTop") else null,
                        cropWidth = if (orgJson.has("cropWidth")) orgJson.getInt("cropWidth") else null,
                        cropHeight = if (orgJson.has("cropHeight")) orgJson.getInt("cropHeight") else null,
                        cropX = if (orgJson.has("cropX")) orgJson.getInt("cropX") else null,
                        cropY = if (orgJson.has("cropY")) orgJson.getInt("cropY") else null,
                        cropSize = if (orgJson.has("cropSize")) orgJson.getInt("cropSize") else null,
                        updatedAt = if (orgJson.has("updatedAt")) orgJson.getLong("updatedAt") else null,
                        createdAt = if (orgJson.has("createdAt")) orgJson.getLong("createdAt") else null,
                        coVerificationTemplates = emptyList(), // Can implement if needed
                        roiZones = emptyList(), // Can implement if needed
                        isMultiScaleMode = orgJson.optBoolean("isMultiScaleMode", false),
                        isNeuralEngine = orgJson.optBoolean("isNeuralEngine", false)
                    )
                } catch (e2: Exception) {
                    null
                }
            }
        }

        fun toMap(meta: TemplateMetadata): Map<String, Any> {
            val map = mutableMapOf<String, Any>()
            map["similarityPercent"] = meta.similarityPercent
            map["shapeExpansion"] = meta.shapeExpansion
            map["paddingOffsetPx"] = meta.paddingOffsetPx
            map["isContourMode"] = meta.isContourMode
            map["isShapeOnlyMode"] = meta.isShapeOnlyMode
            map["isCircleShape"] = meta.isCircleShape
            map["colorDeltaEMode"] = meta.colorDeltaEMode
            map["isSmartColorMode"] = meta.isSmartColorMode
            map["optimalDownscaleFactor"] = meta.optimalDownscaleFactor
            map["downscaleFactor"] = meta.downscaleFactor
            map["useCustomClickOffset"] = meta.useCustomClickOffset
            map["clickOffsetX"] = meta.clickOffsetX
            map["clickOffsetY"] = meta.clickOffsetY
            meta.calibratedX?.let { map["calibratedX"] = it }
            meta.calibratedY?.let { map["calibratedY"] = it }
            map["optimalGridStep"] = meta.optimalGridStep
            meta.cropLeft?.let { map["cropLeft"] = it }
            meta.cropTop?.let { map["cropTop"] = it }
            meta.cropWidth?.let { map["cropWidth"] = it }
            meta.cropHeight?.let { map["cropHeight"] = it }
            meta.cropX?.let { map["cropX"] = it }
            meta.cropY?.let { map["cropY"] = it }
            meta.cropSize?.let { map["cropSize"] = it }
            meta.updatedAt?.let { map["updatedAt"] = it }
            meta.createdAt?.let { map["createdAt"] = it }
            map["coVerificationTemplates"] = meta.coVerificationTemplates
            map["isMultiScaleMode"] = meta.isMultiScaleMode
            map["isNeuralEngine"] = meta.isNeuralEngine
            return map
        }
    }
}
