# Critical Fix for Accessibility Service Crash on Screen Lock & Overlay Choice Menu Restoration

## 🔍 Root Cause Analysis

### 1. Why the Accessibility Service Crashed / Broke Down (`в итоге служба накрылась`)
- In `AutoTapAccessibilityService.kt`, line 136 registered `powerShutdownReceiver` for system broadcasts (`ACTION_SCREEN_OFF`, `ACTION_SHUTDOWN`) using `Context.RECEIVER_NOT_EXPORTED`.
- On Android 13+ (API 33+), registering a receiver for system broadcasts with `RECEIVER_NOT_EXPORTED` throws a fatal `SecurityException` (`Registering receiver with RECEIVER_NOT_EXPORTED for system intent ACTION_SCREEN_OFF`).
- This uncaught `SecurityException` inside `onServiceConnected()` crashed and killed the Accessibility Service process instantly when connected or when the screen turned off.

### 2. Restoring Overlay Choice Menus instead of Lists
- When tapping `+` or `RECORD` or selecting scenarios, replace lists with floating choice overlay cards (`ActionTypeSelectionDialog`, `RecordModeSelectionDialog`, `ScenarioChoiceOverlay`) rendered directly on top of the overlay window.

---

## 🛠️ Proposed Solutions & Architectural Fixes

### 1. Fix BroadcastReceiver Registration in `AutoTapAccessibilityService.kt`
- Register system broadcast receiver (`ACTION_SCREEN_OFF`, `ACTION_SHUTDOWN`) using `Context.RECEIVER_EXPORTED` on Android 13+ (API 33+), or without flags on older API levels.
- Wrap receiver registration in defensive `try-catch` to guarantee that receiver registration errors can NEVER crash or kill the Accessibility Service.
- Ensure `ACTION_SCREEN_OFF` gracefully stops macro execution (`macroEngine.stop()`), dismisses screen lock (`screenLockOverlay.dismiss()`), and hides overlays safely.

### 2. Restore Floating Overlay Choice Cards
- Create/restore floating selection cards (`ActionTypeSelectionDialog`, `RecordModeSelectionDialog`) so `+` and `RECORD` open floating overlay cards with big tactile buttons instead of standard lists.

### 3. Re-align Control Panel & Tutorial Steps
- Main Row: `PLAY`, `CAPTURE`, `PLUS`, `RECORD`, `TOGGLE`, `CLOSE` (`X` top-right).
- Sub Row: `HIDE` (Eye), `SETTINGS` (Settings), `LOCK`, `CLEAR`, `SCRIPTS`, `HELP`.
- Sequential step progression in `InteractiveTutorialOverlay.kt`.

---

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 177 1AInspector scripts.

### Manual Verification
- Turn off screen / lock phone during macro execution: verify macro stops safely, screen lock dismisses, and the Accessibility Service remains 100% active without crashing or stopping.
- Tap `+` and `RECORD`: verify floating choice overlay cards appear instead of standard lists.
