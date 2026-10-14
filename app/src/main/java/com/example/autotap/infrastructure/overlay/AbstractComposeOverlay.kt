package com.example.autotap.infrastructure.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Базовый класс для всех оверлеев, переписанных на Jetpack Compose.
 * Автоматически инициализирует Lifecycle, ViewModelStore и SavedStateRegistry 
 * для корректной работы Compose-инфраструктуры внутри WindowManager.
 */
abstract class AbstractComposeOverlay(
    protected val context: Context,
    protected val overlayWindowManager: OverlayWindowManager
) {
    protected var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    /**
     * Создает или возвращает готовый к работе ComposeView.
     * Не добавляет его в WindowManager автоматически! Используйте [show].
     */
    protected fun createComposeView(content: @Composable () -> Unit): ComposeView {
        val view = ComposeView(context).apply {
            setContent(content)
        }

        val owner = OverlayLifecycleOwner()
        lifecycleOwner = owner

        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)

        val coroutineContext = AndroidUiDispatcher.CurrentThread
        val recomposer = Recomposer(coroutineContext)
        view.compositionContext = recomposer

        CoroutineScope(coroutineContext).launch {
            recomposer.runRecomposeAndApplyChanges()
        }

        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        return view
    }

    /**
     * Базовые параметры окна. Переопределите, если оверлею нужен фокус или другой размер.
     */
    protected open fun getLayoutParams(): WindowManager.LayoutParams {
        return overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.TOP or Gravity.START
        )
    }

    open fun show() {
        if (composeView != null) return

        val view = createComposeView {
            Content()
        }
        composeView = view
        overlayWindowManager.addViewSafe(view, getLayoutParams())
    }

    open fun dismiss() {
        composeView?.let {
            overlayWindowManager.removeViewSafe(it)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            it.disposeComposition()
        }
        composeView = null
        lifecycleOwner = null
    }

    @Composable
    abstract fun Content()
}
