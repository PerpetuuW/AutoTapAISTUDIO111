# Recalibration Crash Fix (Empty Coerce Range Guard), Choice Modal, & Strict Layout Inspector Suite

## Problem
1. **Recalibration Crash on Edge Selection**: In `AutoTapOrchestrator.kt`, `cropW.coerceIn(4, effectiveScreenshot.width - cropX)` threw `IllegalArgumentException: Cannot coerce value to an empty range` whenever `cropX` or `cropY` was near or beyond the edge of the screenshot (`width - cropX < 4`), causing an unhandled app crash without logcat output.
2. **Redundant Calibration Button Removal**: In `EditActionDialog.kt`, the redundant "КАЛИБРОВКА" button must be removed, leaving "РЕДАКТИРОВАТЬ" as the single full-width button.
3. **Unified Choice Modal for Quick Ring & Step Settings**: Both quick action ring and step settings must trigger the same screen choice modal (**"НА ТЕКУЩЕМ ЭКРАНЕ"** vs **"НА СОХРАНЕННОМ СКРИНШОТЕ"**), clean of emojis.
4. **100% Codebase Emoji & Text Overflow Inspector Suite**: Expand 1AInspector to scan 100% of Kotlin files in `app/src/main/java` for zero emojis and universal text overflow safety.

## Proposed Changes

### AutoTapOrchestrator.kt
- [MODIFY] [AutoTapOrchestrator.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt)
  - Replace unsafe `coerceIn` range calls with edge-guarded bounds:
    ```kotlin
    val maxX = (effectiveScreenshot.width - 4).coerceAtLeast(0)
    val maxY = (effectiveScreenshot.height - 4).coerceAtLeast(0)
    val safeX = cropX.coerceIn(0, maxX)
    val safeY = cropY.coerceIn(0, maxY)
    val safeW = cropW.coerceIn(4, (effectiveScreenshot.width - safeX).coerceAtLeast(4))
    val safeH = cropH.coerceIn(4, (effectiveScreenshot.height - safeY).coerceAtLeast(4))
    ```
  - Wrap `executeRecaptureWithScreenshot` in defensive `try-catch` blocks with `AppLogger.logError` logging and overlay state recovery (`controlPanelOverlay.show()`, `targetManager.setOverlaysVisible(true)`).
  - Remove emojis (`📷` and `🖼️`) from choice modal buttons.
  - Connect both quick ring and step settings calibration calls to `showCalibrationChoiceModal`.

### EditActionDialog.kt
- [MODIFY] [EditActionDialog.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt)
  - Remove `btnCalibDirect` ("КАЛИБРОВКА") and expand "РЕДАКТИРОВАТЬ" to full row width (`MATCH_PARENT`).

### 1AInspector Test Suite
- [MODIFY] [1AInspector/check_no_emojis.py](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/1AInspector/check_no_emojis.py)
  - Expand emoji scanning to check 100% of `.kt` files in `app/src/main/java` for zero emojis.
- [NEW] [1AInspector/check_universal_text_truncation_and_no_overflow.py](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/1AInspector/check_universal_text_truncation_and_no_overflow.py)
  - Scan all UI files to verify text truncation methods (`.take(...)`) are absent and marquee/auto-fit formatting is present.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 172+ 1AInspector scripts.

### Manual Verification
- Test recalibration near screen edges: verify no crash occurs and crop bounds are safely clamped.
- Verify `EditActionDialog` has a single full-width "РЕДАКТИРОВАТЬ" button.
- Verify choice modal shows clean text ("НА ТЕКУЩЕМ ЭКРАНЕ" / "НА СОХРАНЕННОМ СКРИНШОТЕ") without emojis.
