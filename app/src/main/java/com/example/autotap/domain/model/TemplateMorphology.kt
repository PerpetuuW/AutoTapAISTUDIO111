package com.example.autotap.domain.model

enum class TemplateMorphology {
    MICRO,            // <= 576 px^2 (Крестики, стрелки, индикаторы)
    SMALL,            // <= 2500 px^2 (Кнопки интерфейса, переключатели)
    MEDIUM,           // <= 14400 px^2 (Стандартные карточки и диалоги)
    LARGE,            // <= 40000 px^2 (Крупные панели)
    HUGE,             // > 40000 px^2 (Фоновые экраны)
    WIDE,             // Текстовые плашки (соотношение сторон >= 2.8)
    THIN_HORIZONTAL,  // H <= 14, W >= 24 (Полосы HP / прогресс-бары)
    THIN_VERTICAL     // W <= 14, H >= 24 (Вертикальные шкалы)
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
