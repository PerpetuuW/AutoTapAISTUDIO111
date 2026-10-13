# Comprehensive Plan: Calibration UI, Keyboard Fix, Full Execution Info, Incremental Template Names, & Gesture Recorder Enhancements

## Problem
1. **Calibration Menu Collapse Button**: Need a minimize/collapse button (`▼ / ▲`) in `CalibrationOverlay` header to collapse calibration controls during target inspection.
2. **Keyboard Focus Fix**: `CalibrationOverlay` has `FLAG_ALT_FOCUSABLE_IM` in its window parameters, preventing the soft keyboard from appearing when tapping `etTemplateName`.
3. **Sequential Default Template Names**: Default names like `цель_1712345678901` were long and collided. Default names should increment sequentially (`Цель_1`, `Цель_2`, `Цель_3`) to guarantee uniqueness.
4. **Full Execution Info Board**: `RunningBadgeOverlay` truncates template and step titles due to narrow width constraints (`contentWidthDp = 78`).
5. **Gesture Recorder Differentiating & Logging**: `GestureRecorderOverlay` needs to differentiate gesture types (CLICK, LONG_PRESS, SWIPE, MULTI_POINTER), dispatch parallel touch passthrough actions, and log recorded gestures with detailed diagnostics.

## Proposed Changes

### CalibrationOverlay.kt
- [MODIFY] [CalibrationOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt)
  - Add collapse/minimize button to `headerRow` that toggles calibration controls card visibility.
  - Remove `FLAG_ALT_FOCUSABLE_IM` from window parameters so `etTemplateName` soft keyboard opens.
  - Implement sequential default template name generator (`Цель_1`, `Цель_2`, `Цель_3`) using `templateRepository.loadAllTemplates()`.

### RunningBadgeOverlay.kt
- [MODIFY] [RunningBadgeOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/badge/RunningBadgeOverlay.kt)
  - Expand info container width (`contentWidthDp = 110`) and marquee capacity so full template names and step details are shown without truncation.

### GestureRecorderOverlay.kt
- [MODIFY] [GestureRecorderOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/record/GestureRecorderOverlay.kt)
  - Differentiate gestures: CLICK, LONG_PRESS, SWIPE, and MULTI_POINTER/PINCH (`event.pointerCount > 1`).
  - Add comprehensive logging via `AppLogger.log(context, "GESTURE_RECORDER", ...)` for all captured gestures and passthrough dispatching.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run inspection scripts.

### Manual Verification
- Test collapse/expand button in `CalibrationOverlay`.
- Verify soft keyboard opens on tapping `etTemplateName`.
- Verify sequential default template names (`Цель_1`, `Цель_2`, ...).
- Verify `RunningBadgeOverlay` shows full template names without truncation.
- Test gesture recorder capturing CLICK, SWIPE, LONG_PRESS, and multi-pointer events with detailed logs.
