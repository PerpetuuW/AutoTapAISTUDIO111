package com.example.autotap.core.config

/**
 * Единая централизованная конфигурация всех значений по умолчанию приложения AutoTap.
 *
 * Все компоненты (компьютерное зрение, калибровка, оверлеи, настройки, замок, оркестратор)
 * ссылаются на константы из этого файла. Изменение значения здесь мгновенно обновляет
 * поведение по умолчанию во всей системе.
 */
object AppConfig {

    /**
     * Конфигурация алгоритмов Компьютерного Зрения (OpenCV / ZNCC / CIELAB ΔE)
     */
    object Vision {
        const val DEFAULT_SIMILARITY_PERCENT = 75
        const val MIN_SIMILARITY_PERCENT = 55
        const val MAX_SIMILARITY_PERCENT = 98
        const val AUTO_CALIBRATION_SAFETY_MARGIN_PERCENT = 5
        const val DEFAULT_SHAPE_EXPANSION_PX = 45
        const val DEFAULT_PADDING_OFFSET_PX = 2
        const val DEFAULT_AI_TIMEOUT_SECONDS = 5
        const val DEFAULT_POLL_INTERVAL_MS = 300L
        const val STREAMING_POLL_INTERVAL_MS = 33L
        const val DEFAULT_DOWNSCALE_FACTOR = 1
    }

    /**
     * Конфигурация жестов, кликов и таймингов исполнения
     */
    object Timing {
        const val DEFAULT_CLICK_DURATION_MS = 120L
        const val DEFAULT_SWIPE_DURATION_MS = 300L
        const val DEFAULT_PATH_DURATION_MS = 1000L
        const val DEFAULT_CLICK_PAUSE_MS = 200L
        const val DEFAULT_HOLD_DURATION_MS = 120L
        const val DEBOUNCE_PERSIST_DELAY_MS = 350L
        const val TOUCH_DEBOUNCE_MS = 350L
    }

    /**
     * Конфигурация AFK Замка экрана (AMOLED Burn-in Guard)
     */
    object ScreenLock {
        const val HOLD_UNLOCK_DURATION_MS = 3000L
        const val DRIFT_RADIUS_DP = 100
        const val DEFAULT_SCREEN_BRIGHTNESS = 0.01f
        const val PIXEL_SHIFT_INTERVAL_MS = 45000L
        const val UI_CARD_WIDTH_DP = 260
        const val UI_CARD_BOTTOM_MARGIN_DP = 60
    }

    /**
     * Конфигурация интерфейса, плавающей панели и мишеней
     */
    object UI {
        const val BUTTON_TOUCH_TARGET_MIN_DP = 48
        const val CONTROL_PANEL_BUTTON_SIZE_DP = 32
        const val CONTROL_PANEL_SUBROW_BUTTON_SIZE_DP = 30
        const val QUICK_RING_RADIUS_DP = 64f
        const val QUICK_RING_BUTTON_SIZE_DP = 38
        const val MAX_LRU_CACHE_SIZE_MB = 64
    }

    /**
     * Конфигурация монетизации и рекламы
     */
    object Monetization {
        const val MIN_WATCH_DURATION_MS = 10000L
    }
}
