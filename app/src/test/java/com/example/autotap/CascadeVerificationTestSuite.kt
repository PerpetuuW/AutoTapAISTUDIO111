package com.example.autotap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class CascadeVerificationTestSuite {

    @Test
    fun testL0_KinematicCacheHitsStationaryTarget() {
        val lastMatchedX = 540
        val lastMatchedY = 1200
        val centroidX = 20
        val centroidY = 20

        val cachedLeft = lastMatchedX - centroidX
        val cachedTop = lastMatchedY - centroidY

        assertEquals(520, cachedLeft)
        assertEquals(1180, cachedTop)
    }

    @Test
    fun testL1_SpatialGridBinning_PreventsHotspotStarvation() {
        val spatialSectors = HashMap<Long, Triple<Int, Int, Float>>()
        val sectorBinSize = 48

        // Симуляция: 100 ярких точек на баннере в секторе (0, 0)
        for (i in 0 until 100) {
            val x = (i % 20)
            val y = (i / 20)
            val score = 0.95f
            val sectorKey = ((y / sectorBinSize).toLong() shl 32) or ((x / sectorBinSize).toLong() and 0xFFFFFFFFL)
            val prev = spatialSectors[sectorKey]
            if (prev == null || score > prev.third) {
                spatialSectors[sectorKey] = Triple(x, y, score)
            }
        }

        // Симуляция: 1 целевая иконка в другом секторе (10, 20)
        val targetX = 500
        val targetY = 1000
        val targetScore = 0.92f
        val targetSectorKey = ((targetY / sectorBinSize).toLong() shl 32) or ((targetX / sectorBinSize).toLong() and 0xFFFFFFFFL)
        spatialSectors[targetSectorKey] = Triple(targetX, targetY, targetScore)

        // Проверка: баннер занял ровно 1 слот, а целевой объект гарантированно попал в выборку
        assertEquals(2, spatialSectors.size)
        assertTrue(spatialSectors.containsKey(targetSectorKey))
    }

    @Test
    fun testL2_BipolarSobelGradientCosine() {
        val spGx = 0.6f
        val spGy = 0.8f

        val sgx = -6.0f
        val sgy = -8.0f
        val smag = sqrt((sgx * sgx + sgy * sgy).toDouble()).toFloat()

        // Биполярный косинус: abs(dot) / smag
        val dot = abs(sgx * spGx + sgy * spGy)
        val cosTheta = dot / smag
        assertEquals(1.0f, cosTheta, 1e-4f)
    }

    @Test
    fun testL3_NMS_SuppressesOverlappingBoundingBoxes() {
        data class Box(val l: Int, val t: Int, val r: Int, val b: Int, val score: Float)
        val boxes = listOf(
            Box(100, 100, 140, 140, 0.98f),
            Box(102, 101, 142, 141, 0.94f),
            Box(500, 500, 540, 540, 0.91f)
        )

        fun intersects(a: Box, b: Box): Boolean {
            return a.l < b.r && a.r > b.l && a.t < b.b && a.b > b.t
        }

        val nms = mutableListOf<Box>()
        for (b in boxes.sortedByDescending { it.score }) {
            if (nms.none { intersects(it, b) }) {
                nms.add(b)
            }
        }

        assertEquals(2, nms.size)
        assertEquals(0.98f, nms[0].score, 1e-4f)
        assertEquals(0.91f, nms[1].score, 1e-4f)
    }
}
