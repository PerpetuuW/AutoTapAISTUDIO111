package com.example.autotap.infrastructure.overlay.panel

import android.content.Context
import android.graphics.RectF
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.autotap.infrastructure.overlay.AbstractComposeOverlay
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.model.OverlayVisibilityMode
import com.example.autotap.infrastructure.overlay.model.PanelDisplayMode
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class ControlPanelState(
    val mode: PanelDisplayMode = PanelDisplayMode.COMPACT_SINGLE_ROW,
    val isPlaying: Boolean = false,
    val visibilityMode: OverlayVisibilityMode = OverlayVisibilityMode.FULL,
    val isNumbersHidden: Boolean = false
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
        val btnW = dp(42)
        val btnH = dp(42)
        val pad = dp(8)
        val margin = dp(8)

        val colIndex = when (tagKey) {
            "BTN_PLAY_VIEW" -> 0
            "BTN_ADD", "BTN_CAPTURE" -> 1
            "BTN_RECORD" -> 2
            "BTN_TOGGLE_VIEW" -> 3
            "BTN_TUTORIAL", "BTN_HIDE" -> 4
            "BTN_SETTINGS" -> 5
            else -> -1
        }

        if (colIndex >= 0) {
            val left = posX + pad + colIndex * (btnW + margin)
            val top = posY + pad
            return android.graphics.Rect(left, top, left + btnW, top + btnH)
        }

        val subRowIndex = when (tagKey) {
            "BTN_CLOSE" -> 0
            "BTN_LOCK" -> 1
            "BTN_CLEAR", "BTN_WIPE" -> 2
            "BTN_GRAPH" -> 3
            "BTN_SCRIPTS" -> 4
            "BTN_HELP" -> 5
            else -> -1
        }

        if (subRowIndex >= 0) {
            val left = posX + pad + subRowIndex * (dp(36) + dp(6))
            val top = posY + pad + btnH + margin
            return android.graphics.Rect(left, top, left + dp(36), top + dp(36))
        }

        return android.graphics.Rect(posX, posY, posX + dp(280), posY + dp(90))
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

    fun hide() {
        dismiss()
    }

    val containerView: android.view.View? get() = super.composeView

    // Accessor for the view to allow WindowManager updates inside Compose drag
    private val panelView: android.view.View? get() = super.composeView

    @Composable
    override fun Content() {
        val state by overlayState.collectAsState()

        val globalAlpha by animateFloatAsState(
            targetValue = if (state.visibilityMode == OverlayVisibilityMode.TRANSPARENT) 0.40f else 1.0f,
            animationSpec = tween(200),
            label = "alpha"
        )

        var dragOffsetX by remember { mutableFloatStateOf(0f) }
        var dragOffsetY by remember { mutableFloatStateOf(0f) }

        val dragModifier = Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { /* vibrate slightly? */ },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetX += dragAmount.x
                    dragOffsetY += dragAmount.y

                    val params = getLayoutParams()
                    params.x += dragOffsetX.toInt()
                    params.y += dragOffsetY.toInt()

                    val maxX = (dm.widthPixels - dp(50)).coerceAtLeast(0)
                    params.x = params.x.coerceIn(0, maxX)
                    params.y = params.y.coerceIn(dp(20), dm.heightPixels - dp(80))

                    posX = params.x
                    posY = params.y

                    panelView?.let { overlayWindowManager.updateViewSafe(it, params) }
                    
                    dragOffsetX = 0f
                    dragOffsetY = 0f
                }
            )
        }

        Box(modifier = Modifier.alpha(globalAlpha)) {
            when (state.mode) {
                PanelDisplayMode.MINI_BUBBLE -> MiniBubble(state, dragModifier)
                PanelDisplayMode.COMPACT_SINGLE_ROW, PanelDisplayMode.EXPANDED -> ExpandedPanel(state, dragModifier)
            }
        }
    }

    @Composable
    private fun MiniBubble(state: ControlPanelState, dragModifier: Modifier) {
        Row(
            modifier = dragModifier
                .background(
                    brush = Brush.verticalGradient(listOf(Color(0xFF161B22), Color(0xFF0D1117))),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(20.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayPauseButton(state.isPlaying, 36.dp) {
                if (state.isPlaying) listener.onPlayClicked() else listener.onPlayClicked()
            }
            Spacer(modifier = Modifier.width(6.dp))
            VectorIconButton(
                icon = VectorIconDrawer.IconType.PANEL_COLLAPSE,
                size = 36.dp,
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
    private fun ExpandedPanel(state: ControlPanelState, dragModifier: Modifier) {
        Column(
            modifier = dragModifier
                .background(
                    brush = Brush.verticalGradient(listOf(Color(0xF0161B22), Color(0xF00D1117))),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(1.dp, Color(0xFF262D3D), RoundedCornerShape(20.dp))
                .padding(8.dp)
        ) {
            // MAIN ROW
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayPauseButton(state.isPlaying, 42.dp) {
                    if (state.isPlaying) listener.onPlayClicked() else listener.onPlayClicked()
                }
                Spacer(modifier = Modifier.width(8.dp))

                VectorIconButton(
                    icon = VectorIconDrawer.IconType.PLUS,
                    size = 42.dp,
                    bgStart = Color(0xFF162032),
                    bgEnd = Color(0xFF0F172A),
                    stroke = Color(0xFF38BDF8),
                    iconColor = Color(0xFF58A6FF)
                ) {
                    listener.onAddClicked()
                }
                Spacer(modifier = Modifier.width(8.dp))

                VectorIconButton(
                    icon = VectorIconDrawer.IconType.RECORD,
                    size = 42.dp,
                    bgStart = Color(0xFF3A1016),
                    bgEnd = Color(0xFF1C080F),
                    stroke = Color(0xFFF04438),
                    iconColor = Color(0xFFF43F5E)
                ) {
                    listener.onRecordClicked()
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Expand/Collapse Mode Toggle
                val isExpanded = state.mode == PanelDisplayMode.EXPANDED
                VectorIconButton(
                    icon = if (isExpanded) VectorIconDrawer.IconType.PANEL_COLLAPSE else VectorIconDrawer.IconType.PANEL_EXPAND,
                    size = 42.dp,
                    bgStart = Color(0xFF1F2937),
                    bgEnd = Color(0xFF111827),
                    stroke = Color(0xFF374151),
                    iconColor = Color(0xFF94A3B8)
                ) {
                    applyDisplayMode(if (isExpanded) PanelDisplayMode.COMPACT_SINGLE_ROW else PanelDisplayMode.EXPANDED)
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Eye Toggle
                val eyeBgStart = animateColorAsState(when(state.visibilityMode) {
                    OverlayVisibilityMode.FULL -> Color(0xFF162032)
                    OverlayVisibilityMode.TRANSPARENT -> Color(0xFF2E123D)
                    OverlayVisibilityMode.HIDDEN -> Color(0xFF3B2606)
                }, label = "eyeBgS").value
                val eyeBgEnd = animateColorAsState(when(state.visibilityMode) {
                    OverlayVisibilityMode.FULL -> Color(0xFF0F172A)
                    OverlayVisibilityMode.TRANSPARENT -> Color(0xFF170924)
                    OverlayVisibilityMode.HIDDEN -> Color(0xFF1F1403)
                }, label = "eyeBgE").value
                val eyeStroke = animateColorAsState(when(state.visibilityMode) {
                    OverlayVisibilityMode.FULL -> Color(0xFF38BDF8)
                    OverlayVisibilityMode.TRANSPARENT -> Color(0xFFA78BFA)
                    OverlayVisibilityMode.HIDDEN -> Color(0xFFF59E0B)
                }, label = "eyeStr").value
                val eyeIconColor = animateColorAsState(when(state.visibilityMode) {
                    OverlayVisibilityMode.FULL -> Color(0xFF38BDF8)
                    OverlayVisibilityMode.TRANSPARENT -> Color(0xFFA78BFA)
                    OverlayVisibilityMode.HIDDEN -> Color(0xFFFBBF24)
                }, label = "eyeIc").value

                VectorIconButton(
                    icon = VectorIconDrawer.IconType.VISIBILITY,
                    size = 42.dp,
                    bgStart = eyeBgStart,
                    bgEnd = eyeBgEnd,
                    stroke = eyeStroke,
                    iconColor = eyeIconColor
                ) {
                    listener.onToggleNumbersClicked()
                }
                Spacer(modifier = Modifier.width(8.dp))

                VectorIconButton(
                    icon = VectorIconDrawer.IconType.SETTINGS,
                    size = 42.dp,
                    bgStart = Color(0xFF1F2937),
                    bgEnd = Color(0xFF111827),
                    stroke = Color(0xFF4B5563),
                    iconColor = Color(0xFF9CA3AF)
                ) {
                    listener.onSettingsClicked()
                }
            }

            // SUB ROW
            AnimatedVisibility(visible = state.mode == PanelDisplayMode.EXPANDED) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VectorIconButton(
                        icon = VectorIconDrawer.IconType.PANEL_COLLAPSE,
                        size = 36.dp,
                        bgStart = Color(0xFF1F2937),
                        bgEnd = Color(0xFF111827),
                        stroke = Color(0xFF374151),
                        iconColor = Color(0xFF94A3B8)
                    ) {
                        applyDisplayMode(PanelDisplayMode.MINI_BUBBLE)
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    VectorIconButton(
                        icon = VectorIconDrawer.IconType.LOCK,
                        size = 36.dp,
                        bgStart = Color(0xFF1F2937),
                        bgEnd = Color(0xFF111827),
                        stroke = Color(0xFF374151),
                        iconColor = Color(0xFF94A3B8)
                    ) {
                        listener.onScreenLockClicked()
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    VectorIconButton(
                        icon = VectorIconDrawer.IconType.CLEAR,
                        size = 36.dp,
                        bgStart = Color(0xFF2A0E0C),
                        bgEnd = Color(0xFF1C0908),
                        stroke = Color(0xFF7F1D1D),
                        iconColor = Color(0xFFF04438)
                    ) {
                        listener.onClearAllClicked()
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    VectorIconButton(
                        icon = VectorIconDrawer.IconType.ACTION_SUBROUTINE,
                        size = 36.dp,
                        bgStart = Color(0xFF14241B),
                        bgEnd = Color(0xFF09140E),
                        stroke = Color(0xFF059669),
                        iconColor = Color(0xFF34D399)
                    ) {
                        listener.onCaptureClicked()
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    VectorIconButton(
                        icon = VectorIconDrawer.IconType.SCRIPTS,
                        size = 36.dp,
                        bgStart = Color(0xFF0D2E3A),
                        bgEnd = Color(0xFF061A24),
                        stroke = Color(0xFF0369A1),
                        iconColor = Color(0xFF38BDF8)
                    ) {
                        listener.onScriptsClicked()
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    VectorIconButton(
                        icon = VectorIconDrawer.IconType.HELP,
                        size = 36.dp,
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
    private fun PlayPauseButton(isPlaying: Boolean, size: Dp, onClick: () -> Unit) {
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
            onClick = onClick
        )
    }

    @Composable
    private fun VectorIconButton(
        icon: VectorIconDrawer.IconType,
        size: Dp,
        bgStart: Color,
        bgEnd: Color,
        stroke: Color,
        iconColor: Color,
        onClick: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(Brush.verticalGradient(listOf(bgStart, bgEnd)), CircleShape)
                .border(1.dp, stroke, CircleShape)
                .clip(CircleShape)
                .clickable { onClick() }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padding = size.toPx() * 0.25f
                val bounds = RectF(padding, padding, size.toPx() - padding, size.toPx() - padding)
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
