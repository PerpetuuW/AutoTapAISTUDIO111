package com.example.autotap.infrastructure.vision

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

object ColorDetector {

    fun parseHexColor(hex: String): Int {
        val clean = if (hex.startsWith("#")) hex.substring(1).trim() else hex.trim()
        return when (clean.length) {
            6 -> {
                val rgb = clean.toLongOrNull(16)?.toInt() ?: 0
                (0xFF shl 24) or (rgb and 0xFFFFFF)
            }
            8 -> clean.toLongOrNull(16)?.toInt() ?: 0
            else -> 0xFF00F5D4.toInt()
        }
    }

    fun isColorMatch(
        actualPixel: Int,
        expectedColorHex: String,
        tolerance: Int,
        useDeltaE: Boolean = false
    ): Boolean {
        val expectedColor = parseHexColor(expectedColorHex)

        if (useDeltaE) {
            val deltaE = computeDeltaE(actualPixel, expectedColor)
            val maxDeltaE = if (tolerance == 0) 1e-4 else (tolerance * 0.35).coerceAtLeast(1.5)
            return deltaE <= maxDeltaE
        }

        val dr = abs(((actualPixel ushr 16) and 0xFF) - ((expectedColor ushr 16) and 0xFF))
        val dg = abs(((actualPixel ushr 8) and 0xFF) - ((expectedColor ushr 8) and 0xFF))
        val db = abs((actualPixel and 0xFF) - (expectedColor and 0xFF))
        return (dr + dg + db) <= (tolerance * 3)
    }

    fun checkColorAt(
        bitmap: Bitmap,
        targetX: Int,
        targetY: Int,
        expectedColorHex: String,
        tolerance: Int,
        useDeltaE: Boolean = false
    ): Boolean {
        if (bitmap.isRecycled || targetX !in 0 until bitmap.width || targetY !in 0 until bitmap.height) {
            return false
        }
        val pixel = bitmap.getPixel(targetX, targetY)
        return isColorMatch(pixel, expectedColorHex, tolerance, useDeltaE)
    }

    fun sRgbToLab(color: Int): DoubleArray {
        var r = ((color ushr 16) and 0xFF) / 255.0
        var g = ((color ushr 8) and 0xFF) / 255.0
        var b = (color and 0xFF) / 255.0

        r = if (r > 0.04045) ((r + 0.055) / 1.055).pow(2.4) else r / 12.92
        g = if (g > 0.04045) ((g + 0.055) / 1.055).pow(2.4) else g / 12.92
        b = if (b > 0.04045) ((b + 0.055) / 1.055).pow(2.4) else b / 12.92

        val x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047
        val y = (r * 0.2126 + g * 0.7152 + b * 0.0722) / 1.00000
        val z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883

        fun f(t: Double): Double = if (t > 0.008856) cbrt(t) else (7.787 * t) + (16.0 / 116.0)

        val fx = f(x)
        val fy = f(y)
        val fz = f(z)

        val l = (116.0 * fy) - 16.0
        val a = 500.0 * (fx - fy)
        val bVal = 200.0 * (fy - fz)
        return doubleArrayOf(l, a, bVal)
    }

    fun computeDeltaE(c1: Int, c2: Int): Double {
        val lab1 = sRgbToLab(c1)
        val lab2 = sRgbToLab(c2)
        val dL = lab1[0] - lab2[0]
        val da = lab1[1] - lab2[1]
        val db = lab1[2] - lab2[2]
        return sqrt(dL * dL + da * da + db * db)
    }
}
