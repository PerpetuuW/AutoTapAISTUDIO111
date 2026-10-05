package com.example.autotap.domain.model

data class MacroScenario(
    val name: String,
    val version: Int = 1,
    val deviceSpecs: DeviceDisplaySpecs,
    val actions: List<MacroAction> = emptyList(),
    val globalClickDurationMs: Long = 120L,
    val globalSwipeDurationMs: Long = 300L,
    val createdAt: Long = System.currentTimeMillis()
)
