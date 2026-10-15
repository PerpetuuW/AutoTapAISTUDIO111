package com.example.autotap.infrastructure.orchestrator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import com.example.autotap.core.logger.AppLogger

/**
 * Монитор остановки выполнения макроса/графа при погашении экрана (ACTION_SCREEN_OFF).
 * Полностью заменяет датчик тряски (акселерометр) и работает без вибрации.
 */
class ScreenOffStopMonitor(
    private val context: Context,
    private val onScreenTurnedOff: () -> Unit
) {
    private var isRegistered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                AppLogger.log(context, "SCREEN_OFF_MONITOR", "Экран погас (ACTION_SCREEN_OFF) — запуск аварийной остановки макроса")
                onScreenTurnedOff.invoke()
            }
        }
    }

    fun startMonitoring() {
        if (isRegistered) return
        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(screenOffReceiver, filter)
            }
            isRegistered = true
            AppLogger.log(context, "SCREEN_OFF_MONITOR", "Мониторинг погашения экрана АКТИВИРОВАН")
        } catch (e: Exception) {
            AppLogger.logError(context, "SCREEN_OFF_MONITOR_ERROR", e)
        }
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(screenOffReceiver)
            AppLogger.log(context, "SCREEN_OFF_MONITOR", "Мониторинг погашения экрана ДЕАКТИВИРОВАН")
        } catch (_: Exception) {}
        isRegistered = false
    }

    fun isScreenInteractive(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isInteractive ?: true
    }
}
