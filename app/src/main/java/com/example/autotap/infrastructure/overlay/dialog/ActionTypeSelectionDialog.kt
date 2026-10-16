package com.example.autotap.infrastructure.overlay.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.autotap.domain.model.ActionType
import com.example.autotap.infrastructure.overlay.OverlayWindowManager

/**
 * Плавающая карточка выбора типа действия для создания шагов (без традиционных списков).
 */
@SuppressLint("SetTextI18n")
class ActionTypeSelectionDialog(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val onActionSelected: (ActionType) -> Unit
) {
    private var dialogView: View? = null
    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    fun show() {
        if (dialogView != null) return

        val cardW = dp(300).coerceAtMost((dm.widthPixels * 0.92f).toInt())

        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8161B22".toColorInt(), "#F80D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(16f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            val p = dp(10)
            setPadding(p, p, p, p)
            elevation = dpF(20f)
        }
        dialogView = rootCard

        val tvTitle = TextView(context).apply {
            text = "ВЫБОР ТИПА ДЕЙСТВИЯ"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#38BDF8".toColorInt())
            setPadding(0, 0, 0, dp(8))
        }
        rootCard.addView(tvTitle)

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
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            rowTypes.forEach { (type, label) ->
                val btn = Button(context).apply {
                    text = label
                    textSize = 8.5f
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setTextColor(Color.WHITE)
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf("#162032".toColorInt(), "#0F172A".toColorInt())
                    ).apply {
                        cornerRadius = dpF(6f)
                        setStroke(dp(1), "#30363D".toColorInt())
                    }
                    minHeight = 0; minimumHeight = 0
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    setOnClickListener {
                        dismiss()
                        onActionSelected(type)
                    }
                }
                row.addView(btn, LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                    val m = dp(2)
                    setMargins(m, m, m, m)
                })
            }
            rootCard.addView(row)
        }

        val btnCancel = Button(context).apply {
            text = "ОТМЕНА"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#9CA3AF".toColorInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#1F2937".toColorInt(), "#111827".toColorInt())
            ).apply {
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#374151".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener { dismiss() }
        }
        rootCard.addView(btnCancel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)).apply {
            topMargin = dp(6)
        })

        val params = overlayWindowManager.createDialogLayoutParams(
            width = cardW,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.CENTER
        ).apply {
            dimAmount = 0.5f
        }
        overlayWindowManager.addViewSafe(rootCard, params)
    }

    fun dismiss() {
        dialogView?.let {
            overlayWindowManager.removeViewSafe(it)
            dialogView = null
        }
    }
}
