package com.example.autotap.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager

object AccessibilityUtils {

    fun isServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false

        try {
            val runningViaAm = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                val sInfo = it.resolveInfo?.serviceInfo
                sInfo != null && sInfo.packageName == context.packageName &&
                        (sInfo.name == serviceClass.name || it.id.contains(serviceClass.simpleName))
            }
            if (runningViaAm) return true
        } catch (_: Exception) {}

        val enabledServices = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
        } catch (_: Exception) { "" }

        if (enabledServices.isBlank()) return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        val myComponentName = ComponentName(context, serviceClass).flattenToString()
        val myShortComponentName = ComponentName(context, serviceClass).flattenToShortString()

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(myComponentName, ignoreCase = true) ||
                componentName.equals(myShortComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
