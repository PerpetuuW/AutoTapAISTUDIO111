package com.example.autotap.infrastructure.monetization

import android.app.Activity
import android.content.Context
import com.example.autotap.core.logger.AppLogger
import com.yandex.mobile.ads.common.AdRequestConfiguration
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.MobileAds
import com.yandex.mobile.ads.rewarded.Reward
import com.yandex.mobile.ads.rewarded.RewardedAd
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoader

/**
 * Менеджер монетизации и рекламы с вознаграждением Yandex Mobile Ads SDK для RuStore.
 *
 * Особенности:
 *  1. Рабочий промышленный блок рекламы RuStore / Yandex: "R-M-20133804-1" (AutoTap PRO gift).
 *  2. Официальный тестовый блок Yandex: "demo-rewarded-yandex" для мгновенного тестирования на телефоне без ожидания модерации.
 *  3. Автоматический фоллбэк на тестовый блок при отсутствии заглавного филла.
 */
object YandexAdsManager {

    // Рабочий блок RuStore / Yandex Ads из кабинета пользователя
    const val PROD_REWARDED_AD_UNIT_ID = "R-M-20133804-1"

    // Тестовый официальный блок Yandex Mobile Ads
    const val DEMO_REWARDED_AD_UNIT_ID = "demo-rewarded-yandex"

    // Минимальное время просмотра рекламного ролика (15 секунд) для защиты от преждевременного выхода
    private const val MIN_WATCH_DURATION_MS = 15_000L

    private const val PREFS_NAME = "autotap_ads_prefs"
    private const val KEY_TEST_DEVICE_MODE = "pref_test_device_mode"

    private val isAdLoading = java.util.concurrent.atomic.AtomicBoolean(false)
    private val isAdShowing = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var isInitialized = false

