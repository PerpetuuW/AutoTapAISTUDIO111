package com.example.autotap.infrastructure.overlay.dialog


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.repository.IScenarioRepository
import com.example.autotap.domain.repository.ITemplateRepository
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.capture.MaskPreviewView
import com.example.autotap.infrastructure.overlay.capture.RoiSelectorOverlay
import com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import java.io.File

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class EditActionDialog(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val templateRepository: ITemplateRepository,
    private val scenarioRepository: IScenarioRepository,
    private val action: MacroAction,
    private val totalActionsCount: Int,
    private val onSave: (MacroAction) -> Unit,
    private val onClone: (MacroAction) -> Unit,
    private val onDelete: (MacroAction) -> Unit,
    private val onCalibrate: (MacroAction) -> Unit,
    private val onRecapture: ((MacroAction) -> Unit)? = null,
    private val onSelectRoi: ((MacroAction) -> Unit)? = null,
    private val onNavigateStep: (Int) -> Unit,
    private val onTest: ((MacroAction) -> Unit)? = null
) {

    private var dialogView: View? = null
    private var selectedType = action.type
    private var boundTemplatePath: String = action.templatePath

    private val selectedMultiPaths = LinkedHashSet<String>().apply {
        if (!action.multiTemplatePaths.isNullOrEmpty()) {
            addAll(action.multiTemplatePaths.filter { it.isNotBlank() })
        }
        if (action.templatePath.isNotBlank()) {
            add(action.templatePath)
        }
    }

    private var isMultiScale = action.isMultiScaleMode
    private var isDeltaEMode = action.colorDeltaEMode
    private var isShapeOnly = action.isShapeOnlyMode
    private var isNeuralEngine = action.isNeuralEngine
        // Режим формы инкапсулирован в метаданных шаблона
    private var btnToggleEngineRef: Button? = null

    private var currentRoiLeft = action.roiLeft
    private var currentRoiTop = action.roiTop
    private var currentRoiRight = action.roiRight
    private var currentRoiBottom = action.roiBottom


    private var currentActionSimilarity = action.similarityPercent
    private var isNotifyOnMatch: Boolean = action.notifyOnMatch

    private var selectedSubroutineScenario: String = action.subroutineTarget.ifEmpty { action.targetScriptOrQuery.ifEmpty { action.subroutineTag } }
    private var selectedTargetOccurrenceIndex = action.targetOccurrenceIndex
    private var isOcrUseOffset = action.useCustomClickOffset
    private var ocrOffsetX = action.clickOffsetX
    private var ocrOffsetY = action.clickOffsetY
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null


    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density


    private fun mergeTemplateBitmaps(bitmaps: List<android.graphics.Bitmap>): android.graphics.Bitmap? {
        if (bitmaps.size < 2) return null
        val base = bitmaps[0]
        val w = base.width
        val h = base.height
        val totalPixels = w * h

        val basePixels = IntArray(totalPixels)
        base.getPixels(basePixels, 0, w, 0, 0, w, h)

        val validBitmaps = mutableListOf<android.graphics.Bitmap>()
        validBitmaps.add(base)

        for (i in 1 until bitmaps.size) {
            val cur = bitmaps[i]
            val resized = if (cur.width != w || cur.height != h) {
                android.graphics.Bitmap.createScaledBitmap(cur, w, h, true)
            } else cur

            val curPixels = IntArray(totalPixels)
            resized.getPixels(curPixels, 0, w, 0, 0, w, h)

            var matchCount = 0
            for (p in 0 until totalPixels) {
                val c1 = basePixels[p]
                val c2 = curPixels[p]
                val a1 = (c1 ushr 24) and 0xFF
                val a2 = (c2 ushr 24) and 0xFF
                if (a1 < 32 && a2 < 32) {
                    matchCount++
                    continue
                }
                val dr = Math.abs(((c1 ushr 16) and 0xFF) - ((c2 ushr 16) and 0xFF))
                val dg = Math.abs(((c1 ushr 8) and 0xFF) - ((c2 ushr 8) and 0xFF))
                val db = Math.abs((c1 and 0xFF) - (c2 and 0xFF))
                if (dr + dg + db < 65) matchCount++
            }
            val similarity = matchCount.toFloat() / totalPixels
            if (similarity >= 0.50f) {
                validBitmaps.add(resized)
            }
        }

        if (validBitmaps.size < 2) return null

        val outPixels = IntArray(totalPixels)
        val allArrays = validBitmaps.map { b ->
            val arr = IntArray(totalPixels)
            b.getPixels(arr, 0, w, 0, 0, w, h)
            arr
        }

        for (p in 0 until totalPixels) {
            var isOpaque = true
            var rSum = 0; var gSum = 0; var bSum = 0
            for (arr in allArrays) {
                val c = arr[p]
                val a = (c ushr 24) and 0xFF
                if (a < 64) {
                    isOpaque = false
                    break
                }
                rSum += (c ushr 16) and 0xFF
                gSum += (c ushr 8) and 0xFF
                bSum += c and 0xFF
            }

            if (!isOpaque) {
                outPixels[p] = 0
                continue
            }

            val count = allArrays.size
            val avgR = rSum / count
            val avgG = gSum / count
            val avgB = bSum / count

            var variance = 0
            for (arr in allArrays) {
                val c = arr[p]
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                variance += Math.abs(r - avgR) + Math.abs(g - avgG) + Math.abs(b - avgB)
            }
            val avgVar = variance / count

            if (avgVar <= 50) {
                outPixels[p] = (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
            } else {
                outPixels[p] = 0
            }
        }


        val res = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        res.setPixels(outPixels, 0, w, 0, 0, w, h)
        return res
        }

        fun show() {

        if (dialogView != null) return

        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val cardW = dp(340).coerceAtMost((screenW * 0.96f).toInt())
        val scrollMaxH = dp(420).coerceAtMost((screenH * 0.72f).toInt())

        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F817112B".toColorInt(), "#F80E091A".toColorInt())
            ).apply {
                cornerRadius = dpF(16f)
                setStroke(dp(1), "#8B5CF6".toColorInt())
            }
            val p = dp(8)
            setPadding(p, p, p, p)
            elevation = dpF(18f)
        }
        dialogView = rootCard

        val btnHideKeyboard = Button(context).apply {
            text = "СКРЫТЬ КЛАВИАТУРУ"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#312E81".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#6366F1".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = View.GONE
            setOnClickListener { hideKeyboard(this) }
        }
        rootCard.addView(btnHideKeyboard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)).apply {
            bottomMargin = dp(4)
        })

        val dmScreenH = context.resources.displayMetrics.heightPixels
        fun updateKbBtnVisibility() {
            val r = Rect()
            rootCard.getWindowVisibleDisplayFrame(r)
            val keypadHeight = dmScreenH - r.bottom
            val isFocused = rootCard.findFocus() is EditText
            val isKeyboardOpen = keypadHeight > (dmScreenH * 0.15f)
            btnHideKeyboard.visibility = if (isKeyboardOpen || isFocused) View.VISIBLE else View.GONE
        }

        btnHideKeyboard.setOnClickListener {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentFocused = rootCard.findFocus()
            val token = currentFocused?.windowToken ?: rootCard.windowToken
            imm?.hideSoftInputFromWindow(token, 0)
            currentFocused?.clearFocus()
            rootCard.clearFocus()
            btnHideKeyboard.visibility = View.GONE
        }

        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            updateKbBtnVisibility()
        }
        rootCard.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)

        val navHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(8f)
            }
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }

        val btnPrev = createNavVectorBtn(VectorIconDrawer.IconType.STEP_PREV) { onNavigateStep(action.id - 1) }
        navHeader.addView(btnPrev)

        val stepInfoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), 0)
        }

        var startRawX = 0f
        var startRawY = 0f
        var initLpX = 0
        var initLpY = 0
        var isDraggingHeader = false
        val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

        stepInfoLayout.setOnTouchListener { _, event ->
            val lp = rootCard.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    initLpX = lp.x
                    initLpY = lp.y
                    isDraggingHeader = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    if (Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop || isDraggingHeader) {
                        isDraggingHeader = true
                        lp.x = (initLpX + dx).toInt()
                        lp.y = (initLpY + dy).toInt()
                        overlayWindowManager.updateViewSafe(rootCard, lp)
                        true
                    } else {
                        false
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    isDraggingHeader = false
                    true
                }
                else -> false
            }
        }

        val tvTitle = TextView(context).apply {
            text = "::: ШАГ ${action.id} из $totalActionsCount :::"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#58A6FF".toColorInt())
        }
        stepInfoLayout.addView(tvTitle)
        navHeader.addView(stepInfoLayout, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnNext = createNavVectorBtn(VectorIconDrawer.IconType.STEP_NEXT) { onNavigateStep(action.id + 1) }
        navHeader.addView(btnNext)

        val btnHelp = createNavVectorBtn(
            type = VectorIconDrawer.IconType.HELP,
            iconColor = "#38BDF8".toColorInt(),
            bgColor = "#1E293B",
            strokeColor = "#38BDF8"
        ) {
            InteractiveTutorialOverlay(
                context = context,
                overlayWindowManager = overlayWindowManager,
                mode = InteractiveTutorialOverlay.TutorialMode.EDIT_STEP,
                hostViewProvider = { dialogView }
            ).show()
        }
        navHeader.addView(btnHelp)

        val btnClose = createNavVectorBtn(VectorIconDrawer.IconType.CLOSE) { dismiss() }
        navHeader.addView(btnClose)
        rootCard.addView(navHeader)

        val scrollView = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val limitedHeightSpec = MeasureSpec.makeMeasureSpec(scrollMaxH, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, limitedHeightSpec)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            isVerticalScrollBarEnabled = true
        }

        // [V16.0] СЕГМЕНТИРОВАННАЯ ТРЕХВКЛАДОЧНАЯ АРХИТЕКТУРА (MATERIAL 3)
        val tabBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor("#161224".toColorInt())
                cornerRadius = dpF(8f)
                setStroke(dp(1), "#3E2A6E".toColorInt())
            }
            val p = dp(2)
            setPadding(p, p, p, p)
        }

        var activeTabIndex = if (selectedType == ActionType.TRIGGER || selectedType == ActionType.OCR || selectedType == ActionType.COLOR_CHECK) 1 else 0

        val tabContainerParams = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val tabContainerVision = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val tabContainerLogic = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(4)
            setPadding(0, p, 0, p)
        }
        contentLayout.addView(tabContainerParams)
        contentLayout.addView(tabContainerVision)
        contentLayout.addView(tabContainerLogic)

        val allTypeButtons = ArrayList<Button>()
        var layoutTimingsRef: View? = null

        var layoutTriggerCardRef: LinearLayout? = null
        var layoutPinchRef: LinearLayout? = null
        var layoutPathRef: View? = null
        var layoutSubroutinePickerRef: View? = null
        var layoutTimeoutRef: View? = null
        var layoutOcrRef: View? = null
        var layoutColorRef: View? = null
        var layoutJumpsRef: View? = null

        var tvSimRef: TextView? = null
        var btnToggleScaleRef: Button? = null

        val btnTabParams = Button(context)
        val btnTabVision = Button(context)
        val btnTabLogic = Button(context)

        fun updateTabStyles() {
            fun styleTab(btn: Button, isSelected: Boolean, accentHex: String) {
                btn.setTextColor(if (isSelected) Color.BLACK else Color.WHITE)
                btn.background = GradientDrawable().apply {
                    setColor(if (isSelected) accentHex.toColorInt() else Color.TRANSPARENT)
                    cornerRadius = dpF(6f)
                }
            }
            styleTab(btnTabParams, activeTabIndex == 0, "#4F46E5")
            styleTab(btnTabVision, activeTabIndex == 1, "#6366F1")
            styleTab(btnTabLogic, activeTabIndex == 2, "#3B82F6")

            tabContainerParams.visibility = if (activeTabIndex == 0) View.VISIBLE else View.GONE
            tabContainerVision.visibility = if (activeTabIndex == 1) View.VISIBLE else View.GONE
            tabContainerLogic.visibility = if (activeTabIndex == 2) View.VISIBLE else View.GONE
            }

            fun setupTabBtn(btn: Button, title: String, tabIdx: Int) {
            btn.apply {
                text = title
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                minHeight = 0; minimumHeight = 0
                includeFontPadding = false
                setPadding(dp(4), dp(6), dp(4), dp(6))
                setOnClickListener {
                    activeTabIndex = tabIdx
                    updateTabStyles()
                }
            }
            tabBar.addView(btn, LinearLayout.LayoutParams(0, dp(30), 1f))
            }

            setupTabBtn(btnTabParams, "ПАРАМЕТРЫ", 0)
            setupTabBtn(btnTabVision, "ЗРЕНИЕ (CV)", 1)
            setupTabBtn(btnTabLogic, "ПЕРЕХОДЫ", 2)
            rootCard.addView(tabBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
            bottomMargin = dp(4)
            })

            fun updateDynamicSections() {
            val isTrigger = (selectedType == ActionType.TRIGGER)
            val isOcr = (selectedType == ActionType.OCR)
            val isColor = (selectedType == ActionType.COLOR_CHECK)
            val isSubroutine = (selectedType == ActionType.SUBROUTINE)
            val isReturn = (selectedType == ActionType.RETURN)
            val isPinch = (selectedType == ActionType.PINCH)
            val isDetection = isTrigger || isOcr || isColor

            btnTabVision.visibility = if (isDetection) View.VISIBLE else View.GONE
            btnTabLogic.visibility = if (isDetection || isSubroutine) View.VISIBLE else View.GONE

            if (!isDetection && activeTabIndex == 1) {
                activeTabIndex = 0
            }
            if (!isDetection && !isSubroutine && activeTabIndex == 2) {
                activeTabIndex = 0
            }

            layoutTimingsRef?.visibility = if (!isReturn) View.VISIBLE else View.GONE

            layoutTriggerCardRef?.visibility = if (isTrigger) View.VISIBLE else View.GONE
            layoutPinchRef?.visibility = if (isPinch) View.VISIBLE else View.GONE
            layoutPathRef?.visibility = if (selectedType == ActionType.PATH) View.VISIBLE else View.GONE
            layoutSubroutinePickerRef?.visibility = if (isSubroutine) View.VISIBLE else View.GONE
            layoutTimeoutRef?.visibility = if (isDetection) View.VISIBLE else View.GONE
            layoutOcrRef?.visibility = if (isOcr) View.VISIBLE else View.GONE
            layoutColorRef?.visibility = if (isColor) View.VISIBLE else View.GONE
            layoutJumpsRef?.visibility = if (isDetection) View.VISIBLE else View.GONE

            updateTabStyles()
            }

            // [ТАБ 1]: Чистые кнопки выбора базового жеста
            val typeRow1A = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(2)) }
            allTypeButtons.add(createTypeBtn("КЛИК", ActionType.CLICK, typeRow1A, allTypeButtons) { updateDynamicSections() })
            allTypeButtons.add(createTypeBtn("УДЕРЖАНИЕ", ActionType.LONG_PRESS, typeRow1A, allTypeButtons) { updateDynamicSections() })
            tabContainerParams.addView(typeRow1A)


            val typeRow1B = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(3)) }
            allTypeButtons.add(createTypeBtn("СВАЙП", ActionType.SWIPE, typeRow1B, allTypeButtons) { updateDynamicSections() })
            allTypeButtons.add(createTypeBtn("ПУТЬ", ActionType.PATH, typeRow1B, allTypeButtons) { updateDynamicSections() })
            allTypeButtons.add(createTypeBtn("ПИНЧ", ActionType.PINCH, typeRow1B, allTypeButtons) { updateDynamicSections() })
            tabContainerParams.addView(typeRow1B)

            val layoutPath = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            layoutPathRef = layoutPath

            val tvPathHeader = TextView(context).apply {
                text = "ТОЧКИ ТРАЕКТОРИИ ПУТИ:"
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor("#94A3B8".toColorInt())
                setPadding(0, dp(2), 0, dp(2))
            }
            val tvPathSub = TextView(context).apply {
                text = "Любую точку можно свободно перемещать по экрану"
                textSize = 7.5f
                setTextColor("#64748B".toColorInt())
                setPadding(0, 0, 0, dp(4))
            }
            layoutPath.addView(tvPathHeader)
            layoutPath.addView(tvPathSub)

            val pathControlsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }


            val btnAddPoint = createSmallButton("+ ТОЧКА") {
                val pts = if (action.pathPoints.size >= 2) {
                    val mPts = action.pathPoints.toMutableList()
                    val p1 = mPts[mPts.size - 2]
                    val p2 = mPts.last()
                    val midX = (p1.x + p2.x) / 2f + dpF(15f)
                    val midY = (p1.y + p2.y) / 2f - dpF(15f)
                    mPts.add(mPts.size - 1, com.example.autotap.domain.model.Point2D(midX, midY, 0L))
                    mPts
                } else {
                    listOf(
                        com.example.autotap.domain.model.Point2D(action.posX, action.posY, 0L),
                        com.example.autotap.domain.model.Point2D(action.posX + dpF(40f), action.posY + dpF(40f), 0L),
                        com.example.autotap.domain.model.Point2D(action.posX + dpF(80f), action.posY + dpF(80f), 0L)
                    )
                }
                onSave(action.copy(pathPoints = pts, endX = pts.last().x, endY = pts.last().y))
                dismiss()
                Toast.makeText(context, "Точка добавлена (всего: ${pts.size})", Toast.LENGTH_SHORT).show()
            }

            val btnRemovePoint = createSmallButton("- ТОЧКА") {
                if (action.pathPoints.size > 2) {
                    val mPts = action.pathPoints.toMutableList()
                    mPts.removeAt(mPts.size - 2)
                    onSave(action.copy(pathPoints = mPts, endX = mPts.last().x, endY = mPts.last().y))
                    dismiss()
                    Toast.makeText(context, "Точка удалена (осталось: ${mPts.size})", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Минимум 2 точки (старт и конец)", Toast.LENGTH_SHORT).show()
                }
            }

            val btnReversePath = createSmallButton("РЕВЕРС") {
                if (action.pathPoints.size >= 2) {
                    val rev = action.pathPoints.reversed()
                    onSave(action.copy(pathPoints = rev, posX = rev.first().x, posY = rev.first().y, endX = rev.last().x, endY = rev.last().y))
                    dismiss()
                    Toast.makeText(context, "Направление пути развернуто!", Toast.LENGTH_SHORT).show()
                }
            }

            pathControlsRow.addView(btnAddPoint, LinearLayout.LayoutParams(0, dp(24), 1.2f).apply { marginEnd = dp(2) })
            pathControlsRow.addView(btnRemovePoint, LinearLayout.LayoutParams(0, dp(24), 1.2f).apply { marginEnd = dp(2) })
            pathControlsRow.addView(btnReversePath, LinearLayout.LayoutParams(0, dp(24), 1.1f))
            layoutPath.addView(pathControlsRow)
            tabContainerParams.addView(layoutPath)

            // [ТАБ 2]: Кнопки выбора типа компьютерного зрения
            val typeRow2A = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(2), 0, dp(4)) }
            allTypeButtons.add(createTypeBtn("ШАБЛОН", ActionType.TRIGGER, typeRow2A, allTypeButtons) { updateDynamicSections() })
            allTypeButtons.add(createTypeBtn("OCR ТЕКСТ", ActionType.OCR, typeRow2A, allTypeButtons) { updateDynamicSections() })
            allTypeButtons.add(createTypeBtn("ЦВЕТ", ActionType.COLOR_CHECK, typeRow2A, allTypeButtons) { updateDynamicSections() })
            tabContainerVision.addView(typeRow2A)

            // [ТАБ 3]: Кнопки логики
            val typeRow3 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(2), 0, dp(4)) }
            allTypeButtons.add(createTypeBtn("ПОДПРОГРАММА", ActionType.SUBROUTINE, typeRow3, allTypeButtons) { updateDynamicSections() })
            allTypeButtons.add(createTypeBtn("ВОЗВРАТ", ActionType.RETURN, typeRow3, allTypeButtons) { updateDynamicSections() })
            tabContainerLogic.addView(typeRow3)

        val layoutPinch = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor("#161B22".toColorInt())
                cornerRadius = dpF(8f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            val p = dp(6)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                val m = dp(3)
                setMargins(0, m, 0, m)
            }
        }
        layoutPinchRef = layoutPinch
        val (etPinchStart, _) = createNumericInputWithSteppers(layoutPinch, "Начальное расстояние точек сжатия (px):", action.pinchStartDistance.toInt().toString(), 50L)
        val (etPinchEnd, _) = createNumericInputWithSteppers(layoutPinch, "Конечное расстояние точек сжатия (px):", action.pinchEndDistance.toInt().toString(), 50L)
        contentLayout.addView(layoutPinch)

        val layoutTriggerCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#161B22".toColorInt(), "#0D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(8f)
                setStroke(dp(1), "#FB923C".toColorInt())
            }
            val p = dp(6)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                val m = dp(3)
                setMargins(0, m, 0, m)
            }
        }
        layoutTriggerCardRef = layoutTriggerCard

        val tvCardHeader = TextView(context).apply {
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor("#FB923C".toColorInt())
        }
        layoutTriggerCard.addView(tvCardHeader)

        val previewRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }

        val previewView = MaskPreviewView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
        }
        previewRow.addView(previewView)

        val metaInfoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        val tvTplName = TextView(context).apply {
            textSize = 8.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
        }
        metaInfoLayout.addView(tvTplName)

        val tvTplSpecs = TextView(context).apply {
            textSize = 7.5f
            setTextColor("#8B949E".toColorInt())
        }
        metaInfoLayout.addView(tvTplSpecs)
        previewRow.addView(metaInfoLayout, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        layoutTriggerCard.addView(previewRow)

        var allTemplates = templateRepository.loadAllTemplates().toMutableList()

        fun applyTemplateMetadata(path: String) {
            val meta = templateRepository.getTemplateMetadata(path) ?: return

            meta["similarityPercent"]?.let { currentActionSimilarity = (it as? Int) ?: 88 }
            meta["isMultiScaleMode"]?.let { isMultiScale = (it as? Boolean) ?: false }
            meta["isShapeOnlyMode"]?.let { isShapeOnly = (it as? Boolean) ?: false }
            meta["colorDeltaEMode"]?.let { isDeltaEMode = (it as? Boolean) ?: false }
            meta["isNeuralEngine"]?.let { isNeuralEngine = (it as? Boolean) ?: false }

            tvSimRef?.text = "ПОРОГ: $currentActionSimilarity%"

            val btnScaleStr = if (isMultiScale) "МАСШТАБ: ПИРАМИДА (0.88x - 1.12x)" else "МАСШТАБ: ФИКСИРОВАННЫЙ (1:1)"
            btnToggleScaleRef?.text = btnScaleStr
            btnToggleScaleRef?.setTextColor(if (isMultiScale) Color.BLACK else Color.WHITE)
            btnToggleScaleRef?.background = GradientDrawable().apply {
                setColor(if (isMultiScale) "#312E81".toColorInt() else "#21262D".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), if (isMultiScale) "#6366F1".toColorInt() else "#3E3859".toColorInt())
            }

            // Параметры формы синхронизированы в метаданных шаблона
            Toast.makeText(context, "Все параметры формы загружены из шаблона", Toast.LENGTH_SHORT).show()
        }

                // [V17.0] Параметры поиска управляются строго шаблоном (кнопка применения снята)

        fun updateMultiSelectHeader() {
            val count = selectedMultiPaths.size
            tvCardHeader.text = "МУЛЬТИПОИСК ($count из ${allTemplates.size} выбрано):"
        }

                        fun refreshActiveTemplateCard() {
            updateMultiSelectHeader()
            val effectivePrimary = boundTemplatePath.ifBlank { selectedMultiPaths.firstOrNull() ?: "" }
            val bmp = if (effectivePrimary.isNotBlank()) templateRepository.getTemplate(effectivePrimary) else null
            if (bmp != null && !bmp.isRecycled) {
                val metaMap = templateRepository.getTemplateMetadata(effectivePrimary)
                val metaSim = (metaMap?.get("similarityPercent") as? Number)?.toInt() ?: 85
                val isShape = (metaMap?.get("isShapeOnlyMode") as? Boolean) ?: false
                val offX = (metaMap?.get("clickOffsetX") as? Number)?.toInt() ?: 0
                val offY = (metaMap?.get("clickOffsetY") as? Number)?.toInt() ?: 0

                previewView.bind(bmp, isMask = true, isCircle = action.isCircleShape)
                tvTplName.text = File(effectivePrimary).nameWithoutExtension
                // [V13.8] Read-Only панель метаданных (размеры, порог, режим)
                tvTplSpecs.text = "РАЗРЕШЕНИЕ: ${bmp.width}x${bmp.height} px\nПОРОГ: $metaSim% | РЕЖИМ: ${if (isShape) "КОНТУР" else "ГИБРИД"}\nСМЕЩЕНИЕ КЛИКА: X:$offX, Y:$offY"
            } else {
                previewView.bind(null, isMask = false)
                tvTplName.text = "Шаблоны не выбраны"
                tvTplSpecs.text = "Выберите шаблон из библиотеки или вырежьте новый"
            }
        }
        refreshActiveTemplateCard()

        var renderCarouselItems: () -> Unit = {}

        val multiControlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }

        val btnSelectAll = createSmallButton("ВЫБРАТЬ ВСЕ (${allTemplates.size})") {
            selectedMultiPaths.clear()
            allTemplates.forEach { selectedMultiPaths.add(it.first) }
            if (boundTemplatePath.isBlank() && allTemplates.isNotEmpty()) {
                boundTemplatePath = allTemplates.first().first
            }
            refreshActiveTemplateCard()
            renderCarouselItems()
            Toast.makeText(context, "Выбраны все ${allTemplates.size} шаблона", Toast.LENGTH_SHORT).show()
        }

        multiControlRow.addView(btnSelectAll, LinearLayout.LayoutParams(0, dp(24), 1.0f).apply { marginEnd = dp(2) })

        val btnMergeTemplates = createSmallButton("ОБЪЕДИНИТЬ") {
            val targets = if (selectedMultiPaths.size >= 2) selectedMultiPaths.toList()
                          else allTemplates.map { it.first }
            if (targets.size < 2) {
                Toast.makeText(context, "Выберите минимум 2 шаблона для объединения", Toast.LENGTH_SHORT).show()
                return@createSmallButton
            }
            val bitmaps = targets.mapNotNull { p -> templateRepository.getTemplate(p) }
            if (bitmaps.size < 2) {
                Toast.makeText(context, "Не удалось загрузить изображения шаблонов", Toast.LENGTH_SHORT).show()
                return@createSmallButton
            }
            val mergedBmp = mergeTemplateBitmaps(bitmaps)
            if (mergedBmp == null) {
                Toast.makeText(context, "Шаблоны слишком разные (совпадение меньше 50%)", Toast.LENGTH_LONG).show()
                return@createSmallButton
            }
            val metaObj = org.json.JSONObject().apply {
                put("similarity", 70)
                put("isShapeOnly", false)
            }
            val newPath = templateRepository.saveTemplate("Merged", null, mergedBmp, mergedBmp, metaObj)
            allTemplates = templateRepository.loadAllTemplates().toMutableList()
            boundTemplatePath = newPath
            selectedMultiPaths.clear()
            selectedMultiPaths.add(newPath)
            refreshActiveTemplateCard()
            renderCarouselItems()
            Toast.makeText(context, "Создан общий инвариантный шаблон без фона!", Toast.LENGTH_SHORT).show()
        }
        multiControlRow.addView(btnMergeTemplates, LinearLayout.LayoutParams(0, dp(24), 1.1f).apply { marginEnd = dp(2) })

        val btnMoveToFolder = createSmallButton("В ПАПКУ") {

            val targets = if (selectedMultiPaths.isNotEmpty()) selectedMultiPaths.toList()
                          else if (boundTemplatePath.isNotBlank()) listOf(boundTemplatePath)
                          else emptyList()

            if (targets.isEmpty()) {
                Toast.makeText(context, "Выберите шаблоны для перемещения", Toast.LENGTH_SHORT).show()
                return@createSmallButton
            }

            // Инлайн-диалог перемещения с защитой от Window Token Exception
            val inputLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor("#1E1B2E".toColorInt())
                    cornerRadius = dpF(6f)
                    setStroke(dp(1), "#6366F1".toColorInt())
                }
                setPadding(dp(6), dp(2), dp(6), dp(2))
            }
            val etFolderName = EditText(context).apply {
                hint = "Имя папки (Кнопки, Меню...)"
                textSize = 8.5f
                setTextColor(Color.WHITE)
                setHintTextColor("#64748B".toColorInt())
                background = null
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f)
            }
            val btnConfirmMove = createTextActionButton("OK", "#312E81", "#818CF8") {
                val fName = etFolderName.text.toString().trim()
                if (fName.isNotBlank() && targets.isNotEmpty()) {
                    val sampleFile = File(targets.first())
                    val baseDir = if (sampleFile.parentFile?.parentFile?.name == "templates") {
                        sampleFile.parentFile?.parentFile
                    } else if (sampleFile.parentFile?.name == "templates") {
                        sampleFile.parentFile
                    } else {
                        File(context.filesDir, "templates")
                    }
                    val targetDir = File(baseDir ?: File(context.filesDir, "templates"), fName).apply { mkdirs() }
                    targets.forEach { path ->
                        val maskFile = File(path)
                        if (maskFile.exists()) {
                            val baseName = maskFile.name.removePrefix("mask_")
                            val rawFile = File(maskFile.parentFile, "raw_$baseName")
                            val screenFile = File(maskFile.parentFile, "screen_$baseName")
                            val metaFile = File(maskFile.parentFile, "${maskFile.nameWithoutExtension}.json")

                            maskFile.renameTo(File(targetDir, maskFile.name))
                            if (rawFile.exists()) rawFile.renameTo(File(targetDir, rawFile.name))
                            if (screenFile.exists()) screenFile.renameTo(File(targetDir, screenFile.name))
                            if (metaFile.exists()) metaFile.renameTo(File(targetDir, metaFile.name))
                            }
                            }
                            allTemplates = templateRepository.loadAllTemplates().toMutableList()
                            selectedMultiPaths.clear()
                            boundTemplatePath = allTemplates.firstOrNull()?.first ?: ""
                            refreshActiveTemplateCard()
                            renderCarouselItems()
                            layoutTriggerCard.removeView(inputLayout)
                            Toast.makeText(context, "Перемещено ${targets.size} шаблонов в '$fName'", Toast.LENGTH_SHORT).show()
                            }
                            }
                            val btnCancelMove = createTextActionButton("X", "#2A1420", "#F43F5E") {
                layoutTriggerCard.removeView(inputLayout)
            }
            inputLayout.addView(etFolderName)
            inputLayout.addView(btnConfirmMove, LinearLayout.LayoutParams(dp(36), dp(26)).apply { marginEnd = dp(2) })
            inputLayout.addView(btnCancelMove, LinearLayout.LayoutParams(dp(26), dp(26)))

            val idx = layoutTriggerCard.indexOfChild(multiControlRow)
            layoutTriggerCard.addView(inputLayout, idx + 1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
                bottomMargin = dp(3)
            })
            etFolderName.requestFocus()
        }
        multiControlRow.addView(btnMoveToFolder, LinearLayout.LayoutParams(0, dp(24), 1.1f).apply { marginEnd = dp(2) })

                val btnDeleteCurrent = createSmallButton("УДАЛИТЬ") {
            val toDelete = if (selectedMultiPaths.isNotEmpty()) {
                selectedMultiPaths.toList()
            } else if (boundTemplatePath.isNotBlank()) {
                listOf(boundTemplatePath)
            } else emptyList()

            if (toDelete.isNotEmpty()) {
                toDelete.forEach { templateRepository.moveToTrash(it) }
                allTemplates = templateRepository.loadAllTemplates().toMutableList()
                selectedMultiPaths.clear()
                boundTemplatePath = allTemplates.firstOrNull()?.first ?: ""
                refreshActiveTemplateCard()
                renderCarouselItems()
                Toast.makeText(context, "Удалено шаблонов: ${toDelete.size}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Выберите шаблоны для удаления", Toast.LENGTH_SHORT).show()
            }
        }
        multiControlRow.addView(btnDeleteCurrent, LinearLayout.LayoutParams(0, dp(24), 0.9f).apply { marginEnd = dp(2) })

        val btnClearTemplates = createSmallButton("ОЧИСТИТЬ") {
            selectedMultiPaths.clear()
            boundTemplatePath = ""
            refreshActiveTemplateCard()
            renderCarouselItems()
            Toast.makeText(context, "Шаблоны удалены из шага", Toast.LENGTH_SHORT).show()
        }
        multiControlRow.addView(btnClearTemplates, LinearLayout.LayoutParams(0, dp(24), 0.9f))
        layoutTriggerCard.addView(multiControlRow)

        val libraryScroll = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val libraryRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        var currentCarouselFolder: String? = null
        renderCarouselItems = {
            libraryRow.removeAllViews()

            if (currentCarouselFolder != null) {
                val backCard = FrameLayout(context).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor("#1E1B4B".toColorInt())
                        cornerRadius = dpF(8f)
                        setStroke(dp(1), "#818CF8".toColorInt())
                    }
                    val tvBack = android.widget.TextView(context).apply {
                        text = "[< НАЗАД]"
                        textSize = 8f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        setTextColor("#A5B4FC".toColorInt())
                        gravity = android.view.Gravity.CENTER
                    }
                    addView(tvBack, FrameLayout.LayoutParams(-1, -1))
                    layoutParams = LinearLayout.LayoutParams(dp(54), dp(58)).apply { marginEnd = dp(4) }
                    setOnClickListener {
                        currentCarouselFolder = null
                        renderCarouselItems()
                    }
                }
                libraryRow.addView(backCard)
            } else {
                val folders = (templateRepository as? com.example.autotap.infrastructure.storage.TemplateRepositoryImpl)?.listFolders()?.filter { it != "default" } ?: emptyList()
                for (folderName in folders) {
                    val folderCard = FrameLayout(context).apply {
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor("#1F2937".toColorInt())
                            cornerRadius = dpF(8f)
                            setStroke(dp(1), "#F59E0B".toColorInt())
                        }
                        val tvFolder = android.widget.TextView(context).apply {
                            text = folderName.take(8)
                            textSize = 7.5f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            setTextColor("#FBBF24".toColorInt())
                            gravity = android.view.Gravity.CENTER
                        }
                        addView(tvFolder, FrameLayout.LayoutParams(-1, -1))
                        layoutParams = LinearLayout.LayoutParams(dp(54), dp(58)).apply { marginEnd = dp(4) }
                        setOnClickListener {
                            currentCarouselFolder = folderName
                            renderCarouselItems()
                        }
                    }
                    libraryRow.addView(folderCard)
                }
            }

            val filteredTemplates = if (currentCarouselFolder != null) {
                allTemplates.filter { java.io.File(it.first).parentFile?.name == currentCarouselFolder }
            } else {
                allTemplates.filter { java.io.File(it.first).parentFile?.name in listOf("default", "templates", null) }
            }

            for ((path, bmp) in filteredTemplates) {
                val isSelected = selectedMultiPaths.contains(path)
                val isPrimary = (path == boundTemplatePath)

                val meta = templateRepository.getTemplateMetadata(path)
                val isShape = (meta?.get("isShapeOnlyMode") as? Boolean) ?: (meta?.get("isShapeOnly") as? Boolean) ?: false
                val rawSim = (meta?.get("similarityPercent") as? Number)?.toInt() ?: 85
                val metaSim = if (rawSim <= 35) 85 else rawSim
                val metaExp = (meta?.get("shapeExpansion") as? Number)?.toInt() ?: 25

                val thumbCard = FrameLayout(context).apply {
                    background = GradientDrawable().apply {
                        setColor(if (isSelected) "#256366F1".toColorInt() else "#1A142E".toColorInt())
                        cornerRadius = dpF(8f)
                        setStroke(dp(1), if (isSelected) "#6366F1".toColorInt() else "#2F234F".toColorInt())
                    }
                    val p = dp(3)
                    setPadding(p, p, p, p)
                    layoutParams = LinearLayout.LayoutParams(dp(68), dp(84)).apply { marginEnd = dp(6) }
                    val btnDeleteBadge = TextView(context).apply {
                        text = "X"
                        textSize = 8f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor("#DC2626".toColorInt())
                        }
                        val sz = dp(16)
                        layoutParams = FrameLayout.LayoutParams(sz, sz, Gravity.TOP or Gravity.END).apply {
                            setMargins(0, dp(1), dp(1), 0)
                        }
                        setOnClickListener {
                            templateRepository.moveToTrash(path)
                            allTemplates = templateRepository.loadAllTemplates().toMutableList()
                            selectedMultiPaths.remove(path)
                            if (boundTemplatePath == path) {
                                boundTemplatePath = selectedMultiPaths.firstOrNull() ?: ""
                            }
                            refreshActiveTemplateCard()
                            renderCarouselItems()
                            Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                        }
                    }
                    addView(btnDeleteBadge)

                    setOnClickListener {
                        if (selectedMultiPaths.contains(path)) {
                            selectedMultiPaths.remove(path)
                            if (boundTemplatePath == path) {
                                boundTemplatePath = selectedMultiPaths.firstOrNull() ?: ""
                            }
                        } else {
                            selectedMultiPaths.add(path)
                            boundTemplatePath = path
                            applyTemplateMetadata(path)
                        }
                        refreshActiveTemplateCard()
                        renderCarouselItems()
                    }
                }

                val contentLinear = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }

                // ВЕРХНИЕ БЕЙДЖИ: ФОРМА И ПОРОГ (БЕЗ EMOJI)
                val badgeRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(14))
                }

                val tvShapeBadge = TextView(context).apply {
                    text = if (isShape) "ФОРМА" else "ГИБРИД"
                    textSize = 5.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isShape) "#FFB703".toColorInt() else "#38BDF8".toColorInt())
                    background = GradientDrawable().apply {
                        setColor(if (isShape) "#2E2010".toColorInt() else "#0E2433".toColorInt())
                        cornerRadius = dpF(3f)
                        setStroke(dp(1), if (isShape) "#FFB703".toColorInt() else "#38BDF8".toColorInt())
                    }
                    setPadding(dp(2), 0, dp(2), 0)
                }
                badgeRow.addView(tvShapeBadge, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

                val tvThresholdBadge = TextView(context).apply {
                    text = "$metaSim%"
                    textSize = 5.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor("#C084FC".toColorInt())
                    gravity = Gravity.END
                    setPadding(0, 0, dp(1), 0)
                }
                badgeRow.addView(tvThresholdBadge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                contentLinear.addView(badgeRow)

                val iv = ImageView(context).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                        topMargin = dp(2)
                        bottomMargin = dp(2)
                    }
                }
                contentLinear.addView(iv)

                val tvName = TextView(context).apply {
                    text = File(path).nameWithoutExtension.removePrefix("mask_").take(8)
                    textSize = 6f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isSelected) "#818CF8".toColorInt() else "#E2E8F0".toColorInt())
                    gravity = Gravity.CENTER
                }
                contentLinear.addView(tvName)

                val tvSpecs = TextView(context).apply {
                    text = "$metaSim% | ${metaExp}px\n${if (isShape) "ФОРМА" else "ЦВЕТ"}"
                    textSize = 5f
                    typeface = Typeface.MONOSPACE
                    setTextColor("#94A3B8".toColorInt())
                    gravity = Gravity.CENTER
                    setLineSpacing(0f, 0.9f)
                }
                contentLinear.addView(tvSpecs)
                thumbCard.addView(contentLinear, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

                if (isSelected) {
                    val checkmarkView = object : View(context) {
                        private val checkBounds = RectF()
                        override fun onDraw(canvas: Canvas) {
                            super.onDraw(canvas)
                            val s = dpF(8f)
                            checkBounds.set(width / 2f - s / 2f, height / 2f - s / 2f, width / 2f + s / 2f, height / 2f + s / 2f)
                            VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.CHECK, checkBounds, Color.BLACK, dpF(1.5f))
                        }
                    }.apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor((if (isPrimary) "#FFB703" else "#6366F1").toColorInt())
                        }
                        layoutParams = FrameLayout.LayoutParams(dp(12), dp(12), Gravity.BOTTOM or Gravity.END).apply {
                            setMargins(0, 0, dp(2), dp(2))
                        }
                    }
                    thumbCard.addView(checkmarkView)
                }

                libraryRow.addView(thumbCard)
            }
        }
        renderCarouselItems()
        libraryScroll.addView(libraryRow)
        layoutTriggerCard.addView(libraryScroll)

                val btnToggleEngine = Button(context).apply {
                btnToggleEngineRef = this
                val updateBtn = {

                text = if (isNeuralEngine) "ДВИЖОК: НЕЙРОСЕТЬ" else "ДВИЖОК: OPENCV"
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                setColor(if (isNeuralEngine) "#4C1D95".toColorInt() else "#0F172A".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), if (isNeuralEngine) "#A855F7".toColorInt() else "#38BDF8".toColorInt())
                }
                }
                textSize = 8f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                minHeight = 0; minimumHeight = 0
                setPadding(dp(6), dp(4), dp(6), dp(4))
                updateBtn()
                setOnClickListener {
                isNeuralEngine = !isNeuralEngine
                updateBtn()
                Toast.makeText(context, if (isNeuralEngine) "Режим: Google ML Kit (Нейросеть)" else "Режим: OpenCV (Каскадный поиск)", Toast.LENGTH_SHORT).show()
                }
                }
        layoutTriggerCard.addView(btnToggleEngine, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)).apply {
            topMargin = dp(4)
            bottomMargin = dp(3)
        })

        // [V17.0] ШАБЛОН - ЕДИНСТВЕННЫЙ ИСТОЧНИК ИСТИНЫ
        val cvActionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(2))
        }



        val btnCalibDirect = createTextActionButton("КАЛИБРОВКА", "#1F3554", "#38BDF8") {
            dismiss()
            onCalibrate(action.copy(templatePath = boundTemplatePath, isNeuralEngine = isNeuralEngine))
        }
        val btnRecalibRaw = createTextActionButton("РЕДАКТИРОВАТЬ", "#281745", "#A78BFA") {

            dismiss()
            onRecapture?.invoke(action.copy(templatePath = boundTemplatePath, isNeuralEngine = isNeuralEngine))
        }
        val hasRoi = currentRoiLeft != null && currentRoiRight != null
        val btnRoiSelect = createTextActionButton(if (hasRoi) "ROI: ЗАДАН" else "+ ОБЛАСТЬ ROI", if (hasRoi) "#3D2611" else "#1F2937", if (hasRoi) "#F59E0B" else "#FBBF24") {
            dismiss()
            onSelectRoi?.invoke(action.copy(
                templatePath = boundTemplatePath,
                isNeuralEngine = isNeuralEngine,
                roiLeft = currentRoiLeft,
                roiTop = currentRoiTop,
                roiRight = currentRoiRight,
                roiBottom = currentRoiBottom
            ))
        }
        cvActionsRow.addView(btnCalibDirect, LinearLayout.LayoutParams(0, dp(34), 1.0f).apply { marginEnd = dp(3) })
        cvActionsRow.addView(btnRecalibRaw, LinearLayout.LayoutParams(0, dp(34), 1.0f).apply { marginEnd = dp(3) })
        cvActionsRow.addView(btnRoiSelect, LinearLayout.LayoutParams(0, dp(34), 1.2f))
        layoutTriggerCard.addView(cvActionsRow)



        // Блок выбора ПОДПРОГРАММЫ
        val layoutSubroutinePicker = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor("#161B22".toColorInt())
                cornerRadius = dpF(8f)
                setStroke(dp(1), "#A78BFA".toColorInt())
            }
            val p = dp(6)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                val m = dp(3)
                setMargins(0, m, 0, m)
            }
        }
        layoutSubroutinePickerRef = layoutSubroutinePicker

        val etSubroutineName = createTextInput(layoutSubroutinePicker, "Целевой сценарий подпрограммы:", selectedSubroutineScenario)
        val availableScenarios = scenarioRepository.listScenarios()
        if (availableScenarios.isNotEmpty()) {
            val scScroll = HorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4)
                }
            }
            val scRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (sc in availableScenarios) {
                val btnSc = createSmallButton(sc) {
                    selectedSubroutineScenario = sc
                    etSubroutineName.setText(sc)
                }
                scRow.addView(btnSc, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(24)).apply { marginEnd = dp(3) })
            }
            scScroll.addView(scRow)
            layoutSubroutinePicker.addView(scScroll)
        }
        contentLayout.addView(layoutSubroutinePicker)

        // [ЭРГОНОМИКА М3]: Базовые параметры кликов и действий видны сразу без спойлеров
        val layoutTimings = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        layoutTimingsRef = layoutTimings
        val (etDelay, _) = createNumericInputWithSteppers(layoutTimings, "Пауза шага (мс):", action.delayMs.toString(), 50L)
        val (etHold, _) = createNumericInputWithSteppers(layoutTimings, "Длительность нажатия (мс):", action.holdDurationMs.coerceAtMost(action.delayMs).toString(), 20L)

        // Инвариант: длительность нажатия не может превышать периодичность шага
        etHold.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val d = etDelay.text.toString().toLongOrNull() ?: action.delayMs
                val h = etHold.text.toString().toLongOrNull() ?: action.holdDurationMs
                if (h > d && d > 0L) {
                    etHold.setText(d.toString())
                    Toast.makeText(context, "Длительность нажатия ограничена периодичностью ($d мс)", Toast.LENGTH_SHORT).show()
                }
            }
        }
        etDelay.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val d = etDelay.text.toString().toLongOrNull() ?: action.delayMs
                val h = etHold.text.toString().toLongOrNull() ?: action.holdDurationMs
                if (h > d && d > 0L) {
                    etHold.setText(d.toString())
                }
            }
        }

        val (etRepeats, _) = createNumericInputWithSteppers(layoutTimings, "Повторения (0 = бесконечно):", action.repeatCount.toString(), 1L)
        val (etRadius, _) = createNumericInputWithSteppers(layoutTimings, "Разброс радиуса (px):", action.randomRadiusPx.toString(), 1L)
        contentLayout.addView(layoutTimings)
        // АККОРДЕОН РАСШИРЕННЫХ НАСТРОЕК (для шаблонов и сложных условий)

        // [Clean Ergonomics] Пустой аккордеон ликвидирован


        val btnToggleNotify = Button(context).apply {

            text = if (isNotifyOnMatch) "ОПОВЕЩЕНИЕ ПРИ НАХОЖДЕНИИ: ВКЛ" else "ОПОВЕЩЕНИЕ ПРИ НАХОЖДЕНИИ: ВЫКЛ"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(if (isNotifyOnMatch) "#34D399".toColorInt() else "#8B949E".toColorInt())
            background = GradientDrawable().apply {
                setColor(if (isNotifyOnMatch) "#132E27".toColorInt() else "#161B22".toColorInt())
                cornerRadius = dpF(8f)
                setStroke(dp(1), (if (isNotifyOnMatch) "#34D399" else "#30363D").toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener {
                isNotifyOnMatch = !isNotifyOnMatch
                text = if (isNotifyOnMatch) "ОПОВЕЩЕНИЕ ПРИ НАХОЖДЕНИИ: ВКЛ" else "ОПОВЕЩЕНИЕ ПРИ НАХОЖДЕНИИ: ВЫКЛ"
                setTextColor(if (isNotifyOnMatch) "#34D399".toColorInt() else "#8B949E".toColorInt())
                background = GradientDrawable().apply {
                    setColor(if (isNotifyOnMatch) "#132E27".toColorInt() else "#161B22".toColorInt())
                    cornerRadius = dpF(8f)
                    setStroke(dp(1), (if (isNotifyOnMatch) "#34D399" else "#30363D").toColorInt())
                }
            }
        }



        layoutTimings.addView(btnToggleNotify, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)).apply {
            topMargin = dp(6)
        })

        val advancedContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val layoutTimeout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }


        layoutTimeoutRef = layoutTimeout
        val (etAiTimeout, _) = createNumericInputWithSteppers(layoutTimeout, "Таймаут поиска (сек):", (if (action.aiTimeoutSeconds > 0) action.aiTimeoutSeconds else 5).toString(), 1L)
        advancedContainer.addView(layoutTimeout)

        val layoutJumps = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = "JUMPS_SECTION"
            background = GradientDrawable().apply {
                setColor("#18122C".toColorInt())
                cornerRadius = dpF(8f)
                setStroke(dp(1), "#36255C".toColorInt())
            }
            val p = dp(6)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                val m = dp(3)
                setMargins(0, m, 0, m)
            }
        }
        layoutJumpsRef = layoutJumps

        val (etJumpMatch, _) = createNumericInputWithSteppers(layoutJumps, "Переход при успехе (Шаг #, 0 = След.):", (action.jumpToStepOnMatch ?: 0).toString(), 1L)
        val (etJumpTimeout, _) = createNumericInputWithSteppers(layoutJumps, "Переход при таймауте (Шаг #, 0 = След.):", (action.jumpToStepOnTimeout ?: 0).toString(), 1L)
        advancedContainer.addView(layoutJumps)

        contentLayout.addView(advancedContainer)

        // OCR
        val layoutOcr = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        layoutOcrRef = layoutOcr
        val etOcrQuery = createTextInput(layoutOcr, "Искомый текст / Выражение:", action.targetScriptOrQuery)

        val hasRoiOcr = currentRoiLeft != null && currentRoiRight != null
        val btnRoiOcr = createTextActionButton(if (hasRoiOcr) "ОБЛАСТЬ ПОИСКА: ЗАДАНА" else "+ ВЫБРАТЬ ОБЛАСТЬ ТЕКСТА (ROI)", if (hasRoiOcr) "#3D2611" else "#1F2937", if (hasRoiOcr) "#F59E0B" else "#38BDF8") {
            dismiss()
            onSelectRoi?.invoke(action.copy(
                targetScriptOrQuery = etOcrQuery.text.toString().trim(),
                targetOccurrenceIndex = selectedTargetOccurrenceIndex,
                useCustomClickOffset = isOcrUseOffset,
                clickOffsetX = ocrOffsetX,
                clickOffsetY = ocrOffsetY,
                roiLeft = currentRoiLeft,
                roiTop = currentRoiTop,
                roiRight = currentRoiRight,
                roiBottom = currentRoiBottom
            ))
        }
        layoutOcr.addView(btnRoiOcr, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)).apply {
            topMargin = dp(4)
            bottomMargin = dp(4)
        })

        // [Порядковый номер совпадения - выбор из найденных на экране]
        val tvOccurrenceStatus = TextView(context).apply {
            text = "Выбранный вариант совпадения: #${selectedTargetOccurrenceIndex + 1}"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#38BDF8".toColorInt())
            setPadding(0, dp(4), 0, dp(2))
        }
        layoutOcr.addView(tvOccurrenceStatus)

        fun performOcrScreenScan() {
            val q = etOcrQuery.text.toString().trim()
            val service = com.example.autotap.infrastructure.accessibility.AutoTapAccessibilityService.instance
            if (service == null) {
                android.widget.Toast.makeText(context, "Служба кликера не активна", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            dismiss()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val screenshot = service.captureScreenshotSync(1500L)
                if (screenshot == null) {
                    android.widget.Toast.makeText(context, "Не удалось сделать снимок экрана", android.widget.Toast.LENGTH_SHORT).show()
                    show()
                    return@postDelayed
                }
                val roi = if (currentRoiLeft != null && currentRoiRight != null) android.graphics.Rect(currentRoiLeft!!, currentRoiTop!!, currentRoiRight!!, currentRoiBottom!!) else null
                val matches = com.example.autotap.infrastructure.ocr.OcrEngine.findTextOnScreen(screenshot, q, 2500L, roi)
                screenshot.recycle()

                if (matches.isEmpty()) {
                    android.widget.Toast.makeText(context, "Совпадений для '$q' не обнаружено", android.widget.Toast.LENGTH_SHORT).show()
                    show()
                } else if (matches.size == 1) {
                    selectedTargetOccurrenceIndex = 0
                    tvOccurrenceStatus.text = "Выбранный вариант совпадения: #1"
                    android.widget.Toast.makeText(context, "Найдено 1 совпадение в (${matches[0].clickX}, ${matches[0].clickY})", android.widget.Toast.LENGTH_SHORT).show()
                    show()
                } else {
                    com.example.autotap.infrastructure.overlay.dialog.OcrCandidatePickerDialog(
                        context = context,
                        overlayWindowManager = overlayWindowManager,
                        candidates = matches,
                        onRescanRequested = {
                            performOcrScreenScan()
                        }
                    ) { idx, _ ->
                        selectedTargetOccurrenceIndex = idx
                        tvOccurrenceStatus.text = "Выбранный вариант совпадения: #${selectedTargetOccurrenceIndex + 1}"
                        show()
                    }.show()
                }
            }, 200L)
        }

        // Интерактивный поиск всех вариантов на текущем экране
        val btnSearchAllCandidates = Button(context).apply {
            text = "[🔍] СКАН И ВЫБОР ТЕКСТА НА ЭКРАНЕ"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            minHeight = 0; minimumHeight = 0
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#1E1B4B".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#818CF8".toColorInt())
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                performOcrScreenScan()
            }
        }
        layoutOcr.addView(btnSearchAllCandidates, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)).apply {
            topMargin = dp(4)
            bottomMargin = dp(4)
        })

        // [Галочка и калибровка оффсета клика]
        val cbUseOcrOffset = android.widget.CheckBox(context).apply {
            text = "Точный оффсет клика (вынос точки нажатия)"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#EC4899".toColorInt())
            isChecked = isOcrUseOffset
            setOnCheckedChangeListener { _, isChecked ->
                isOcrUseOffset = isChecked
            }
        }
        layoutOcr.addView(cbUseOcrOffset, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2)
        })

        val btnSetupOcrOffset = Button(context).apply {
            text = "[+] НАСТРОИТЬ ВЫНОС ТОЧКИ КЛИКА И ОФФСЕТ"
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            minHeight = 0; minimumHeight = 0
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#2E1022".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#EC4899".toColorInt())
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener {
                dismiss()
                val targetX = action.posX.coerceAtLeast(100f)
                val targetY = action.posY.coerceAtLeast(100f)
                com.example.autotap.infrastructure.overlay.capture.OcrOffsetCalibrationOverlay(
                    context = context,
                    overlayWindowManager = overlayWindowManager,
                    targetText = etOcrQuery.text.toString().trim().ifEmpty { "Пример Текста" },
                    targetCenterX = targetX,
                    targetCenterY = targetY,
                    initialOffsetX = ocrOffsetX,
                    initialOffsetY = ocrOffsetY
                ) { dx, dy ->
                    isOcrUseOffset = true
                    cbUseOcrOffset.isChecked = true
                    ocrOffsetX = dx
                    ocrOffsetY = dy
                    show()
                }.show()
            }
        }
        layoutOcr.addView(btnSetupOcrOffset, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)).apply {
            bottomMargin = dp(4)
        })

        val ocrPrefs = context.getSharedPreferences("autotap_recent_ocr", android.content.Context.MODE_PRIVATE)
        val rawOcrHistory = ocrPrefs.getString("recent_queries", "") ?: ""
        val ocrHistoryList = if (rawOcrHistory.isBlank()) mutableListOf() else rawOcrHistory.split("|||").filter { it.isNotBlank() }.toMutableList()

        if (ocrHistoryList.isNotEmpty()) {
            val scrollOcrHistory = android.widget.HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(2)
                    bottomMargin = dp(4)
                }
            }
            val historyChipsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            ocrHistoryList.take(8).forEach { queryItem ->
                val displayLabel = if (queryItem.length > 16) queryItem.take(14) + ".." else queryItem
                val btnChip = createSmallButton(displayLabel) {
                    etOcrQuery.setText(queryItem)
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)).apply {
                        marginEnd = dp(4)
                    }
                }
                historyChipsRow.addView(btnChip)
            }
            scrollOcrHistory.addView(historyChipsRow)
            layoutOcr.addView(scrollOcrHistory)
        }
        contentLayout.addView(layoutOcr)

        // Цвет
        val layoutColor = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        layoutColorRef = layoutColor
        val etColorHex = createTextInput(layoutColor, "HEX цвет пикселя (#RRGGBB):", action.targetColorHex)
        val (etColorTolerance, _) = createNumericInputWithSteppers(layoutColor, "Допуск цвета (0..100):", action.colorTolerance.toString(), 5L)

        val btnToggleDeltaE = Button(context).apply {
            val updateDeltaBtn = {
                text = if (isDeltaEMode) "КОЛОРИМЕТРИЯ: CIELAB DELTA-E (СВЕТОСТОЙКИЙ)" else "КОЛОРИМЕТРИЯ: sRGB ЕВКЛИД"
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(if (isDeltaEMode) "#312E81".toColorInt() else "#1E1B2E".toColorInt())
                    cornerRadius = dpF(6f)
                    setStroke(dp(1), if (isDeltaEMode) "#818CF8".toColorInt() else "#3E3859".toColorInt())
                }
            }
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(4), dp(6), dp(4))
            updateDeltaBtn()
            setOnClickListener {
                isDeltaEMode = !isDeltaEMode
                updateDeltaBtn()
            }
        }
        layoutColor.addView(btnToggleDeltaE, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(26)).apply {
            topMargin = dp(2)
            bottomMargin = dp(4)
        })
        contentLayout.addView(layoutColor)

        // [V17.0] Безопасная изоляция: отсоединение от текущего родителя перед добавлением в таб
        fun safeAttachTo(v: android.view.View?, target: android.widget.LinearLayout) {
            if (v == null) return
            (v.parent as? android.view.ViewGroup)?.removeView(v)
            target.addView(v)
        }
        safeAttachTo(layoutPinch, tabContainerParams)
        safeAttachTo(layoutSubroutinePicker, tabContainerParams)
        safeAttachTo(layoutTimings, tabContainerParams)

        safeAttachTo(layoutTriggerCard, tabContainerVision)
        safeAttachTo(layoutOcr, tabContainerVision)
        safeAttachTo(layoutColor, tabContainerVision)

        safeAttachTo(layoutTimeout, tabContainerLogic)
        safeAttachTo(layoutJumps, tabContainerLogic)

        scrollView.addView(contentLayout)
        rootCard.addView(scrollView)

        // [V16.0] ЭРГОНОМИЧНАЯ ПАНЕЛЬ ДЕЙСТВИЙ (ФИТТС >= 46dp, БЕЗ МИССКЛИКОВ)
        val actionToolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(2))
        }

        val btnTestStep = createTextActionButton("ТЕСТ", "#281745", "#A78BFA") {
            val jumpM = etJumpMatch.text.toString().toIntOrNull()?.let { if (it > 0) it else null }
            val jumpT = etJumpTimeout.text.toString().toIntOrNull()?.let { if (it > 0) it else null }
            val subTarget = if (selectedType == ActionType.SUBROUTINE) etSubroutineName.text.toString().trim() else action.subroutineTarget
            val d = maxOf(10L, etDelay.text.toString().toLongOrNull() ?: action.delayMs)
            val h = (etHold.text.toString().toLongOrNull() ?: action.holdDurationMs).coerceIn(10L, d)
            val currentAct = action.copy(
                type = selectedType,
                isNeuralEngine = isNeuralEngine,
                roiLeft = currentRoiLeft, roiTop = currentRoiTop, roiRight = currentRoiRight, roiBottom = currentRoiBottom,
                templatePath = boundTemplatePath, multiTemplatePaths = selectedMultiPaths.toList(),
                similarityPercent = currentActionSimilarity, isMultiScaleMode = isMultiScale,
                colorDeltaEMode = isDeltaEMode, isShapeOnlyMode = isShapeOnly,
                pinchStartDistance = etPinchStart.text.toString().toFloatOrNull() ?: action.pinchStartDistance,
                pinchEndDistance = etPinchEnd.text.toString().toFloatOrNull() ?: action.pinchEndDistance,
                delayMs = d,
                holdDurationMs = h,
                jumpToStepOnMatch = jumpM, jumpToStepOnTimeout = jumpT,
                subroutineTarget = subTarget, subroutineTag = subTarget,
                targetScriptOrQuery = when (selectedType) { ActionType.SUBROUTINE -> subTarget; ActionType.OCR -> etOcrQuery.text.toString().trim(); else -> action.targetScriptOrQuery },
                targetOccurrenceIndex = selectedTargetOccurrenceIndex,
                useCustomClickOffset = isOcrUseOffset,
                clickOffsetX = ocrOffsetX,
                clickOffsetY = ocrOffsetY,
                targetColorHex = etColorHex.text.toString().trim(),
                colorTolerance = etColorTolerance.text.toString().toIntOrNull() ?: 15
            )
            onTest?.invoke(currentAct)
        }
        val btnCloneStep = createTextActionButton("КЛОН", "#1A1433", "#58A6FF") { dismiss(); onClone(action) }
        val btnDeleteStep = createTextActionButton("УДАЛИТЬ", "#2E1218", "#F43F5E") { dismiss(); onDelete(action) }

        actionToolbar.addView(btnTestStep, LinearLayout.LayoutParams(0, dp(34), 1.2f).apply { marginEnd = dp(3) })
        actionToolbar.addView(btnCloneStep, LinearLayout.LayoutParams(0, dp(34), 0.9f).apply { marginEnd = dp(3) })
        actionToolbar.addView(btnDeleteStep, LinearLayout.LayoutParams(0, dp(34), 0.9f))
        rootCard.addView(actionToolbar)

        // ДОМИНАНТНАЯ КНОПКА СОХРАНЕНИЯ (PRIMARY ACTION, 46dp)
        val btnSaveFull = Button(context).apply {
            text = "СОХРАНИТЬ ШАГ"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#10B981".toColorInt(), "#059669".toColorInt())
            ).apply {
                cornerRadius = dpF(8f)
                setStroke(dp(1), "#34D399".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            includeFontPadding = false
            elevation = dpF(4f)
            setOnClickListener {
                val effectivePrimary = boundTemplatePath.ifBlank { selectedMultiPaths.firstOrNull() ?: "" }
                val jumpM = etJumpMatch.text.toString().toIntOrNull()?.let { if (it > 0) it else null }
                val jumpT = etJumpTimeout.text.toString().toIntOrNull()?.let { if (it > 0) it else null }
                val subTarget = if (selectedType == ActionType.SUBROUTINE) etSubroutineName.text.toString().trim() else action.subroutineTarget
                val d = maxOf(10L, etDelay.text.toString().toLongOrNull() ?: action.delayMs)
                val h = (etHold.text.toString().toLongOrNull() ?: action.holdDurationMs).coerceIn(10L, d)
                val updated = action.copy(
                    type = selectedType,
                    templatePath = effectivePrimary,
                    multiTemplatePaths = selectedMultiPaths.toList(),
                    isNeuralEngine = isNeuralEngine,
                    pinchStartDistance = etPinchStart.text.toString().toFloatOrNull() ?: action.pinchStartDistance,
                    pinchEndDistance = etPinchEnd.text.toString().toFloatOrNull() ?: action.pinchEndDistance,
                    delayMs = d,
                    holdDurationMs = h,
                    repeatCount = etRepeats.text.toString().toIntOrNull() ?: 1,
                    randomRadiusPx = etRadius.text.toString().toIntOrNull() ?: 0,
                    aiTimeoutSeconds = etAiTimeout.text.toString().toIntOrNull() ?: (if (selectedMultiPaths.size > 1) 0 else 5),
                    jumpToStepOnMatch = jumpM, jumpToStepOnTimeout = jumpT,
                    subroutineTarget = subTarget, subroutineTag = subTarget,
                    targetScriptOrQuery = when (selectedType) { ActionType.SUBROUTINE -> subTarget; ActionType.OCR -> etOcrQuery.text.toString().trim(); else -> action.targetScriptOrQuery },
                    targetOccurrenceIndex = selectedTargetOccurrenceIndex,
                    useCustomClickOffset = isOcrUseOffset,
                    clickOffsetX = ocrOffsetX,
                    clickOffsetY = ocrOffsetY,

                    targetColorHex = etColorHex.text.toString().trim().ifEmpty { "#6366F1" },
                    colorTolerance = etColorTolerance.text.toString().toIntOrNull() ?: 15,
                    roiLeft = currentRoiLeft, roiTop = currentRoiTop, roiRight = currentRoiRight, roiBottom = currentRoiBottom,
                    notifyOnMatch = isNotifyOnMatch
                    )
                dismiss()
                onSave(updated)
            }
        }
        rootCard.addView(btnSaveFull, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply {
            topMargin = dp(2)
        })

        updateDynamicSections()

        val params = overlayWindowManager.createDialogLayoutParams(width = cardW, height = WindowManager.LayoutParams.WRAP_CONTENT)
        overlayWindowManager.addViewSafe(rootCard, params)
    }

    private fun hideKeyboard(view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    fun dismiss() {
        dialogView?.let {
            hideKeyboard(it)
            globalLayoutListener?.let { l -> it.viewTreeObserver.removeOnGlobalLayoutListener(l) }
            overlayWindowManager.removeViewSafe(it)
            dialogView = null
        }
    }

    private fun createCategoryLabel(textStr: String): TextView {
        return TextView(context).apply {
            text = textStr
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#8B949E".toColorInt())
            setPadding(0, dp(3), 0, dp(1))
        }
    }

    private fun createNavVectorBtn(
        type: VectorIconDrawer.IconType,
        iconColor: Int = Color.WHITE,
        bgColor: String = "#21262D",
        strokeColor: String = "#30363D",
        onClick: () -> Unit
    ): View {
        return object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(5f), dpF(5f), width - dpF(5f), height - dpF(5f))
                VectorIconDrawer.drawIcon(canvas, type, bounds, iconColor, dpF(1.8f))
            }
        }.apply {
            background = GradientDrawable().apply {
                setColor(bgColor.toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), strokeColor.toColorInt())
            }
            val sz = dp(26)
            layoutParams = LinearLayout.LayoutParams(sz, sz).apply {
                val m = dp(1)
                setMargins(m, 0, m, 0)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createTypeBtn(name: String, type: ActionType, parent: LinearLayout, allButtons: List<Button>, onSelect: () -> Unit): Button {
        val btn = Button(context).apply {
            text = name
            textSize = 7f
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 0; minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            updateStyle(this, type == selectedType)
            setOnClickListener {
                selectedType = type
                allButtons.forEach { updateStyle(it, it.text == name) }
                onSelect()
            }
        }
        parent.addView(btn, LinearLayout.LayoutParams(0, dp(26), 1f).apply {
            val m = dp(1)
            setMargins(m, m, m, m)
        })
        return btn
    }

    private fun updateStyle(btn: Button, isSelected: Boolean) {
        btn.setTextColor(Color.WHITE)
        btn.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (isSelected) intArrayOf("#4F46E5".toColorInt(), "#3730A3".toColorInt())
            else intArrayOf("#1E1B2E".toColorInt(), "#161322".toColorInt())
        ).apply {
            cornerRadius = dpF(6f)
            setStroke(dp(1), if (isSelected) "#6366F1".toColorInt() else "#2F2A42".toColorInt())
        }
    }

    private fun createSmallButton(textStr: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = textStr
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#1A142E".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#3E2A6E".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(4), dp(3), dp(4), dp(3))
            setOnClickListener { onClick() }
        }
    }

    private fun createTextActionButton(label: String, bgHex: String, textHex: String, onClick: () -> Unit): View {
        return Button(context).apply {
            text = label
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(textHex.toColorInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(bgHex.toColorInt(), "#161B22".toColorInt())
            ).apply {
                cornerRadius = dpF(6f)
                setStroke(dp(1), textHex.toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { onClick() }
        }
    }

    private fun createNumericInputWithSteppers(parent: LinearLayout, label: String, initialValue: String, stepDelta: Long): Pair<EditText, LinearLayout> {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }

        val tv = TextView(context).apply {
            text = label
            textSize = 8.5f
            includeFontPadding = false
            setTextColor("#8B949E".toColorInt())
            setPadding(0, dp(2), 0, dp(1))
        }
        container.addView(tv)

        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val et = EditText(context).apply {
            setText(initialValue)
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 10.5f
            setTextColor(Color.WHITE)
            includeFontPadding = false
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = GradientDrawable().apply {
                setColor("#161B22".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            val p = dp(5)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(4) }
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    v.requestFocus()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        v.windowInsetsController?.show(WindowInsets.Type.ime())
                    }
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    @Suppress("DEPRECATION")
                    imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }
                false
            }
        }
        inputRow.addView(et)

        val btnMinus = createSmallButton("-$stepDelta") {
            val cur = et.text.toString().toLongOrNull() ?: 0L
            val next = (cur - stepDelta).coerceAtLeast(0L)
            et.setText(next.toString())
        }
        inputRow.addView(btnMinus, LinearLayout.LayoutParams(dp(36), dp(28)).apply { marginEnd = dp(2) })

        val btnPlus = createSmallButton("+$stepDelta") {
            val cur = et.text.toString().toLongOrNull() ?: 0L
            val next = cur + stepDelta
            et.setText(next.toString())
        }
        inputRow.addView(btnPlus, LinearLayout.LayoutParams(dp(36), dp(28)))

        container.addView(inputRow)
        parent.addView(container)
        return Pair(et, container)
    }

    private fun createTextInput(parent: LinearLayout, label: String, initialValue: String): EditText {
        val tv = TextView(context).apply {
            text = label
            textSize = 8.5f
            includeFontPadding = false
            setTextColor("#8B949E".toColorInt())
            setPadding(0, dp(2), 0, dp(1))
        }
        parent.addView(tv)

        val et = EditText(context).apply {
            setText(initialValue)
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 10.5f
            setTextColor(Color.WHITE)
            includeFontPadding = false
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = GradientDrawable().apply {
                setColor("#161B22".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#30363D".toColorInt())
            }
            val p = dp(5)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30))
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    v.requestFocus()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        v.windowInsetsController?.show(WindowInsets.Type.ime())
                    }
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    @Suppress("DEPRECATION")
                    imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }
                false
            }
        }
        parent.addView(et)
        return et
    }
}

private fun String.toColorInt(): Int = android.graphics.Color.parseColor(this)
