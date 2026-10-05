package com.example.autotap.infrastructure.ocr

import kotlin.math.abs

object ExpressionEvaluator {

    private val OPERATORS = listOf("<=", ">=", "!=", "==", "<", ">")
    private val NUMBER_REGEX = Regex("""[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?[kmbKMBкКмМбБ]?""")

    fun cleanToken(raw: String): String {
        var s = raw.trim()
        var prev = ""
        while (s != prev) {
            prev = s
            s = s.removePrefix("\"").removeSuffix("\"")
                .removePrefix("'").removeSuffix("'")
                .removePrefix("{").removeSuffix("}")
                .trim()
        }
        return s
    }

    fun parseGameNumber(raw: String): Double? {
        val clean = cleanToken(raw).replace(" ", "").replace(",", "").lowercase()
        val match = NUMBER_REGEX.find(clean)?.value ?: return clean.toDoubleOrNull()
        return when {
            match.endsWith("k") || match.endsWith("к") -> {
                val numPart = match.removeSuffix("k").removeSuffix("к")
                numPart.toDoubleOrNull()?.let { it * 1_000.0 }
            }
            match.endsWith("m") || match.endsWith("м") -> {
                val numPart = match.removeSuffix("m").removeSuffix("м")
                numPart.toDoubleOrNull()?.let { it * 1_000_000.0 }
            }
            match.endsWith("b") || match.endsWith("б") -> {
                val numPart = match.removeSuffix("b").removeSuffix("б")
                numPart.toDoubleOrNull()?.let { it * 1_000_000_000.0 }
            }
            else -> match.toDoubleOrNull()
        }
    }

    fun evaluate(expression: String, variables: Map<String, String>): Boolean {
        try {
            val exp = expression.trim()
            if (exp.isEmpty()) return false
            val op = OPERATORS.firstOrNull { exp.contains(it) } ?: return false

            val parts = exp.split(op, limit = 2)
            if (parts.size < 2) return false

            val leftKey = cleanToken(parts[0])
            val rightKey = cleanToken(parts[1])

            val leftVal = variables[leftKey] ?: parts[0]
            val rightVal = variables[rightKey] ?: parts[1]

            val leftNum = parseGameNumber(leftVal)
            val rightNum = parseGameNumber(rightVal)

            return if (leftNum != null && rightNum != null) {
                when (op) {
                    "==" -> abs(leftNum - rightNum) < 1e-6
                    "!=" -> abs(leftNum - rightNum) >= 1e-6
                    "<" -> leftNum < rightNum - 1e-6
                    ">" -> leftNum > rightNum + 1e-6
                    "<=" -> leftNum <= rightNum + 1e-6
                    ">=" -> leftNum >= rightNum - 1e-6
                    else -> false
                }
            } else {
                val cleanLeft = cleanToken(leftVal)
                val cleanRight = cleanToken(rightVal)
                when (op) {
                    "==" -> cleanLeft.equals(cleanRight, ignoreCase = true)
                    "!=" -> !cleanLeft.equals(cleanRight, ignoreCase = true)
                    "<=" -> cleanLeft.compareTo(cleanRight, ignoreCase = true) <= 0
                    ">=" -> cleanLeft.compareTo(cleanRight, ignoreCase = true) >= 0
                    "<" -> cleanLeft.compareTo(cleanRight, ignoreCase = true) < 0
                    ">" -> cleanLeft.compareTo(cleanRight, ignoreCase = true) > 0
                    else -> false
                }
            }
        } catch (_: Exception) {
            return false
        }
    }
}
