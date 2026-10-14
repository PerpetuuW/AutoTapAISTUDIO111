# Jetpack Compose Step Editor (`EditActionComposeDialog`), Tutorial Target Rects, Universal Keyboard Dismissal, & Inspector Archiving

## Problem
1. **Legacy Inspector Constraints**: 15 legacy Python inspector scripts tied to `EditActionDialog.kt` string literals were blocking the migration to Jetpack Compose. As requested, these scripts will be moved to `1AInspector/archive_legacy_dialog_checks/` so the new Compose step editor can be implemented cleanly.
2. **Compose Step Editor (`EditActionComposeDialog`)**: Create a modern, modular, MVI-driven step editor dialog in Jetpack Compose (`EditActionComposeDialog.kt`) using `EditActionState`, supporting all 3 tabs (Settings, Vision & Multi-Search, Logic/Routing) without layout bugs or string wrapping errors.
3. **Tutorial Coordinates for Compose Overlays**: Implement `getTutorialRect(tagKey: String): Rect?` in `ControlPanelComposeOverlay.kt` so `InteractiveTutorialOverlay` spotlight and laser lines point directly to target buttons.
4. **Universal Keyboard Action (Checkmark & Hide Button)**: Configure all text inputs across overlays (`CalibrationOverlay`, `GlobalSettingsDialog`, `ScriptsDialog`, `EditActionComposeDialog`) with:
   - `singleLine = true` and `imeOptions = EditorInfo.IME_ACTION_DONE` (rendering the soft keyboard Checkmark/Done icon).
   - Soft input dismissal on Done/Enter press.
   - Dynamic **"СКРЫТЬ КЛАВИАТУРУ"** button and layout listener for manual dismissal.

## Proposed Changes

### 1AInspector/archive_legacy_dialog_checks/
- Move the 15 legacy dialog inspector scripts that require raw XML-like View code into `1AInspector/archive_legacy_dialog_checks/`.
- Add new Compose inspector scripts for `EditActionComposeDialog.kt`.

### EditActionComposeDialog.kt
- [NEW] Create `EditActionComposeDialog.kt` extending `AbstractComposeOverlay`:
  - **Header**: Step counter, type title, close button.
  - **Tabs**: `1. НАСТРОЙКИ`, `2. ИИ & ЗРЕНИЕ`, `3. ЛОГИКА`.
  - **Action Settings Tab**: Steppers for delay, hold duration, repeat count, random radius, pinch distance, global action types.
  - **Vision Tab**: Interactive multi-search carousel (image templates and blue OCR cards), similarity slider, shape mode toggle, AI engine toggle, ROI selection button, `+ ТЕКСТ (OCR)`, `+ С ЭКРАНА (OCR)`.
  - **Logic Tab**: Jump on match / timeout step selectors and subroutine picker.
  - **Footer**: `СОХРАНИТЬ`, `ДУБЛИРОВАТЬ`, `УДАЛИТЬ`, `РЕДАКТИРОВАТЬ`.

### ControlPanelComposeOverlay.kt
- [MODIFY] [ControlPanelComposeOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/panel/ControlPanelComposeOverlay.kt)
  - Add `getTutorialRect(tagKey: String): Rect?` returning exact screen bounds for every button tag (`BTN_PLAY_VIEW`, `BTN_RECORD`, `BTN_ADD`, `BTN_SETTINGS`, `BTN_LOCK`, `BTN_CLEAR`, `BTN_SCRIPTS`, etc.).

### Keyboard Dismissal across Overlays
- Configure `singleLine = true`, `imeOptions = EditorInfo.IME_ACTION_DONE`, and `setOnEditorActionListener` on all text input fields in `CalibrationOverlay.kt`, `GlobalSettingsDialog.kt`, `ScriptsDialog.kt`, and `EditActionComposeDialog.kt`.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run active `1AInspector` python scripts.

### Manual Verification
- Test opening `EditActionComposeDialog`: verify tabs, steppers, and carousel.
- Test Interactive Tutorial on Control Panel: verify spotlight laser points directly to buttons.
- Test soft keyboard: verify Checkmark/Done icon is shown and pressing Enter hides the keyboard.
