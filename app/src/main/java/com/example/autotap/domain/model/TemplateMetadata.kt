package com.example.autotap.domain.model

import androidx.annotation.Keep
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
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
    @OptIn(InternalSerializationApi::class)
    fun toJsonString(): String {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        return json.encodeToString(serializer(), this)
    }

    companion object {
        @OptIn(InternalSerializationApi::class)
        fun fromJsonString(jsonStr: String): TemplateMetadata? {
            return try {
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString(serializer(), jsonStr)
            } catch (_: Exception) {
                null
            }
        }
    }
}
