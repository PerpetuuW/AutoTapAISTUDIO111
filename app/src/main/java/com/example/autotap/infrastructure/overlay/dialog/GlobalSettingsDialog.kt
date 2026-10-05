package com.example.autotap.infrastructure.overlay.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer

@SuppressLint("SetTextI18n")
class GlobalSettingsDialog(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private var currentClickDuration: Long,
    private var currentSwipeDuration: Long,
    private var currentPathDuration: Long = 5000L,
    private val onSaved: (Long, Long, Long) -> Unit
) {
    private var dialogView: View? = null
    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    fun show() {
        if (dialogView != null) return

        val screenH = dm.heightPixels
        val cardW = dp(320).coerceAtMost((dm.widthPixels * 0.94f).toInt())
        val maxScrollH = (screenH * 0.62f).toInt().coerceAtLeast(dp(180))

        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F817112B".toColorInt(), "#F80E091A".toColorInt())
            ).apply {
                cornerRadius = dpF(18f)
                setStroke(dp(1), "#2E2250".toColorInt())
            }
            val p = dp(12)
            setPadding(p, p, p, p)
            elevation = dpF(16f)
        }
        dialogView = rootCard

        val header = LinearLayout(context).apply {
            tag = "HEADER"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }

        val tvTitle = TextView(context).apply {
            text = "ГЛОБАЛЬНЫЕ НАСТРОЙКИ"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#A78BFA".toColorInt())
        }
        header.addView(tvTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnHelp = createIconButton(VectorIconDrawer.IconType.HELP, "#21262D", "#38BDF8", dp(28)) {
            InteractiveTutorialOverlay(
                context = context,
                overlayWindowManager = overlayWindowManager,
                mode = InteractiveTutorialOverlay.TutorialMode.SETTINGS,
                hostViewProvider = { dialogView }
            ).show()
        }
        header.addView(btnHelp)
        rootCard.addView(header)

        val scrollView = object : android.widget.ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val limitedSpec = MeasureSpec.makeMeasureSpec(maxScrollH, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, limitedSpec)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            isVerticalScrollBarEnabled = true
        }

        val scrollContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tvClickLabel = TextView(context).apply {
            text = "Период клика по умолчанию (мс):"
            textSize = 9f
            includeFontPadding = false
            setTextColor("#8B949E".toColorInt())
            setPadding(0, dp(4), 0, dp(2))
        }
        scrollContent.addView(tvClickLabel)

        val etClick = EditText(context).apply {
            tag = "CLICK"
            setText(currentClickDuration.toString())
            textSize = 11f
            setTextColor(Color.WHITE)
            includeFontPadding = false
            inputType = InputType.TYPE_CLASS_NUMBER
            background = GradientDrawable().apply {
                setColor("#1B1430".toColorInt())
                cornerRadius = dpF(8f)
                setStroke(dp(1), "#36275E".toColorInt())
            }
            val p = dp(6)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                minHeight = dp(38)
            }
        }
        scrollContent.addView(etClick)

        val presetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(4))
        }
        fun applyPreset(ms: Long) { etClick.setText(ms.toString()) }
        presetRow.addView(createPresetChip("ЧЕЛОВЕК (250мс)") { applyPreset(250L) }, LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginEnd = dp(2) })
        presetRow.addView(createPresetChip("БЕЗОПАСНО (120мс)") { applyPreset(120L) }, LinearLayout.LayoutParams(0, dp(26), 1.1f).apply { marginEnd = dp(2) })
        presetRow.addView(createPresetChip("БЫСТРО (60мс)") { applyPreset(60L) }, LinearLayout.LayoutParams(0, dp(26), 1f))
        scrollContent.addView(presetRow)

        // [V170.0] Переключатель помощника закрытия рекламы
        val adAssistRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(4))
        }
        val tvAdAssist = TextView(context).apply {
            text = "Помощник закрытия рекламы:"
            textSize = 9.5f
            includeFontPadding = false
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val isAssistOn = com.example.autotap.core.license.LicenseManager.isAdAssistantEnabled(context)
        val btnToggleAssist = Button(context).apply {
            var curState = isAssistOn
            text = if (curState) "ВКЛЮЧЕН" else "ВЫКЛЮЧЕН"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            minHeight = 0; minimumHeight = 0
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            setTextColor(if (curState) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (curState) "#10B981" else "#21262D"))
                cornerRadius = dpF(4f)
            }
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(28))
            setOnClickListener {
                curState = !curState
                com.example.autotap.core.license.LicenseManager.setAdAssistantEnabled(context, curState)
                text = if (curState) "ВКЛЮЧЕН" else "ВЫКЛЮЧЕН"
                setTextColor(if (curState) Color.BLACK else Color.WHITE)
                (background as? GradientDrawable)?.setColor(Color.parseColor(if (curState) "#10B981" else "#21262D"))
            }
        }
        adAssistRow.addView(tvAdAssist)
        adAssistRow.addView(btnToggleAssist)
        scrollContent.addView(adAssistRow)

        val prefs = context.getSharedPreferences("autotap_prefs", Context.MODE_PRIVATE)
        val autoGraphRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(4))
        }
        val tvAutoGraph = TextView(context).apply {
            text = "АВТО-ПРЕДЛОЖЕНИЕ ГРАФА:"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            setPadding(0, 0, dp(4), 0)
        }
        var autoGraphState = prefs.getBoolean("PREF_PROMPT_AUTO_GRAPH", true)
        val btnToggleAutoGraph = Button(context).apply {
            text = if (autoGraphState) "ВКЛЮЧЕНО" else "ВЫКЛЮЧЕНО"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            minHeight = 0; minimumHeight = 0
            gravity = Gravity.CENTER
            setTextColor(if (autoGraphState) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (autoGraphState) "#10B981" else "#21262D"))
                cornerRadius = dpF(4f)
            }
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(28))
            setOnClickListener {
                autoGraphState = !autoGraphState
                prefs.edit().putBoolean("PREF_PROMPT_AUTO_GRAPH", autoGraphState).apply()
                text = if (autoGraphState) "ВКЛЮЧЕНО" else "ВЫКЛЮЧЕНО"
                setTextColor(if (autoGraphState) Color.BLACK else Color.WHITE)
                (background as? GradientDrawable)?.setColor(Color.parseColor(if (autoGraphState) "#10B981" else "#21262D"))
            }
        }
        autoGraphRow.addView(tvAutoGraph, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        autoGraphRow.addView(btnToggleAutoGraph)
        scrollContent.addView(autoGraphRow)

        val tvSwipeLabel = TextView(context).apply {
            text = "Длительность свайпа по умолчанию (мс):"
            textSize = 9f
            includeFontPadding = false
            setTextColor("#8B949E".toColorInt())
            setPadding(0, dp(6), 0, dp(2))
        }
        scrollContent.addView(tvSwipeLabel)

        val etSwipe = EditText(context).apply {
            tag = "SWIPE"
            setText(currentSwipeDuration.toString())
            textSize = 11f
            setTextColor(Color.WHITE)
            includeFontPadding = false
            inputType = InputType.TYPE_CLASS_NUMBER
            background = GradientDrawable().apply {
                setColor("#161B22".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            val p = dp(6)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                minHeight = dp(38)
            }
        }
        scrollContent.addView(etSwipe)

        val tvPathLabel = TextView(context).apply {
            text = "Длительность пути по умолчанию (мс):"
            textSize = 9f
            includeFontPadding = false
            setTextColor("#8B949E".toColorInt())
            setPadding(0, dp(6), 0, dp(2))
        }
        scrollContent.addView(tvPathLabel)

        val etPath = EditText(context).apply {
            tag = "PATH"
            setText(currentPathDuration.toString())
            textSize = 11f
            setTextColor(Color.WHITE)
            includeFontPadding = false
            inputType = InputType.TYPE_CLASS_NUMBER
            background = GradientDrawable().apply {
                setColor("#161B22".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            val p = dp(6)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                minHeight = dp(38)
            }
        }
        scrollContent.addView(etPath)

        val pathPresetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(4))
        }
        val applyPathPreset = { ms: Long -> etPath.setText(ms.toString()) }
        pathPresetRow.addView(createPresetChip("1с") { applyPathPreset(1000L) }, LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginEnd = dp(2) })
        pathPresetRow.addView(createPresetChip("3с") { applyPathPreset(3000L) }, LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginEnd = dp(2) })
        pathPresetRow.addView(createPresetChip("5с") { applyPathPreset(5000L) }, LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginEnd = dp(2) })
        pathPresetRow.addView(createPresetChip("10с") { applyPathPreset(10000L) }, LinearLayout.LayoutParams(0, dp(26), 1f))
        scrollContent.addView(pathPresetRow)

        scrollView.addView(scrollContent)
        rootCard.addView(scrollView)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        val btnCancel = createActionIconBtn(VectorIconDrawer.IconType.CLOSE, "#1B1430", "#FDA4AF") {
            dismiss()
        }
        btnRow.addView(btnCancel, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4) })

        val btnSave = createActionIconBtn(VectorIconDrawer.IconType.CHECK, "#6D28D9", "#A78BFA").apply {
            tag = "SAVE"
        }
        btnSave.setOnClickListener {
            val click = etClick.text.toString().trim().replace(',', '.').toDoubleOrNull()?.toLong()?.coerceIn(10L, 5000L) ?: 120L
            val swipe = etSwipe.text.toString().trim().replace(',', '.').toDoubleOrNull()?.toLong()?.coerceIn(50L, 10000L) ?: 300L
            val path = etPath.text.toString().trim().replace(',', '.').toDoubleOrNull()?.toLong()?.coerceIn(100L, 30000L) ?: 5000L
            onSaved(click, swipe, path)
            dismiss()
            Toast.makeText(context, "Настройки сохранены!", Toast.LENGTH_SHORT).show()
        }
        btnRow.addView(btnSave, LinearLayout.LayoutParams(0, dp(38), 1.5f))
        rootCard.addView(btnRow)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val btnRestricted = Button(context).apply {
                text = "РАЗРЕШИТЬ ОГРАНИЧЕННЫЕ НАСТРОЙКИ (ANDROID 13+)"
                textSize = 8f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                minHeight = 0
                minimumHeight = 0
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor("#D97706".toColorInt())
                    cornerRadius = dpF(6f)
                }
                setPadding(dp(6), dp(4), dp(6), dp(4))
                setOnClickListener {
                    Toast.makeText(context, "Нажмите ⋮ (три точки) вверху справа -> Разрешить ограниченные настройки", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    dismiss()
                }
            }
            rootCard.addView(btnRestricted, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)).apply {
                topMargin = dp(6)
            })
        }

        val params = overlayWindowManager.createDialogLayoutParams(
            width = cardW,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.CENTER
        ).apply {
            dimAmount = 0.55f
        }
        overlayWindowManager.addViewSafe(rootCard, params)
    }

    private fun createPresetChip(label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            minHeight = 0; minimumHeight = 0
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(2), dp(4), dp(2))
            setTextColor("#C4B5FD".toColorInt())
            background = GradientDrawable().apply {
                setColor("#1F1738".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#3A2963".toColorInt())
            }
            setOnClickListener { onClick() }
        }
    }

    fun dismiss() {
        dialogView?.let {
            overlayWindowManager.removeViewSafe(it)
            dialogView = null
        }
    }

    private fun createIconButton(type: VectorIconDrawer.IconType, bgHex: String, iconHex: String, sizePx: Int, onClick: () -> Unit): View {
        return object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(6f), dpF(6f), width - dpF(6f), height - dpF(6f))
                VectorIconDrawer.drawIcon(canvas, type, bounds, iconHex.toColorInt(), dpF(2f))
            }
        }.apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(bgHex.toColorInt(), "#161B22".toColorInt())
            ).apply {
                cornerRadius = dpF(6f)
                setStroke(dp(1), iconHex.toColorInt())
            }
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            setOnClickListener { onClick() }
        }
    }

    private fun createActionIconBtn(type: VectorIconDrawer.IconType, bgHex: String, iconHex: String, onClick: () -> Unit = {}): View {
        return object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(8f), dpF(8f), width - dpF(8f), height - dpF(8f))
                VectorIconDrawer.drawIcon(canvas, type, bounds, iconHex.toColorInt(), dpF(2.2f))
            }
        }.apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(bgHex.toColorInt(), "#161B22".toColorInt())
            ).apply {
                cornerRadius = dpF(8f)
                setStroke(dp(1), iconHex.toColorInt())
            }
            elevation = dpF(4f)
            setOnClickListener { onClick() }
        }
    }
}
