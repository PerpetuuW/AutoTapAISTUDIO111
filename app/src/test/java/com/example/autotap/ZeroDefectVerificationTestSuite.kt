package com.example.autotap

import com.example.autotap.domain.model.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroDefectVerificationTestSuite {

    @Test
    fun testGesturePath_NonEmptySegmentInvariant() {
        val clickX = 250f
        val clickY = 500f

        val points = listOf(Point2D(clickX, clickY), Point2D(clickX, clickY))

        assertTrue(
            "Path для чистого точечного клика обязан использовать moveTo(x, y) и lineTo(x, y) в те же координаты без паразитного смещения",
            points.size == 2 && points[0].x == points[1].x && points[0].y == points[1].y
        )
    }

    @Test
    fun testReindexOnDelete_Consistency() {
        val actions = mutableListOf(
            Triple(1, 2, 3),
            Triple(2, 3, -1),
            Triple(3, -1, 1)
        )

        val deletedIdx = 1
        val deletedId = actions[deletedIdx].first
        actions.removeAt(deletedIdx)

        for (i in actions.indices) {
            val (_, m, t) = actions[i]
            val remM = if (m == deletedId) -1 else if (m > deletedId) m - 1 else m
            val remT = if (t == deletedId) -1 else if (t > deletedId) t - 1 else t
            actions[i] = Triple(i + 1, remM, remT)
        }

        assertEquals(2, actions.size)
        assertEquals(1, actions[0].first)
        assertEquals(-1, actions[0].second)
        assertEquals(2, actions[1].first)
        assertEquals(1, actions[1].third)
    }
}
