# Root-Cause Fix for Running Badge Drag & Stop Button, plus Inspector Module

## Root-Cause Analysis
1. **Stop & Lock Button Click Bug**: In `RunningBadgeComposeOverlay.kt`, the Stop (`onStopClick`) and Lock (`onLockClick`) buttons were bound using `pointerInput` -> `detectDragGestures(onDragEnd = ...)` instead of `.clickable { ... }`. Simple taps/clicks did not trigger `onDragEnd`, causing the Stop button to fail.
2. **Overlay Drag Reset Bug**: `getLayoutParams()` in `RunningBadgeComposeOverlay.kt` was hardcoding default coordinates on every call instead of maintaining `posX` and `posY` state variables. Any drag attempt immediately snapped back to the hardcoded default coordinates.
3. **Inspector Test Module**: A new python inspector script (`1AInspector/check_running_badge_compose_and_stop_button.py`) will be created to enforce these invariants automatically.

## Proposed Changes

### RunningBadgeComposeOverlay.kt
- [MODIFY] [RunningBadgeComposeOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/badge/RunningBadgeComposeOverlay.kt)
  - Maintain `posX` and `posY` state variables and update them in `getLayoutParams()`.
  - Fix window drag listener in `BadgeCard` to update `posX` and `posY` and invoke `overlayWindowManager.updateViewSafe`.
  - Replace `pointerInput(Unit) { detectDragGestures }` on Lock and Stop buttons with `.clip(CircleShape).clickable { onLockClick() }` and `.clip(CircleShape).clickable { onStopClick() }`.

### 1AInspector/check_running_badge_compose_and_stop_button.py
- [NEW] Create `1AInspector/check_running_badge_compose_and_stop_button.py` to test and verify that:
  - `RunningBadgeComposeOverlay.kt` exists and uses `posX` / `posY`.
  - Lock and Stop buttons use `.clickable`.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 175 1AInspector scripts.

### Manual Verification
- Open Running Badge overlay during execution:
  - Tap Stop button: verify macro stops immediately.
  - Drag the Running Badge card across the screen: verify smooth dragging without snapping back.
