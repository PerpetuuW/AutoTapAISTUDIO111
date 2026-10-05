# 1AProtocol: 01. АВТОМАТИЗАЦИЯ, ЖЕСТЫ И УПРАВЛЕНИЕ ПОТОКОМ

## 1. КИНЕМАТИКА ЖЕСТОВ И АППАРАТНЫЙ ДЕБАУНС
- **Single-Phase Tap**: клик регистрируется строго по `MotionEvent.ACTION_UP`, если смещение не превысило `TouchSlop` (10dp). Запрещен клик по `ACTION_DOWN`.
- **Debounce Barrier**: повторные касания в радиусе 30px с интервалом менее 350мс отбрасываются как аппаратные дубли.
- **StrokeDescription Compat**: при `minSdk 24` конструктор `StrokeDescription` изолируется через метод-мост `createStrokeCompat` с рантайм-ветвлением на API 26+.

## 2. ЭТАЛОННЫЕ СИСТЕМНЫЕ ДЕЙСТВИЯ (SYSTEM ACTS)
- `GLOBAL_BACK`: мгновенное закрытие модальных окон, диалогов и клавиатур через нативный `AccessibilityService.GLOBAL_ACTION_BACK`.
- `GLOBAL_HOME`: сворачивание приложений через `AccessibilityService.GLOBAL_ACTION_HOME`.
- `DELAY`: поддержка недетерминированного интервала паузы `minMs..maxMs` для защиты от антибот-детекторов.

## 3. УСТРОЙСТВА ВЕТВЛЕНИЯ ЦИКЛОВ (LOOP ROUTER)
- Узел `LOOP_COUNTER` маршрутизирует выполнение по двум портам:
  - 🟢 `out_loop_body` (`ИТЕРАЦИЯ (i < N)`): переход в тело цикла.
  - 🟣 `out_loop_done` (`ЗАВЕРШЕНО (i ≥ N)`): терминальный выход со сбросом счетчика в `0`.

## 4. ДВУХРЕЖИМНАЯ ЗАПИСЬ И ДИСПАТЧЕР ДЕЙСТВИЙ
- **Smart Recording**: при тапе снимается скриншот, вырезается область 64dp, классифицируется морфология, создается `ActionType.TRIGGER` с `clickAiTarget = true`. Наложение простого клика на ИИ-шаблон блокируется.
- **Raw Gesture Recording**: фиксация физических жестов `CLICK`, `SWIPE`, `PATH`.
- **Action Dispatcher**: `TRIGGER` сразу инициирует `startCaptureForStep()`, `COLOR_CHECK` — пипетку `startEyedropper()`, `SWIPE` — спаренные мишени.
