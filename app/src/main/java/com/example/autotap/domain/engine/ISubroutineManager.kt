package com.example.autotap.domain.engine

import com.example.autotap.domain.model.MacroAction

data class SubroutineFrame(
    val scenarioName: String,
    val actions: List<MacroAction>,
    val returnIndex: Int
)

interface ISubroutineManager {
    fun pushFrame(scenarioName: String, actions: List<MacroAction>, returnIndex: Int): Boolean
    fun popFrame(): SubroutineFrame?
    fun clear()
    val currentDepth: Int
}
