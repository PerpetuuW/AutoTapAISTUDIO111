# Walkthrough - Fixing Screen Brightness Pulsing During Search and Lock

## Changes

### ScreenLockOverlay.kt
- Updated `setTemporarilyTransparent` to toggle `bgLockContainer` background color (`Color.TRANSPARENT` vs `currentBgColor`) instead of modifying window view alpha. This ensures WindowManager maintains `screenBrightness` attributes firmly without dropping/resetting them during accessibility screenshot capture, eliminating brightness pulsing and flashing.

### AutoTapAccessibilityService.kt
- Updated `wakeUpScreenIfNeeded` to use `PowerManager.PARTIAL_WAKE_LOCK` instead of `SCREEN_DIM_WAKE_LOCK`, keeping the CPU awake for screenshots without turning on display backlights or altering brightness.

## Verification Results

### Automated Tests
- Gradle Build: `:app:compileDebugKotlin` finished successfully.
- Inspector Check: `python 1AInspector/check_orchestrator_and_keyboard_fix.py` passed with `[PASS]`.
