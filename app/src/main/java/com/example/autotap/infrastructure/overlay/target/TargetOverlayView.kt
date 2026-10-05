package com.example.autotap.infrastructure.overlay.target

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.infrastructure.storage.TemplateRepositoryImpl

@SuppressLint("SetTextI18n")
class TargetOverlayView(
    context: Context,
    val isEndTarget: Boolean = false,
    private val templateProvider: ((String) -> Bitmap?)? = null
) : FrameLayout(context) {

    val circleContainer: FrameLayout
    val tvNumber: TextView
    val ivTemplate: ImageView
    val tvCornerBadge: TextView

    private val dm = context.resources.displayMetrics
    private fun dp(value: Int): Int = (value * dm.density).toInt()
    private fun dpF(value: Float): Float = value * dm.density

    init {
        elevation = 0f
        clipChildren = false
        clipToPadding = false


        // [V31.2] Идеально гладкий круг без обрезания обводки 2.5dp (clipToOutline отключен)
        circleContainer = FrameLayout(context).apply {
            elevation = 0f
            clipChildren = true
            clipToPadding = true
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }

        ivTemplate = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        val padImg = dp(4)
        ivTemplate.setPadding(padImg, padImg, padImg, padImg)
        circleContainer.addView(ivTemplate, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))

        tvNumber = TextView(context).apply {
            textSize = if (isEndTarget) 10f else 11.5f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            includeFontPadding = false
        }
        val p = dp(2)
        tvNumber.setPadding(p, p, p, p)
        circleContainer.addView(tvNumber, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))


        // [V29.1] Монолитный круглый жетон мишени по центру без паразитных пузырей
        val circleLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER)
        addView(circleContainer, circleLp)


        // [V20.7] tvCornerBadge — аккуратный бейдж номера шага в стиле M3 (#38BDF8)
        tvCornerBadge = TextView(context).apply {
            textSize = 9f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setTextColor("#38BDF8".toColorInt())
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(4f)
                setColor("#E60B0814".toColorInt())
                setStroke(dpF(1.2f).toInt(), "#38BDF8".toColorInt())
            }
            val pX = dp(4)
            val pY = dp(1)
            setPadding(pX, pY, pX, pY)
            minHeight = dp(16)
            minWidth = dp(16)
            visibility = View.GONE
            elevation = dpF(2f)
        }
        val badgeLp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(1)
            marginEnd = dp(1)
        }
        addView(tvCornerBadge, badgeLp)
    }


    fun bindAction(action: MacroAction, isNumbersHidden: Boolean) {
        // [V40.0] Тотальное скрытие меток: при включенном глазе скрываются круги, превью и номера
        if (isNumbersHidden) {
            circleContainer.visibility = View.INVISIBLE
            tvCornerBadge.visibility = View.GONE
            ivTemplate.visibility = View.GONE
            tvNumber.visibility = View.INVISIBLE
            return
        } else {
            circleContainer.visibility = View.VISIBLE
        }

        // Гарантированный сброс артефактов: исключение пустого маленького окна на обычных шагах
        ivTemplate.setImageDrawable(null)
        ivTemplate.visibility = View.GONE
        tvCornerBadge.background = null
        tvCornerBadge.text = ""
        tvCornerBadge.visibility = View.GONE

        if (action.type == ActionType.PATH) {
            tvCornerBadge.visibility = View.VISIBLE
            tvCornerBadge.text = "ПУТЬ"
            tvCornerBadge.background = GradientDrawable().apply {
                setColor(Color.parseColor("#7C3AED"))
                cornerRadius = dpF(4f)
            }
        }

        val labelText = when {
            isEndTarget -> "${action.id}E"
            action.subroutineTag.isNotEmpty() && action.subroutineTag.matches(Regex("""^[0-9]+(\.[0-9]+)+$""")) -> action.subroutineTag
            else -> "${action.id}"
        }

        // [V29.2] Динамический сброс topMargin для обычного клика по контракту check_orchestrator_and_keyboard_fix
        // [V34.1] Контейнер всегда строго по центру без паразитных сдвигов
        (circleContainer.layoutParams as? MarginLayoutParams)?.let { lp ->
            lp.topMargin = 0
            lp.bottomMargin = 0
            circleContainer.layoutParams = lp
        }

        if (isEndTarget) {
            ivTemplate.visibility = View.GONE
            tvCornerBadge.visibility = View.GONE
            tvNumber.visibility = if (isNumbersHidden) INVISIBLE else VISIBLE
            tvNumber.text = labelText
            tvNumber.setTextColor("#58A6FF".toColorInt())
            circleContainer.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor("#E6161B22".toColorInt())
                setStroke(dpF(2.5f).toInt(), "#58A6FF".toColorInt())
            }
            background = null
            return
        }

        when (action.type) {
            ActionType.TRIGGER -> {
                val bmp = if (action.templatePath.isNotBlank()) {
                    templateProvider?.invoke(action.templatePath)
                        ?: TemplateRepositoryImpl(context).getTemplate(action.templatePath)
                } else null


                if (bmp != null && !bmp.isRecycled) {
                    ivTemplate.setImageBitmap(bmp)
                    ivTemplate.visibility = View.VISIBLE
                    tvNumber.visibility = View.GONE
                    tvCornerBadge.text = "#${action.id}"
                    tvCornerBadge.visibility = if (isNumbersHidden || tvCornerBadge.text.isNullOrBlank()) View.GONE else View.VISIBLE
                } else {
                    ivTemplate.visibility = View.GONE
                    tvCornerBadge.visibility = View.GONE
                    tvCornerBadge.text = ""
                    tvNumber.visibility = if (isNumbersHidden) View.INVISIBLE else View.VISIBLE
                    tvNumber.text = labelText
                    tvNumber.setTextColor("#FB923C".toColorInt())
                }

                val isCirc = action.isCircleShape
                circleContainer.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf("#E6161B22".toColorInt(), "#E60D1117".toColorInt())
                ).apply {
                    shape = if (isCirc) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
                    if (!isCirc) {
                        cornerRadius = dpF(8f)
                    }
                    setStroke(dpF(2.5f).toInt(), "#FB923C".toColorInt())
                }
                background = null
            }
            ActionType.COLOR_CHECK -> {
                ivTemplate.visibility = View.GONE
                tvCornerBadge.visibility = if (isNumbersHidden) View.GONE else View.VISIBLE
                tvCornerBadge.text = "${action.id}"
                tvCornerBadge.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpF(5f)
                    setColor(Color.WHITE)
                }
                tvCornerBadge.setTextColor(Color.BLACK)

                val color = try { Color.parseColor(action.targetColorHex) } catch (_: Exception) { "#EC4899".toColorInt() }
                tvNumber.visibility = View.GONE
                circleContainer.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dpF(2.5f).toInt(), Color.WHITE)
                }
                background = null
            }
            ActionType.CLICK, ActionType.LONG_PRESS, ActionType.SWIPE,
            ActionType.PATH, ActionType.PINCH, ActionType.OCR,
            ActionType.SUBROUTINE, ActionType.RETURN, ActionType.GLOBAL_BACK,
            ActionType.GLOBAL_HOME, ActionType.DELAY -> {
                ivTemplate.visibility = View.GONE
                tvCornerBadge.visibility = View.GONE
                tvNumber.visibility = if (isNumbersHidden) View.INVISIBLE else View.VISIBLE
                tvNumber.text = labelText

                val colorHex = when (action.type) {
                    ActionType.CLICK -> "#8B5CF6"
                    ActionType.LONG_PRESS -> "#A78BFA"
                    ActionType.SWIPE -> "#C084FC"
                    ActionType.PATH -> "#A78BFA"
                    ActionType.PINCH -> "#C084FC"
                    ActionType.OCR -> "#38BDF8"
                    ActionType.SUBROUTINE -> "#A78BFA"
                    ActionType.RETURN -> "#9E95B8"
                    ActionType.GLOBAL_BACK -> "#F43F5E"
                    ActionType.GLOBAL_HOME -> "#3B82F6"
                    ActionType.DELAY -> "#6B7280"
                    ActionType.TRIGGER -> "#F59E0B"
                    ActionType.COLOR_CHECK -> "#EC4899"
                }
                val parsedColor = colorHex.toColorInt()
                tvNumber.setTextColor(Color.WHITE)
                tvNumber.typeface = Typeface.DEFAULT_BOLD

                circleContainer.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf("#E6161B22".toColorInt(), "#E60D1117".toColorInt())
                ).apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dpF(2.5f).toInt(), parsedColor)
                }
                background = null
            }
        }
    }
}
