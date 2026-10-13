# Walkthrough - New Template Isolation, Calibration Progress Bar, & Universal Keyboard Hide Button

## Changes

### CalibrationOverlay.kt
- **New Template Isolation**: Fixed template save logic so that new template captures (`existingTemplatePath == null`) always save as a new unique file on disk, preventing existing templates from being overwritten and preserving all templates in the multi-search carousel.
- **Calibration Progress Bar**: Added a horizontal progress indicator (`pBarReevaluate`) above the preview row that becomes visible whenever ZNCC evaluation / parameter matching is executing.
- **Keyboard Dismissal & Hide Button**: Added a dedicated `"СКРЫТЬ КЛАВИАТУРУ"` button that appears when text input is active, and ensured `Enter` / `IME_ACTION_DONE` closes the keyboard.

### GlobalSettingsDialog.kt & ScriptsDialog.kt
- Ensured `"СКРЫТЬ КЛАВИАТУРУ"` button and `Enter` (`IME_ACTION_DONE`) keyboard hiding on all text inputs.

## Verification Results

### Automated Tests
- Gradle Build: `:app:compileDebugKotlin` finished successfully.
- 1AInspector Suite: All `172 / 172` inspector modules passed 100% (`PASSED: 172 / 172`).
