# Walkthrough - Choice Menus, Control Panel Sub-Row Cleanup, Vector Stop Button, & Tagged Log Filtering

## Changes

### 1. Action Type & Recording Mode Selection Menus (`ControlPanelComposeOverlay.kt`)
- **`+` Button Choice Menu**: Tapping `+` now opens `ActionTypeSelectionMenu` displaying chips for all 11 action types (`CLICK`, `LONG_PRESS`, `SWIPE`, `PATH`, `TRIGGER`, `OCR`, `COLOR_CHECK`, `SUBROUTINE`, `DELAY`, `GLOBAL_BACK`, `GLOBAL_HOME`). Selecting any action type instantly routes it to `listener.onAddActionSelected(type)` for step creation.
- **`RECORD` Button Choice Menu**: Tapping `RECORD` now opens `RecordModeSelectionMenu`:
  - **УМНАЯ ЗАПИСЬ (ИИ-шаблоны)** -> `onSmartRecordClicked()`
  - **ОБЫЧНАЯ ЗАПИСЬ (Живые жесты)** -> `onRecordClicked()`

### 2. Control Panel Sub-Row Layout Cleanup (`ControlPanelComposeOverlay.kt`)
- Removed the duplicate collapse button from the 2nd row (sub-row).
- The 2nd row now cleanly contains: `AFK ЗАМОК`, `ОЧИСТИТЬ ВСЕ`, `СЦЕНАРИИ`, and `СПРАВКА`.

### 3. Running Badge Vector Stop Button (`RunningBadgeComposeOverlay.kt`)
- Restored pre-migration Stop button rendering using `Canvas` + `VectorIconDrawer.drawIcon(canvas, VectorIconDrawer.IconType.STOP, ...)` with exact red gradient (`#F04438` -> `#B42318`) and stroke (`#FDA29B`).

### 4. Interactive Tag Log Filtering (`LogViewerDialog.kt`)
- Updated tag extraction regex to `Regex("\\[([A-Z0-9_]{3,25})\\]")`.
- `LogViewerDialog` reliably extracts all categories (`CV_EXECUTION`, `MACRO_ENGINE`, `FATAL_CRASH`, `ANR_FREEZE`, `PANEL`, `STORAGE`, `LOCK`, `GESTURE`, `SUBROUTINE`, etc.) and renders them as neon chip buttons at the top of the log viewer for instant one-tap filtering.

## Verification Results

### Automated Tests
- Gradle Build: `:app:compileDebugKotlin` finished successfully.
- 1AInspector Suite: All `159 / 159` active inspector modules passed 100% (`PASSED: 159 / 159`).
