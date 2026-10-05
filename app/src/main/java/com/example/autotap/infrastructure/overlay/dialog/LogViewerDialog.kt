package com.example.autotap.infrastructure.overlay.dialog

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.ui.VectorIconDrawer
import java.io.File

@SuppressLint("SetTextI18n")
class LogViewerDialog(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager
) {

    private var dialogView: View? = null
    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    fun show() {
        if (dialogView != null) return

        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val cardW = dp(340).coerceAtMost((screenW * 0.95f).toInt())
        val cardH = dp(460).coerceAtMost((screenH * 0.85f).toInt())

        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F8161B22".toColorInt(), "#F80D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(16f)
                setStroke(dp(1), "#58A6FF".toColorInt())
            }
            val p = dp(10)
            setPadding(p, p, p, p)
            elevation = dpF(16f)
        }
        dialogView = rootCard

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(8f)
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }


        val tvTitle = TextView(context).apply {
            text = "ЖУРНАЛ ТЕЛЕМЕТРИИ И ОШИБОК"
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            setTextColor("#38BDF8".toColorInt())
        }
        header.addView(tvTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btnClose = createVectorBtn(VectorIconDrawer.IconType.CLOSE, "#21262D", "#F04438", dp(28)) {
            dismiss()
        }
        header.addView(btnClose)
        rootCard.addView(header)

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                val m = dp(6)
                setMargins(0, m, 0, m)
            }
            background = GradientDrawable().apply {
                setColor("#161B22".toColorInt())
                cornerRadius = dpF(8f)
                setStroke(dp(1), "#21262D".toColorInt())
            }
            val p = dp(8)
            setPadding(p, p, p, p)
        }

        val tvLogs = TextView(context).apply {
            text = AppLogger.getLogs(context)
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setTextColor("#C9D1D9".toColorInt())
            setTextIsSelectable(true)
        }
        scrollView.addView(tvLogs)
        rootCard.addView(scrollView)

        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }

        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnRefresh = Button(context).apply {
            text = "ОБНОВИТЬ"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#1F6FEB".toColorInt())
                cornerRadius = dpF(6f)
            }
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener {
                tvLogs.text = AppLogger.getLogs(context)
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                Toast.makeText(context, "Логи обновлены", Toast.LENGTH_SHORT).show()
            }
        }
        actionsRow.addView(btnRefresh, LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(3) })

        val btnCopy = Button(context).apply {
            text = "КОПИРОВАТЬ"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(6f)
            }
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("AutoTap Logs", tvLogs.text))
                Toast.makeText(context, "Логи скопированы в буфер!", Toast.LENGTH_SHORT).show()
            }
        }
        actionsRow.addView(btnCopy, LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(3) })

        val btnShare = Button(context).apply {
            text = "ЭКСПОРТ"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(6f)
            }
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(4), 0, dp(4), 0)
                        setOnClickListener {
                dismiss()
                AppLogger.shareLogs(context, tvLogs.text.toString())
            }
        }
        actionsRow.addView(btnShare, LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(3) })

        val btnClear = Button(context).apply {
            text = "ОЧИСТИТЬ"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor("#F04438".toColorInt())
            background = GradientDrawable().apply {
                setColor("#21262D".toColorInt())
                cornerRadius = dpF(6f)
                setStroke(dp(1), "#F04438".toColorInt())
            }
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener {
                AppLogger.clearLogs(context)
                tvLogs.text = "Журнал логов пуст."
                Toast.makeText(context, "Логи полностью очищены!", Toast.LENGTH_SHORT).show()
            }
        }
        actionsRow.addView(btnClear, LinearLayout.LayoutParams(0, dp(34), 1f))
        rootCard.addView(actionsRow)

        val params = overlayWindowManager.createLayoutParams(
            width = cardW,
            height = cardH,
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.CENTER
        )
        overlayWindowManager.addViewSafe(rootCard, params)
    }

    fun dismiss() {
        dialogView?.let {
            overlayWindowManager.removeViewSafe(it)
            dialogView = null
        }
    }

    private fun createVectorBtn(type: VectorIconDrawer.IconType, bgHex: String, iconHex: String, sizePx: Int, onClick: () -> Unit): View {
        return object : View(context) {
            private val bounds = RectF()
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                bounds.set(dpF(6f), dpF(6f), width - dpF(6f), height - dpF(6f))
                VectorIconDrawer.drawIcon(canvas, type, bounds, iconHex.toColorInt(), dpF(2f))
            }
        }.apply {
            background = GradientDrawable().apply {
                cornerRadius = dpF(6f)
                setColor(bgHex.toColorInt())
            }
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            setOnClickListener { onClick() }
        }
    }
}
