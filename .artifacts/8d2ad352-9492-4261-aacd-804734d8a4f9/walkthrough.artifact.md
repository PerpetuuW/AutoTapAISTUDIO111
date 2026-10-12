# Walkthrough - Bug Fixes & UX Enhancements

## Changes

### EditActionDialog.kt
- Wrapped template rename `EditText` inside a padded `LinearLayout` container with `MATCH_PARENT` layout params to prevent crashes when attempting to rename templates.

### CalibrationOverlay.kt
- Shortened default template names to `Цель_<4 digits>` (e.g., `Цель_4829`) so they fit nicely on template previews.
- Synchronized `multiTemplatePaths` when saving and calibrating templates in multi-search mode, fixing issues where multi-search failed to locate the updated template.

## Verification Results

### Automated Tests
- Gradle Build: `:app:compileDebugKotlin` finished successfully.
- Inspector Check: `python 1AInspector/check_orchestrator_and_keyboard_fix.py` passed with `[PASS]`.
