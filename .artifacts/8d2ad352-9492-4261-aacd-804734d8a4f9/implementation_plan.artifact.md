# Detailed Root Cause Analysis & Optimization Plan for Overlay Lag and Missing Close Button

## 🔍 Root Cause Analysis

### 1. Why the Close Button (`X`) Was Missing
- In `ControlPanelComposeOverlay.kt`, the main row buttons were set to `42.dp` width with `8.dp` spacing across 8 buttons: `8 * 42dp + 7 * 8dp = 392dp` total width.
- Standard Android phone screens in portrait mode have a width of `360dp` to `380dp`.
- Because `392dp > 360dp`, the 8th button at the far right (**`CLOSE` / `X`**) was physically pushed outside the visible screen bounds beyond the right edge of the display, leaving the 6th/7th buttons (`Eye` / `Settings`) at the visible right edge.

### 2. Why the Lag / Frame Stutters Occurred
- **IPC WindowManager Throttling**: During overlay dragging, `detectDragGestures` invoked `overlayWindowManager.updateViewSafe(it, params)` on **every single touch pixel event** (120-240 times/sec). This flooded Android's system `WindowManagerService` via binder IPC calls, freezing the UI thread.
- **Continuous 40ms Recomposition Loop**: `RunningBadgeComposeOverlay.kt` had a `while(true) { delay(40L); currentProgress = ... }` loop running on the main UI thread 25 times/sec, triggering continuous recomposition of the entire badge overlay.
- **Unthrottled Recomposition Animations**: 5 simultaneous `animateColorAsState` / `animateFloatAsState` transitions in `ControlPanelComposeOverlay.kt` triggered full-tree recompositions during visibility and state changes.

---

## 🛠️ Proposed Solutions & Architectural Changes

### 1. Fix Button Width & Layout in `ControlPanelComposeOverlay.kt`
- Reduce main row button size from `42.dp` to `32.dp` and spacing from `8.dp` to `4.dp`. Total row width becomes `8 * 32dp + 7 * 4dp = 284dp`.
- `284dp` fits comfortably on ALL phone displays (360dp+), ensuring the red **`CLOSE` (`X`) button is 100% visible at the top-right corner**.
- Update `getTutorialRect` column index mapping for `32.dp` size.

### 2. Drag Throttling & WindowManager Performance Fix
- Throttle `updateViewSafe` calls during drag so window updates occur at most once every 16ms (60 FPS max), completely eliminating Binder IPC spam.
- In `RunningBadgeComposeOverlay.kt`, update progress bar only when elapsed time changes meaningfully instead of spinning an unconditional 40ms main-thread delay loop.

### 3. Memoized Vector Canvas Rendering
- In `VectorIconButton`, memoize `RectF()` using `remember(size) { RectF() }` and pre-calculate color ARGB integers to eliminate object creation inside `Canvas` drawing scopes.

---

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 175 1AInspector scripts.

### Manual Verification
- Verify `CLOSE` (`X`) button is 100% visible on the main panel top-right corner.
- Drag the control panel and running badge overlay: verify smooth 60 FPS movement without frame drops or lag.
