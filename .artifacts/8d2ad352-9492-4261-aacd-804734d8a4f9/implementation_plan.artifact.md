# Fix Manual Mask Studio Editing, Tool Diameter Display, & Full Search Title Truncation

## Problem
1. **Manual Mask Studio Editing**: In `MagicWandEditorOverlay.kt`, temporary bitmaps were being recycled during touch drag (`ACTION_MOVE`), causing bitmap recycling crashes during manual brush/eraser editing.
2. **Tool Diameter Display**: The brush/eraser radius slider in `MagicWandEditorOverlay.kt` needs to explicitly display the tool diameter in pixels (e.g., `ДИАМЕТР КИСТИ: 8px`).
3. **Execution Info Board Truncation ("Цель_")**: In `MacroExecutionEngine.kt`, `searchTitle` was calling `.take(10)` on `nameWithoutExtension` (which included `mask_`), truncating `"mask_Цель_1"` down to `"mask_Цель_"`, leaving only `"Цель_"` on the info board.

## Proposed Changes

### MacroExecutionEngine.kt
- [MODIFY] [MacroExecutionEngine.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/engine/MacroExecutionEngine.kt)
  - Remove `.take(10)` truncation from template names in `searchTitle`. Remove `mask_` prefix before formatting, producing full informative titles like `Поиск: Цель_1` or `Поиск: Кнопка`.

### MagicWandEditorOverlay.kt
- [MODIFY] [MagicWandEditorOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/capture/MagicWandEditorOverlay.kt)
  - Prevent premature bitmap recycling during touch drag (`ACTION_MOVE`) in `safeReplaceCurrentMask`, ensuring brush, eraser, and magic wand manual editing work smoothly.
  - Display tool diameter (`brushRadius * 2`) in `tvToolParamLabel` when adjusting the brush/eraser slider.

### RunningBadgeOverlay.kt
- [MODIFY] [RunningBadgeOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/badge/RunningBadgeOverlay.kt)
  - Ensure full untruncated `searchTitle` is passed to `tvActionStatus` and smoothly scrolled via `MarqueeTextView`.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 172 1AInspector scripts.

### Manual Verification
- Test manual mask editing (brush, eraser, wand) in `MagicWandEditorOverlay`.
- Verify tool diameter (`ДИАМЕТР КИСТИ: Xpx`) updates in real time when adjusting the slider.
- Verify running badge status displays full target name (e.g., `Поиск: Цель_1`) without getting truncated at `Цель_`.
