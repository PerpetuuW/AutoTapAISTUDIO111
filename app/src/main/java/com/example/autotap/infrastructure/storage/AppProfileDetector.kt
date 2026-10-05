package com.example.autotap.infrastructure.storage

import android.content.Context
import android.content.SharedPreferences

class AppProfileDetector(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("autotap_app_profiles", Context.MODE_PRIVATE)

    fun bindPackageToScript(packageName: String, scriptName: String) {
        val cleanPkg = packageName.trim()
        val cleanScript = scriptName.trim()
        if (cleanPkg.isEmpty()) return
        if (cleanScript.isEmpty()) {
            removeBinding(cleanPkg)
        } else {
            prefs.edit().putString(cleanPkg, cleanScript).apply()
        }
    }

    fun getScriptForPackage(packageName: String): String? {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) return null
        return prefs.getString(cleanPkg, null)
    }

    fun removeBinding(packageName: String) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) return
        prefs.edit().remove(cleanPkg).apply()
    }

    fun getAllBindings(): Map<String, String> {
        return prefs.all.mapNotNull { (k, v) ->
            if (v is String && v.isNotBlank()) k.trim() to v.trim() else null
        }.toMap()
    }
}
