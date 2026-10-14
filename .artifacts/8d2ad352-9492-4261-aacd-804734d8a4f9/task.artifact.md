# Task List: Control Panel Compose Migration

- `[x]` Create `ControlPanelState` data class in a new `ControlPanelComposeOverlay.kt` file.
- `[x]` Implement Compose-based `VectorIconButton` component.
- `[x]` Build `ControlPanelComposeOverlay` extending `AbstractComposeOverlay` with MVI StateFlow.
- `[x]` Replicate all 3 display modes (MINI_BUBBLE, COMPACT_SINGLE_ROW, EXPANDED) with smooth dragging.
- `[x]` Update `AutoTapOrchestrator.kt` to use the new Compose overlay instead of the legacy one.
- `[x]` Build and verify compilation.
- `[x]` Run all 174+ 1AInspector scripts and create walkthrough.
