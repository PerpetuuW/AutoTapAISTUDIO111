package com.example.autotap.domain.model

enum class TemplateMorphology {
    MICRO,            // <= 900 px^2 (Крестики, стрелки, индикаторы)
    SMALL,            // <= 3600 px^2 (Кнопки интерфейса, переключатели)
    MEDIUM,           // <= 16000 px^2 (Стандартные карточки и диалоги)
    LARGE,            // <= 45000 px^2 (Крупные панели)
    HUGE,             // > 45000 px^2 (Фоновые экраны)
    WIDE,             // Текстовые плашки (соотношение сторон >= 2.6)
    THIN_HORIZONTAL,  // H in 1..16, W >= 20 (Полосы HP / прогресс-бары)
    THIN_VERTICAL     // W in 1..16, H >= 20 (Вертикальные шкалы)
}

enum class TemplateArchetype {
    MONOCHROME_SILHOUETTE, // Крестики, стрелки, плюсики (форма)
    TEXT_BANNER,           // Кнопки "Вступить", "Продолжить", "OK" (текстовый гибрид)
    CIRCULAR_BADGE,        // Радары, энергия, круглые медали (круговой контур)
    MEDIA_CARD             // Видео-карточки, кнопки X2 (прямоугольный гибрид)
}

data class TemplateAnalysisResult(
    val morphology: TemplateMorphology,
    val archetype: TemplateArchetype,
    val suggestedScanStep: Int,
    val suggestedSobelWeight: Float,
    val isSubpixelRecommended: Boolean,
    val isShapeOnlyRecommended: Boolean = false
)