    fun isTestDeviceMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TEST_DEVICE_MODE, false)
    }

    fun setTestDeviceMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TEST_DEVICE_MODE, enabled)
            .apply()
    }

    fun initialize(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                MobileAds.initialize(context.applicationContext) {
                    AppLogger.log(null, "YANDEX_ADS", "Yandex Mobile Ads SDK инициализирован для RuStore")
                }
                isInitialized = true
            } catch (e: Throwable) {
                AppLogger.logError(null, "YANDEX_ADS_INIT", e)
            }
        }
    }

    /**
     * Показывает рекламу с вознаграждением (Rewarded Ad).
     */
    fun showRewardedAd(
        activity: Activity,
        forceTestMode: Boolean = false,
        onRewarded: () -> Unit,
        onStatusMessage: (String) -> Unit
    ) {
        initialize(activity)

        if (isAdShowing.get()) {
            AppLogger.log(null, "YANDEX_ADS", "Игнорирование запроса: реклама уже воспроизводится на экране")
            onStatusMessage("Реклама уже открыта!")
            return
        }

        if (isAdLoading.compareAndSet(false, true)) {
            val effectiveTestMode = forceTestMode || isTestDeviceMode(activity)
            val primaryUnitId = if (effectiveTestMode) DEMO_REWARDED_AD_UNIT_ID else PROD_REWARDED_AD_UNIT_ID
            AppLogger.log(null, "YANDEX_ADS", "Запрос показа рекламы: unitId=$primaryUnitId (testMode=$effectiveTestMode)")
            onStatusMessage("Загрузка рекламного видеоролика Yandex...")

            loadAndShowInternal(
                activity = activity,
                adUnitId = primaryUnitId,
                isFallback = false,
                forceTestMode = effectiveTestMode,
                onRewarded = onRewarded,
                onStatusMessage = { msg ->
                    if (msg.contains("Ошибка") || msg.contains("Не удалось")) {
                        isAdLoading.set(false)
                    }
                    onStatusMessage(msg)
                }
            )
        } else {
            AppLogger.log(null, "YANDEX_ADS", "Игнорирование запроса: реклама уже загружается...")
            onStatusMessage("Загрузка ролика уже выполняется...")
        }
    }

    private fun loadAndShowInternal(
        activity: Activity,
        adUnitId: String,
        isFallback: Boolean,
        forceTestMode: Boolean,
        onRewarded: () -> Unit,
        onStatusMessage: (String) -> Unit
    ) {
        val loader = RewardedAdLoader(activity)
        val config = AdRequestConfiguration.Builder(adUnitId).build()

        val rewardClaimed = java.util.concurrent.atomic.AtomicBoolean(false)
        var adStartTimestamp = 0L

        loader.setAdLoadListener(object : RewardedAdLoadListener {
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                AppLogger.log(null, "YANDEX_ADS", "Рекламный ролик успешно загружен: $adUnitId")
                onStatusMessage("Реклама готова! Показ...")

                rewardedAd.setAdEventListener(object : RewardedAdEventListener {
                    override fun onAdShown() {
                        adStartTimestamp = System.currentTimeMillis()
                        isAdShowing.set(true)
                        isAdLoading.set(false)
                        AppLogger.log(null, "YANDEX_ADS", "Рекламный ролик отображен на экране (старт: $adStartTimestamp)")
                    }

                    override fun onAdDismissed() {
                        isAdShowing.set(false)
                        isAdLoading.set(false)
                        val durationMs = if (adStartTimestamp > 0L) System.currentTimeMillis() - adStartTimestamp else 0L
                        AppLogger.log(null, "YANDEX_ADS", "Пользователь закрыл рекламный ролик (время просмотра: ${durationMs}мс)")
                        if (adStartTimestamp > 0L && durationMs < MIN_WATCH_DURATION_MS && !rewardClaimed.get()) {
                            onStatusMessage("Просмотр завершен слишком рано (${durationMs / 1000}с < 15с). Награда не зачислена.")
                        }
                    }

                    override fun onRewarded(reward: Reward) {
                        val durationMs = if (adStartTimestamp > 0L) System.currentTimeMillis() - adStartTimestamp else MIN_WATCH_DURATION_MS
                        AppLogger.log(null, "YANDEX_ADS", "Событие SDK onRewarded: время просмотра ${durationMs}мс")

                        if (adStartTimestamp > 0L && durationMs < MIN_WATCH_DURATION_MS) {
                            AppLogger.log(null, "YANDEX_ADS", "Защита 15с: Награда отклонена (просмотрено всего ${durationMs / 1000}с)")
                            onStatusMessage("Просмотр завершен раньше 15 секунд! Награда не зачислена.")
                            isAdShowing.set(false)
                            isAdLoading.set(false)
                            return
                        }

                        if (rewardClaimed.compareAndSet(false, true)) {
                            AppLogger.log(null, "YANDEX_ADS", "Вознаграждение зачислено (1 ролик, ${durationMs / 1000}с): type=${reward.type}, amount=${reward.amount}")
                            isAdShowing.set(false)
                            isAdLoading.set(false)
                            onRewarded()
                        } else {
                            AppLogger.log(null, "YANDEX_ADS", "Предотвращено повторное зачисление рекламы за один показ")
                        }
                    }

                    override fun onAdFailedToShow(adError: com.yandex.mobile.ads.common.AdError) {
                        isAdShowing.set(false)
                        isAdLoading.set(false)
                        AppLogger.log(null, "YANDEX_ADS", "Ошибка показа рекламы: ${adError.description}")
                        onStatusMessage("Ошибка показа видео: ${adError.description}")
                    }

                    override fun onAdClicked() {
                        AppLogger.log(null, "YANDEX_ADS", "Клик по рекламе")
                    }

                    override fun onAdImpression(impressionData: ImpressionData?) {
                        AppLogger.log(null, "YANDEX_ADS", "Фиксация показа рекламы (Impression)")
                    }
                })

                rewardedAd.show(activity)
            }

            override fun onAdFailedToLoad(error: AdRequestError) {
                AppLogger.log(null, "YANDEX_ADS", "Сбой загрузки $adUnitId: ${error.description} (code=${error.code})")

                if (!isFallback && adUnitId == PROD_REWARDED_AD_UNIT_ID) {
                    AppLogger.log(null, "YANDEX_ADS", "Фоллбэк на тестовый блок $DEMO_REWARDED_AD_UNIT_ID")
                    onStatusMessage("Загрузка тестового видеоролика Yandex...")
                    loadAndShowInternal(
                        activity = activity,
                        adUnitId = DEMO_REWARDED_AD_UNIT_ID,
                        isFallback = true,
                        forceTestMode = forceTestMode,
                        onRewarded = onRewarded,
                        onStatusMessage = onStatusMessage
                    )
                } else {
                    isAdLoading.set(false)
                    isAdShowing.set(false)
                    onStatusMessage("Не удалось загрузить рекламный ролик (${error.description}). Попробуйте тестовый режим.")
                }
            }
        })

        loader.loadAd(config)
    }
}
