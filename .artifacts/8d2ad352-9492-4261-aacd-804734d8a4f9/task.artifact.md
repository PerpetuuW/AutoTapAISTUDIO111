# Task List: Sequential Architectural Migration

**Phase 1: Infrastructure & First Overlay Migration**
- `[x]` Add Jetpack Compose, LeakCanary, and Detekt to Gradle (`libs.versions.toml` & `build.gradle.kts`).
- `[x]` Implement `OverlayLifecycleOwner` to allow Jetpack Compose to run inside raw `WindowManager` Views.
- `[x]` Refactor `RunningBadgeOverlay.kt` to use Jetpack Compose and `StateFlow` (MVI pattern).
- `[x]` Build and verify compilation.
- `[x]` Ensure 1AInspector scripts pass and create walkthrough.

*(Subsequent overlays will be migrated in following iterations after Phase 1 is verified)*
