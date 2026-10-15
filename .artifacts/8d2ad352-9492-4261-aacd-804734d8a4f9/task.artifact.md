# Task List

- `[x]` Reduce main row button size in `ControlPanelComposeOverlay.kt` to `34.dp` and spacing to `4.dp` so CLOSE (`X`) button is 100% visible at top-right
- `[x]` Optimize `VectorIconButton` drawing using `remember { RectF() }` to eliminate GC allocation during render
- `[x]` Set `DisposeOnDetachedFromWindowOrReleasedFromPool` composition strategy in `AbstractComposeOverlay.kt`
- `[x]` Update `getTutorialRect` coordinates for `34.dp` button sizes
- `[x]` Build and verify compilation
- `[x]` Run all 175 1AInspector scripts and create walkthrough
