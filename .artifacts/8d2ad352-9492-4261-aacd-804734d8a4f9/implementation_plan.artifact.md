# Complete Audit & Restoration of Choice Menus, Control Panel Layout, Running Badge Stop Button, & Journal Tag Logging

## Problem
1. **Missing Action Type & Record Mode Choice Menus**:
   - Tapping `+` should open an Action Type Selection Menu (`CLICK`, `LONG_PRESS`, `SWIPE`, `PATH`, `PINCH`, `TRIGGER`, `OCR`, `COLOR_CHECK`, `SUBROUTINE`, `DELAY`, `GLOBAL_BACK`, `GLOBAL_HOME`).
   - Tapping `RECORD` should open a Recording Mode Selection Menu (**УМНАЯ ЗАПИСЬ** vs **ОБЫЧНАЯ ЗАПИСЬ**).
2. **Control Panel Layout Cleanup**:
   - In `ControlPanelComposeOverlay.kt`, the 2nd row had a duplicate collapse button. It must be removed so the 2nd row cleanly contains `LOCK`, `CLEAR`, `SCRIPTS`, and `HELP`.
3. **Running Badge Stop Button Restoration**:
   - In `RunningBadgeComposeOverlay.kt`, the Stop button was rendered as plain text instead of using custom canvas vector drawing with the exact pre-migration gradient/stroke styling (`#F04438` -> `#B42318`, stroke `#FDA29B`).
4. **Log Journal Tag Filtering (`LogViewerDialog.kt`)**:
   - The tag extraction regex in `LogViewerDialog.kt` failed on lines with leading prefixes (`=== [2026-10-14 ...][FATAL_CRASH]`). It must be updated to `Regex("\\[([A-Z0-9_]{3,25})\\]")` so all log tags (`CV_EXECUTION`, `MACRO_ENGINE`, `FATAL_CRASH`, `ANR_FREEZE`, `PANEL`, `STORAGE`, `LOCK`, `GESTURE`, `SUBROUTINE`, etc.) populate as interactive filter chips.

## Proposed Changes

### ControlPanelComposeOverlay.kt & AutoTapOrchestrator.kt
- [MODIFY] [ControlPanelComposeOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/panel/ControlPanelComposeOverlay.kt)
  - Remove duplicate collapse button from 2nd row.
  - Add Action Type Selection popup menu when `+` is tapped.
  - Add Recording Mode Selection popup menu (**УМНАЯ ЗАПИСЬ** vs **ОБЫЧНАЯ ЗАПИСЬ**) when `RECORD` is tapped.
- [MODIFY] [AutoTapOrchestrator.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt)
  - Route selected action types from menu to `targetManager.addAction(MacroAction(type = selectedType))`.

### RunningBadgeComposeOverlay.kt
- [MODIFY] [RunningBadgeComposeOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/badge/RunningBadgeComposeOverlay.kt)
  - Restore pre-migration Stop button rendering using `Canvas` + `VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.STOP, ...)` with exact gradient (`#F04438` -> `#B42318`) and stroke (`#FDA29B`).

### LogViewerDialog.kt
- [MODIFY] [LogViewerDialog.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/LogViewerDialog.kt)
  - Update regex to `Regex("\\[([A-Z0-9_]{3,25})\\]")` to extract all log categories (`CV_EXECUTION`, `MACRO_ENGINE`, `FATAL_CRASH`, `ANR_FREEZE`, `PANEL`, `STORAGE`, `LOCK`, `GESTURE`, `SUBROUTINE`, etc.) into `tagScroll` chip buttons.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 174+ 1AInspector scripts.

### Manual Verification
- Tap `+` on Control Panel: verify Action Type Selection menu appears.
- Tap `RECORD` on Control Panel: verify Recording Mode selection menu appears.
- Verify 2nd row has no duplicate collapse button.
- Check Running Badge Stop button: verify exact pre-migration vector icon and red gradient.
- Open Log Journal (`LogViewerDialog`): verify tag chips (`ALL`, `CV_EXECUTION`, `MACRO_ENGINE`, `PANEL`, `ANR_FREEZE`, etc.) appear and filter logs accurately.
