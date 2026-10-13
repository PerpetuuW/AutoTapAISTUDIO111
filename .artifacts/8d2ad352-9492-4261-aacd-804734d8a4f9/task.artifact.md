# Task List

- `[x]` Fix CalibrationOverlay.kt so new template captures always use `rawTemplateBitmap` (preventing old mask overlay) and maintain isolated metadata
- `[x]` Add explicit emergency close and cancellation logging (`AppLogger.log(context, "OVERLAY_CANCEL", ...)`) across CalibrationOverlay.kt, EditActionDialog.kt, and GestureRecorderOverlay.kt
- `[x]` Build and verify compilation
- `[x]` Run all 172 1AInspector scripts and create walkthrough
