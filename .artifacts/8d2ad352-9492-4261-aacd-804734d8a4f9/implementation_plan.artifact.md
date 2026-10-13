# Remove Drag Handle Button from Quick Steps Ring Overlay & Multi-Search Quick Capture

## Problem
1. **Quick Steps Drag Handle Removal**: In `TargetQuickRingOverlay.kt` (quick steps radial ring menu), the drag handle button (`DRAG_HANDLE`) is unnecessary and should be removed. The main control panel overlay (`ControlPanelOverlay.kt`) should remain untouched.
2. **Quick Steps Template Capture**: Capturing a new template for an existing step in quick steps mode (`TargetOverlayManager.kt`) should append the template to `multiTemplatePaths` (multi-search) without overwriting existing template files.
3. **Dedicated Recalibration**: Recalibration (overwriting/updating existing template files in-place) remains exclusive to the steps menu (`EditActionDialog`).

## Proposed Changes

### TargetQuickRingOverlay.kt
- [MODIFY] [TargetQuickRingOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/ring/TargetQuickRingOverlay.kt)
  - Remove the `DRAG_HANDLE` button entry from `buttonsConfig` in `TargetQuickRingOverlay.kt`.

### TargetOverlayManager.kt
- [MODIFY] [TargetOverlayManager.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/target/TargetOverlayManager.kt)
  - In `startCaptureForStep(targetAction)`:
    - Pass `existingTemplatePath = null` so quick captures do not overwrite existing template files.
    - Append the new template path `path` to `targetAction.multiTemplatePaths`.
    - Show Toast: `"Шаблон добавлен к мультипоиску шага #${targetAction.id} (всего: ${multiPaths.size})"`.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run inspection scripts.

### Manual Verification
- Verify that `TargetQuickRingOverlay` no longer displays the drag handle button, while main overlay remains untouched.
- Verify quick step capture adds new templates to `multiTemplatePaths` without overwriting existing templates.
