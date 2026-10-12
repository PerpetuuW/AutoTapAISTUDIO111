# Unify Play/Stop Buttons and Circular Border for Collapsed Play Button

## Problem
1. In the collapsed main overlay (`ControlPanelOverlay` mini bubble layout), the Play button (`btnMiniPlay`) currently has rounded rectangle corners (`cornerRadiusPx = dpF(12f)`) rather than a circular border (`isCircleShape = true`), breaking symmetry with circular controls.
2. Play, Pause, and Stop buttons across overlays (`ControlPanelOverlay`, `RunningBadgeOverlay`, etc.) have disparate implementations and styling definitions. They need to be unified.

## Proposed Changes

### ControlPanelOverlay.kt
- [MODIFY] [ControlPanelOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/panel/ControlPanelOverlay.kt)
  - Update `btnMiniPlay` in the collapsed main overlay to be circular (`isCircleShape = true`, `cornerRadiusPx = width / 2f` or using `CanvasIconButton` with `isCircleShape = true`), while keeping the expanded main toolbar Play/Pause buttons as-is (pills/rounded rects).
  - Unify styling and colors of Play/Pause/Stop/Action buttons.

### RunningBadgeOverlay.kt
- [MODIFY] [RunningBadgeOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/badge/RunningBadgeOverlay.kt)
  - Unify stop/play button dimensions, padding, and stroke/gradient colors with `CanvasIconButton` design system across overlays.

### CanvasIconButton.kt / VectorIconDrawer.kt
- [MODIFY] [CanvasIconButton.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/ui/CanvasIconButton.kt)
  - Ensure uniform rendering for Play, Pause, and Stop icon types and circular/rounded bounds.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run 1AInspector checks.

### Manual Verification
- Verify that the collapsed main overlay Play button has a circular border.
- Verify that expanded panel Play/Pause buttons remain as-is.
- Verify running badge stop button is unified.
