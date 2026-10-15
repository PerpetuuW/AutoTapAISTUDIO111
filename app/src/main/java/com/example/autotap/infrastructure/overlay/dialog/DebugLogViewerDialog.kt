package com.example.autotap.infrastructure.overlay.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.autotap.core.logger.AppLogger

class DebugLogViewerDialog(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootLayout: FrameLayout? = null
    private var logTextView: TextView? = null
    
    private val availableTags = listOf("ALL", "CV_EXECUTION", "CV_PROFILER", "GESTURE", "STORAGE", "VISION", "OCR")
    private val activeTags = mutableSetOf("ALL")

    fun show() {
        if (rootLayout != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        rootLayout = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
        }

        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(32, 32, 32, 32)
        }
        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.CENTER
            setMargins(48, 120, 48, 120)
        }

        val title = TextView(context).apply {
            text = "Движок: Логирование"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        cardLayout.addView(title)

        val tagsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 24)
        }
        val tagsScroll = ScrollView(context).apply {
            addView(tagsContainer)
        }
        
        availableTags.forEach { tag ->
            val btn = Button(context).apply {
                text = tag
                setTextColor(if (activeTags.contains(tag)) Color.WHITE else Color.GRAY)
                setBackgroundColor(if (activeTags.contains(tag)) Color.parseColor("#3B82F6") else Color.parseColor("#334155"))
                setOnClickListener {
                    if (tag == "ALL") {
                        activeTags.clear()
                        activeTags.add("ALL")
                    } else {
                        activeTags.remove("ALL")
                        if (activeTags.contains(tag)) activeTags.remove(tag) else activeTags.add(tag)
                        if (activeTags.isEmpty()) activeTags.add("ALL")
                    }
                    refreshLogText()
                    for (i in 0 until tagsContainer.childCount) {
                        val c = tagsContainer.getChildAt(i) as Button
                        c.setTextColor(if (activeTags.contains(c.text.toString())) Color.WHITE else Color.GRAY)
                        c.setBackgroundColor(if (activeTags.contains(c.text.toString())) Color.parseColor("#3B82F6") else Color.parseColor("#334155"))
                    }
                }
            }
            tagsContainer.addView(btn)
        }
        cardLayout.addView(tagsScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400))

        logTextView = TextView(context).apply {
            setTextColor(Color.parseColor("#10B981"))
            textSize = 12f
            setLineSpacing(4f, 1f)
        }
        
        val logScroll = ScrollView(context).apply {
            addView(logTextView)
        }
        val scrollParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        cardLayout.addView(logScroll, scrollParams)

        val closeBtn = Button(context).apply {
            text = "ЗАКРЫТЬ"
            setBackgroundColor(Color.parseColor("#EF4444"))
            setTextColor(Color.WHITE)
            setOnClickListener { dismiss() }
        }
        cardLayout.addView(closeBtn)

        rootLayout?.addView(cardLayout, cardParams)
        windowManager.addView(rootLayout, params)

        refreshLogText()
        AppLogger.onRecentLogUpdated = {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                refreshLogText()
            }
        }
    }

    private fun refreshLogText() {
        val logs = AppLogger.getLogsByTags(activeTags.toList())
        logTextView?.text = logs.joinToString("\n\n")
    }

    fun dismiss() {
        AppLogger.onRecentLogUpdated = null
        rootLayout?.let {
            windowManager.removeView(it)
            rootLayout = null
        }
    }
}