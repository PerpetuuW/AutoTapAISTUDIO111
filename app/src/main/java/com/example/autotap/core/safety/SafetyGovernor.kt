package com.example.autotap.core.safety

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class SafetyGovernor(private val context: Context) {

    enum class ThermalZone {
        OPTIMAL,     // < 38.5°C: полная скорость (60 FPS, минимальный опрос)
        ELEVATED,    // 38.5°C .. 41.0°C: мягкий троттлинг (+100мс к опросу)
        THROTTLED,   // 41.0°C .. 43.5°C: энергосбережение (+350мс к опросу)
        CRITICAL     // > 43.5°C или заряд < 10%: безопасная пауза до остывания
    }

    data class ThermalTelemetry(
        val zone: ThermalZone,
        val tempCelsius: Float,
        val batteryPct: Int,
        val isCharging: Boolean,
        val suggestedCoolingDelayMs: Long
    )

    private var lastTelemetryTime = 0L
    private var cachedTelemetry = ThermalTelemetry(ThermalZone.OPTIMAL, 36.0f, 100, false, 0L)
    private val telemetryLock = Any()

    fun getTelemetry(): ThermalTelemetry {
        val now = SystemClock.elapsedRealtime()
        synchronized(telemetryLock) {
            if (now - lastTelemetryTime < 1500L) {
                return cachedTelemetry
            }
            try {
                val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val bStatus = context.registerReceiver(null, iFilter)
                if (bStatus != null) {
                    val level = bStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = bStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100

                    val tempDeci = bStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                    val tempCelsius = tempDeci / 10f

                    val status = bStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    val zone = when {
                        tempCelsius > 43.5f || batteryPct < 10 -> ThermalZone.CRITICAL
                        tempCelsius > 41.0f -> ThermalZone.THROTTLED
                        tempCelsius > 38.5f -> ThermalZone.ELEVATED
                        else -> ThermalZone.OPTIMAL
                    }

                    val coolingDelay = when (zone) {
                        ThermalZone.OPTIMAL -> 0L
                        ThermalZone.ELEVATED -> 120L
                        ThermalZone.THROTTLED -> 350L
                        ThermalZone.CRITICAL -> 2000L
                    }

                    cachedTelemetry = ThermalTelemetry(zone, tempCelsius, batteryPct, isCharging, coolingDelay)
                    lastTelemetryTime = now
                    return cachedTelemetry
                }
            } catch (_: Exception) {}
            return cachedTelemetry
        }
    }

    fun checkThermalAndBattery(): Boolean {
        val telem = getTelemetry()
        return telem.zone != ThermalZone.CRITICAL
    }

    fun calculateJitter(radius: Int): Long {
        return if (radius > 0) Random.nextLong(-12L, 14L) else 0L
    }

    fun computeGaussianOffset(radius: Int): Pair<Float, Float> {
        if (radius <= 0) return Pair(0f, 0f)
        val u1 = Random.nextDouble().coerceAtLeast(1e-7)
        val u2 = Random.nextDouble()
        val mag = sqrt(-2.0 * ln(u1)) * (radius / 2.6)
        val angle = 2.0 * Math.PI * u2
        val gx = (mag * cos(angle)).toFloat().coerceIn(-radius.toFloat(), radius.toFloat())
        val gy = (mag * sin(angle)).toFloat().coerceIn(-radius.toFloat(), radius.toFloat())
        return Pair(gx, gy)
    }
}
