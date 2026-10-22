package com.example.autotap.infrastructure.overlay.panel

import android.content.Context
import android.graphics.RectF
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autotap.domain.model.ActionType
import com.example.autotap.infrastructure.overlay.AbstractComposeOverlay
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.model.OverlayVisibilityMode
import com.example.autotap.infrastructure.overlay.model.PanelDisplayMode
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class ControlPanelState(
    val mode: PanelDisplayMode = PanelDisplayMode.EXPANDED,
    val isPlaying: Boolean = false,
    val visibilityMode: OverlayVisibilityMode = OverlayVisibilityMode.FULL,
    val isNumbersHidden: Boolean = false,
    val showAddActionMenu: Boolean = false,
    val showRecordMenu: Boolean = false
)

class ControlPanelComposeOverlay(
    context: Context,
    overlayWindowManager: OverlayWindowManager,
    private val listener: ControlPanelListener
) : AbstractComposeOverlay(context, overlayWindowManager) {

    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()

    private val overlayState = MutableStateFlow(ControlPanelState())
    private var posX = dp(20)
    private var posY = dp(140)

    override fun getLayoutParams(): WindowManager.LayoutParams {
        val params = super.getLayoutParams()
        params.x = posX
        params.y = posY
        return params
    }

    fun getTutorialRect(tagKey: String): android.graphics.Rect? {
        val btnW = dp(30)
        val btnH = dp(30)
        val pad = dp(8)
        val margin = dp(4)

        val colIndex = when (tagKey) {
            "BTN_PLAY_VIEW" -> 0
            "BTN_CAPTURE" -> 1
            "BTN_ADD" -> 2
            "BTN_RECORD" -> 3
            "BTN_TOGGLE_VIEW" -> 4
            "BTN_TUTORIAL", "BTN_HIDE" -> 5
            "BTN_SETTINGS" -> 6
            "BTN_CLOSE" -> 7
            else -> -1
        }

        if (colIndex >= 0) {
            val left = posX + pad + colIndex * (btnW + margin)
            val top = posY + pad
            return android.graphics.Rect(left, top, left + btnW, top + btnH)
        }

        val subRowIndex = when (tagKey) {
            "BTN_GRAPH" -> 0
            "BTN_DEBUG" -> 1
            "BTN_SCRIPTS" -> 2
            "BTN_LOCK" -> 3
            "BTN_CLEAR", "BTN_WIPE" -> 4
            "BTN_LOGS" -> 5
            "BTN_HELP" -> 6
            else -> -1
        }

        if (subRowIndex >= 0) {
            val left = posX + pad + subRowIndex * (btnW + margin)
            val top = posY + pad + btnH + margin
            return android.graphics.Rect(left, top, left + btnW, top + btnH)
        }

        return android.graphics.Rect(posX, posY, posX + dp(280), posY + dp(80))
    }

    fun updateVisibilityMode(mode: OverlayVisibilityMode) {
        overlayState.update { it.copy(
            visibilityMode = mode,
            isNumbersHidden = (mode == OverlayVisibilityMode.HIDDEN)
        ) }
    }

    fun updateNumbersHiddenState(isHidden: Boolean) {
        updateVisibilityMode(if (isHidden) OverlayVisibilityMode.HIDDEN else OverlayVisibilityMode.FULL)
    }

    fun setPlayState(isPlaying: Boolean) {
        overlayState.update { it.copy(isPlaying = isPlaying) }
    }

    fun applyDisplayMode(mode: PanelDisplayMode) {
        overlayState.update { it.copy(mode = mode) }
    }

    override fun hide() {
        super.hide()
    }

    val containerView: android.view.View? get() = super.composeView

    // Accessor for the view to allow WindowManager updates inside Compose drag
    private val panelView: android.view.View? get() = super.composeView

    @Composable
    override fun Content() {
        val state by overlayState.collectAsState()

        val globalAlpha = if (state.visibilityMode == OverlayVisibilityMode.TRANSPARENT) 0.40f else 1.0f

        var lastDragUpdateTime by remember { mutableLongStateOf(0L) }

        val dragModifier = Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { /* vibrate slightly? */ },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val maxX = (dm.widthPixels - dp(50)).coerceAtLeast(0)
                    posX = (posX + dragAmount.x.toInt()).coerceIn(0, maxX)
                    posY = (posY + dragAmount.y.toInt()).coerceIn(dp(20), dm.heightPixels - dp(80))

                    val now = System.currentTimeMillis()
                    if (now - lastDragUpdateTime >= 16L) {
                        lastDragUpdateTime = now
                        panelView?.let { overlayWindowManager.updateViewSafe(it, getLayoutParams()) }
                    }
                }
            )
        }

        Box(modifier = Modifier.alpha(globalAlpha)) {
            when (state.mode) {
                PanelDisplayMode.MINI_BUBBLE -> MiniBubble(state, modifier = dragModifier)
                PanelDisplayMode.COMPACT_SINGLE_ROW, PanelDisplayMode.EXPANDED -> ExpandedPanel(state, modifier = dragModifier)
            }
        }
    }

    @Composable
    private fun MiniBubble(state: ControlPanelState, modifier: Modifier = Modifier) {
        Row(
            modifier = modifier
                .background(
                    brush = Brush.verticalGradient(listOf(Color(0xFF161B22), Color(0xFF0D1117))),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(20.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayPauseButton(
                isPlaying = state.isPlaying,
                size = 30.dp,
                onClick = { listener.onPlayClicked() },
                onLongClick = { listener.onPlayLongClicked() }
            )
            Spacer(modifier = Modifier.width(4.dp))
            VectorIconButton(
                icon = VectorIconDrawer.IconType.PANEL_COLLAPSE,
                size = 30.dp,
                bgStart = Color(0xFF1F2937),
                bgEnd = Color(0xFF111827),
                stroke = Color(0xFF374151),
                iconColor = Color(0xFF94A3B8)
            ) {
                applyDisplayMode(PanelDisplayMode.COMPACT_SINGLE_ROW)
            }
        }
    }

    @Composable
    private fun ExpandedPanel(state: ControlPanelState, modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .background(
                    brush = Brush.verticalGradient(listOf(Color(0xF0161B22), Color(0xF00D1117))),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(1.dp, Color(0xFF262D3D), RoundedCornerShape(20.dp))
                .padding(6.dp)
        ) {
            // MAIN ROW
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayPauseButton(
                    isPlaying = state.isPlaying,
                    size = 30.dp,
                    onClick = { listener.onPlayClicked() },
                    onLongClick = { listener.onPlayLongClicked() }
                )
                Spacer(modifier = Modifier.width(4.dp))

                // 2. CAPTURE BUTTON (Сразу за кнопкой Play!)
                VectorIconButton(
                    icon = VectorIconDrawer.IconType.CAPTURE,
                    size = 30.dp,
                    bgStart = Color(0xFF162032),
                    bgEnd = Color(0xFF0F172A),
                    stroke = Color(0xFF38BDF8),
                    iconColor = Color(0xFF38BDF8)
                ) {
                    listener.onCaptureClicked()
                }
                Spacer(modifier = Modifier.width(4.dp))

                // 3. PLUS BUTTON (Добавить мишень / меню действий)
                VectorIconButton(
                    icon = VectorIconDrawer.IconType.PLUS,
                    size = 30.dp,
                    bgStart = Color(0xFF162032),
                    bgEnd = Color(0xFF0F172A),
                    stroke = Color(0xFF38BDF8),
                    iconColor = Color(0xFF58A6FF)
                ) {
                    overlayState.update { it.copy(showAddActionMenu = !it.showAddActionMenu, showRecordMenu = false) }
                }
                Spacer(modifier = Modifier.width(4.dp))

                // 4. RECORD BUTTON (Запись жестов)
                VectorIconButton(
                    icon = VectorIconDrawer.IconType.RECORD,
                    size = 30.dp,
                    bgStart = Color(0xFF3A1016),
                    bgEnd = Color(0xFF1C080F),
                    stroke = Color(0xFFF04438),
                    iconColor = Color(0xFFF43F5E)
                ) {
                    overlayState.update { it.copy(showRecordMenu = !it.showRecordMenu, showAddActionMenu = false) }
                }
                Spacer(modifier = Modifier.width(4.dp))

                // 5. Expand/Collapse Mode Toggle
                val isExpanded = state.mode == PanelDisplayMode.EXPANDED
                VectorIconButton(
                    icon = if (isExpanded) VectorIconDrawer.IconType.PANEL_COLLAPSE else VectorIconDrawer.IconType.PANEL_EXPAND,
                    size = 30.dp,
                    bgStart = Color(0xFF1F2937),
                    bgEnd = Color(0xFF111827),
                    stroke = Color(0xFF374151),
                    iconColor = Color(0xFF94A3B8)
                ) {
                    applyDisplayMode(if (isExpanded) PanelDisplayMode.COMPACT_SINGLE_ROW else PanelDisplayMode.EXPANDED)
                }
                Spacer(modifier = Modifier.width(4.dp))

                // 6. Eye Toggle (Zero-Allocation Direct Color Mapping)
                val eyeBgStart = when (state.visibilityMode) {
                    OverlayVisibilityMode.FULL -> Color(0xFF162032)
                    OverlayVisibilityMode.TRANSPARENT -> Color(0xFF2E123D)
                    OverlayVisibilityMode.HIDDEN -> Color(0xFF3B2606)
                }
                val eyeBgEnd = when (state.visibilityMode) {
                    OverlayVisibilityMode.FULL -> Color(0xFF0F172A)
                    OverlayVisibilityMode.TRANSPARENT -> Color(0xFF170924)
                    OverlayVisibilityMode.HIDDEN -> Color(0xFF1F1403)
                }
                val eyeStroke = when (state.visibilityMode) {
                    OverlayVisibilityMode.FULL -> Color(0xFF38BDF8)
                    OverlayVisibilityMode.TRANSPARENT -> Color(0xFFA78BFA)
                    OverlayVisibilityMode.HIDDEN -> Color(0xFFF59E0B)
                }
                val eyeIconColor = when (state.visibilityMode) {
                    OverlayVisibilityMode.FULL -> Color(0xFF38BDF8)
                    OverlayVisibilityMode.TRANSPARENT -> Color(0xFFA78BFA)
                    OverlayVisibilityMode.HIDDEN -> Color(0xFFFBBF24)
                }

                VectorIconButton(
                    icon = VectorIconDrawer.IconType.VISIBILITY,
                    size = 30.dp,
                    bgStart = eyeBgStart,
                    bgEnd = eyeBgEnd,
                    stroke = eyeStroke,
                    iconColor = eyeIconColor
                ) {
                    listener.onToggleNumbersClicked()
                }
                Spacer(modifier = Modifier.width(4.dp))

                // 7. SETTINGS
                VectorIconButton(
                    icon = VectorIconDrawer.IconType.SETTINGS,
                    size = 30.dp,
                    bgStart = Color(0xFF1F2937),
                    bgEnd = Color(0xFF111827),
                    stroke = Color(0xFF4B5563),
                    iconColor = Color(0xFF9CA3AF)
                ) {
                    listener.onSettingsClicked()
                }
                Spacer(modifier = Modifier.width(4.dp))

                // 8. CLOSE OVERLAY BUTTON (Крестик закрытия оверлея справа сверху)
                VectorIconButton(
                    icon = VectorIconDrawer.IconType.CLOSE,
                    size = 30.dp,
                    bgStart = Color(0xFF2A0E0C),
                    bgEnd = Color(0xFF1C0908),
                    stroke = Color(0xFF7F1D1D),
                    iconColor = Color(0xFFF04438)
                ) {
                    listener.onCloseClicked()
                }
            }

            // ADD ACTION TYPE SELECTION MENU
            AnimatedVisibility(visible = state.showAddActionMenu) {
                ActionTypeSelectionMenu(
                    onSelect = { type ->
                        listener.onAddActionSelected(type)
                    },
                    onDismiss = {
                        overlayState.update { it.copy(showAddActionMenu = false) }
                    }
                )
            }

            // RECORD MODE SELECTION MENU
            AnimatedVisibility(visible = state.showRecordMenu) {
                RecordModeSelectionMenu(
                    onSmartRecord = { listener.onSmartRecordClicked() },
                    onNormalRecord = { listener.onRecordClicked() },
                    onDismiss = {
                        overlayState.update { it.copy(showRecordMenu = false) }
                    }
                )
            }

            // SUB ROW
            AnimatedVisibility(visible = state.mode == PanelDisplayMode.EXPANDED) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. GRAPH EDITOR
                    VectorIconButton(
                        icon = VectorIconDrawer.IconType.GRAPH_TREE,
                        size = 30.dp,
                        bgStart = Color(0xFF162032),
                        bgEnd = Color(0xFF0F172A),
                        stroke = Color(0xFF38BDF8),
                        iconColor = Color(0xFF38BDF8)
                    ) {
                        listener.onGraphClicked()
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // 2. STEP DEBUGGER
                    VectorIconButton(
                        icon = VectorIconDrawer.IconType.PLAY,
                        size = 30.dp,
                        bgStart = Color(0xFF1E1B4B),
                        bgEnd = Color(0xFF0F172A),
                        stroke = Color(0xFF818CF8),
                        iconColor = Color(0xFFA78BFA)
                    ) {
                        listener.onPlayLongClicked()
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // 3. SCRIPTS MANAGER
                    VectorIconButton(
                        icon = VectorIconDrawer.IconType.SCRIPTS,
                        size = 30.dp,
                        bgStart = Color(0xFF0D2E3A),
                        bgEnd = Color(0xFF061A24),
                        stroke = Color(0xFF0369A1),
                        iconColor = Color(0xFF38BDF8)
                    ) {
                        listener.onScriptsClicked()
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // 4. LOCK
                    VectorIconButton(
                        icon = VectorIconDrawer.IconType.LOCK,
                        size = 30.dp,
                        bgStart = Color(0xFF1F2937),
                        bgEnd = Color(0xFF111827),
                        stroke = Color(0xFF374151),
                        iconColor = Color(0xFF94A3B8)
                    ) {
                        listener.onScreenLockClicked()
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // 5. CLEAR ALL
                    VectorIconButton(
                        icon = VectorIconDrawer.IconType.CLEAR,
                        size = 30.dp,
                        bgStart = Color(0xFF2A0E0C),
                        bgEnd = Color(0xFF1C0908),
                        stroke = Color(0xFF7F1D1D),
                        iconColor = Color(0xFFF04438)
                    ) {
                        listener.onClearAllClicked()
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // 6. LOGS
                    VectorIconButton(
                        icon = VectorIconDrawer.IconType.LOGS,
                        size = 30.dp,
                        bgStart = Color(0xFF1F2937),
                        bgEnd = Color(0xFF111827),
                        stroke = Color(0xFF4B5563),
                        iconColor = Color(0xFF38BDF8)
                    ) {
                        listener.onLogsClicked()
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // 7. HELP / TUTORIAL
                    VectorIconButton(
                        icon = VectorIconDrawer.IconType.HELP,
                        size = 30.dp,
                        bgStart = Color(0xFF1F2937),
                        bgEnd = Color(0xFF111827),
                        stroke = Color(0xFF4B5563),
                        iconColor = Color(0xFF9CA3AF)
                    ) {
                        listener.onHelpClicked()
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionTypeSelectionMenu(onSelect: (ActionType) -> Unit, onDismiss: () -> Unit) {
        Column(
            modifier = Modifier
                .padding(top = 6.dp)
                .background(Color(0xFF161B22), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(12.dp))
                .padding(6.dp)
        ) {
            Text("ВЫБОР ТИПА ДЕЙСТВИЯ:", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            val types = listOf(
                ActionType.CLICK to "КЛИК",
                ActionType.LONG_PRESS to "УДЕРЖАНИЕ",
                ActionType.SWIPE to "СВАЙП",
                ActionType.PATH to "ПУТЬ",
                ActionType.TRIGGER to "ИИ ТРИГГЕР",
                ActionType.OCR to "OCR ТЕКСТ",
                ActionType.COLOR_CHECK to "ЦВЕТ",
                ActionType.SUBROUTINE to "ПОДПРОГРАММА",
                ActionType.DELAY to "ЗАДЕРЖКА",
                ActionType.GLOBAL_BACK to "НАЗАД",
                ActionType.GLOBAL_HOME to "ДОМОЙ"
            )

            types.chunked(3).forEach { rowTypes ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    rowTypes.forEach { (type, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .background(Color(0xFF21262D), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(4.dp))
                                .clickable {
                                    onSelect(type)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun RecordModeSelectionMenu(onSmartRecord: () -> Unit, onNormalRecord: () -> Unit, onDismiss: () -> Unit) {
        Column(
            modifier = Modifier
                .padding(top = 6.dp)
                .background(Color(0xFF161B22), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFF43F5E), RoundedCornerShape(12.dp))
                .padding(6.dp)
        ) {
            Text("ВЫБОР РЕЖИМА ЗАПИСИ:", color = Color(0xFFF43F5E), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(Color(0xFF12201A), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF1E3E30), RoundedCornerShape(6.dp))
                    .clickable {
                        onSmartRecord()
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("УМНАЯ ЗАПИСЬ (ИИ-шаблоны)", color = Color(0xFF86EFAC), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(Color(0xFF22183D), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF3E2B6B), RoundedCornerShape(6.dp))
                    .clickable {
                        onNormalRecord()
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("ОБЫЧНАЯ ЗАПИСЬ (Живые жесты)", color = Color(0xFFC4B5FD), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun PlayPauseButton(isPlaying: Boolean, size: Dp, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
        val bgStart = animateColorAsState(if (isPlaying) Color(0xFF3A1016) else Color(0xFF0D2E1E), label = "pbgS").value
        val bgEnd = animateColorAsState(if (isPlaying) Color(0xFF1C080F) else Color(0xFF061A10), label = "pbgE").value
        val stroke = animateColorAsState(if (isPlaying) Color(0xFFF43F5E) else Color(0xFF34D399), label = "pStr").value

        VectorIconButton(
            icon = if (isPlaying) VectorIconDrawer.IconType.PAUSE else VectorIconDrawer.IconType.PLAY,
            size = size,
            bgStart = bgStart,
            bgEnd = bgEnd,
            stroke = stroke,
            iconColor = Color.White,
            onClick = onClick,
            onLongClick = onLongClick
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun VectorIconButton(
        icon: VectorIconDrawer.IconType,
        size: Dp,
        bgStart: Color,
        bgEnd: Color,
        stroke: Color,
        iconColor: Color,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit
    ) {
        val bounds = remember(size) { RectF() }
        Box(
            modifier = Modifier
                .size(size)
                .background(Brush.verticalGradient(listOf(bgStart, bgEnd)), CircleShape)
                .border(1.dp, stroke, CircleShape)
                .clip(CircleShape)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    } else {
                        Modifier.clickable { onClick() }
                    }
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padding = size.toPx() * 0.25f
                bounds.set(padding, padding, size.toPx() - padding, size.toPx() - padding)
                drawIntoCanvas { canvas ->
                    VectorIconDrawer.drawIcon(
                        canvas.nativeCanvas,
                        icon,
                        bounds,
                        android.graphics.Color.argb(
                            (iconColor.alpha * 255).toInt(),
                            (iconColor.red * 255).toInt(),
                            (iconColor.green * 255).toInt(),
                            (iconColor.blue * 255).toInt()
                        ),
                        size.toPx() * 0.05f
                    )
                }
            }
        }
    }
}
