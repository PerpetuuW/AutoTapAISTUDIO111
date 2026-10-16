# Architectural Plan: Transition Floating Overlays to Pure Native 120 FPS Kotlin Canvas Views

## Problem & Architectural Goal
The user requested: *"нужно перевести весь тяжелый Компос на рисование в котлин, либо использовать библиотеку, которая специализируется на оверлеях"*

Jetpack Compose inside Android `WindowManager` system overlays creates continuous runtime overhead (Recomposer loops, `ViewTreeLifecycleOwner` events, compose context re-evaluations).

To achieve **zero-lag, instant 120 FPS response, zero GC pressure, and 100% reliable layout bounds**, we will migrate the floating overlays from Jetpack Compose back to our ultra-lightweight, native **Kotlin Canvas View architecture** (`CanvasIconButton`, `VectorIconDrawer`, `FrameLayout`, `LinearLayout`).

---

## Proposed Changes & Component Architecture

### 1. Control Panel Overlay (`ControlPanelOverlay.kt`)
- Un-deprecate `ControlPanelOverlay.kt`.
- Build the main row using native Kotlin `LinearLayout` and `CanvasIconButton`:
  1. `BTN_PLAY_VIEW` (Play/Pause)
  2. `BTN_CAPTURE` (Capture ИИ-вырезка шаблона — ngay за Play)
  3. `BTN_ADD` (`+` Добавить мишень)
  4. `BTN_RECORD` (Запись жестов)
  5. `BTN_TOGGLE_VIEW` (Шторка)
  6. `BTN_HIDE` (Глаз / Прозрачность)
  7. `BTN_SETTINGS` (Настройки)
  8. `BTN_CLOSE` (Крестик закрытия оверлея **СПРАВА СВЕРХУ**)
- Sub Row (2nd row):
  1. `BTN_LOCK` (AFK Замок)
  2. `BTN_CLEAR` (Очистить все)
  3. `BTN_SCRIPTS` (Сценарии)
  4. `BTN_HELP` (Справка)
- Compact button sizing (`30dp` button, `4dp` padding): Total main row width is `268dp`, fitting comfortably on all phone screens (360dp+).
- The red `BTN_CLOSE` (`X`) is **100% visible at the top-right corner**.
- Smooth touch drag listener with `posX` and `posY` state updating via `updateViewLayout`.

### 2. Execution Badge Overlay (`RunningBadgeOverlay.kt`)
- Convert running execution badge to pure native Kotlin View (`RunningBadgeOverlay.kt`).
- Progress bar rendered via pure `Canvas` on-draw.
- Lock and Stop buttons rendered via `CanvasIconButton` with `onClickListener` for instant response and zero lag.

### 3. Orchestrator Binding (`AutoTapOrchestrator.kt`)
- Route `controlPanelOverlay` and `runningBadgeOverlay` to native Kotlin Canvas overlays (`ControlPanelOverlay` & `RunningBadgeOverlay`).

### 4. Inspector Test Suite (`1AInspector`)
- Update `1AInspector/check_control_panel_compose_size_and_close_button.py` and all 177 inspector modules to verify pure Kotlin Canvas overlay contracts.

---

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 177 1AInspector scripts.

### Manual Verification
- Open Control Panel: verify instant 120 FPS responsiveness, zero lag, smooth dragging, and red `CLOSE` (`X`) button 100% visible at top-right.
- Run macro: verify Running Badge displays progress and Stop button halts execution instantly.
