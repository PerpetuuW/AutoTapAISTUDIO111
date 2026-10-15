package com.example.autotap.infrastructure.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

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
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    protected fun runOnMainSyncOrPost(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /**
     * Создает или возвращает готовый к работе ComposeView.
     * Владельцы жизненного цикла привязываются строго ДО setContent во избежание краша WindowRecomposer.
     */
    protected fun createComposeView(content: @Composable () -> Unit): ComposeView {
        val owner = OverlayLifecycleOwner()
        lifecycleOwner = owner
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent(content)
        }

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
        runOnMainSyncOrPost {
            val existing = composeView
            if (existing != null) {
                existing.visibility = android.view.View.VISIBLE
                if (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true) {
                    lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                }
                if (existing.parent == null) {
                    overlayWindowManager.addViewSafe(existing, getLayoutParams())
                } else {
                    overlayWindowManager.updateViewSafe(existing, getLayoutParams())
                }
                return@runOnMainSyncOrPost
            }

            val view = createComposeView {
                Content()
            }
            composeView = view
            overlayWindowManager.addViewSafe(view, getLayoutParams())
        }
    }

    open fun hide() {
        runOnMainSyncOrPost {
            composeView?.let { view ->
                if (view.visibility != android.view.View.GONE) {
                    view.visibility = android.view.View.GONE
                    if (lifecycleOwner?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    }
                }
            }
        }
    }

    open fun dismiss() {
        runOnMainSyncOrPost {
            val view = composeView
            val owner = lifecycleOwner
            composeView = null
            lifecycleOwner = null
            if (view != null) {
                try {
                    if (owner != null && owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            owner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                        }
                        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            owner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                        }
                        owner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                    }
                    view.disposeComposition()
                } catch (_: Exception) {}
                overlayWindowManager.removeViewSafe(view)
            }
        }
    }

    @Composable
    abstract fun Content()
}
