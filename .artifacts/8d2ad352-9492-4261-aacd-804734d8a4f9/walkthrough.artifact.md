# Walkthrough - Multi-Search Raw Frame Isolation & Emergency Close Logging

## Changes

### CalibrationOverlay.kt
- Fixed template raw image initialization: `currentWorkingRaw` is strictly initialized to `rawTemplateBitmap` (the newly captured screen fragment) when capturing new templates. This ensures newly captured templates for multi-search or new steps never have previous template masks overlaid on top of them.
- Added explicit cancellation/dismissal logging: `AppLogger.log(context, "OVERLAY_CANCEL", "...")` in `dismiss()`.

### EditActionDialog.kt & GestureRecorderOverlay.kt
- Added explicit cancellation/dismissal logging: `AppLogger.log(context, "OVERLAY_CANCEL", "...")` in `dismiss()`.

## Verification Results

### Automated Tests
- Gradle Build: `:app:compileDebugKotlin` finished successfully.
- 1AInspector Suite: All `172 / 172` inspector modules passed 100% (`PASSED: 172 / 172`).
