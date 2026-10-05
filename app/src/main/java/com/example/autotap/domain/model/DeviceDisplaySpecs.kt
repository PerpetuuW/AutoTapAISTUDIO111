package com.example.autotap.domain.model

data class DeviceDisplaySpecs(
    val screenWidth: Int,
    val screenHeight: Int,
    val densityDpi: Int,
    val density: Float,
    val isLandscape: Boolean
)
