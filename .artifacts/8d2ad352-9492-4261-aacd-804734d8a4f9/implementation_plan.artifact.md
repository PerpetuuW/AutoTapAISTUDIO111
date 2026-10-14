# Control Panel Capture Button Position, Tutorial Spotlight Precision, & Inspector Suite Restoration

## Problem
1. **Unsuitable Icon & Position for Capture Button**: The primary template capture button (`BTN_CAPTURE` / "Вырезка шаблона") was positioned incorrectly. As the primary tool, it must be located **immediately after the Play button** with the dedicated viewfinder icon `VectorIconDrawer.IconType.CAPTURE` and cyan accent (`#38BDF8`).
2. **Interactive Tutorial Spotlight Misalignment**: `InteractiveTutorialOverlay` was receiving `hostViewProvider = { composeView }` instead of `{ this }`. Calling reflection `getTutorialRect` on `ComposeView` threw `NoSuchMethodException`, causing the tutorial to fall back to highlighting the entire panel instead of pointing directly to individual buttons.
3. **Restore Inspector Modules**: Move the 15 archived Python inspector scripts back from `1AInspector/archive_legacy_dialog_checks/` to `1AInspector/` and update them so the full **174 / 174** inspector suite passes 100%.

## Proposed Changes

### ControlPanelComposeOverlay.kt
- [MODIFY] [ControlPanelComposeOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/panel/ControlPanelComposeOverlay.kt)
  - Reorder main row buttons:
    1. `PLAY` / `PAUSE` (`BTN_PLAY_VIEW`)
    2. `CAPTURE` (`BTN_CAPTURE` — icon `VectorIconDrawer.IconType.CAPTURE`, cyan accent `#38BDF8`, right after Play!)
    3. `PLUS` (`BTN_ADD` — icon `VectorIconDrawer.IconType.PLUS`)
    4. `RECORD` (`BTN_RECORD` — icon `VectorIconDrawer.IconType.RECORD`)
    5. `PANEL_EXPAND` / `PANEL_COLLAPSE` (`BTN_TOGGLE_VIEW`)
    6. `VISIBILITY` (`BTN_HIDE` / Eye)
    7. `SETTINGS` (`BTN_SETTINGS`)
  - Pass `hostViewProvider = { this }` when opening `InteractiveTutorialOverlay`.
  - Update `getTutorialRect` column mapping to reflect the 7-button main row layout.

### 1AInspector Suite
- [MODIFY] Move all 15 scripts from `1AInspector/archive_legacy_dialog_checks/` back to `1AInspector/`.
- Update any legacy checks so all 174 inspector modules run and pass 100%.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 174 1AInspector scripts (`python -c ...`).

### Manual Verification
- Verify `CAPTURE` button is positioned right after Play/Pause on the main row with the viewfinder icon.
- Open Interactive Tutorial: verify laser line and spotlight point directly to each button on the main panel.
