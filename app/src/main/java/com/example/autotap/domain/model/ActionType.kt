package com.example.autotap.domain.model

enum class ActionType {
    CLICK,          // Одиночное нажатие с настраиваемой длительностью и рандомизацией
    LONG_PRESS,     // Длительное удержание в точке
    SWIPE,          // [Устарело, слито с PATH] Прямой векторный свайп
    PATH,           // Универсальный путь (Gesture / Virtual Stick)
    PINCH,          // Двупальцевый жест сжатия / растяжения (масштабирование камеры в играх)
    TRIGGER,        // Компьютерное зрение (ИИ-поиск графического шаблона)
    OCR,            // Оптическое распознавание текста на экране (ML Kit)
    COLOR_CHECK,    // Проверка цвета пикселя с настраиваемым допуском
    SUBROUTINE,     // Вызов подпрограммы по стабильному тегу
    RETURN,         // Возврат из подпрограммы в точку вызова
    GLOBAL_BACK,    // Системная кнопка "Назад" (AccessibilityService.GLOBAL_ACTION_BACK)
    GLOBAL_HOME,    // Системная кнопка "Домой" (AccessibilityService.GLOBAL_ACTION_HOME)
    DELAY           // Пауза / Человеческая рандомизированная задержка
}

enum class PlayMode {
    GESTURE,
    VIRTUAL_STICK
}
