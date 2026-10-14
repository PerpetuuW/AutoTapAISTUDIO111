# Task List: Backend Enhancements (Phase A, B, C)

**Phase A: Smooth Path Interpolation**
- `[x]` Implement Catmull-Rom Spline interpolation in `PathCompressionEngine.kt`.
- `[x]` Integrate smooth path generation into `PathExecutors.kt` (`GesturePathPlayer` & `VirtualStickPathPlayer`).
- `[x]` Build and verify compilation.
- `[x]` Run 1AInspector scripts.

**Phase B: Subroutine Safety & Telemetry**
- `[x]` Add Subroutine Call Stack to `SafetyGovernor.kt`.
- `[x]` Add granular `[SUBROUTINE]` logging in `MacroExecutionEngine.kt`.

**Phase C: App Profile Detector Optimization**
- `[x]` Add caching, debouncing, and whitelist system to `AppProfileDetector.kt`.
