# Backend Enhancements (A, B, C in order)

## Goal
Implement a series of high-impact backend improvements focusing on gesture fluidity, subroutine safety, and reliable background detection, executed sequentially.

## Phase A: Smooth Path Interpolation (Human-like Gestures)
**Problem:** `PathCompressionEngine` heavily compresses points into sharp angles (zig-zags). When played back via `AccessibilityService` or `VirtualStick`, the movement is perfectly linear and robotic, which can trigger game anti-cheats or look unnatural.
**Proposed Change:**
- Add a Spline Interpolation module (e.g., Catmull-Rom spline) to `PathExecutors.kt` or `PathCompressionEngine.kt`.
- When `PathPlayer` executes a path, it will generate smooth intermediate points, converting sharp angles into fluid, human-like curves.

## Phase B: Subroutine Safety & Telemetry
**Problem:** `ActionType.SUBROUTINE` allows jumping to another script. If Script A calls Script B, and Script B calls Script A, it creates an infinite recursion that will crash the app with a `StackOverflowError`.
**Proposed Change:**
- Enhance `SafetyGovernor.kt` to maintain a call stack for subroutines.
- Set a hard limit (e.g., depth of 10). If exceeded, gracefully abort execution and log the cycle.
- Add detailed `AppLogger` entries for `[SUBROUTINE]` jumps to easily trace execution flow in the Log Viewer.

## Phase C: App Profile Detector Optimization
**Problem:** `AppProfileDetector.kt` switches macros based on the active foreground app. If a system notification (`com.android.systemui`), volume panel, or transient ad overlay pops up, the detector might think the game was closed and abruptly stop the macro.
**Proposed Change:**
- Implement a caching and debouncing mechanism in `AppProfileDetector.kt`.
- Ignore known transient packages (System UI, package installer, etc.).
- Allow a brief "grace period" (e.g., 2-3 seconds) before switching profiles to ride out temporary interruptions.

## Verification Plan
For each phase:
- Compile and build (`:app:compileDebugKotlin`).
- Run all 174+ `1AInspector` python scripts to ensure no layout/protocol regressions.

## User Review Required

> [!IMPORTANT]
> I have formalized the plan to execute A, B, and C in order.
> Please **approve** this plan so I can begin immediately with **Phase A: Smooth Path Interpolation**.