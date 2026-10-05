package com.example.autotap.domain.gateway

import com.example.autotap.domain.model.Point2D

interface IGestureGateway {
    fun performClick(x: Float, y: Float, durationMs: Long): Boolean
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean
    fun performPath(points: List<Point2D>, durationMs: Long): Boolean
    fun performPinch(centerX: Float, centerY: Float, startDistance: Float, endDistance: Float, durationMs: Long): Boolean
    fun vibrateFeedback(durationMs: Long)
}
