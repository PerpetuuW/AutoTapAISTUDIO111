# Layer View Mode Selector in Mask Studio & Translucent Shape Contour Unification

## Problem
1. **Mask Studio Layer Selection**: In `MagicWandEditorOverlay.kt`, the editor always drew `rawBitmap` and `currentMask` overlaid together, preventing users from isolating and viewing/editing either layer independently.
2. **Translucent Shape / Arrow Fragmentation**: Translucent templates or arrow shapes with inner gradients were being fragmented into internal sub-regions during segmentation because inner luminance/alpha variations were treated as background.

## Proposed Changes

### MagicWandEditorOverlay.kt
- [MODIFY] [MagicWandEditorOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/capture/MagicWandEditorOverlay.kt)
  - Add a 3-way Layer View Mode toggle button (`СЛОЙ: НАЛОЖЕНИЕ` / `МАСКА` / `ЭТАЛОН`).
  - In `onDraw`:
    - `OVERLAY`: Draw `rawBitmap` + `currentMask` (alpha = 170).
    - `MASK_ONLY`: Draw only `currentMask` (full alpha = 255) on checkerboard.
    - `RAW_ONLY`: Draw only `rawBitmap` on checkerboard.

### SmartMaskEngine.kt
- [MODIFY] [SmartMaskEngine.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/vision/SmartMaskEngine.kt)
  - Factoring non-zero alpha `(c ushr 24) and 0xFF > 25` into shape foreground detection for translucent templates.
  - Invert perimeter flood-fill `backgroundMask` (`!backgroundMask.get(i)`) to ensure inner shape holes/gradients inside translucent arrows are filled as solid contiguous shape foreground.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 172 1AInspector scripts.

### Manual Verification
- Test layer mode toggle in `MagicWandEditorOverlay` (`НАЛОЖЕНИЕ`, `МАСКА`, `ЭТАЛОН`).
- Test segmenting a translucent arrow/shape and verify it remains a unified single shape without inner fragmentation.
