# Root-Cause Fix for Template Disappearance (.png Extension), Removal of Merge Button, and Independent Neural Threshold (98%)

## Root-Cause Discovery: Why Templates Disappeared
In `TemplateRepositoryImpl.kt` (`saveTemplateWithScreen`), when `customFileName` was passed (e.g. `"Цель_1"`), `cleanName` was formatted as `"mask_Цель_1"` **without** the `.png` extension. The file was written to disk as `mask_Цель_1`. Subsequently, `loadAllTemplates()` filtered for files ending with `.png` (`it.name.endsWith(".png")`), causing all templates saved with custom/default names to be filtered out and completely disappear from `loadAllTemplates()` and the carousel!

## Proposed Changes

### TemplateRepositoryImpl.kt
- [MODIFY] [TemplateRepositoryImpl.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/storage/TemplateRepositoryImpl.kt)
  - Ensure `cleanName` in `saveTemplateWithScreen` always enforces the `.png` extension (e.g., `mask_Цель_1.png`), guaranteeing every saved template is recognized by `loadAllTemplates()` and displayed in the carousel.

### EditActionDialog.kt
- [MODIFY] [EditActionDialog.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt)
  - Remove `btnMergeTemplates` ("ОБЪЕДИНИТЬ") and its merge logic so toolbar buttons fit on a single clean row.
  - In `renderCarouselItems()`, ensure `displayTemplates` includes ALL templates in `selectedMultiPaths` regardless of folder filter.

### NeuralVisualMatcher.kt
- [MODIFY] [NeuralVisualMatcher.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/vision/NeuralVisualMatcher.kt)
  - Decouple Neural Engine threshold from OpenCV and use a dedicated 98% / neural threshold (`minSimilarityPercent.coerceIn(98, 99)`).

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 172 1AInspector scripts.

### Manual Verification
- Capture a new template via quick actions or calibration and verify it receives the `.png` extension and appears in `loadAllTemplates()` and the carousel.
- Verify "ОБЪЕДИНИТЬ" button is removed and action toolbar fits on a single line.
- Verify Neural Engine uses dedicated 98% threshold.
