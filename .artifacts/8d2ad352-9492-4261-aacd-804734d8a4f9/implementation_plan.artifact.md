# OCR Multi-Search Engine Integration, Screen Area OCR Selector, & Carousel Support

## Problem
1. **OCR Queries Skipped in Multi-Search**: In `MacroExecutionEngine.kt`, `resolveActiveTemplatesRobust()` ignored `"ocr:..."` entries in `multiTemplatePaths` because `templateRepository.getTemplate("ocr:Text")` returned `null`. Consequently, manually added `"ocr:..."` entries were skipped during macro execution.
2. **Missing Screen Area OCR Selection**: Users could only add OCR queries by manually typing text in a dialog, but could not select an area directly on the live screen to extract text via ML Kit OCR.
3. **Carousel Display for OCR Items**: OCR items in `selectedMultiPaths` need to be consistently displayed and removable in the `EditActionDialog` carousel alongside image templates.

## Proposed Changes

### MacroExecutionEngine.kt
- [MODIFY] [MacroExecutionEngine.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/engine/MacroExecutionEngine.kt)
  - During multi-search polling (`ActionType.TRIGGER` or `ActionType.OCR`), extract all `"ocr:Text"` entries from `action.multiTemplatePaths`.
  - For each `"ocr:Text"` entry, execute ML Kit `OcrEngine.findTextOnScreen(screenshot, query, timeoutMs, roi, variableContext)`.
  - Convert recognized OCR text results into valid `MatchCandidate` objects (`templateName = "ocr:$query"`, `score = 0.95f`, `clickX`, `clickY`) and evaluate them alongside image template candidates.

### EditActionDialog.kt
- [MODIFY] [EditActionDialog.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt)
  - Add `+ ТЕКСТ С ЭКРАНА` button in `multiControlRow`.
  - On click, trigger `onSelectScreenOcrText` callback to launch on-screen frame capture.
  - Render all `"ocr:..."` entries in `selectedMultiPaths` as interactive OCR cards in the carousel with click-to-remove actions.

### AutoTapOrchestrator.kt
- [MODIFY] [AutoTapOrchestrator.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt)
  - Add `startScreenTextOcrSelection(action)`:
    - Hide overlays and open `CaptureFrameOverlay`.
    - Crop the selected screen region and run `OcrEngine.findTextOnScreen` to extract recognized text.
    - Append `"ocr:ExtractedText"` to `action.multiTemplatePaths` and re-open `EditActionDialog`.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 174+ 1AInspector scripts.

### Manual Verification
- Test manual OCR text entry (`"ocr:Query"`) in multi-search and verify `MacroExecutionEngine` detects the text and performs a click.
- Test `+ ТЕКСТ С ЭКРАНА`: select a screen area with text, verify ML Kit OCR extracts the text, appends `"ocr:Text"` to multi-search, and renders it in the carousel.
