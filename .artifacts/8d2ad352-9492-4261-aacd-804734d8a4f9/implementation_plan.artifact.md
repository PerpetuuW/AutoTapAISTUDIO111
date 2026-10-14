# Phase 2: Jetpack Compose Migration for Control Panel Overlay

## Goal
Migrate the complex, tag-based manual UI creation of `ControlPanelOverlay.kt` to a clean, declarative Jetpack Compose UI (`ControlPanelComposeOverlay.kt`). This will eliminate manual view lookups (`findViewWithTag`), unify state management, and enable smooth expanding/collapsing animations.

## Proposed Changes

### 1. Unified State Management
- Define a `ControlPanelState` data class encapsulating:
  - `mode: PanelDisplayMode` (`COMPACT_SINGLE_ROW`, `EXPANDED`, `MINI_BUBBLE`)
  - `isPlaying: Boolean`
  - `visibilityMode: OverlayVisibilityMode` (`FULL`, `TRANSPARENT`, `HIDDEN`)
- Use `MutableStateFlow<ControlPanelState>` to drive the entire UI deterministically.

### 2. UI Reconstruction in Compose
- Create `ControlPanelComposeOverlay.kt` inheriting from `AbstractComposeOverlay`.
- Replicate the 3 modes:
  - **MINI_BUBBLE**: The 2-button floating pill (Play/Pause and Expand).
  - **COMPACT_SINGLE_ROW**: The main row (Play, Rec, Target, Expand/Collapse, Hide, Settings).
  - **EXPANDED**: Main row + Sub row (Lock, Wipe, Subroutines, Scripts, Help).
- Utilize standard Compose modifiers (`Modifier.pointerInput`, `detectDragGestures`) for smooth window dragging (which updates `WindowManager.LayoutParams`).
- Create a Compose-based `VectorIconButton` component to wrap custom canvas icon drawing, removing the old `CanvasIconButton` view.

### 3. Orchestrator Integration
- Modify `AutoTapOrchestrator.kt` to instantiate and use `ControlPanelComposeOverlay` instead of the legacy `ControlPanelOverlay`.
- Hook all callbacks to `ControlPanelListener`.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 174+ 1AInspector scripts.

### Manual Verification
- Expand/collapse animations should work flawlessly.
- Dragging the control panel should feel smooth and boundary-constrained.
- Playing/Stopping should correctly update the icon to Pause/Play across all modes.
- Toggling "Eye" should cycle through visibility modes smoothly.
