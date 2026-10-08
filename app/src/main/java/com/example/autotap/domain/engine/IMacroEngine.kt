package com.example.autotap.domain.engine

import android.graphics.Bitmap
import com.example.autotap.domain.model.ExecutionState
import com.example.autotap.domain.model.MacroScenario
import com.example.autotap.domain.model.graph.GraphMacroScenario
import kotlinx.coroutines.flow.StateFlow

interface IMacroEngine {
    val executionState: StateFlow<ExecutionState>
    fun start(scenario: MacroScenario, templates: Map<String, Bitmap>, isDebug: Boolean = false)
    fun startGraph(scenario: GraphMacroScenario, templates: Map<String, Bitmap>, isDebug: Boolean = false)
    fun stop()
    fun pause()
    fun resume()
    fun stepDebugNext()
}
