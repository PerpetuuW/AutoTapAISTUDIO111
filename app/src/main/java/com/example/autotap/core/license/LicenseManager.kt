package com.example.autotap.core.license

import android.content.Context
import java.util.Locale

object LicenseManager {
    private const val PREFS_NAME = "autotap_license_prefs"
    private const val KEY_IS_SUBSCRIBED = "is_pro_subscribed"
    private const val KEY_PASS_UNTIL = "rewarded_pass_until"
    private const val KEY_ADS_WATCHED = "ads_watched_count"
    const val ADS_REQUIRED_FOR_PASS = 5
    private const val PASS_DURATION_MS = 12 * 3600 * 1000L

    enum class Feature(val title: String) {
        AI_ENGINE("AI Нейропоиск"),
        RECORDING("Запись жестов"),
        MULTI_TEMPLATE("Мультипоиск")
    }

    // [V140.0] Доверенный аппаратный вайтлист разработчика (полный безлимитный доступ)
    private val DEVELOPER_DEVICE_IDS = setOf("vwobfqlrsc8tbyu")

    fun isDeveloperDevice(context: Context): Boolean {
        val androidId = try {
            android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)?.lowercase() ?: ""
        } catch (_: Exception) { "" }

        val buildSerial = try {
            @Suppress("DEPRECATION")
            android.os.Build.SERIAL.lowercase()
        } catch (_: Exception) { "" }

        val systemSerial = try {
            val spClass = Class.forName("android.os.SystemProperties")
            val getMethod = spClass.getMethod("get", String::class.java)
            (getMethod.invoke(null, "ro.serialno") as? String)?.lowercase() ?: ""
        } catch (_: Exception) { "" }

        return DEVELOPER_DEVICE_IDS.any { id ->
            id == androidId || id == buildSerial || id == systemSerial
        }
    }

    // [V170.0] Флаг активной сессии просмотра наградной рекламы
    @Volatile
    var isRewardAdSessionActive: Boolean = false

    fun isAdAssistantEnabled(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return p.getBoolean("pref_ad_assistant_enabled", true)
    }

    fun setAdAssistantEnabled(context: Context, enabled: Boolean) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        p.edit().putBoolean("pref_ad_assistant_enabled", enabled).apply()
    }


    fun isFeatureUnlocked(context: Context, feature: Feature): Boolean {
        if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) return true
        if (isDeveloperDevice(context)) return true

        if (isSubscribed(context)) return true
        val passUntil = getPassUntilTimestamp(context)
        if (System.currentTimeMillis() < passUntil) return true
        // В бесплатной версии доступ к созданию/поиску шаблонов открыт только во время наградной сессии рекламы
        return isRewardAdSessionActive
    }

    fun isProActive(context: Context): Boolean {
        return isDeveloperDevice(context) || isSubscribed(context) || System.currentTimeMillis() < getPassUntilTimestamp(context)
    }

    fun isSubscribed(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return p.getBoolean(KEY_IS_SUBSCRIBED, false)
    }

    fun setSubscribed(context: Context, subscribed: Boolean) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        p.edit().putBoolean(KEY_IS_SUBSCRIBED, subscribed).apply()
    }

    fun getPassUntilTimestamp(context: Context): Long {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return p.getLong(KEY_PASS_UNTIL, 0L)
    }

    fun getWatchedAdsCount(context: Context): Int {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return p.getInt(KEY_ADS_WATCHED, 0)
    }

    fun registerAdWatched(context: Context): Pair<Int, Boolean> {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var count = p.getInt(KEY_ADS_WATCHED, 0) + 1
        var activated = false
        if (count >= ADS_REQUIRED_FOR_PASS) {
            val passUntil = System.currentTimeMillis() + PASS_DURATION_MS
            p.edit()
                .putLong(KEY_PASS_UNTIL, passUntil)
                .putInt(KEY_ADS_WATCHED, 0)
                .apply()
            count = 0
            activated = true
        } else {
            p.edit().putInt(KEY_ADS_WATCHED, count).apply()
        }
        return Pair(count, activated)
    }

    fun getFormattedTimeRemaining(context: Context): String? {
        if (isDeveloperDevice(context)) return "БЕЗЛИМИТ (DEV)"
        if (isSubscribed(context)) return "ПОДПИСКА 50 ₽/МЕС (АКТИВНА)"
        val diff = getPassUntilTimestamp(context) - System.currentTimeMillis()
        if (diff <= 0L) return null
        val hours = diff / (3600 * 1000L)
        val mins = (diff % (3600 * 1000L)) / (60 * 1000L)
        return String.format(Locale.US, "%02d ч %02d мин", hours, mins)
    }
}
