# Fix Multi-Search New Template Isolation & Add Emergency Close Logging

## Problem
1. **Old Mask Overlay on New Capture**: When capturing a new template for multi-search, `currentWorkingRaw` in `CalibrationOverlay.kt` was being loaded from `existingTemplatePath` (the old mask) instead of using the newly captured raw screen fragment (`rawTemplateBitmap`). Each template must have its own isolated raw image, mask, and metadata file.
2. **Emergency Close Logging**: Emergency dismissals, cancellations, and closes across overlay components (`CalibrationOverlay`, `EditActionDialog`, `GestureRecorderOverlay`, `ControlPanelOverlay`, etc.) lack dedicated cancellation logging.

## Proposed Changes

### CalibrationOverlay.kt
- [MODIFY] [CalibrationOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt)
  - Ensure `currentWorkingRaw` is initialized strictly to `rawTemplateBitmap` (the newly captured frame) for new template captures, preventing the old template mask from overlaying newly captured frames.
  - Ensure each template saved in multi-search has its own distinct mask (`mask_XXXX.png`), raw image (`raw_XXXX.png`), and JSON metadata (`mask_XXXX.json`).
  - Add explicit cancellation logging in `onCancelled()` and `dismiss()` via `AppLogger.log(context, "OVERLAY_CANCEL", ...)`.

### Overlay Components (EditActionDialog, GestureRecorderOverlay, ControlPanelOverlay, etc.)
- [MODIFY] [EditActionDialog.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt)
- [MODIFY] [GestureRecorderOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/record/GestureRecorderOverlay.kt)
  - Add explicit cancellation and emergency close logging (`AppLogger.log(context, "OVERLAY_CANCEL", ...)`) whenever an overlay is dismissed or cancelled.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 172 1AInspector scripts.

### Manual Verification
- Capture a new template in multi-search mode and verify that the newly cropped raw frame is clean (no old mask overlaid) and generates its own independent mask and metadata file.
- Verify cancellation and emergency closes emit detailed `OVERLAY_CANCEL` log entries in `AppLogger`.
