package com.example.autotap.infrastructure.overlay.lock

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import java.util.Locale
import kotlin.math.hypot
import kotlin.random.Random

@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class ScreenLockOverlay(
    private val context: Context,
    private val overlayWindowManager: OverlayWindowManager
) {

    private var bgLockContainer: FrameLayout? = null
    private var uiLockContainer: LinearLayout? = null
    private var bgWindowParams: WindowManager.LayoutParams? = null
    private var uiWindowParams: WindowManager.LayoutParams? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val dm = context.resources.displayMetrics
    private fun dp(v: Int): Int = (v * dm.density).toInt()
    private fun dpF(v: Float): Float = v * dm.density

    private var countdownRunnable: Runnable? = null
    private var pixelShiftRunnable: Runnable? = null
    private var holdStartTime = 0L
    private val totalHoldDurationMs = 3000L
    private var sessionStartTime = 0L

    private val prefs: SharedPreferences = context.getSharedPreferences("autotap_lock_prefs", Context.MODE_PRIVATE)
    private var isBlackScreenMode: Boolean
        get() = prefs.getBoolean("is_black_screen", true)
        set(value) = prefs.edit().putBoolean("is_black_screen", value).apply()

    private var lockBrightness: Float
        get() = prefs.getFloat("lock_brightness", 0.01f)
        set(value) = prefs.edit().putFloat("lock_brightness", value).apply()

    var onLockDismissedListener: (() -> Unit)? = null

    fun isShowing(): Boolean = bgLockContainer != null

    fun setTemporarilyTransparent(transparent: Boolean) {
        mainHandler.post {
            bgLockContainer?.alpha = if (transparent) 0f else 1f
            uiLockContainer?.alpha = if (transparent) 0f else 1f
        }
    }


        fun setPassthroughEnabled(enabled: Boolean) {
            val bg = bgLockContainer ?: return
            val params = bgWindowParams ?: return
            val ui = uiLockContainer
            val uiParams = uiWindowParams
            if (enabled) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                uiParams?.let { it.flags = it.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE }
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                uiParams?.let { it.flags = it.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() }
            }
            overlayWindowManager.updateViewSafe(bg, params)
            if (ui != null && uiParams != null) {
                overlayWindowManager.updateViewSafe(ui, uiParams)
            }
        }

    fun show() {
        if (isShowing()) return
        AppLogger.log(context, "LOCK", "Активация AFK защиты экрана (AMOLED Burn-in Guard)")
        sessionStartTime = System.currentTimeMillis()


        val initialProgress = (lockBrightness * 100).toInt().coerceIn(0, 100)
        val initialColor = if (initialProgress == 0) Color.BLACK else {
            val alpha = (255 * (1.0f - (initialProgress / 100f) * 0.35f)).toInt().coerceIn(160, 255)
            Color.argb(alpha, 0, 0, 0)
        }
        val bg = FrameLayout(context).apply {
            setBackgroundColor(initialColor)
            setOnTouchListener { _, _ -> true }
        }
        bgLockContainer = bg

        val bgParams = overlayWindowManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            gravity = Gravity.TOP or Gravity.START
        ).apply {
            screenBrightness = if (initialProgress == 0) 0.01f else (initialProgress / 100f).coerceIn(0.01f, 1.0f)
        }
        bgWindowParams = bgParams
        overlayWindowManager.addViewSafe(bg, bgParams)

        val anchorCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf("#F0161B22".toColorInt(), "#F00D1117".toColorInt())
            ).apply {
                cornerRadius = dpF(20f)
                setStroke(dp(1), "#38BDF8".toColorInt())
            }
            val px = dp(16)
            val py = dp(10)
            setPadding(px, py, px, py)
            elevation = dpF(24f)
        }
        uiLockContainer = anchorCard

        val unlockTouchArea = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }


        val tvStatus = TextView(context).apply {
            text = " УДЕРЖИВАЙТЕ ЗДЕСЬ 3 СЕК"
            setTextColor("#38BDF8".toColorInt())
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            includeFontPadding = false
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }
        unlockTouchArea.addView(tvStatus)

        val tvUptime = TextView(context).apply {
            text = "СЕССИЯ: 00:00:00"
            setTextColor("#8B949E".toColorInt())
            textSize = 8.5f
            typeface = Typeface.MONOSPACE
            includeFontPadding = false
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        unlockTouchArea.addView(tvUptime)

        val bgProgress = GradientDrawable().apply {
            setColor("#21262D".toColorInt())
            cornerRadius = dpF(3f)
        }

        val fillProgress = GradientDrawable().apply {
            setColor("#38BDF8".toColorInt())
            cornerRadius = dpF(3f)
        }
        val clipFill = ClipDrawable(fillProgress, Gravity.START, ClipDrawable.HORIZONTAL)
        val progressDrawable = LayerDrawable(arrayOf(bgProgress, clipFill)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }

        val pBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(dp(210), dp(5)).apply {
                bottomMargin = dp(8)
            }
            max = 100
            progress = 0
            this.progressDrawable = progressDrawable
        }
        unlockTouchArea.addView(pBar)
        anchorCard.addView(unlockTouchArea)


        // [V21.2] Единый умный ползунок: 0% = True AMOLED Black (0 Вт), >0% = полупрозрачный просмотр игры
        val tvModeStatus = TextView(context).apply {
            val curProg = (lockBrightness * 100).toInt().coerceIn(0, 100)
            text = if (curProg == 0) "AMOLED ECO: ЭКРАН ВЫКЛ (0 Вт)" else "ПРОСМОТР: ЯРКОСТЬ $curProg%"
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (curProg == 0) "#34D399".toColorInt() else "#38BDF8".toColorInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(4))
        }
        anchorCard.addView(tvModeStatus)

        val controlsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val tvSun = TextView(context).apply {
            text = "ЯРКОСТЬ"
            textSize = 8.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dp(4), 0, dp(4), 0)
        }
        controlsRow.addView(tvSun)

        val sbBrightness = SeekBar(context).apply {
            max = 100
            progress = (lockBrightness * 100).toInt().coerceIn(0, 100)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, prog: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val brRatio = prog / 100f
                        lockBrightness = brRatio
                        if (prog == 0) {
                            tvModeStatus.text = "AMOLED ECO: ЭКРАН ВЫКЛ (0 Вт)"
                            tvModeStatus.setTextColor("#34D399".toColorInt())
                            bgLockContainer?.setBackgroundColor(Color.BLACK)
                        } else {
                            tvModeStatus.text = "ПРОСМОТР: ЯРКОСТЬ $prog%"
                            tvModeStatus.setTextColor("#38BDF8".toColorInt())
                            val alpha = (255 * (1.0f - brRatio * 0.35f)).toInt().coerceIn(160, 255)
                            bgLockContainer?.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
                        }
                        val p = bgWindowParams ?: return
                        p.screenBrightness = if (prog == 0) 0.01f else brRatio.coerceIn(0.01f, 1.0f)
                        bgLockContainer?.let { overlayWindowManager.updateViewSafe(it, p) }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        controlsRow.addView(sbBrightness)
        anchorCard.addView(controlsRow)

        var touchDownX = 0f
        var touchDownY = 0f

        fun stopCountdown() {
            countdownRunnable?.let { mainHandler.removeCallbacks(it) }

            countdownRunnable = null
            pBar.progress = 0
            tvStatus.text = " УДЕРЖИВАЙТЕ ЗДЕСЬ 3 СЕК"
            tvStatus.setTextColor("#38BDF8".toColorInt())
            }

        fun vibrate(ms: Long) {
            try {
                val vibrator = context.getSystemService(Vibrator::class.java)
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(ms)
                    }
                }
            } catch (_: Exception) {}
        }

        fun startCountdown() {
            holdStartTime = System.currentTimeMillis()
            vibrate(25L)

            countdownRunnable = object : Runnable {
                override fun run() {
                    val elapsed = System.currentTimeMillis() - holdStartTime
                    val remainingMs = (totalHoldDurationMs - elapsed).coerceAtLeast(0L)
                    val remainingSec = remainingMs / 1000.0
                    val pct = ((elapsed.toFloat() / totalHoldDurationMs.toFloat()) * 100).toInt().coerceIn(0, 100)

                    pBar.progress = pct

                    if (remainingMs <= 0) {
                        vibrate(60L)
                        dismiss()
                    } else {
                        tvStatus.text = String.format(Locale.US, " СНЯТИЕ: %.1f с", remainingSec)
                        tvStatus.setTextColor("#FFB703".toColorInt())
                        mainHandler.postDelayed(this, 40L)
                    }
                }
            }
            mainHandler.post(countdownRunnable as Runnable)
        }

        unlockTouchArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    startCountdown()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (hypot((event.rawX - touchDownX).toDouble(), (event.rawY - touchDownY).toDouble()) > dp(50)) {
                        stopCountdown()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopCountdown()
                    true
                }
                else -> false
            }
        }

        val uiParams = overlayWindowManager.createLayoutParams(
            width = dp(260),
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            y = dp(60)
        }
        uiWindowParams = uiParams
        overlayWindowManager.addViewSafe(anchorCard, uiParams)

                val uptimeTickerRunnable = object : Runnable {
            override fun run() {
                if (bgLockContainer == null) return
                val uptimeSec = (System.currentTimeMillis() - sessionStartTime).coerceAtLeast(0L) / 1000L
                val hours = uptimeSec / 3600
                val mins = (uptimeSec % 3600) / 60
                val secs = uptimeSec % 60
                tvUptime.text = String.format(Locale.US, "СЕССИЯ: %02d:%02d:%02d", hours, mins, secs)
                mainHandler.postDelayed(this, 1000L)
            }
        }
        mainHandler.post(uptimeTickerRunnable)

        pixelShiftRunnable = object : Runnable {
            override fun run() {
                val card = uiLockContainer ?: return
                val p = uiWindowParams ?: return
                val shiftY = dp(60) + Random.nextInt(-8, 9)
                p.y = shiftY
                overlayWindowManager.updateViewSafe(card, p)
                mainHandler.postDelayed(this, 45000L)
            }
        }
        mainHandler.postDelayed(pixelShiftRunnable as Runnable, 45000L)
    }

    fun dismiss() {
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        countdownRunnable = null
        pixelShiftRunnable?.let { mainHandler.removeCallbacks(it) }
        pixelShiftRunnable = null

        bgLockContainer?.let {
            overlayWindowManager.removeViewSafe(it)
            bgLockContainer = null
        }
        uiLockContainer?.let {
            overlayWindowManager.removeViewSafe(it)
            uiLockContainer = null
        }
        bgWindowParams = null
        uiWindowParams = null
        onLockDismissedListener?.invoke()
    }
}
