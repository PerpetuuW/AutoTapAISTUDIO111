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
import com.example.autotap.infrastructure.overlay.OverlayWindowManager

/**
 * Плавающая карточка выбора режима записи (Умная запись vs Обычная запись).
 */
@SuppressLint("SetTextI18n")
class RecordModeSelectionDialog(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager,
    private val onSmartRecord: () -> Unit,
    private val onNormalRecord: () -> Unit
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
                setStroke(dp(1), "#F43F5E".toColorInt())
            }
            val p = dp(12)
            setPadding(p, p, p, p)
            elevation = dpF(20f)
        }
        dialogView = rootCard

        val tvTitle = TextView(context).apply {
            text = "ВЫБОР РЕЖИМА ЗАПИСИ"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#F43F5E".toColorInt())
            setPadding(0, 0, 0, dp(8))
        }
        rootCard.addView(tvTitle)

        val btnSmart = Button(context).apply {
            text = "УМНАЯ ЗАПИСЬ (ИИ-шаблоны)"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#86EFAC".toColorInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#12201A".toColorInt(), "#0B1510".toColorInt())
            ).apply {
                cornerRadius = dpF(8f)
                setStroke(dp(1), "#1E3E30".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener {
                dismiss()
                onSmartRecord()
            }
        }
        rootCard.addView(btnSmart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply {
            bottomMargin = dp(6)
        })

        val btnNormal = Button(context).apply {
            text = "ОБЫЧНАЯ ЗАПИСЬ (Живые жесты)"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor("#C4B5FD".toColorInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#22183D".toColorInt(), "#150E28".toColorInt())
            ).apply {
                cornerRadius = dpF(8f)
                setStroke(dp(1), "#3E2B6B".toColorInt())
            }
            minHeight = 0; minimumHeight = 0
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener {
                dismiss()
                onNormalRecord()
            }
        }
        rootCard.addView(btnNormal, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply {
            bottomMargin = dp(6)
        })

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
        rootCard.addView(btnCancel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)))

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
