package com.example.autotap.infrastructure.overlay.graph

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import com.example.autotap.core.math.LinearToGraphMigrator
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.graph.EvaluationPolicy
import com.example.autotap.domain.model.graph.GraphMacroScenario
import com.example.autotap.domain.model.graph.NodeActionSpec
import com.example.autotap.domain.model.graph.NodeTriggerSpec
import com.example.autotap.domain.model.graph.ScenarioEdge
import com.example.autotap.domain.model.graph.ScenarioNode
import com.example.autotap.domain.model.graph.TriggerBehavior
import com.example.autotap.domain.repository.IScenarioRepository
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.infrastructure.storage.TemplateRepositoryImpl
import java.io.File
import java.util.UUID

@SuppressLint("SetTextI18n")
class GraphEditorOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val scenarioRepository: IScenarioRepository
) {
    private var overlayContainerView: FrameLayout? = null
    private var canvasView: GraphCanvasView? = null
    private var inspectorCard: LinearLayout? = null
    private var currentScenario: GraphMacroScenario? = null

    var onStartGraphRequested: ((GraphMacroScenario) -> Unit)? = null

    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

        fun show(scenarioName: String) {
        if (overlayContainerView != null) return
        AppLogger.log(context, "GRAPH_LIFECYCLE", "Открытие редактора графа для сценария '$scenarioName'")
        val graph = try {
            scenarioRepository.loadGraphScenario(scenarioName)
                ?: scenarioRepository.loadScenario(scenarioName)?.let { linear ->
                    val converted = LinearToGraphMigrator.linearToGraph(linear)
                    scenarioRepository.saveGraphScenario(converted)
                    AppLogger.log(context, "GRAPH_LIFECYCLE", "Сценарий '$scenarioName' успешно мигрирован в граф")
                    converted
                }
        } catch (e: Exception) {
            AppLogger.logError(context, "GRAPH_LOAD_CRASH", e)
            Toast.makeText(context, "Ошибка загрузки графа: ${e.message}", Toast.LENGTH_LONG).show()
            return
        } ?: run {
            AppLogger.log(context, "GRAPH_LIFECYCLE", "Сценарий '$scenarioName' не найден в репозитории")
            return
        }

        currentScenario = graph

        val root = FrameLayout(context).apply { setBackgroundColor("#0B0813".toColorInt()) }
        overlayContainerView = root

        // [V70.0] Изоляция видового экрана: вертикальная компоновка исключает наложение тулбара на холст
        val contentVertical = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Верхний командный блок управления (фиксированная высота, изолирован от холста)
        val topContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf("#F8140E24".toColorInt(), "#F8100B1D".toColorInt()))
            setPadding(dp(10), dp(6), dp(10), dp(4))
        }

        val canvas = GraphCanvasView(context).apply {
            setGraphScenario(graph)
            onNodeSelected = { showNodeInspector(it) }
            onScenarioModified = { this.scenario?.let { currentScenario = it } }
            onTemplateThumbnailClicked = { node, trigIdx ->
                showCustomTemplatePickerDialog(node, trigIdx)
            }
            onWireToEmptySpace = { fromNode, fromPort, wx, wy ->
                showWireToEmptyMenu(fromNode, fromPort, wx, wy)
            }
        }
        canvasView = canvas

        // 1. Верхний ряд заголовка: на всю ширину экрана (защита от вертикального сжатия)
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
        }

        val tvTitle = TextView(context).apply {
            text = "ГРАФ: ${graph.name}"
            setTextColor("#EDE9FE".toColorInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        titleRow.addView(tvTitle)

        // [V66.0] Кнопка обучения размещена статично в шапке (всегда видна на экране без скролла)
        val btnHelp = createToolbarBtn("[?] ОБУЧЕНИЕ", "#1E293B", "#38BDF8".toColorInt()) {
            InteractiveTutorialOverlay(
                context = context,
                overlayWindowManager = overlayWindowManager,
                mode = InteractiveTutorialOverlay.TutorialMode.GRAPH,
                hostViewProvider = { canvasView }
            ).show()
        }.apply { layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(30)).apply { marginEnd = dp(6) } }
        titleRow.addView(btnHelp)

        val tvNodesBadge = TextView(context).apply {
            text = "УЗЛОВ: ${graph.nodes.size}"
            setTextColor("#94A3B8".toColorInt())
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                setColor("#1F1C2E".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#382F54".toColorInt())
            }
        }
        titleRow.addView(tvNodesBadge)
        topContainer.addView(titleRow)

        // 2. Второй ряд: Горизонтальный скролл панели команд управления
        val commandScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
        }
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnPlay = createToolbarBtn("ПУСК", "#10B981", Color.BLACK) {
            currentScenario?.let { onStartGraphRequested?.invoke(it) }
            dismiss()
        }.apply { layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)).apply { marginEnd = dp(6) } }
        topBar.addView(btnPlay)

        val btnFit = createToolbarBtn("ОБЗОР", "#1E1B4B", "#A78BFA".toColorInt()) {
            canvasView?.resetViewToFit()
        }.apply { layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)).apply { marginEnd = dp(6) } }
        topBar.addView(btnFit)

        val btnAlign = createToolbarBtn("ВЫРОВНЯТЬ", "#0E3A4B", "#38BDF8".toColorInt()) {
            canvasView?.autoAlignNodes()
            Toast.makeText(context, "Узлы графа аккуратно выровнены", Toast.LENGTH_SHORT).show()
        }.apply { layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)).apply { marginEnd = dp(6) } }
        topBar.addView(btnAlign)

        val btnSave = createToolbarBtn("СОХРАНИТЬ", "#8B5CF6", Color.WHITE) {
            currentScenario?.let { scenarioRepository.saveGraphScenario(it) }
            Toast.makeText(context, "Граф сохранен", Toast.LENGTH_SHORT).show()
        }.apply { layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)).apply { marginEnd = dp(6) } }
        topBar.addView(btnSave)

        val btnClose = createToolbarBtn("ЗАКРЫТЬ", "#1B1430", "#F43F5E".toColorInt()) {
            dismiss()
        }.apply { layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(36)) }
        topBar.addView(btnClose)

        commandScroll.addView(topBar)
        topContainer.addView(commandScroll)

        // ПАЛИТРА ТИПОВЫХ ДЕЙСТВИЙ АВТОМАТИЗАЦИИ (ГОРИЗОНТАЛЬНЫЙ СКРОЛЛ)
        val paletteScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(4), 0, dp(2))
        }
        val paletteRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

