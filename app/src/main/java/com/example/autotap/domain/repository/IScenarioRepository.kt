package com.example.autotap.domain.repository

import com.example.autotap.domain.model.MacroScenario
import com.example.autotap.domain.model.graph.GraphMacroScenario

interface IScenarioRepository {
    fun saveScenario(scenario: MacroScenario): Boolean
    fun loadScenario(name: String): MacroScenario?
    fun saveGraphScenario(scenario: GraphMacroScenario): Boolean
    fun loadGraphScenario(name: String): GraphMacroScenario?
    fun hasGraphScenario(name: String): Boolean
    fun listScenarios(): List<String>
    fun deleteScenario(name: String): Boolean
    fun duplicateScenario(name: String): Boolean
    fun normalizeScenarioName(rawName: String): String
}
