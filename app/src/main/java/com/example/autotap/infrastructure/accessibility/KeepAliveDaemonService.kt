package com.example.autotap.infrastructure.accessibility

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.autotap.R
import com.example.autotap.core.logger.AppLogger

class KeepAliveDaemonService : Service() {

    companion object {
        private const val CHANNEL_ID = "autotap_daemon_channel"
        private const val NOTIF_ID = 9902
    }

    override fun onCreate() {
        super.onCreate()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "AutoTap Core System",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Поддерживает работу службы спец. возможностей в фоне"
                    setShowBadge(false)
                }
                getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            }
        } catch (e: Throwable) {
            AppLogger.logError(this, "DAEMON", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AutoTap активен")
                .setContentText("Служба автоматизации работает в фоне")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val hasSpecialUsePerm = checkSelfPermission("android.permission.FOREGROUND_SERVICE_SPECIAL_USE") ==
                        PackageManager.PERMISSION_GRANTED
                if (hasSpecialUsePerm) {
                    startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    AppLogger.log(this, "DAEMON", "Запуск FGS без специального типа из-за отсутствия FOREGROUND_SERVICE_SPECIAL_USE")
                    startForeground(NOTIF_ID, notification)
                }
            } else {
                startForeground(NOTIF_ID, notification)
            }
            return START_STICKY
        } catch (se: SecurityException) {
            AppLogger.logError(this, "DAEMON_SECURITY", se)
            stopSelf()
            return START_NOT_STICKY
        } catch (t: Throwable) {
            AppLogger.logError(this, "DAEMON_CRASH", t)
            stopSelf()
            return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