// 1. ПРИОРИТЕТ 1: ШАБЛОНЫ И ЗРЕНИЕ (ПЕРВЫЕ В ПАЛИТРЕ)
        paletteRow.addView(createPaletteBtn("ПОИСК ШАБЛОНА", "#10B981") { addTypicalVisionNode(TriggerBehavior.WAIT_APPEAR) })
        paletteRow.addView(createPaletteBtn("ЖДАТЬ ИСЧЕЗНОВЕНИЯ", "#06B6D4") { addTypicalVisionNode(TriggerBehavior.WAIT_DISAPPEAR) })

        // 2. ПРИОРИТЕТ 2: БАЗОВЫЕ ЖЕСТЫ
        paletteRow.addView(createPaletteBtn("КЛИК", "#3B82F6") { addTypicalActionNode("Клик", ActionType.CLICK) })
        paletteRow.addView(createPaletteBtn("2x КЛИК", "#2563EB") { addTypicalDoubleTapNode() })
        paletteRow.addView(createPaletteBtn("УДЕРЖАНИЕ", "#1D4ED8") { addTypicalActionNode("Удержание", ActionType.LONG_PRESS, holdMs = 1200L) })
        paletteRow.addView(createPaletteBtn("СВАЙП", "#EC4899") { addTypicalSwipeNode() })

        // 3. ПРИОРИТЕТ 3: УПРАВЛЕНИЕ И ЛОГИКА
        paletteRow.addView(createPaletteBtn("РАНДОМ ПАУЗА", "#6B7280") { addTypicalRandomDelayNode() })
        paletteRow.addView(createPaletteBtn("ЦИКЛ (N РАЗ)", "#A855F7") { addTypicalLoopNode() })
        paletteRow.addView(createPaletteBtn("ПЕРЕХОД (GOTO)", "#F59E0B") { addTypicalGotoNode() })
        paletteRow.addView(createPaletteBtn("НАЗАД", "#F43F5E") { addTypicalSystemNode("Назад (Back)", ActionType.GLOBAL_BACK) })
        paletteRow.addView(createPaletteBtn("ДОМОЙ", "#38BDF8") { addTypicalSystemNode("Домой (Home)", ActionType.GLOBAL_HOME) })

        paletteScroll.addView(paletteRow)
        topContainer.addView(paletteScroll)

        // Добавление в изолированный вертикальный контейнер
        contentVertical.addView(topContainer, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        contentVertical.addView(canvas, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(contentVertical, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        overlayWindowManager.addViewSafe(root, overlayWindowManager.createLayoutParams(width = MATCH_PARENT, height = MATCH_PARENT))
    }

    private fun createToolbarBtn(title: String, bgColorHex: String, textColor: Int, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = title
            setTextColor(textColor)
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(bgColorHex.toColorInt())
                cornerRadius = dpF(6f)
            }
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(32)).apply { marginEnd = dp(4) }
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { onClick() }
        }
    }



    private fun createPaletteBtn(title: String, accentHex: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor("#1E1736".toColorInt())
                cornerRadius = dpF(5f)
                setStroke(dp(1), accentHex.toColorInt())
            }
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(26)).apply { marginEnd = dp(4) }
            setPadding(dp(6), 0, dp(6), 0)
            setOnClickListener { onClick() }
        }
    }

    private fun getNextNodeIdAndCoords(): Triple<String, Float, Float> {
        val sc = currentScenario ?: return Triple("node_1", 140f, 220f)
        var nextNum = sc.nodes.size + 1
        while (sc.nodes.containsKey("node_$nextNum")) nextNum++
        val posX = 80f + ((nextNum % 4) * 50f)
        val posY = 140f + ((nextNum % 4) * 40f)
        return Triple("node_$nextNum", posX, posY)
    }

    private fun addTypicalActionNode(name: String, type: ActionType, holdMs: Long = 120L) {
        val sc = currentScenario ?: return
        val (id, x, y) = getNextNodeIdAndCoords()
        val node = ScenarioNode(
            id = id,
            title = "$name (#${id.removePrefix("node_")})",
            canvasX = x, canvasY = y,
            entryActions = listOf(NodeActionSpec(id = "act_$id", type = type, x = 540f, y = 1200f, holdDurationMs = holdMs)),
            standardPorts = listOf("out_default")
        )
        insertNodeAndRefresh(sc, node)
    }

    private fun addTypicalDoubleTapNode() {
        val sc = currentScenario ?: return
        val (id, x, y) = getNextNodeIdAndCoords()
        val node = ScenarioNode(
            id = id,
            title = "2x Клик (#${id.removePrefix("node_")})",
            canvasX = x, canvasY = y,
            entryActions = listOf(
                NodeActionSpec("act_1_$id", ActionType.CLICK, 540f, 1200f, holdDurationMs = 50L, delayAfterMs = 90L),
                NodeActionSpec("act_2_$id", ActionType.CLICK, 540f, 1200f, holdDurationMs = 50L, delayAfterMs = 150L)
            ),
            standardPorts = listOf("out_default")
        )
        insertNodeAndRefresh(sc, node)
    }

    private fun addTypicalSwipeNode() {
        val sc = currentScenario ?: return
        val (id, x, y) = getNextNodeIdAndCoords()
        val node = ScenarioNode(
            id = id,
            title = "Свайп (#${id.removePrefix("node_")})",
            canvasX = x, canvasY = y,
            entryActions = listOf(
                NodeActionSpec("act_$id", ActionType.SWIPE, x = 540f, y = 1600f, endX = 540f, endY = 600f, holdDurationMs = 350L)
            ),
            standardPorts = listOf("out_default")
        )
        insertNodeAndRefresh(sc, node)
    }

    private fun addTypicalPauseNode() {
        val sc = currentScenario ?: return
        val (id, x, y) = getNextNodeIdAndCoords()
        val node = ScenarioNode(
            id = id,
            title = "Пауза 1.5с (#${id.removePrefix("node_")})",
            canvasX = x, canvasY = y,
            entryActions = listOf(
                NodeActionSpec("act_$id", ActionType.CLICK, 0f, 0f, holdDurationMs = 0L, delayAfterMs = 1500L)
            ),
            standardPorts = listOf("out_default")
        )
        insertNodeAndRefresh(sc, node)
    }

    private fun addTypicalVisionNode(behavior: TriggerBehavior) {
        val sc = currentScenario ?: return
        val (id, x, y) = getNextNodeIdAndCoords()
        val isDisappear = behavior == TriggerBehavior.WAIT_DISAPPEAR
        val trig = NodeTriggerSpec(
            behavior = behavior,
            id = "trig_$id",
            name = if (isDisappear) "Ждать исчезновения" else "Поиск шаблона",
            type = ActionType.TRIGGER,
            targetPortId = "out_match_trig_$id"
        )
        val node = ScenarioNode(
            id = id,
            title = if (isDisappear) "Ожидание исчезновения" else "Поиск объекта",
            canvasX = x, canvasY = y,
            triggers = listOf(trig),
            timeoutSeconds = 7,
            standardPorts = listOf("out_timeout")
        )
        insertNodeAndRefresh(sc, node)
    }

    private fun addTypicalGotoNode() {
        val sc = currentScenario ?: return
        val (id, x, y) = getNextNodeIdAndCoords()
        val node = ScenarioNode(
            id = id,
            title = "Переход GOTO",
            canvasX = x, canvasY = y,
            standardPorts = listOf("out_default")
        )
        insertNodeAndRefresh(sc, node)
    }

    private fun addTypicalSystemNode(name: String, actionType: ActionType) {
        val sc = currentScenario ?: return
        val (id, x, y) = getNextNodeIdAndCoords()
        val node = ScenarioNode(
            id = id,
            title = name,
            canvasX = x, canvasY = y,
            entryActions = listOf(NodeActionSpec("act_$id", actionType, 0f, 0f)),
            standardPorts = listOf("out_default")
        )
        insertNodeAndRefresh(sc, node)
    }

    private fun addTypicalRandomDelayNode() {
        val sc = currentScenario ?: return
        val (id, x, y) = getNextNodeIdAndCoords()
        val node = ScenarioNode(
            id = id,
            title = "Рандом пауза 1..3с",
            canvasX = x, canvasY = y,
            entryActions = listOf(NodeActionSpec("act_$id", ActionType.DELAY, 0f, 0f, delayAfterMs = 1000L, holdDurationMs = 3000L)),
            standardPorts = listOf("out_default")
        )
        insertNodeAndRefresh(sc, node)
    }

    private fun addTypicalLoopNode() {
        val sc = currentScenario ?: return
        val (id, x, y) = getNextNodeIdAndCoords()
        val node = ScenarioNode(
            id = id,
            title = "Счетчик цикла (x3)",
            canvasX = x, canvasY = y,
            entryActions = listOf(NodeActionSpec("act_$id", ActionType.CLICK, 0f, 0f, repeatCount = 3)),
            standardPorts = listOf("out_loop_body", "out_loop_done")
        )
        insertNodeAndRefresh(sc, node)
    }

        private fun insertNodeAndRefresh(sc: GraphMacroScenario, node: ScenarioNode) {
        try {
            val nodes = sc.nodes.toMutableMap()
            nodes[node.id] = node
            val updated = sc.copy(nodes = nodes)
            currentScenario = updated
            canvasView?.setGraphScenario(updated)
            scenarioRepository.saveGraphScenario(updated)
            AppLogger.log(context, "GRAPH_NODE", "Успешно добавлен узел [${node.id}] '${node.title}' (триггеров: ${node.triggers.size}, портов: ${node.standardPorts.size})")
            Toast.makeText(context, "Добавлен блок: ${node.title}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AppLogger.logError(context, "GRAPH_NODE_CRASH", e)
            Toast.makeText(context, "Ошибка добавления узла: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNodeInspector(node: ScenarioNode) {
        inspectorCard?.let { overlayContainerView?.removeView(it) }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(14)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor("#F817112B".toColorInt())
                setStroke(dp(1), "#8B5CF6".toColorInt())
                cornerRadius = dpF(16f)
            }
            elevation = dpF(24f)
        }
        inspectorCard = card


        val title = TextView(context).apply {
            text = "БЛОК: ${node.title}"
            setTextColor("#38BDF8".toColorInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        }
        card.addView(title)

                // [V12.4] СЕКЦИЯ ПРИВЯЗКИ ШАБЛОНОВ ДЛЯ УЗЛОВ ЗРЕНИЯ
        val isVisionNode = node.triggers.isNotEmpty() || node.title.contains("Поиск", ignoreCase = true) || node.title.contains("исчезновения", ignoreCase = true)
        if (isVisionNode) {
            val tvTemplateHeader = TextView(context).apply {
                text = "ШАБЛОНЫ И ТРИГГЕРЫ УЗЛА:"
                setTextColor("#10B981".toColorInt())
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(6), 0, dp(4))
            }
            card.addView(tvTemplateHeader)

            val currentTrig = node.triggers.firstOrNull()
            val templateName = if (currentTrig?.templatePath.isNullOrBlank()) "Шаблон не привязан" else File(currentTrig!!.templatePath).nameWithoutExtension

            val tvCurrentTpl = TextView(context).apply {
                text = "Текущий: $templateName"
                setTextColor(Color.WHITE)
                textSize = 9f
                setPadding(0, 0, 0, dp(4))
            }
            card.addView(tvCurrentTpl)

            val tplActionsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(6))
            }

            val btnPickTemplate = Button(context).apply {
                text = "ВЫБРАТЬ ИЗ БАЗЫ"
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor("#2563EB".toColorInt())
                    cornerRadius = dpF(6f)
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1.25f).apply { marginEnd = dp(4) }
                setOnClickListener {
                    overlayContainerView?.removeView(card)
                    showCustomTemplatePickerDialog(node, 0)
                }
            }
            tplActionsRow.addView(btnPickTemplate)

            // [V100.0] Кнопка добавления новой независимой ветки шаблона в узел
            val btnAddBranch = Button(context).apply {
                text = "[+] ВЕТКА"
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor("#059669".toColorInt())
                    cornerRadius = dpF(6f)
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 0.8f)
                setOnClickListener {
                    val newIdx = node.triggers.size + 1
                    val newTrigId = "trig_${node.id}_$newIdx"
                    val newTrig = com.example.autotap.domain.model.graph.NodeTriggerSpec(
                        id = newTrigId,
                        name = "Ветка #$newIdx",
                        type = ActionType.TRIGGER,
                        templatePath = "",
                        targetPortId = "out_match_$newTrigId",
                        similarityThreshold = 85
                    )
                    val updatedNode = node.copy(triggers = node.triggers + newTrig)
                    currentScenario?.let { sc ->
                        val nodesMap = sc.nodes.toMutableMap()
                        nodesMap[node.id] = updatedNode
                        val newSc = sc.copy(nodes = nodesMap)
                        currentScenario = newSc
                        canvasView?.setGraphScenario(newSc)
                        scenarioRepository.saveGraphScenario(newSc)
                    }
                    overlayContainerView?.removeView(card)
                    showCustomTemplatePickerDialog(updatedNode, node.triggers.size)
                }
            }
            tplActionsRow.addView(btnAddBranch)
            card.addView(tplActionsRow)
            }

            // [V100.0] Интерактивные степперы параметров цикла (repeatCount) и задержек
            val loopAction = node.entryActions.firstOrNull { it.repeatCount > 1 }
            val isLoopNode = loopAction != null || node.standardPorts.contains("out_loop_body") || node.title.contains("Цикл", ignoreCase = true)
            if (isLoopNode) {
            val tvParamHeader = TextView(context).apply {
                text = "ПАРАМЕТРЫ ЦИКЛА:"
                setTextColor("#C084FC".toColorInt())
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(6), 0, dp(4))
            }
            card.addView(tvParamHeader)

            val currentRepeats = loopAction?.repeatCount ?: 3
            val stepperRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(6))
            }
            val tvRepeatsLabel = TextView(context).apply {
                text = "Итераций: $currentRepeats"
                setTextColor(Color.WHITE)
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }
            val btnMinus = Button(context).apply {
                text = "-"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply { setColor("#21262D".toColorInt()); cornerRadius = dpF(4f) }
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(28)).apply { marginEnd = dp(6) }
                setOnClickListener {
                    val next = (currentRepeats - 1).coerceAtLeast(1)
                    val updatedActions = if (node.entryActions.isEmpty()) {
                        listOf(com.example.autotap.domain.model.graph.NodeActionSpec(id = "act_${node.id}", type = ActionType.CLICK, x = 540f, y = 1200f, repeatCount = next))
                    } else {
                        node.entryActions.map { it.copy(repeatCount = next) }
                    }
                    val updatedNode = node.copy(entryActions = updatedActions)
                    currentScenario?.let { sc ->
                        val nodesMap = sc.nodes.toMutableMap()
                        nodesMap[node.id] = updatedNode
                        val newSc = sc.copy(nodes = nodesMap)
                        currentScenario = newSc
                        canvasView?.setGraphScenario(newSc)
                        scenarioRepository.saveGraphScenario(newSc)
                    }
                    tvRepeatsLabel.text = "Итераций: $next"
                }
            }
            val btnPlus = Button(context).apply {
                text = "+"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply { setColor("#21262D".toColorInt()); cornerRadius = dpF(4f) }
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(28))
                setOnClickListener {
                    val next = (currentRepeats + 1).coerceAtMost(999)
                    val updatedActions = if (node.entryActions.isEmpty()) {
                        listOf(com.example.autotap.domain.model.graph.NodeActionSpec(id = "act_${node.id}", type = ActionType.CLICK, x = 540f, y = 1200f, repeatCount = next))
                    } else {
                        node.entryActions.map { it.copy(repeatCount = next) }
                    }
                    val updatedNode = node.copy(entryActions = updatedActions)
                    currentScenario?.let { sc ->
                        val nodesMap = sc.nodes.toMutableMap()
                        nodesMap[node.id] = updatedNode
                        val newSc = sc.copy(nodes = nodesMap)
                        currentScenario = newSc
                        canvasView?.setGraphScenario(newSc)
                        scenarioRepository.saveGraphScenario(newSc)
                    }
                    tvRepeatsLabel.text = "Итераций: $next"
                }
            }
            stepperRow.addView(tvRepeatsLabel)
            stepperRow.addView(btnMinus)
            stepperRow.addView(btnPlus)
            card.addView(stepperRow)
            }

        // РОУТЕР ВЕТВЛЕНИЙ (НАГЛЯДНАЯ МАРШРУТИЗАЦИЯ ВЫХОДОВ)
        val tvBranchHeader = TextView(context).apply {
            text = "МАРШРУТЫ ВЕТВЛЕНИЯ:"
            setTextColor("#A78BFA".toColorInt())
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(4))
        }
        card.addView(tvBranchHeader)

        val sc = currentScenario
        // [V85.0] Лаконичные метки портов против вытеснения и обрезки текста целевых узлов
        val allPorts = node.triggers.map { it.targetPortId to if (it.behavior == TriggerBehavior.WAIT_DISAPPEAR) "ИСЧЕЗЛО" else "НАЙДЕНО" } +
                node.standardPorts.map { it to (if (it == "out_timeout") "ТАЙМАУТ" else "ДАЛЕЕ") }

        for ((portId, portLabel) in allPorts) {
            val edge = sc?.edges?.firstOrNull { it.fromNodeId == node.id && it.fromPortId == portId }
            val targetTitle = sc?.nodes?.get(edge?.toNodeId)?.title ?: "НЕ ПРИВЯЗАН"

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }

            val tvLabel = TextView(context).apply {
                text = "$portLabel ->"
                setTextColor(if (portId == "out_timeout") "#F59E0B".toColorInt() else if (portId.startsWith("out_match")) "#10B981".toColorInt() else "#C084FC".toColorInt())
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.1f)
            }
            row.addView(tvLabel)

            val btnTarget = Button(context).apply {
                text = targetTitle
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                minHeight = 0; minimumHeight = 0
                setPadding(dp(4), 0, dp(4), 0)
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                background = GradientDrawable().apply {
                    setColor("#23183F".toColorInt())
                    cornerRadius = dpF(6f)
                    setStroke(dp(1), "#3E2A6E".toColorInt())
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1.4f)
                setOnClickListener {
                    showNodeSelectionDialog(node.id, portId)
                }
            }
            row.addView(btnTarget)
            card.addView(row)
        }

        val btnRowBottom = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }

        // [V110.0] Ликвидация лишней кнопки старта: точка входа задается протяжкой провода от узла СТАРТ
        val isPermanentStart = node.id == "node_start" || node.title == "СТАРТ"

        if (!isPermanentStart) {
            val btnDeleteNode = Button(context).apply {
                text = "УДАЛИТЬ УЗЕЛ"
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply { setColor("#F04438".toColorInt()); cornerRadius = dpF(6f) }
                setOnClickListener {
                    val curSc = currentScenario ?: return@setOnClickListener
                    if (curSc.nodes.size <= 1) {
                        Toast.makeText(context, "Нельзя удалить единственный узел", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val nodes = curSc.nodes.toMutableMap()
                    nodes.remove(node.id)
                    val edges = curSc.edges.filterNot { it.fromNodeId == node.id || it.toNodeId == node.id }
                    val newEntry = if (curSc.entryNodeId == node.id) nodes.keys.firstOrNull { it == "node_start" } ?: nodes.keys.first() else curSc.entryNodeId
                    val updated = curSc.copy(nodes = nodes, edges = edges, entryNodeId = newEntry)
                    currentScenario = updated
                    canvasView?.setGraphScenario(updated)
                    overlayContainerView?.removeView(card)
                }
            }
            btnRowBottom.addView(btnDeleteNode, LinearLayout.LayoutParams(0, dp(34), 1.1f).apply { marginEnd = dp(8) })

            val btnCloseInspector = Button(context).apply {
                text = "ЗАКРЫТЬ"
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply { setColor("#21262D".toColorInt()); cornerRadius = dpF(6f) }
                setOnClickListener { overlayContainerView?.removeView(card) }
            }
            btnRowBottom.addView(btnCloseInspector, LinearLayout.LayoutParams(0, dp(34), 0.9f))
        } else {
            val btnCloseStartNode = Button(context).apply {
                text = "ЗАКРЫТЬ"
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply { setColor("#21262D".toColorInt()); cornerRadius = dpF(6f) }
                setOnClickListener { overlayContainerView?.removeView(card) }
            }
            btnRowBottom.addView(btnCloseStartNode, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)))
        }

        card.addView(btnRowBottom)

        val lp = FrameLayout.LayoutParams(dp(320), WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(16) }
        overlayContainerView?.addView(card, lp)
    }

            private var nodeSelectCard: android.view.View? = null

    private fun showNodeSelectionDialog(fromNodeId: String, fromPortId: String) {
        nodeSelectCard?.let { overlayContainerView?.removeView(it) }
        val sc = currentScenario ?: return
        val candidateNodes = sc.nodes.values.filter { it.id != fromNodeId }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(14)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor("#F816112C".toColorInt())
                setStroke(dp(1), "#8B5CF6".toColorInt())
                cornerRadius = dpF(12f)
            }
            elevation = dpF(30f)
        }
        nodeSelectCard = card

        val tvH = TextView(context).apply {
            text = "ВЫБЕРИТЕ ЦЕЛЕВОЙ ШАГ"
            setTextColor("#A78BFA".toColorInt())
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        }
        card.addView(tvH)

        val btnUnbind = Button(context).apply {
            text = "ОТВЯЗАТЬ (НЕТ СВЯЗИ)"
            textSize = 8.5f
            setTextColor("#F43F5E".toColorInt())
            background = GradientDrawable().apply {
                setColor("#261420".toColorInt())
                cornerRadius = dpF(4f)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(30)).apply { bottomMargin = dp(6) }
            setOnClickListener {
                val edges = sc.edges.filterNot { it.fromNodeId == fromNodeId && it.fromPortId == fromPortId }.toMutableList()
                val updated = sc.copy(edges = edges)
                currentScenario = updated
                canvasView?.setGraphScenario(updated)
                overlayContainerView?.removeView(card)
                nodeSelectCard = null
                sc.nodes[fromNodeId]?.let { showNodeInspector(it) }
                Toast.makeText(context, "Ветка отвязана", Toast.LENGTH_SHORT).show()
            }
        }
        card.addView(btnUnbind)

        val scroll = android.widget.ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(180))
        }
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        candidateNodes.forEach { target ->
            val b = Button(context).apply {
                text = target.title
                textSize = 9f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor("#23183F".toColorInt())
                    cornerRadius = dpF(4f)
                    setStroke(dp(1), "#3E2A6E".toColorInt())
                }
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(30)).apply { bottomMargin = dp(4) }
                setOnClickListener {
                    val edges = sc.edges.filterNot { it.fromNodeId == fromNodeId && it.fromPortId == fromPortId }.toMutableList()
                    edges.add(ScenarioEdge("edge_${UUID.randomUUID().toString().take(6)}", fromNodeId, fromPortId, target.id, "in_entry"))
                    val updated = sc.copy(edges = edges)
                    currentScenario = updated
                    canvasView?.setGraphScenario(updated)
                    overlayContainerView?.removeView(card)
                    nodeSelectCard = null
                    sc.nodes[fromNodeId]?.let { showNodeInspector(it) }
                    Toast.makeText(context, "Связано с: ${target.title}", Toast.LENGTH_SHORT).show()
                }
            }
            list.addView(b)
        }
        scroll.addView(list)
        card.addView(scroll)

                val lp = FrameLayout.LayoutParams(dp(310), WRAP_CONTENT, Gravity.CENTER)
        overlayContainerView?.addView(card, lp)
    }

            private var templatePickerCard: android.view.View? = null

    private fun showCustomTemplatePickerDialog(node: ScenarioNode, trigIdx: Int) {
        templatePickerCard?.let { overlayContainerView?.removeView(it) }
        val tplRepo = TemplateRepositoryImpl(context)
        val allTpls = tplRepo.loadAllTemplates()
        if (allTpls.isEmpty()) {
            Toast.makeText(context, "В базе нет сохраненных шаблонов", Toast.LENGTH_SHORT).show()
            return
        }

        AppLogger.log(context, "GRAPH_TEMPLATE", "Открытие оверлей-селектора шаблонов для узла [${node.id}] '${node.title}', индекс триггера: $trigIdx, доступно масок: ${allTpls.size}")

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(14)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor("#F816112C".toColorInt())
                setStroke(dp(1), "#10B981".toColorInt())
                cornerRadius = dpF(16f)
            }
            elevation = dpF(30f)
        }
        templatePickerCard = card

        // [V95.0] Мультивыбор шаблонов (Мультипоиск) в графовом редакторе
        val curTrig = node.triggers.getOrNull(trigIdx)
        val selectedPaths = LinkedHashSet<String>()
        curTrig?.multiTemplatePaths?.filter { it.isNotBlank() }?.let { selectedPaths.addAll(it) }
        if (selectedPaths.isEmpty() && !curTrig?.templatePath.isNullOrBlank()) {
            selectedPaths.add(curTrig!!.templatePath)
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        val tvHeader = TextView(context).apply {
            text = "МУЛЬТИВЫБОР ШАБЛОНОВ (ИИ)"
            setTextColor("#10B981".toColorInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        headerRow.addView(tvHeader)

        val tvSelectedBadge = TextView(context).apply {
            text = "ВЫБРАНО: ${selectedPaths.size}"
            setTextColor("#38BDF8".toColorInt())
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                setColor("#1E293B".toColorInt())
                cornerRadius = dpF(4f)
                setStroke(dp(1), "#0284C7".toColorInt())
            }
        }
        headerRow.addView(tvSelectedBadge)
        card.addView(headerRow)

        val scroll = android.widget.ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(240))
        }
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val itemCardsMap = LinkedHashMap<String, LinearLayout>()

        allTpls.forEach { (path, bmp) ->
            val name = File(path).nameWithoutExtension
            val metaMap = tplRepo.getTemplateMetadata(path)
            val metaSim = (metaMap?.get("similarityPercent") as? Number)?.toInt() ?: 85
            val isShape = (metaMap?.get("isShapeOnlyMode") as? Boolean) ?: false

            val itemCard = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = dp(6)
                setPadding(p, p, p, p)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) }
            }

            fun updateItemCardState(isSelected: Boolean) {
                itemCard.background = GradientDrawable().apply {
                    setColor(if (isSelected) "#243E36".toColorInt() else "#1E1736".toColorInt())
                    cornerRadius = dpF(6f)
                    setStroke(dp(1), if (isSelected) "#10B981".toColorInt() else "#3E2A6E".toColorInt())
                }
            }
            updateItemCardState(selectedPaths.contains(path))
            itemCardsMap[path] = itemCard

            val ivThumb = android.widget.ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) }
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                background = GradientDrawable().apply {
                    setColor("#0B0813".toColorInt())
                    cornerRadius = dpF(4f)
                }
                setImageBitmap(bmp)
            }
            itemCard.addView(ivThumb)

            val infoCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }
            val tvName = TextView(context).apply {
                text = name
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }
            infoCol.addView(tvName)

            val tvSub = TextView(context).apply {
                text = "${bmp.width}x${bmp.height} px • ${if (isShape) "ФОРМА" else "ГИБРИД"} • $metaSim%"
                textSize = 8f
                setTextColor("#9E95B8".toColorInt())
            }
            infoCol.addView(tvSub)
            itemCard.addView(infoCol)

            itemCard.setOnClickListener {
                if (selectedPaths.contains(path)) {
                    if (selectedPaths.size > 1) {
                        selectedPaths.remove(path)
                        updateItemCardState(false)
                    } else {
                        Toast.makeText(context, "Должен остаться хотя бы один шаблон", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    selectedPaths.add(path)
                    updateItemCardState(true)
                }
                tvSelectedBadge.text = "ВЫБРАНО: ${selectedPaths.size}"
            }

            list.addView(itemCard)
        }
        scroll.addView(list)
        card.addView(scroll)

        val btnActionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
        }

        val btnApply = Button(context).apply {
            text = "ПРИМЕНИТЬ ВЫБОР"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                setColor("#10B981".toColorInt())
                cornerRadius = dpF(6f)
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1.2f).apply { marginEnd = dp(6) }
            setOnClickListener {
                if (selectedPaths.isEmpty()) {
                    Toast.makeText(context, "Выберите хотя бы один шаблон", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val primaryPath = selectedPaths.first()
                val pathsList = selectedPaths.toList()
                val metaMap = tplRepo.getTemplateMetadata(primaryPath)
                val metaSim = (metaMap?.get("similarityPercent") as? Number)?.toInt() ?: 85
                val isShape = (metaMap?.get("isShapeOnlyMode") as? Boolean) ?: false
                val baseName = File(primaryPath).nameWithoutExtension.removePrefix("mask_").take(12)
                val compositeName = if (pathsList.size > 1) "$baseName (+${pathsList.size - 1})" else baseName

                val updatedTriggers = if (node.triggers.isEmpty()) {
                    listOf(NodeTriggerSpec(id = "trig_${node.id}", name = compositeName, type = ActionType.TRIGGER, templatePath = primaryPath, multiTemplatePaths = pathsList, targetPortId = "out_match_trig_${node.id}", similarityThreshold = metaSim, isShapeOnlyMode = isShape))
                } else {
                    node.triggers.mapIndexed { idx, t ->
                        if (idx == trigIdx) {
                            t.copy(templatePath = primaryPath, multiTemplatePaths = pathsList, name = compositeName, similarityThreshold = metaSim, isShapeOnlyMode = isShape)
                        } else t
                    }
                }
                val updatedNode = node.copy(triggers = updatedTriggers)
                currentScenario?.let { sc ->
                    val nodesMap = sc.nodes.toMutableMap()
                    nodesMap[node.id] = updatedNode
                    val newSc = sc.copy(nodes = nodesMap)
                    currentScenario = newSc
                    canvasView?.setGraphScenario(newSc)
                    scenarioRepository.saveGraphScenario(newSc)
                }
                overlayContainerView?.removeView(card)
                templatePickerCard = null
                showNodeInspector(updatedNode)
                Toast.makeText(context, "Привязано шаблонов: ${pathsList.size}", Toast.LENGTH_SHORT).show()
            }
        }
        btnActionsRow.addView(btnApply)

        val btnCancel = Button(context).apply {
            text = "ОТМЕНА"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor("#F43F5E".toColorInt())
            background = GradientDrawable().apply {
                setColor("#2E1218".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#E11D48".toColorInt())
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 0.8f)
            setOnClickListener {
                overlayContainerView?.removeView(card)
                templatePickerCard = null
            }
        }
        btnActionsRow.addView(btnCancel)
        card.addView(btnActionsRow)

        val lp = FrameLayout.LayoutParams(dp(330), WRAP_CONTENT, Gravity.CENTER)
        overlayContainerView?.addView(card, lp)
    }

            private var cascadeMenuCard: android.view.View? = null

        // [V66.0] 100% очистка от эмодзи, надежные отступы диалога и защита от срезания кнопок
        // [V70.0] Гармонизированное меню действий в едином стиле AutoTap (Clean Material 3)
        private fun showWireToEmptyMenu(fromNodeId: String, fromPortId: String, worldX: Float, worldY: Float) {
            val categories = listOf(
                "КОМПЬЮТЕРНОЕ ЗРЕНИЕ (OPENCV ⇄ AI)" to {
                    showCascadeCategoryDialog("КОМПЬЮТЕРНОЕ ЗРЕНИЕ", listOf(
                        "ПОИСК ШАБЛОНА (ПОЯВЛЕНИЕ)" to { spawnAndConnectNode(fromNodeId, fromPortId, worldX, worldY, "ИИ Поиск", ActionType.TRIGGER, com.example.autotap.domain.model.graph.TriggerBehavior.WAIT_APPEAR) },
                        "ОЖИДАНИЕ ИСЧЕЗНОВЕНИЯ" to { spawnAndConnectNode(fromNodeId, fromPortId, worldX, worldY, "ИИ Исчезновение", ActionType.TRIGGER, com.example.autotap.domain.model.graph.TriggerBehavior.WAIT_DISAPPEAR) },
                        "ПРОВЕРКА ЦВЕТА (ПИПЕТКА)" to { spawnAndConnectNode(fromNodeId, fromPortId, worldX, worldY, "Цвет", ActionType.COLOR_CHECK, com.example.autotap.domain.model.graph.TriggerBehavior.WAIT_APPEAR) }
                    ))
                },
                "ФИЗИЧЕСКИЕ ЖЕСТЫ" to {
                    showCascadeCategoryDialog("ФИЗИЧЕСКИЕ ЖЕСТЫ", listOf(
                        "ОДИНОЧНЫЙ ТАП (КЛИК)" to { spawnAndConnectNode(fromNodeId, fromPortId, worldX, worldY, "Клик", ActionType.CLICK) },
                        "ДВОЙНОЙ ТАП (2x КЛИК)" to { spawnAndConnectNode(fromNodeId, fromPortId, worldX, worldY, "2x Клик", ActionType.CLICK) },
                        "СВАЙП / СКОЛЬЖЕНИЕ" to { spawnAndConnectNode(fromNodeId, fromPortId, worldX, worldY, "Свайп", ActionType.SWIPE) },
                        "ДОЛГОЕ НАЖАТИЕ" to { spawnAndConnectNode(fromNodeId, fromPortId, worldX, worldY, "Удержание", ActionType.LONG_PRESS) }
                    ))
                },
                "УПРАВЛЕНИЕ И ЛОГИКА" to {
                    showCascadeCategoryDialog("УПРАВЛЕНИЕ И ЛОГИКА", listOf(
                        "ПАУЗА / ЗАДЕРЖКА" to { spawnAndConnectNode(fromNodeId, fromPortId, worldX, worldY, "Пауза", ActionType.DELAY) },
                        "СЛУЧАЙНЫЙ ДЕЛАЙ" to { spawnAndConnectNode(fromNodeId, fromPortId, worldX, worldY, "Рандом пауза", ActionType.DELAY) },
                        "ЦИКЛ (N РАЗ)" to { spawnAndConnectNode(fromNodeId, fromPortId, worldX, worldY, "Цикл", ActionType.CLICK) }
                    ))
                },
                "СИСТЕМНЫЕ КОМАНДЫ" to {
                    showCascadeCategoryDialog("СИСТЕМНЫЕ КОМАНДЫ", listOf(
                        "КНОПКА НАЗАД (BACK)" to { spawnAndConnectNode(fromNodeId, fromPortId, worldX, worldY, "Назад", ActionType.GLOBAL_BACK) },
                        "КНОПКА ДОМОЙ (HOME)" to { spawnAndConnectNode(fromNodeId, fromPortId, worldX, worldY, "Домой", ActionType.GLOBAL_HOME) }
                    ))
                }
            )
            showCascadeCategoryDialog("ДОБАВИТЬ УЗЕЛ В ГРАФ", categories)
            }

            private fun spawnAndConnectNode(
            fromNodeId: String, fromPortId: String, worldX: Float, worldY: Float,
            name: String, type: ActionType, behavior: com.example.autotap.domain.model.graph.TriggerBehavior? = null
            ) {
            val sc = currentScenario ?: return
            val fromNode = sc.nodes[fromNodeId]
            var nextNum = sc.nodes.size + 1
            while (sc.nodes.containsKey("node_$nextNum")) nextNum++
            val newId = "node_$nextNum"

            // [V70.0] Истинная анти-коллизия: строгое назначение вычисленных targetX и targetY
            val targetX = if (fromNode != null && worldX < fromNode.canvasX + dpF(300f)) fromNode.canvasX + dpF(340f) else worldX
            var targetY = worldY
            while (sc.nodes.values.any { kotlin.math.abs(it.canvasX - targetX) < dpF(280f) && kotlin.math.abs(it.canvasY - targetY) < dpF(160f) }) {
            targetY += dpF(140f)
            }

            val newNode = if (type == ActionType.TRIGGER || type == ActionType.COLOR_CHECK) {
            val trig = com.example.autotap.domain.model.graph.NodeTriggerSpec(
                id = "trig_${newId}_1",
                name = "$name #$nextNum",
                type = type,
                behavior = behavior ?: com.example.autotap.domain.model.graph.TriggerBehavior.WAIT_APPEAR,
                similarityThreshold = 85,
                targetPortId = "out_match",
                targetQueryOrColor = if (type == ActionType.COLOR_CHECK) "#38BDF8" else ""
            )
            ScenarioNode(
                id = newId,
                title = "$name #$nextNum",
                canvasX = targetX,
                canvasY = targetY,
                triggers = listOf(trig),
                standardPorts = listOf("timeout")
            )
            } else {
            ScenarioNode(
                id = newId,
                title = "$name #$nextNum",
                canvasX = targetX,
                canvasY = targetY,
                entryActions = listOf(com.example.autotap.domain.model.graph.NodeActionSpec(id = "act_$newId", type = type, x = 540f, y = 1200f)),
                standardPorts = listOf("out_default")
            )
            }

            val newEdge = ScenarioEdge(
            id = "edge_${java.util.UUID.randomUUID().toString().take(6)}",
            fromNodeId = fromNodeId,
            fromPortId = fromPortId,
            toNodeId = newId,
            toPortId = "in_entry"
            )

            val updatedNodes = sc.nodes.toMutableMap().apply { put(newId, newNode) }
            val updatedEdges = sc.edges.toMutableList().apply {
            removeAll { it.fromNodeId == fromNodeId && it.fromPortId == fromPortId }
            add(newEdge)
            }
            val updatedSc = sc.copy(nodes = updatedNodes, edges = updatedEdges)
            currentScenario = updatedSc
            canvasView?.setGraphScenario(updatedSc)
            scenarioRepository.saveGraphScenario(updatedSc)
            }

            private fun showCascadeCategoryDialog(categoryTitle: String, items: List<Pair<String, () -> Unit>>) {
        cascadeMenuCard?.let { overlayContainerView?.removeView(it) }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(14)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor("#F816112C".toColorInt())
                setStroke(dp(1), "#8B5CF6".toColorInt())
                cornerRadius = dpF(12f)
            }
            elevation = dpF(30f)
        }
        cascadeMenuCard = card

        val tvHeader = TextView(context).apply {
            text = categoryTitle
            setTextColor("#A78BFA".toColorInt())
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        }
        card.addView(tvHeader)

        items.forEach { (actionTitle, onSelected) ->
            val btn = Button(context).apply {
                text = actionTitle
                textSize = 9f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor("#23183F".toColorInt())
                    cornerRadius = dpF(4f)
                    setStroke(dp(1), "#3E2A6E".toColorInt())
                }
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(32)).apply { bottomMargin = dp(4) }
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener {
                    overlayContainerView?.removeView(card)
                    cascadeMenuCard = null
                    onSelected()
                }
            }
            card.addView(btn)
        }

        val btnCancel = Button(context).apply {
            text = "ОТМЕНА"
            textSize = 8.5f
            setTextColor("#F43F5E".toColorInt())
            background = GradientDrawable().apply {
                setColor("#2E1218".toColorInt())
                cornerRadius = dpF(4f)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(28)).apply { topMargin = dp(4) }
            setOnClickListener {
                overlayContainerView?.removeView(card)
                cascadeMenuCard = null
            }
        }
        card.addView(btnCancel)

        val lp = FrameLayout.LayoutParams(dp(260), WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(90)
        }
        overlayContainerView?.addView(card, lp)
    }

    fun dismiss() {
        overlayContainerView?.let { overlayWindowManager.removeViewSafe(it) }
        overlayContainerView = null
    }
}
