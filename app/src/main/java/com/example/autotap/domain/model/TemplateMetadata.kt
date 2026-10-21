@file:OptIn(
    kotlinx.serialization.InternalSerializationApi::class,
    kotlinx.serialization.ExperimentalSerializationApi::class
)

package com.example.autotap.domain.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Keep
@Serializable
data class RoiZone(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

@Keep
@Serializable
data class TemplateMetadata(
    val similarityPercent: Int = com.example.autotap.core.config.AppConfig.Vision.DEFAULT_SIMILARITY_PERCENT,
    val shapeExpansion: Int = com.example.autotap.core.config.AppConfig.Vision.DEFAULT_SHAPE_EXPANSION_PX,
    val paddingOffsetPx: Int = com.example.autotap.core.config.AppConfig.Vision.DEFAULT_PADDING_OFFSET_PX,
    val isContourMode: Boolean = true,
    val isShapeOnlyMode: Boolean = false,
    val isCircleShape: Boolean = false,
    val colorDeltaEMode: Boolean = true,
    val isSmartColorMode: Boolean = true,
    val isStrictRoiOnly: Boolean = false,
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
    val tightCropX: Int = 0,
    val tightCropY: Int = 0,
    val tightCropW: Int = 0,
    val tightCropH: Int = 0,
    val cropX: Int? = null,
    val cropY: Int? = null,
    val cropSize: Int? = null,
    val updatedAt: Long? = null,
    val createdAt: Long? = null,
    val coVerificationTemplates: List<String> = emptyList(),
    val roiZones: List<RoiZone> = emptyList(),
    val additionalSearchLocations: List<Point2D> = emptyList(),
    val isMultiScaleMode: Boolean = false,
    val isNeuralEngine: Boolean = false
) {
    fun toJsonString(): String {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        return json.encodeToString(this)
    }

    companion object {
        fun fromJsonString(jsonStr: String): TemplateMetadata? {
            return try {
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString<TemplateMetadata>(jsonStr)
            } catch (_: Exception) {
                null
            }
        }
    }
}
