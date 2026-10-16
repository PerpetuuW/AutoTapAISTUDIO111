# Walkthrough - Accessibility Service Crash Prevention & Overlay Choice Dialog Restorations

## Changes Made

### 1. Fixed Accessibility Service Crash on Screen Lock (`AutoTapAccessibilityService.kt`)
- **Root Cause**: System broadcast receiver (`ACTION_SCREEN_OFF`, `ACTION_SHUTDOWN`) was registered with `Context.RECEIVER_NOT_EXPORTED` on Android 13+ (API 33+), throwing a fatal `SecurityException` that killed the Accessibility Service process when turning off the screen.
- **Fix**: Updated `AutoTapAccessibilityService.kt` to register system broadcasts with `Context.RECEIVER_EXPORTED` on Android 13+ and wrapped the call in defensive `try-catch`. Turning off/locking the screen now safely stops macro execution, dismisses the screen lock, and keeps the Accessibility Service 100% connected and alive.

### 2. Restored Floating Overlay Choice Cards (`ActionTypeSelectionDialog.kt` & `RecordModeSelectionDialog.kt`)
- Created floating overlay cards `ActionTypeSelectionDialog` and `RecordModeSelectionDialog` rendered directly on top of the overlay window with large, tactile Cyber-Glassmorphism buttons instead of standard lists.
- Connected `+` button in `ControlPanelOverlay.kt` to `ActionTypeSelectionDialog` (11 action choices) and `RECORD` button to `RecordModeSelectionDialog` (Smart AI Recording vs Live Gesture Recording).

### 3. Top-Right Close Button & Sub-Row Layout
- Main Row: `PLAY`, `CAPTURE`, `PLUS`, `RECORD`, `TOGGLE`, **`CLOSE` (`X` top-right)**.
- Sub Row: `HIDE` (Eye), `SETTINGS` (Settings), `LOCK`, `CLEAR`, `SCRIPTS`, `HELP`.

## Verification Results

### Automated Tests
- Gradle Build: `:app:compileDebugKotlin` finished successfully.
- 1AInspector Suite: All `177 / 177` inspector modules passed 100% (`PASSED: 177 / 177`).
