package com.example.autotap.infrastructure.overlay.model

enum class OverlayVisibilityMode {
    FULL,            // Полная видимость всех оверлеев и меток
    TRANSPARENT,     // Полупрозрачный режим для всех оверлеев (alpha ~0.30)
    HIDDEN           // Полное скрытие всех меток, иконок, номеров и графа
}
