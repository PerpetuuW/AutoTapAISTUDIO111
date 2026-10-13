# Calibration Progress, New Template Isolation, & Universal Keyboard Hide Button / Enter Handling

## Problem
1. **Previous Template Overwrite in Multi-Search**: When adding a new template via quick actions (`existingTemplatePath == null`), `etTemplateName` was pre-filled with a name that matched an existing template on disk. `CalibrationOverlay` was treating it as an overwrite of the existing file, destroying the previous template and removing it from the carousel.
2. **Calibration Progress Bar**: When adjusting calibration sliders or parameters in `CalibrationOverlay`, there was no visual progress indicator showing that ZNCC matching/re-evaluation was actively running.
3. **Universal Keyboard Dismissal & Hide Button**: All text input fields (`EditText`) across overlay dialogs (`CalibrationOverlay`, `GlobalSettingsDialog`, `ScriptsDialog`, `EditActionDialog`) must hide the keyboard and finish input when `Enter` / `IME_ACTION_DONE` is pressed, and include a dedicated "СКРЫТЬ КЛАВИАТУРУ" button whenever the keyboard is open or an `EditText` is focused.

## Proposed Changes

### CalibrationOverlay.kt
- [MODIFY] [CalibrationOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt)
  - **Fix Template Overwrite**:
    - When `existingTemplatePath == null` (capturing a new template for multi-search or a new step), guarantee that a new unique template file is created (`existingPath = null`). Never overwrite existing templates when creating a new template via quick capture.
    - Generate guaranteed unique sequential default names (`Цель_1`, `Цель_2`, `Цель_3`, ...) that do not collide with any existing templates in the repository.
  - **Calibration Progress Bar**:
    - Add a progress bar / loading indicator (`pBarReevaluate`) in `CalibrationOverlay.kt` near the preview row / header that becomes visible while `reevaluateMatching()` / ZNCC evaluation is executing.
  - **Keyboard Hide Button & Enter Action**:
    - Add `btnHideKeyboard` ("СКРЫТЬ КЛАВИАТУРУ") button and `OnGlobalLayoutListener` to toggle visibility when keyboard is active, and ensure `etTemplateName` / `etFolder` finish input and hide keyboard on `Enter`.

### GlobalSettingsDialog.kt & ScriptsDialog.kt
- [MODIFY] [GlobalSettingsDialog.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/GlobalSettingsDialog.kt)
- [MODIFY] [ScriptsDialog.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/ScriptsDialog.kt)
  - Add `btnHideKeyboard` button and `Enter` / `IME_ACTION_DONE` keyboard hiding on all text inputs.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 172 1AInspector scripts.

### Manual Verification
- Test adding multiple templates via quick actions and verify that all templates remain present in the carousel without any template disappearing.
- Test adjusting sliders in `CalibrationOverlay` and verify the progress bar shows active calibration.
- Verify "СКРЫТЬ КЛАВИАТУРУ" button appears when editing text fields and pressing `Enter` finishes input and closes the keyboard.
