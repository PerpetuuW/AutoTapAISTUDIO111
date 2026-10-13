# Fix Multi-Search Quick Capture, Rename Calibration to Edit, Add Screen Choice Modal, & Remove Test Button

## Problem
1. **Multi-Search Quick Capture Bug**: In `AutoTapOrchestrator.kt`, `onActionAddTemplateRequested` called `startCaptureForStep(act)` with `existingTemplatePath = templatePath`, overwriting existing template files and omitting new paths from `multiTemplatePaths`.
2. **Rename "Калибровка" to "Редактировать"**: The button in quick actions ring should be labeled "Редактировать" instead of "Калибровка".
3. **Calibration Screen Choice Modal**: When "Редактировать" is tapped, a selection dialog should prompt the user to choose between calibrating **"На текущем экране"** (fresh live screenshot) or **"На сохраненном скриншоте"** (saved full screenshot `screen_*.jpg` / `raw_*.png`).
4. **Remove Test Button from Calibration**: The "ТЕСТ" button in `CalibrationOverlay.kt` is unnecessary and should be removed.

## Proposed Changes

### AutoTapOrchestrator.kt
- [MODIFY] [AutoTapOrchestrator.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt)
  - In `onActionAddTemplateRequested`:
    - Pass `existingTemplatePath = null` so quick captures create a new unique template file without overwriting existing templates.
    - Append the new template path `path` to `action.multiTemplatePaths`.
    - Show Toast: `"Шаблон добавлен к мультипоиску шага #X (всего: N)"`.
  - In `startCalibration`:
    - Show a choice dialog: **"На текущем экране"** vs **"На сохраненном скриншоте"**.
    - If "На сохраненном скриншоте" is chosen, load the saved full screenshot (`screen_*.jpg` / `raw_*.png`) associated with the template and pass it to `CalibrationOverlay`.

### TargetQuickRingOverlay.kt
- [MODIFY] [TargetQuickRingOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/ring/TargetQuickRingOverlay.kt)
  - Rename calibration action label/icon to "Редактировать".

### CalibrationOverlay.kt
- [MODIFY] [CalibrationOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt)
  - Remove the "ТЕСТ" (test click) button and its action.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 172 1AInspector scripts.

### Manual Verification
- Test adding a template to multi-search from quick actions (verify previous templates remain intact in `multiTemplatePaths` and carousel).
- Test tapping "Редактировать" in quick ring menu (verify choice modal appears for "На текущем экране" vs "На сохраненном скриншоте").
- Verify "ТЕСТ" button is removed from `CalibrationOverlay`.
