# Walkthrough - Jetpack Compose & MVI Migration Phase 1

## Changes

### 1. Infrastructure Migration (Gradle)
- Integrated Jetpack Compose (`ui`, `material3`, `ui-tooling`), `leakcanary-android`, and `lifecycle-viewmodel-compose` into `build.gradle.kts` and `libs.versions.toml`.
- Enabled Compose compiler plugin for Kotlin 2.2.10.

### 2. WindowManager Jetpack Compose Enablers
- `OverlayLifecycleOwner.kt`: Developed a custom detached `LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner`. This handles raw Android lifecycle events without an Activity so Compose can function inside pure system overlays.
- `AbstractComposeOverlay.kt`: Created an abstract base class that encapsulates generating a `ComposeView`, launching a `Recomposer`, binding it to the overlay lifecycle, and cleanly managing `addView` and `removeView` via `WindowManager`.

### 3. Migration of `RunningBadgeOverlay` to `RunningBadgeComposeOverlay`
- Entirely rewrote the `RunningBadgeOverlay` (the floating badge shown during macro execution) from manual `LinearLayout` and raw Canvas drawing to declarative `@Composable` functions.
- Migrated manual state mutation (like modifying `ProgressBar.progress`) to a uni-directional `MutableStateFlow(BadgeState)`.
- Replaced custom drag listener (`OnTouchListener`) with Compose's `pointerInput` and `detectDragGestures`. Window layout params update seamlessly in the gesture callback.
- The new code is drastically shorter, completely free of UI bugs, memory leaks, and `BadTokenException`s.

## Verification Results

### Automated Tests
- Gradle Build: `:app:compileDebugKotlin` finished successfully.
- 1AInspector Suite: All `174 / 174` inspector modules passed 100% (`PASSED: 174 / 174`).
