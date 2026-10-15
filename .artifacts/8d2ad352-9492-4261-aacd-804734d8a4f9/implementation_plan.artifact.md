# Fix Control Panel Overflow (Visible Top-Right Close Button) & Compose Performance Optimization

## Problem
1. **Close Button Off-Screen Overflow**: Main row buttons in `ControlPanelComposeOverlay.kt` were set to `42.dp` size with `8.dp` spacing across 8 buttons, resulting in a total width of `392.dp`. This exceeded the screen width of portrait devices (~360.dp), causing the 8th button (`CLOSE` / `X`) to be pushed off-screen to the right, leaving the Eye button at the visible edge.
2. **Compose Performance Stutters (Lag)**:
   - `AbstractComposeOverlay.kt` was running a manual `Recomposer` loop on `AndroidUiDispatcher.CurrentThread` instead of using `ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool`.
   - `VectorIconButton` was allocating new `RectF` objects inside `Canvas { ... }` on every draw frame, causing GC stutters.

## Proposed Changes

### ControlPanelComposeOverlay.kt
- [MODIFY] [ControlPanelComposeOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/panel/ControlPanelComposeOverlay.kt)
  - Reduce main row button size from `42.dp` to `34.dp` with `4.dp` spacing. Total width becomes `300.dp`, ensuring all 8 buttons fit on the screen and the `CLOSE` (`X`) button is **100% visible at the top-right corner**.
  - Update `getTutorialRect` column mappings for `34.dp` size.
  - Optimize `VectorIconButton` to use `remember { RectF() }`, eliminating allocation during rendering.

### AbstractComposeOverlay.kt
- [MODIFY] [AbstractComposeOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/AbstractComposeOverlay.kt)
  - Configure `setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)` on `ComposeView` and remove manual `Recomposer` loop for native, zero-lag composition.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 175 1AInspector scripts.

### Manual Verification
- Open Control Panel: verify all 8 buttons fit on a single row and the red CLOSE (`X`) button is clearly visible at the top-right.
- Drag the panel and interact with buttons: verify zero lag and smooth 60 FPS performance.
