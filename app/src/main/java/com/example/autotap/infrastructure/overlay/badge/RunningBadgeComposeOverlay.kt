package com.example.autotap.infrastructure.overlay.badge

import android.content.Context
import android.graphics.Color
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.ExecutionState
import com.example.autotap.infrastructure.overlay.AbstractComposeOverlay
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.projection.MediaProjectionService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class RunningBadgeComposeOverlay(
    context: Context,
    overlayWindowManager: OverlayWindowManager,
    private val onLockClick: () -> Unit,
    private val onStopClick: () -> Unit
) : AbstractComposeOverlay(context, overlayWindowManager) {

    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    
    // MVI StateFlow
    private val overlayState = MutableStateFlow(BadgeState())

    data class BadgeState(
        val isVisible: Boolean = true,
        val isTransparent: Boolean = false,
        val headerText: String = "ШАГ 1/1",
        val actionTitle: String = "Запуск",
        val isSearchingMode: Boolean = false,
        val isInfiniteMode: Boolean = false,
        val searchStartTime: Long = 0L,
        val searchTimeoutMs: Long = 1000L
    )

    private var initialX = dp(8)
    private var initialY = dp(110)

    override fun getLayoutParams(): WindowManager.LayoutParams {
        val params = super.getLayoutParams()
        val cardW = dp(78 + 12)
        params.x = (dm.widthPixels - cardW - dp(10)).coerceAtLeast(dp(6))
        params.y = initialY
        return params
    }

    fun setTemporarilyTransparent(transparent: Boolean) {
        overlayState.update { it.copy(isTransparent = transparent) }
    }

    fun forceVisible() {
        overlayState.update { it.copy(isVisible = true, isTransparent = false) }
    }

    fun updateState(state: ExecutionState.Running) {
        val repeatStr = if (state.totalRepeats > 1) " (${state.currentRepeat}/${state.totalRepeats})" else ""
        val header = "ШАГ ${state.currentStepIndex}/${state.totalSteps}$repeatStr"

        val title = if (state.isSearching) {
            val rawTitle = state.searchTitle.removeSuffix(".png").removeSuffix(".json").removeSuffix(".jpg").removePrefix("mask_").trim()
            if (rawTitle.isNotBlank()) rawTitle else "Поиск..."
        } else {
            when {
                state.activeNodeTitle.isNotEmpty() -> state.activeNodeTitle
                state.stepDescription.isNotEmpty() -> state.stepDescription
                else -> when (state.stepType) {
                    ActionType.CLICK -> "Клик"
                    ActionType.LONG_PRESS -> "Удержание"
                    ActionType.SWIPE, ActionType.PATH -> "Траектория"
                    ActionType.PINCH -> "Пинч"
                    ActionType.SUBROUTINE -> "Сценарий"
                    ActionType.RETURN -> "Возврат"
                    ActionType.GLOBAL_BACK -> "Назад"
                    ActionType.GLOBAL_HOME -> "Домой"
                    ActionType.DELAY -> "Пауза"
                    ActionType.TRIGGER, ActionType.OCR, ActionType.COLOR_CHECK -> "Проверка"
                }
            }
        }

        overlayState.update { 
            it.copy(
                headerText = header,
                actionTitle = title,
                isSearchingMode = state.isSearching,
                isInfiniteMode = state.isInfiniteSearch,
                searchStartTime = if (state.searchStartTime > 0L) state.searchStartTime else System.currentTimeMillis(),
                searchTimeoutMs = if (state.searchTimeoutMs > 0L) state.searchTimeoutMs else 1000L
            ) 
        }
    }

    @Composable
    override fun Content() {
        val state by overlayState.collectAsState()
        
        // Anti-strobe logic
        val effectiveAlpha = if (state.isTransparent && !MediaProjectionService.isStreaming && !state.isSearchingMode) 0f else 1f

        AnimatedVisibility(
            visible = state.isVisible && effectiveAlpha > 0f,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BadgeCard(state)
        }
    }

    @Composable
    private fun BadgeCard(state: BadgeState) {
        // Draggable container logic
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .padding(6.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                        
                        // Update window manager layout params
                        val params = getLayoutParams()
                        params.x += offsetX.toInt()
                        params.y += offsetY.toInt()
                        
                        // Clamp bounds
                        params.x = params.x.coerceIn(dp(6), dm.widthPixels - dp(90))
                        params.y = params.y.coerceIn(dp(20), dm.heightPixels - dp(80))
                        
                        overlayWindowManager.updateViewSafe(composeView ?: return@detectDragGestures, params)
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color(0xFF161B22),
                            androidx.compose.ui.graphics.Color(0xFF0D1117)
                        )
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
                .border(1.dp, androidx.compose.ui.graphics.Color(0xFF38BDF8), RoundedCornerShape(18.dp))
                .padding(6.dp)
                .width(78.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.headerText,
                    color = androidx.compose.ui.graphics.Color(0xFF58A6FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = state.actionTitle,
                    color = if (state.isSearchingMode) androidx.compose.ui.graphics.Color(0xFFFFB703) else androidx.compose.ui.graphics.Color(0xFF34D399),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Progress Bar
                var currentProgress by remember { mutableIntStateOf(0) }
                LaunchedEffect(state.searchStartTime, state.searchTimeoutMs, state.isSearchingMode, state.isInfiniteMode) {
                    while(true) {
                        val elapsed = System.currentTimeMillis() - state.searchStartTime
                        currentProgress = if (state.isSearchingMode && state.isInfiniteMode) {
                            50
                        } else {
                            ((elapsed.toFloat() / state.searchTimeoutMs.toFloat()).coerceIn(0f, 1f) * 100).toInt()
                        }
                        delay(40L)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFF21262D), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(currentProgress / 100f)
                            .height(3.5.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = if (state.isSearchingMode) {
                                        listOf(androidx.compose.ui.graphics.Color(0xFFFFB703), androidx.compose.ui.graphics.Color(0xFFFB8500))
                                    } else {
                                        listOf(androidx.compose.ui.graphics.Color(0xFF34D399), androidx.compose.ui.graphics.Color(0xFF38BDF8))
                                    }
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row {
                    // Lock button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                brush = Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF1F6FEB), androidx.compose.ui.graphics.Color(0xFF161B22))),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .border(1.dp, androidx.compose.ui.graphics.Color(0xFF58A6FF), RoundedCornerShape(18.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = { onLockClick() },
                                    onDrag = { _, _ -> }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val padding = 36.dp.toPx() * 0.25f
                            val bounds = android.graphics.RectF(padding, padding, 36.dp.toPx() - padding, 36.dp.toPx() - padding)
                            drawIntoCanvas { canvas ->
                                VectorIconDrawer.drawIcon(
                                    canvas.nativeCanvas,
                                    VectorIconDrawer.IconType.LOCK,
                                    bounds,
                                    android.graphics.Color.WHITE,
                                    36.dp.toPx() * 0.05f
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(6.dp))

                    // Stop button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                brush = Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFF04438), androidx.compose.ui.graphics.Color(0xFFB42318))),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .border(1.dp, androidx.compose.ui.graphics.Color(0xFFFDA29B), RoundedCornerShape(18.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = { onStopClick() },
                                    onDrag = { _, _ -> }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val padding = 36.dp.toPx() * 0.25f
                            val bounds = android.graphics.RectF(padding, padding, 36.dp.toPx() - padding, 36.dp.toPx() - padding)
                            drawIntoCanvas { canvas ->
                                VectorIconDrawer.drawIcon(
                                    canvas.nativeCanvas,
                                    VectorIconDrawer.IconType.STOP,
                                    bounds,
                                    android.graphics.Color.WHITE,
                                    36.dp.toPx() * 0.05f
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
