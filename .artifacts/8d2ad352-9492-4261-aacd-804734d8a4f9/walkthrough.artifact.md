# Walkthrough - Calibration Menu Collapse, Keyboard Focus Fix, Full Info Board, & Gesture Recording Diagnostics

## Changes

### CalibrationOverlay.kt
- Added a collapse/minimize button (`btnCollapse`) to `headerRow` that toggles calibration controls card visibility (`View.GONE` vs `View.VISIBLE`), allowing the user to collapse `CalibrationOverlay` into a compact header bar and restore it at will.
- Removed `FLAG_ALT_FOCUSABLE_IM` from `CalibrationOverlay` window parameters so that tapping `etTemplateName` opens the soft keyboard.
- Implemented sequential default template name generation (`Цель_1`, `Цель_2`, `Цель_3`) by checking existing templates in `templateRepository`, guaranteeing default names never collide or overwrite each other.

### RunningBadgeOverlay.kt
- Expanded info container width (`contentWidthDp = 120`) and marquee capacity so full template names, step descriptions, and execution progress are displayed without truncation.

### GestureRecorderOverlay.kt
- Enhanced gesture recording with differentiation (CLICK, LONG_PRESS, SWIPE, PATH, PINCH) and detailed logging (`AppLogger.log(context, "GESTURE_RECORDER", ...)`), recording duration, distance, and passthrough details.

### 1AInspector/check_calibration_collapse_and_recording_diagnostics.py
- Created a new inspector module to validate all 5 requirements in automated tests.

## Verification Results

### Automated Tests
- Gradle Build: `:app:compileDebugKotlin` finished successfully.
- New Inspector Module: `python 1AInspector/check_calibration_collapse_and_recording_diagnostics.py` passed with `[PASS]`.
