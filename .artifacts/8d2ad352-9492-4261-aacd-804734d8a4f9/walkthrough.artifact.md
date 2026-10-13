# Walkthrough - Quick Multi-Search Capture, Calibration Source Choice Modal, & Calibration Cleanup

## Changes

### AutoTapOrchestrator.kt
- **Quick Capture Multi-Search Isolation**: Added `startAddTemplateForStep(action)` so that adding a new template via the quick actions ring sets `existingTemplatePath = null` (creating a new unique file) and appends the new path `path` to `action.multiTemplatePaths`.
- **Calibration Source Selection Modal**: Updated calibration entry to present a popup card when historical full screenshots exist:
  - **📷 НА ТЕКУЩЕМ ЭКРАНЕ**: Performs a fresh live screen capture.
  - **🖼️ НА СОХРАНЕННОМ СКРИНШОТЕ**: Calibrates on the saved full screenshot.

### CalibrationOverlay.kt
- Removed the "ТЕСТ КЛИКА" button and its action row.

## Verification Results

### Automated Tests
- Gradle Build: `:app:compileDebugKotlin` finished successfully.
- 1AInspector Suite: All `172 / 172` inspector modules passed 100% (`PASSED: 172 / 172`).
