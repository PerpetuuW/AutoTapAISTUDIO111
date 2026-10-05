package com.example.autotap.core.math

import android.graphics.Rect
import com.example.autotap.domain.model.DeviceDisplaySpecs
import com.example.autotap.domain.model.Point2D
import kotlin.math.min

object CoordinateNormalizer {

    data class ScaleTransform(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float
    )

    fun calculateTransform(src: DeviceDisplaySpecs, dst: DeviceDisplaySpecs): ScaleTransform {
        var srcW = src.screenWidth
        var srcH = src.screenHeight

        if (src.isLandscape != dst.isLandscape) {
            val temp = srcW
            srcW = srcH
            srcH = temp
        }

        if (srcW <= 0 || srcH <= 0 || dst.screenWidth <= 0 || dst.screenHeight <= 0) {
            return ScaleTransform(1.0f, 0f, 0f)
        }

        val sX = dst.screenWidth.toFloat() / srcW.toFloat()
        val sY = dst.screenHeight.toFloat() / srcH.toFloat()
        val uniformScale = min(sX, sY)
        val offsetX = (dst.screenWidth - srcW * uniformScale) / 2f
        val offsetY = (dst.screenHeight - srcH * uniformScale) / 2f

        return ScaleTransform(uniformScale, offsetX, offsetY)
    }

    fun mapPoint(point: Point2D, transform: ScaleTransform, boundsW: Int, boundsH: Int): Point2D {
        val mappedX = (point.x * transform.scale + transform.offsetX).coerceIn(0f, boundsW.toFloat())
        val mappedY = (point.y * transform.scale + transform.offsetY).coerceIn(0f, boundsH.toFloat())
        return Point2D(mappedX, mappedY)
    }

    fun mapRect(left: Int, top: Int, right: Int, bottom: Int, transform: ScaleTransform, boundsW: Int, boundsH: Int): IntArray {
        val p1 = mapPoint(Point2D(left.toFloat(), top.toFloat()), transform, boundsW, boundsH)
        val p2 = mapPoint(Point2D(right.toFloat(), bottom.toFloat()), transform, boundsW, boundsH)
        val l = minOf(p1.x, p2.x).toInt()
        val t = minOf(p1.y, p2.y).toInt()
        val r = maxOf(p1.x, p2.x).toInt().coerceAtLeast(l + 1)
        val b = maxOf(p1.y, p2.y).toInt().coerceAtLeast(t + 1)
        return intArrayOf(l, t, r, b)
    }

    fun mapRect(rect: Rect, transform: ScaleTransform, boundsW: Int, boundsH: Int): Rect {
        val arr = mapRect(rect.left, rect.top, rect.right, rect.bottom, transform, boundsW, boundsH)
        return Rect(arr[0], arr[1], arr[2], arr[3])
    }
}
