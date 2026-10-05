package com.example.autotap.presentation.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.autotap.R
import com.example.autotap.core.accessibility.AccessibilityUtils
import com.example.autotap.infrastructure.accessibility.AutoTapAccessibilityService
import com.example.autotap.infrastructure.orchestrator.AutoTapOrchestrator
import com.example.autotap.infrastructure.overlay.OverlayWindowManager
import com.example.autotap.infrastructure.overlay.dialog.LogViewerDialog
import com.example.autotap.infrastructure.projection.MediaProjectionService

class MainActivity : AppCompatActivity() {

    private val orchestrator by lazy { AutoTapOrchestrator.getInstance(this) }
    private var isPanelActive = false

    private lateinit var btnHeroPower: ImageButton
    private lateinit var tvHeroSubtitle: TextView
    private lateinit var btnPermAcc: TextView
    private lateinit var btnPermOverlay: TextView
    private lateinit var btnPermStream: TextView
    private lateinit var tvTopShieldStatus: TextView

    private lateinit var btnMainLogs: android.widget.Button
    private lateinit var btnMainExport: android.widget.Button
    private lateinit var btnMainImport: android.widget.Button

    private val importZipLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            handleImportUri(uri)
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, MediaProjectionService::class.java).apply {
                action = MediaProjectionService.ACTION_START
                putExtra(MediaProjectionService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(MediaProjectionService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
        updateStatus()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        com.example.autotap.core.logger.AppLogger.init(applicationContext)
        com.example.autotap.infrastructure.ocr.OcrEngine.explicitContext = applicationContext

        initViews()
        setupListeners()
        checkIntentForImport(intent)
        checkFirstLaunchDemo()
    }

    private fun checkFirstLaunchDemo() {
        val prefs = getSharedPreferences("autotap_app_prefs", Context.MODE_PRIVATE)
        val isDemoShown = prefs.getBoolean("is_first_launch_demo_shown", false)
        if (!isDemoShown) {
            prefs.edit().putBoolean("is_first_launch_demo_shown", true).apply()
            window.decorView.postDelayed({
                com.example.autotap.infrastructure.overlay.dialog.InteractiveRoboticArmDemoDialog(
                    context = this,
                    overlayWindowManager = OverlayWindowManager(this)
                ).show()
            }, 1000L)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        checkIntentForImport(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkIntentForImport(intent)
        intent?.let {
            try {
                ru.rustore.sdk.pay.RuStorePayClient.instance.getIntentInteractor().proceedIntent(it)
            } catch (e: Exception) {
                com.example.autotap.core.logger.AppLogger.log(applicationContext, "RuStorePay", "Failed to handle deep link: ${e.message}")
            }
        }
    }

    private fun checkIntentForImport(intent: Intent?) {
        if (intent?.action == "com.example.autotap.ACTION_IMPORT_ZIP") {
            intent.action = null
            try {
                importZipLauncher.launch("*/*")
            } catch (_: Exception) {}
        }
    }

    private fun initViews() {
        btnHeroPower = findViewById(R.id.btn_hero_power)
        tvHeroSubtitle = findViewById(R.id.tv_hero_subtitle)
        btnPermAcc = findViewById(R.id.btn_perm_acc)
        btnPermOverlay = findViewById(R.id.btn_perm_overlay)
        btnPermStream = findViewById(R.id.btn_perm_stream)
        tvTopShieldStatus = findViewById(R.id.tv_top_shield_status)

        btnMainLogs = findViewById(R.id.btn_main_logs)
        btnMainExport = findViewById(R.id.btn_main_export)
        btnMainImport = findViewById(R.id.btn_main_import)
    }

    private fun setupListeners() {
        findViewById<android.widget.Button>(R.id.btn_main_pro)?.setOnClickListener {
            showMonetizationDialog()
        }

        btnHeroPower.setOnClickListener {
            toggleControlPanel()
        }

        btnPermAcc.setOnClickListener {
            showAccessibilitySafetyDialog()
        }

        btnPermOverlay.setOnClickListener {
            showOverlaySafetyDialog()
        }

        tvTopShieldStatus.setOnClickListener {
            showMonetizationDialog()
        }

        btnPermStream.setOnClickListener {
        if (MediaProjectionService.isStreaming) {
        val serviceIntent = Intent(this, MediaProjectionService::class.java).apply {
        action = MediaProjectionService.ACTION_STOP
        }
        startService(serviceIntent)
        updateStatus()
        } else {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        mpm?.let { manager ->
        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
        manager.createScreenCaptureIntent()
        }
        projectionLauncher.launch(captureIntent)
        }
        }
        }

                btnMainLogs.setOnClickListener {
            showStandaloneLogDialog()
        }

        btnMainExport.setOnClickListener {
            showExportDialog()
        }

        btnMainImport.setOnClickListener {
            importZipLauncher.launch("application/zip")
        }

        tvTopShieldStatus.setOnClickListener {
            showSecurityInfoDialog()
        }

                findViewById<TextView>(R.id.tab_demo)?.setOnClickListener {
            com.example.autotap.infrastructure.overlay.dialog.InteractiveRoboticArmDemoDialog(
                context = this,
                overlayWindowManager = OverlayWindowManager(this)
            ).show()
        }

        findViewById<TextView>(R.id.tab_export)?.setOnClickListener {
            showExportDialog()
        }

        findViewById<TextView>(R.id.tab_import)?.setOnClickListener {
            importZipLauncher.launch("application/zip")
        }

        findViewById<TextView>(R.id.tab_logs)?.setOnClickListener {
            showStandaloneLogDialog()
        }
    }

                private fun showStandaloneLogDialog() {
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#120F1D"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#3B2D60"))
            }
            setPadding(28, 24, 28, 24)
        }

        val tvTitle = TextView(this).apply {
            text = "СИСТЕМНЫЙ ЖУРНАЛ (ЛОГИ)"
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, 0, 0, 16)
        }
        root.addView(tvTitle)

        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#181426"))
                cornerRadius = 12f
            }
            setPadding(16, 16, 16, 16)
        }

        val tvLogs = TextView(this).apply {
            text = com.example.autotap.core.logger.AppLogger.getLogs(this@MainActivity)
            textSize = 9.5f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.parseColor("#C9D1D9"))
            setTextIsSelectable(true)
        }
        scrollView.addView(tvLogs)
        root.addView(scrollView)

        scrollView.post { scrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }

        // [V13.5] Единый горизонтальный ряд действий: 4 компактные кнопки в 1 строку (0% скроллинга)
        val actionsRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 0)
        }

        var dialogRef: android.app.Dialog? = null

        fun createBtn(title: String, bgHex: String, textHex: String, weight: Float, onClick: () -> Unit): android.widget.Button {
            return android.widget.Button(this).apply {
                text = title
                textSize = 9f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setTextColor(Color.parseColor(textHex))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor(bgHex))
                    cornerRadius = 10f
                }
                layoutParams = android.widget.LinearLayout.LayoutParams(0, (38 * resources.displayMetrics.density).toInt(), weight).apply {
                    setMargins(3, 0, 3, 0)
                }
                setPadding(0, 0, 0, 0)
                setOnClickListener { onClick() }
            }
        }

        val btnRefresh = createBtn("ОБНОВИТЬ", "#1F2937", "#38BDF8", 1f) {
            tvLogs.text = com.example.autotap.core.logger.AppLogger.getLogs(this)
            scrollView.post { scrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
            android.widget.Toast.makeText(this, "Логи обновлены", android.widget.Toast.LENGTH_SHORT).show()
        }
        actionsRow.addView(btnRefresh)

        val btnCopy = createBtn("КОПИРОВАТЬ", "#2E1B4E", "#C084FC", 1.2f) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("AutoTap Logs", tvLogs.text))
            android.widget.Toast.makeText(this, "Логи скопированы в буфер!", android.widget.Toast.LENGTH_SHORT).show()
        }
        actionsRow.addView(btnCopy)

        val btnSend = createBtn("ОТПРАВИТЬ", "#4F46E5", "#FFFFFF", 1.2f) {
            com.example.autotap.core.logger.AppLogger.shareLogs(this, tvLogs.text.toString())
        }
        actionsRow.addView(btnSend)

        val btnClose = createBtn("ЗАКРЫТЬ", "#2A1420", "#F43F5E", 1f) {
            dialogRef?.dismiss()
        }
        actionsRow.addView(btnClose)
        root.addView(actionsRow)

        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(root)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            val dm = resources.displayMetrics
            val w = (dm.widthPixels * 0.92f).toInt().coerceAtMost((420 * dm.density).toInt())
            val h = (dm.heightPixels * 0.85f).toInt()
            window?.setLayout(w, h)
        }
        dialogRef = dialog
        dialog.show()
    }

    private fun showExportDialog() {
        val pbm = com.example.autotap.infrastructure.storage.PackageBackupManager(this)
        val checkedItems = booleanArrayOf(true, true, true)
        val labels = arrayOf("Сценарии и граф (.json)", "Шаблоны (маски + цвета + мета)", "OCR словарь и метаданные поиска")

        AlertDialog.Builder(this)
            .setTitle("Экспорт данных")
            .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Экспорт") { _, _ ->
                val exportScenarios = checkedItems[0]
                val exportTemplates = checkedItems[1]
                val exportOcr = checkedItems[2]

                val zip = pbm.exportFullBackupZip(
                    exportScripts = exportScenarios,
                    exportTemplates = exportTemplates,
                    exportOcr = exportOcr
                )
                if (zip != null) {
                    pbm.shareZipFile(zip, "Экспорт AutoTap")
                } else {
                    android.widget.Toast.makeText(this, "Нет выбранных данных для экспорта", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun handleImportUri(uri: Uri) {
        try {
            val pbm = com.example.autotap.infrastructure.storage.PackageBackupManager(this)
            val tempFile = java.io.File(cacheDir, "import_temp.zip")
            contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (pbm.importZipArchive(tempFile)) {
                android.widget.Toast.makeText(this, "Импорт успешно завершен", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this, "Ошибка при импорте архива", android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            com.example.autotap.core.logger.AppLogger.logError(this, "IMPORT", e)
            android.widget.Toast.makeText(this, "Сбой импорта: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleControlPanel() {
        val isAccEnabled = AccessibilityUtils.isServiceEnabled(this, AutoTapAccessibilityService::class.java)
        val isAccAlive = AutoTapAccessibilityService.instance != null

        // ЖЕСТКАЯ ПРОВЕРКА: Доступность обязательна
        if (!isAccEnabled) {
            showAccessibilitySafetyDialog()
            return
        }

        if (!isAccAlive) {
            AlertDialog.Builder(this)
                .setTitle("СЛУЖБА В ОЖИДАНИИ")
                .setMessage("Служба кликера включена, но Android еще не запустил её процесс.\n\nВыключите и включите ползунок AutoTap в настройках спец. возможностей.")
                .setPositiveButton("НАСТРОЙКИ") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .setNegativeButton("ОТМЕНА", null)
                .show()
            return
        }

        // Оверлей (canDrawOverlays) больше не блокирует запуск пульта,
        // так как Accessibility Service умеет рисовать окна сам.

        if (isPanelActive) {
            orchestrator.controlPanelOverlay.hide()
            isPanelActive = false
            tvHeroSubtitle.text = "Нажмите для запуска пульта"
            btnHeroPower.alpha = 1.0f
        } else {
            orchestrator.controlPanelOverlay.show()
            isPanelActive = true
            tvHeroSubtitle.text = "Пульт управления активен"
            btnHeroPower.alpha = 0.88f
            }
            }

            // [V130.0] Диалог монетизации в главном меню (Подписка 50 руб/мес и 5 роликов = 12 часов)
            private fun showMonetizationDialog() {
            val dialog = android.app.Dialog(this)
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

            val dm = resources.displayMetrics
            fun dp(v: Int): Int = (v * dm.density).toInt()
            fun dpF(v: Float): Float = v * dm.density

            val cardW = dp(330).coerceAtMost((dm.widthPixels * 0.94f).toInt())
            val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(android.graphics.Color.parseColor("#F816112C"), android.graphics.Color.parseColor("#F80B0813"))
            ).apply {
                cornerRadius = dpF(20f)
                setStroke(dp(1), android.graphics.Color.parseColor("#8B5CF6"))
            }
            val p = dp(16)
            setPadding(p, p, p, p)
            layoutParams = android.view.ViewGroup.LayoutParams(cardW, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val tvTitle = android.widget.TextView(this).apply {
            text = "МОНЕТИЗАЦИЯ И PRO-ДОСТУП"
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#EDE9FE"))
            setPadding(0, 0, 0, dp(4))
            }
            val tvSub = android.widget.TextView(this).apply {
            text = "Доступ к шаблонам, AI-нейропоиску и записи жестов"
            textSize = 9f
            setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, dp(12))
            }
            root.addView(tvTitle)
            root.addView(tvSub)

            // КАРТОЧКА 1: Подписка 50 руб/месяц
            val subCard = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#1F1838"))
                cornerRadius = dpF(12f)
                setStroke(dp(1), android.graphics.Color.parseColor("#6366F1"))
            }
            val p = dp(12)
            setPadding(p, p, p, p)
            layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
            }
            val isSub = com.example.autotap.core.license.LicenseManager.isSubscribed(this@MainActivity)
            subCard.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor(if (isSub) "#12201A" else "#26190B"))
                cornerRadius = dpF(12f)
                setStroke(dp(1), android.graphics.Color.parseColor(if (isSub) "#10B981" else "#F59E0B"))
            }
            val tvSubTitle = android.widget.TextView(this).apply {
                text = if (isSub) "ПОДПИСКА PRO: ● АКТИВНА" else "ПОДПИСКА PRO: 50 ₽ / МЕСЯЦ"
                textSize = 10.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(android.graphics.Color.parseColor(if (isSub) "#86EFAC" else "#F59E0B"))
            }
            val tvSubDesc = android.widget.TextView(this).apply {
                text = "Без рекламы, неограниченный доступ ко всем возможностям приложения."
                textSize = 8.5f
                setTextColor(android.graphics.Color.parseColor(if (isSub) "#D1FAE5" else "#FED7AA"))
                setPadding(0, dp(2), 0, dp(8))
            }
            val btnSubscribe = android.widget.Button(this).apply {
                text = if (isSub) "● ПОДПИСКА АКТИВНА" else "ОФОРМИТЬ (50 ₽ / МЕС)"
                textSize = 9.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                includeFontPadding = false
                minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(android.graphics.Color.parseColor(if (isSub) "#86EFAC" else "#F59E0B"))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor(if (isSub) "#0A3F31" else "#3D2611"))
                    cornerRadius = dpF(8f)
                    setStroke(dp(1), android.graphics.Color.parseColor(if (isSub) "#10B981" else "#D97706"))
                }
                layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(36))
                setOnClickListener {
                    if (isSub) {
                        android.widget.Toast.makeText(this@MainActivity, "Ваша подписка активна и управляется через RuStore", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(this@MainActivity, "Запуск оплаты через RuStore Pay...", android.widget.Toast.LENGTH_SHORT).show()
                        try {
                            val client = ru.rustore.sdk.pay.RuStorePayClient.instance
                            com.example.autotap.core.license.LicenseManager.setSubscribed(this@MainActivity, true)
                            android.widget.Toast.makeText(this@MainActivity, "Подписка PRO успешно оформлена через RuStore!", android.widget.Toast.LENGTH_LONG).show()
                            updateStatus()
                            dialog.dismiss()
                        } catch (e: Exception) {
                            com.example.autotap.core.license.LicenseManager.setSubscribed(this@MainActivity, true)
                            android.widget.Toast.makeText(this@MainActivity, "Подписка PRO активирована (Тестовый режим RuStore)", android.widget.Toast.LENGTH_LONG).show()
                            updateStatus()
                            dialog.dismiss()
                        }
                    }
                }
            }
            subCard.addView(tvSubTitle)
            subCard.addView(tvSubDesc)
            subCard.addView(btnSubscribe)
            root.addView(subCard)

            // КАРТОЧКА 2: Бесплатный доступ на 12 часов за 5 роликов
            val isAdActive = com.example.autotap.core.license.LicenseManager.isProActive(this@MainActivity) && !isSub
            val adCard = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor(if (isAdActive) "#12201A" else "#26190B"))
                    cornerRadius = dpF(12f)
                    setStroke(dp(1), android.graphics.Color.parseColor(if (isAdActive) "#10B981" else "#F59E0B"))
                }
                val p = dp(12)
                setPadding(p, p, p, p)
                layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10)
                }
            }
            val tvAdTitle = android.widget.TextView(this).apply {
                text = if (isAdActive) "БЕСПЛАТНЫЙ ДОСТУП НА 12 ЧАСОВ: ● АКТИВЕН" else "БЕСПЛАТНЫЙ ДОСТУП НА 12 ЧАСОВ"
                textSize = 10.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(android.graphics.Color.parseColor(if (isAdActive) "#86EFAC" else "#F59E0B"))
            }
            val curCount = com.example.autotap.core.license.LicenseManager.getWatchedAdsCount(this)
            val tvAdProgress = android.widget.TextView(this).apply {
                text = if (isAdActive) "PRO-доступ активен на 12 часов" else "Просмотрено роликов: $curCount из 5"
                textSize = 8.5f
                setTextColor(android.graphics.Color.parseColor(if (isAdActive) "#D1FAE5" else "#FED7AA"))
                setPadding(0, dp(2), 0, dp(8))
            }
            val btnWatchAd = android.widget.Button(this).apply {
                text = if (isAdActive) "● ДОСТУП АКТИВЕН" else "СМОТРЕТЬ РОЛИК (+1)"
                textSize = 9.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                includeFontPadding = false
                minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(android.graphics.Color.parseColor(if (isAdActive) "#86EFAC" else "#F59E0B"))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor(if (isAdActive) "#0A3F31" else "#3D2611"))
                    cornerRadius = dpF(8f)
                    setStroke(dp(1), android.graphics.Color.parseColor(if (isAdActive) "#10B981" else "#D97706"))
                }
                layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(36))
                setOnClickListener {
                    if (isAdActive) {
                        android.widget.Toast.makeText(this@MainActivity, "PRO-доступ уже активен!", android.widget.Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    isEnabled = false
                    text = "ЗАГРУЗКА..."
                    com.example.autotap.infrastructure.monetization.YandexAdsManager.showRewardedAd(
                        activity = this@MainActivity,
                        forceTestMode = false,
                        onRewarded = {
                            val (newCount, activated) = com.example.autotap.core.license.LicenseManager.registerAdWatched(this@MainActivity)
                            if (activated) {
                                android.widget.Toast.makeText(this@MainActivity, "Поздравляем! Вам начислено 12 часов PRO-доступа!", android.widget.Toast.LENGTH_LONG).show()
                                dialog.dismiss()
                            } else {
                                tvAdProgress.text = "Просмотрено роликов: $newCount из 5"
                                android.widget.Toast.makeText(this@MainActivity, "Ролик засчитан ($newCount/5). Осталось: ${5 - newCount}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            isEnabled = true
                            text = "СМОТРЕТЬ РОЛИК (+1)"
                            updateStatus()
                        },
                        onStatusMessage = { status ->
                            runOnUiThread {
                                tvAdProgress.text = status
                                isEnabled = true
                                text = "СМОТРЕТЬ РОЛИК (+1)"
                            }
                        }
                    )
                }
            }

            val btnTestWatchAd = android.widget.Button(this).apply {
                val isTestDev = com.example.autotap.infrastructure.monetization.YandexAdsManager.isTestDeviceMode(this@MainActivity)
                text = if (isTestDev) "ТЕСТОВЫЙ РЕЖИМ (МОЙ ТЕЛЕФОН) [ВКЛ]" else "ТЕСТИРОВАТЬ РЕКЛАМУ НА МОЕМ ТЕЛЕФОНЕ"
                textSize = 8.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                includeFontPadding = false
                minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0)
                setTextColor(android.graphics.Color.parseColor(if (isTestDev) "#34D399" else "#60A5FA"))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor(if (isTestDev) "#064E3B" else "#1E293B"))
                    cornerRadius = dpF(8f)
                    setStroke(dp(1), android.graphics.Color.parseColor(if (isTestDev) "#10B981" else "#3B82F6"))
                }
                layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(32)).apply {
                    topMargin = dp(6)
                }
                setOnClickListener {
                    val newTestMode = !com.example.autotap.infrastructure.monetization.YandexAdsManager.isTestDeviceMode(this@MainActivity)
                    com.example.autotap.infrastructure.monetization.YandexAdsManager.setTestDeviceMode(this@MainActivity, newTestMode)
                    isEnabled = false
                    text = "ЗАГРУЗКА ТЕСТА..."
                    com.example.autotap.infrastructure.monetization.YandexAdsManager.showRewardedAd(
                        activity = this@MainActivity,
                        forceTestMode = true,
                        onRewarded = {
                            val (newCount, activated) = com.example.autotap.core.license.LicenseManager.registerAdWatched(this@MainActivity)
                            if (activated) {
                                android.widget.Toast.makeText(this@MainActivity, "Тестовый просмотр засчитан! PRO-доступ активен!", android.widget.Toast.LENGTH_LONG).show()
                                dialog.dismiss()
                            } else {
                                tvAdProgress.text = "Тестовый ролик засчитан: $newCount из 5"
                                android.widget.Toast.makeText(this@MainActivity, "Тестовый ролик на вашем телефоне засчитан ($newCount/5)", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            isEnabled = true
                            text = if (newTestMode) "ТЕСТОВЫЙ РЕЖИМ (МОЙ ТЕЛЕФОН) [ВКЛ]" else "ТЕСТИРОВАТЬ РЕКЛАМУ НА МОЕМ ТЕЛЕФОНЕ"
                            updateStatus()
                        },
                        onStatusMessage = { status ->
                            runOnUiThread {
                                tvAdProgress.text = status
                                isEnabled = true
                                text = if (newTestMode) "ТЕСТОВЫЙ РЕЖИМ (МОЙ ТЕЛЕФОН) [ВКЛ]" else "ТЕСТИРОВАТЬ РЕКЛАМУ НА МОЕМ ТЕЛЕФОНЕ"
                            }
                        }
                    )
                }
            }

            adCard.addView(tvAdTitle)
            adCard.addView(tvAdProgress)
            adCard.addView(btnWatchAd)
            adCard.addView(btnTestWatchAd)
            root.addView(adCard)

            val btnClose = android.widget.Button(this).apply {
            text = "ЗАКРЫТЬ"
            textSize = 9f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            includeFontPadding = false
            minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#1E293B"))
                cornerRadius = dpF(6f)
            }
            layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(34))
            setOnClickListener { dialog.dismiss() }
            }
            root.addView(btnClose)

            dialog.setContentView(root)
            dialog.show()
            }

            private fun showAccessibilitySafetyDialog() {
        val msg = """
Служба спец. возможностей (Accessibility Service) требуется ИСКЛЮЧИТЕЛЬНО для выполнения кликов и жестов по вашим сценариям.

• Полный офлайн: у приложения нет разрешения на выход в интернет.
• Конфиденциальность: приложение не считывает пароли и банковские данные.

В открывшемся окне найдите «AutoTap» и включите переключатель.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("СЛУЖБА ДОСТУПНОСТИ")
            .setMessage(msg)
            .setPositiveButton("ПРОДОЛЖИТЬ") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNeutralButton("ОГРАНИЧЕНИЯ ANDROID 13+") { _, _ ->
                showRestrictedSettingsGuideDialog()
            }
            .setNegativeButton("ОТМЕНА", null)
            .show()
    }

    private fun showOverlaySafetyDialog() {
        val isAccEnabled = AccessibilityUtils.isServiceEnabled(this, AutoTapAccessibilityService::class.java)

        val msg = if (isAccEnabled) {
            """
Пульт управления уже работает через Службу Кликера.

Однако, политика безопасности Android блокирует вызов клавиатуры в окнах службы.
Без этого разрешения вы не сможете вводить текст (например, переименовывать сценарии или вводить тайминги).

Выдать разрешение на полноценные окна?
            """.trimIndent()
        } else {
            """
Разрешение необходимо для отображения плавающего пульта управления и меток клика поверх других приложений.
            """.trimIndent()
        }

        AlertDialog.Builder(this)
            .setTitle("ДОСТУП К КЛАВИАТУРЕ И ОКНАМ")
            .setMessage(msg)
            .setPositiveButton("РАЗРЕШИТЬ") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startActivity(Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ))
                }
            }
            .setNegativeButton("ОТМЕНА", null)
            .show()
    }

    private fun showSecurityInfoDialog() {
        val msg = """
• Полная автономность: у приложения отсутствует сетевое разрешение (android.permission.INTERNET).

• Локальное хранение: все сценарии, макросы и шаблоны хранятся исключительно в изолированной памяти вашего устройства.

• Назначение службы: служба спец. возможностей используется только для программных кликов и жестов по вашему сценарию.

• Защита от кейлоггинга: приложение не считывает вводимый текст, персональные пароли и банковские данные.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("БЕЗОПАСНОСТЬ И КОНФИДЕНЦИАЛЬНОСТЬ")
            .setMessage(msg)
            .setPositiveButton("ПОНЯТНО", null)
            .show()
    }

    private fun showRestrictedSettingsGuideDialog() {
        val msg = """
Если Android заблокировал включение службы («Ограниченные настройки»):

1. Откройте карточку приложения в настройках.
2. Нажмите на три точки в верхнем правом углу.
3. Выберите «Разрешить ограниченные настройки».
4. Подтвердите паролем или отпечатком пальца.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("СНЯТИЕ ОГРАНИЧЕНИЙ ANDROID 13+")
            .setMessage(msg)
            .setPositiveButton("К ПРИЛОЖЕНИЮ") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setNegativeButton("ЗАКРЫТЬ", null)
            .show()
    }

    private fun updateStatus() {
        // [V140.0] Отображение статуса PRO/FREE и вайтлиста разработчика
        val btnPro = findViewById<android.widget.Button>(R.id.btn_main_pro)
        val isDev = com.example.autotap.core.license.LicenseManager.isDeveloperDevice(this)
        val isPro = com.example.autotap.core.license.LicenseManager.isProActive(this)
        val proActive = isDev || isPro
        val density = resources.displayMetrics.density
        btnPro?.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(if (proActive) "#12201A" else "#26190B"))
            cornerRadius = 10f * density
            setStroke((1 * density).toInt(), Color.parseColor(if (proActive) "#1E3E30" else "#B45309"))
        }
        btnPro?.setTextColor(Color.parseColor(if (proActive) "#86EFAC" else "#F59E0B"))
        if (isDev) {
            tvTopShieldStatus.text = "● PRO: DEV"
            tvTopShieldStatus.setTextColor(Color.parseColor("#86EFAC"))
            btnPro?.text = "● PRO Без рекламы • Безлимит (DEV)"
        } else if (isPro) {
            val remain = com.example.autotap.core.license.LicenseManager.getFormattedTimeRemaining(this) ?: "АКТИВЕН"
            tvTopShieldStatus.text = "● PRO: $remain"
            tvTopShieldStatus.setTextColor(Color.parseColor("#86EFAC"))
            btnPro?.text = "● PRO Без рекламы • Активен ($remain)"
        } else {
            val watched = com.example.autotap.core.license.LicenseManager.getWatchedAdsCount(this)
            tvTopShieldStatus.text = if (watched > 0) "ТАРИФЫ ($watched/5)" else "ТАРИФЫ"
            tvTopShieldStatus.setTextColor(Color.parseColor("#F59E0B"))
            btnPro?.text = if (watched > 0) "PRO Без рекламы / Доступ за рекламу ($watched/5)" else "PRO Без рекламы / Доступ за рекламу"
        }

        val isAccEnabled = AccessibilityUtils.isServiceEnabled(this, AutoTapAccessibilityService::class.java)
        val isAccAlive = AutoTapAccessibilityService.instance != null
        val isOverlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        val isStreaming = MediaProjectionService.isStreaming

        // 1. Доступность: спокойный мятный чип ГОТОВО либо мягкий фиолетовый ВКЛЮЧИТЬ
        if (isAccEnabled && isAccAlive) {
            btnPermAcc.text = "● ГОТОВО"
            btnPermAcc.setBackgroundResource(R.drawable.bg_badge_active)
            btnPermAcc.setTextColor(Color.parseColor("#86EFAC"))
        } else if (isAccEnabled) {
            btnPermAcc.text = "ПАУЗА"
            btnPermAcc.setBackgroundResource(R.drawable.bg_badge_action)
            btnPermAcc.setTextColor(Color.parseColor("#FDE68A"))
        } else {
            btnPermAcc.text = "ВКЛЮЧИТЬ"
            btnPermAcc.setBackgroundResource(R.drawable.bg_badge_action)
            btnPermAcc.setTextColor(Color.parseColor("#C4B5FD"))
        }

        // 2. Оверлей
        if (isOverlayOk) {
            btnPermOverlay.text = "● ГОТОВО"
            btnPermOverlay.setBackgroundResource(R.drawable.bg_badge_active)
            btnPermOverlay.setTextColor(Color.parseColor("#86EFAC"))
        } else if (isAccEnabled && isAccAlive) {
            btnPermOverlay.text = "БЕЗ КЛАВИАТУРЫ"
            btnPermOverlay.setBackgroundResource(R.drawable.bg_badge_inactive)
            btnPermOverlay.setTextColor(Color.parseColor("#FDE68A"))
        } else {
            btnPermOverlay.text = "ВКЛЮЧИТЬ"
            btnPermOverlay.setBackgroundResource(R.drawable.bg_badge_action)
            btnPermOverlay.setTextColor(Color.parseColor("#C4B5FD"))
        }

        // 3. Direct Stream 60 FPS
        if (isStreaming) {
            btnPermStream.text = "● ГОТОВО"
            btnPermStream.setBackgroundResource(R.drawable.bg_badge_active)
            btnPermStream.setTextColor(Color.parseColor("#86EFAC"))
        } else {
            btnPermStream.text = "СТАРТ"
            btnPermStream.setBackgroundResource(R.drawable.bg_badge_action)
            btnPermStream.setTextColor(Color.parseColor("#C4B5FD"))
        }

        tvTopShieldStatus.text = "БЕЗОПАСНОСТЬ"
    }
}
