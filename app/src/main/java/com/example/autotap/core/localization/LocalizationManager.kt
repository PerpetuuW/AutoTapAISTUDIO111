package com.example.autotap.core.localization

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocalizationManager {

    private const val PREF_KEY = "app_lang"
    const val LANG_RU = "ru"
    const val LANG_EN = "en"

    fun getLanguage(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences("autotap_settings", Context.MODE_PRIVATE)
        return prefs.getString(PREF_KEY, LANG_RU) ?: LANG_RU
    }

    fun setLanguage(context: Context, lang: String) {
        val prefs: SharedPreferences = context.getSharedPreferences("autotap_settings", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_KEY, lang).apply()
        applyLocale(context)
    }

    fun isRussian(context: Context): Boolean = getLanguage(context) == LANG_RU

    fun toggleLanguage(context: Context): String {
        val next = if (isRussian(context)) LANG_EN else LANG_RU
        setLanguage(context, next)
        return next
    }

    @SuppressLint("AppBundleLocaleChanges")
    fun applyLocale(context: Context) {
        val lang = getLanguage(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    fun wrapContext(context: Context): Context {
        val lang = getLanguage(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }
}
