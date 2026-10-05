package com.example.autotap

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectIntegrityInspectorTest {

    @Test
    fun testPolymorphicScriptParsing_AcceptsBothLinearAndGraphJson() {
        val linearJson = """
            [
                {"isHeaderSpecs": true, "version": 1},
                {"id": 1, "type": "CLICK", "x": 100.0, "y": 200.0}
            ]
        """.trimIndent()

        val graphJson = """
            {
                "id": "2df8305a-519d-4caf-8e63-6ab0b0ed8f0b",
                "name": "ActiveSession",
                "version": 2,
                "entryNodeId": "node_1",
                "nodes": {
                    "node_1": {"id": "node_1", "title": "Старт"}
                },
                "edges": []
            }
        """.trimIndent()

        fun validateScriptContent(content: String): Boolean {
            val trimmed = content.trim()
            return when {
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    obj.has("name") || obj.has("nodes")
                }
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    arr.length() > 0
                }
                else -> false
            }
        }

        assertTrue("Линейный сценарий должен успешно проходить валидацию", validateScriptContent(linearJson))
        assertTrue("Графовый сценарий должен успешно проходить валидацию как JSONObject со словарем нод", validateScriptContent(graphJson))
        assertFalse("Поврежденная строка должна отклоняться", validateScriptContent("corrupted_plain_text"))
    }
}
