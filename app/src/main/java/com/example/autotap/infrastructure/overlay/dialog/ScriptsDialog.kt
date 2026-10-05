package com.example.autotap.infrastructure.overlay.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import com.example.autotap.domain.repository.IScenarioRepository
import com.example.autotap.infrastructure.accessibility.AutoTapAccessibilityService
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import com.example.autotap.infrastructure.storage.AppProfileDetector

@SuppressLint("SetTextI18n")
class ScriptsDialog(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val scenarioRepository: IScenarioRepository,
    private val onSaveCurrentRequested: (String) -> Unit,
    private val onLoadRequested: (String) -> Unit,
    private val onExportRequested: (String) -> Unit,
    private val onOpenGraphRequested: ((String) -> Unit)? = null
) {

    private var dialogView: View? = null
    private var listLayoutRef: LinearLayout? = null
    private val profileDetector by lazy { AppProfileDetector(context) }
    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    fun show() {
        if (dialogView != null) return

        val screenW = dm.widthPixels
        val cardW = dp(330).coerceAtMost((screenW * 0.95f).toInt())
        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8140E24".toColorInt(), "#F80A0714".toColorInt())
            ).apply {
                cornerRadius = dpF(18f)
                setStroke(dp(1), "#2E2250".toColorInt())
            }
            val p = dp(10)
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
            text = "МЕНЕДЖЕР СЦЕНАРИЕВ"
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#A78BFA".toColorInt())
        }
        header.addView(tvTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnHelp = createIconButton(VectorIconDrawer.IconType.HELP, "#21262D", "#818CF8", dp(28)) {
            InteractiveTutorialOverlay(
                context = context,
                overlayWindowManager = overlayWindowManager,
                mode = InteractiveTutorialOverlay.TutorialMode.SCRIPTS,
                hostViewProvider = { dialogView }
            ).show()
        }
        header.addView(btnHelp, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(4) })

        val btnCloseHeader = createIconButton(VectorIconDrawer.IconType.CLOSE, "#21262D", "#F04438", dp(28)) {
            dismiss()
        }
        header.addView(btnCloseHeader)
        rootCard.addView(header)

        // Верхняя панель сохранения нового сценария
        val saveRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val etName = EditText(context).apply {
            tag = "NAME"
            hint = "Имя нового сценария..."
            setHintTextColor("#64748B".toColorInt())
            textSize = 9.5f
            setTextColor(Color.WHITE)
            includeFontPadding = false
            background = GradientDrawable().apply {
                setColor("#161224".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#3E2A6E".toColorInt())
            }
            val p = dp(6)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                marginEnd = dp(4)
            }
        }
        saveRow.addView(etName)

        fun executeSave(name: String) {
            onSaveCurrentRequested(name)
            dismiss()
            Toast.makeText(context, "Сценарий '$name' сохранен", Toast.LENGTH_SHORT).show()
        }

        fun promptOverwrite(name: String, onConfirm: () -> Unit) {
            val confirmCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor("#1E1B2E".toColorInt())
                    cornerRadius = dpF(8f)
                    setStroke(dp(1), "#F59E0B".toColorInt())
                }
                val p = dp(8)
                setPadding(p, p, p, p)
            }
            val tvWarn = TextView(context).apply {
                text = "Сценарий '$name' уже существует. Перезаписать его текущими шагами?"
                textSize = 9f
                setTextColor(Color.WHITE)
            }
            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(6), 0, 0)
            }
            val btnYes = createTextButton("ПЕРЕЗАПИСАТЬ", "#451A03", "#F59E0B") {
                rootCard.removeView(confirmCard)
                onConfirm()
            }
            val btnNo = createTextButton("ОТМЕНА", "#21262D", "#94A3B8") {
                rootCard.removeView(confirmCard)
            }
            btnRow.addView(btnNo, LinearLayout.LayoutParams(dp(70), dp(28)).apply { marginEnd = dp(4) })
            btnRow.addView(btnYes, LinearLayout.LayoutParams(dp(110), dp(28)))
            confirmCard.addView(tvWarn)
            confirmCard.addView(btnRow)
            rootCard.addView(confirmCard, 2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            })
        }

        val btnSave = createTextButton("+ СОХРАНИТЬ", "#312E81", "#818CF8") {
            val name = etName.text.toString().trim()
            if (name.isNotEmpty()) {
                if (scenarioRepository.loadScenario(name) != null || scenarioRepository.hasGraphScenario(name)) {
                    promptOverwrite(name) { executeSave(name) }
                } else {
                    executeSave(name)
                }
            } else {
                Toast.makeText(context, "Введите имя сценария", Toast.LENGTH_SHORT).show()
            }
        }
        saveRow.addView(btnSave, LinearLayout.LayoutParams(dp(100), dp(34)))
        rootCard.addView(saveRow)

        val toolsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(2))
        }

        val btnExportDialog = Button(context).apply {
            text = "ЭКСПОРТ"
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#38BDF8".toColorInt())
            background = GradientDrawable().apply {
                setColor("#1A142E".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(4), dp(6), dp(4))

            setOnClickListener {
                val checkedItems = booleanArrayOf(true, true, true)
                val labels = arrayOf("Сценарии и граф (.json)", "Шаблоны (маски + цвета + мета)", "OCR словарь и метаданные поиска")

                val builder = android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Экспорт данных")
                    .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                        checkedItems[which] = isChecked
                    }
                    .setPositiveButton("Экспорт") { _, _ ->
                        val pbm = com.example.autotap.infrastructure.storage.PackageBackupManager(context)
                        val zip = pbm.exportFullBackupZip(
                            exportScripts = checkedItems[0],
                            exportTemplates = checkedItems[1],
                            exportOcr = checkedItems[2]
                        )
                        if (zip != null) {
                            pbm.shareZipFile(zip, "Экспорт AutoTap")
                        } else {
                            Toast.makeText(context, "Нет выбранных данных для экспорта", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Отмена", null)
                    .create()
                builder.window?.setType(overlayWindowManager.getOverlayType(false))
                builder.show()
            }
            }
            toolsRow.addView(btnExportDialog, LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginEnd = dp(4) })

            val btnImportZip = Button(context).apply {
            text = "ИМПОРТ (ZIP)"
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#818CF8".toColorInt())
            background = GradientDrawable().apply {
            setColor("#1A142E".toColorInt())
            cornerRadius = dpF(6f)
            setStroke(dp(1), "#3E2A6E".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
            try {
            val intent = android.content.Intent(context, com.example.autotap.presentation.main.MainActivity::class.java).apply {
            action = "com.example.autotap.ACTION_IMPORT_ZIP"
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
            dismiss()
            Toast.makeText(context, "Выберите ZIP архив сценария...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть проводник: ${e.message}", Toast.LENGTH_LONG).show()
            }
            }
            }
        toolsRow.addView(btnImportZip, LinearLayout.LayoutParams(0, dp(26), 1f))
        rootCard.addView(toolsRow)

        val screenH = dm.heightPixels
        val maxListH = (screenH * 0.45f).toInt().coerceAtLeast(dp(180))

        val scrollView = ScrollView(context).apply {
            tag = "LIST"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxListH).apply {
                val t = dp(8)
                setMargins(0, t, 0, t)
            }
            isVerticalScrollBarEnabled = true
        }

        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        listLayoutRef = listLayout

        populateScenarioList(listLayout)

        scrollView.addView(listLayout)
        rootCard.addView(scrollView)

        val params = overlayWindowManager.createDialogLayoutParams(
            width = cardW,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.CENTER
        )
        overlayWindowManager.addViewSafe(rootCard, params)
    }

    private fun populateScenarioList(listLayout: LinearLayout) {
        listLayout.removeAllViews()
        val scenarios = scenarioRepository.listScenarios()
        if (scenarios.isEmpty()) {
            val tvEmpty = TextView(context).apply {
                text = "Сохраненных сценариев нет"
                setTextColor("#64748B".toColorInt())
                textSize = 9.5f
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, dp(20))
            }
            listLayout.addView(tvEmpty)
        } else {
            for (scName in scenarios) {
                // Двухуровневая карточка: сверху полное имя, снизу текстовые кнопки
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        setColor("#161224".toColorInt())
                        cornerRadius = dpF(8f)
                        setStroke(dp(1), "#2D204E".toColorInt())
                    }
                    val p = dp(6)
                    setPadding(p, p, p, p)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(4)
                    }
                }

                // Уровень 1: Полное имя сценария (100% ширины) и бейдж шагов
                val topRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val tvScName = TextView(context).apply {
                    text = scName
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                topRow.addView(tvScName, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

                val scObj = scenarioRepository.loadScenario(scName)
                val stepCount = scObj?.actions?.size ?: 0
                val tvStepsBadge = TextView(context).apply {
                    text = "$stepCount шагов"
                    textSize = 7.5f
                    setTextColor("#94A3B8".toColorInt())
                }
                topRow.addView(tvStepsBadge)
                card.addView(topRow)

                // Уровень 2: Понятные текстовые кнопки действий
                val actionsRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, 0)
                }

                val btnLoadSteps = createTextButton("ШАГИ", "#064E3B", "#34D399") {
                    dismiss()
                    onLoadRequested(scName)
                }
                actionsRow.addView(btnLoadSteps, LinearLayout.LayoutParams(0, dp(26), 1.1f).apply { marginEnd = dp(2) })

                val btnGraph = createTextButton("ГРАФ", "#312E81", "#818CF8") {
                    dismiss()
                    onOpenGraphRequested?.invoke(scName)
                }
                actionsRow.addView(btnGraph, LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginEnd = dp(2) })

                val btnOverwrite = createTextButton("ПЕРЕЗАПИСАТЬ", "#281745", "#C084FC") {
                    showInOverlayConfirm("Перезаписать сценарий '$scName' текущими шагами с экрана?") {
                        onSaveCurrentRequested(scName)
                        Toast.makeText(context, "Сценарий '$scName' перезаписан", Toast.LENGTH_SHORT).show()
                        populateScenarioList(listLayout)
                    }
                }
                actionsRow.addView(btnOverwrite, LinearLayout.LayoutParams(0, dp(26), 1.5f).apply { marginEnd = dp(2) })

                val btnMore = createTextButton("ЕЩЕ", "#1E1B2E", "#94A3B8") {
                    showMoreActionsMenu(scName, card, listLayout)
                }
                actionsRow.addView(btnMore, LinearLayout.LayoutParams(0, dp(26), 0.8f))

                card.addView(actionsRow)
                listLayout.addView(card)
            }
        }
    }

    private fun showInOverlayConfirm(msg: String, onConfirm: () -> Unit) {
        val root = dialogView as? LinearLayout ?: return
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor("#1E1B2E".toColorInt())
                cornerRadius = dpF(8f)
                setStroke(dp(1), "#F59E0B".toColorInt())
            }
            val p = dp(8)
            setPadding(p, p, p, p)
        }
        val tv = TextView(context).apply {
            text = msg
            textSize = 9f
            setTextColor(Color.WHITE)
        }
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(6), 0, 0)
        }
        val btnYes = createTextButton("ДА", "#451A03", "#F59E0B") {
            root.removeView(card)
            onConfirm()
        }
        val btnNo = createTextButton("ОТМЕНА", "#21262D", "#94A3B8") {
            root.removeView(card)
        }
        btnRow.addView(btnNo, LinearLayout.LayoutParams(dp(65), dp(28)).apply { marginEnd = dp(4) })
        btnRow.addView(btnYes, LinearLayout.LayoutParams(dp(65), dp(28)))
        card.addView(tv)
        card.addView(btnRow)
        root.addView(card, 2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4)
        })
    }

    private fun showMoreActionsMenu(scName: String, card: LinearLayout, listLayout: LinearLayout) {
        val menuRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(2))
        }
        val btnBind = createTextButton("АВТОЗАПУСК", "#1E1B2E", "#38BDF8") {
            showAppBindingDialog(scName)
        }
        val btnExp = createTextButton("ЭКСПОРТ", "#1E1B2E", "#818CF8") {
            onExportRequested(scName)
        }
        val btnDel = createTextButton("УДАЛИТЬ", "#2E1218", "#F43F5E") {
            showInOverlayConfirm("Удалить сценарий '$scName'?") {
                scenarioRepository.deleteScenario(scName)
                populateScenarioList(listLayout)
                Toast.makeText(context, "Сценарий удален", Toast.LENGTH_SHORT).show()
            }
        }
        menuRow.addView(btnBind, LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginEnd = dp(2) })
        menuRow.addView(btnExp, LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginEnd = dp(2) })
        menuRow.addView(btnDel, LinearLayout.LayoutParams(0, dp(26), 1f))

        if (card.childCount > 2) {
            card.removeViewAt(2)
        } else {
            card.addView(menuRow)
        }
    }

    private fun showAppBindingDialog(scriptName: String) {
        val root = dialogView as? LinearLayout ?: return
        val detectedPkg = AutoTapAccessibilityService.instance?.currentForegroundPackage?.ifEmpty { "com.example.game" } ?: "com.example.game"
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor("#1E1B2E".toColorInt())
                cornerRadius = dpF(8f)
                setStroke(dp(1), "#6366F1".toColorInt())
            }
            val p = dp(8)
            setPadding(p, p, p, p)
        }
        val tv = TextView(context).apply {
            text = "Привязать '$scriptName' к приложению:"
            textSize = 9f
            setTextColor(Color.WHITE)
        }
        val et = EditText(context).apply {
            hint = "com.package.name"
            setHintTextColor("#64748B".toColorInt())
            setText(detectedPkg)
            textSize = 9f
            setTextColor(Color.WHITE)
            background = null
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32))
        }
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(4), 0, 0)
        }
        val btnSave = createTextButton("СОХРАНИТЬ", "#312E81", "#818CF8") {
            val pkg = et.text.toString().trim()
            if (pkg.isNotEmpty()) {
                profileDetector.bindPackageToScript(pkg, scriptName)
                Toast.makeText(context, "Привязано к $pkg", Toast.LENGTH_SHORT).show()
            }
            root.removeView(card)
        }
        val btnCancel = createTextButton("ОТМЕНА", "#21262D", "#94A3B8") {
            root.removeView(card)
        }
        btnRow.addView(btnCancel, LinearLayout.LayoutParams(dp(70), dp(28)).apply { marginEnd = dp(4) })
        btnRow.addView(btnSave, LinearLayout.LayoutParams(dp(95), dp(28)))
        card.addView(tv)
        card.addView(et)
        card.addView(btnRow)
        root.addView(card, 2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4)
        })
        et.requestFocus()
    }

    private fun createTextButton(label: String, bgHex: String, textHex: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            minHeight = 0; minimumHeight = 0
            gravity = Gravity.CENTER
            setTextColor(textHex.toColorInt())
            background = GradientDrawable().apply {
                setColor(bgHex.toColorInt())
                cornerRadius = dpF(5f)
                setStroke(dp(1), textHex.toColorInt())
            }
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener { onClick() }
        }
    }

    fun dismiss() {
        dialogView?.let {
            overlayWindowManager.removeViewSafe(it)
            dialogView = null
            listLayoutRef = null
        }
    }

    private fun createIconButton(type: VectorIconDrawer.IconType, bgHex: String, iconHex: String, sizePx: Int, tagStr: String = "", onClick: () -> Unit = {}): View {
        return object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(6f), dpF(6f), width - dpF(6f), height - dpF(6f))
                VectorIconDrawer.drawIcon(canvas, type, bounds, iconHex.toColorInt(), dpF(2f))
            }
        }.apply {
            tag = tagStr
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
}