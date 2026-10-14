# Root-Cause Fix: False Negatives in Multi-Search (Calibration vs Execution Divergence)

## Problem
In `TemplateMatchingEngine.findMultiTemplatesFastCascade`, the algorithm logs `Результат: 0 целей` (0 matches) despite `CalibrationOverlay` showing `score=92% (PASS)` for the exact same template.

## Root-Cause Analysis
The divergence between single calibration and multi-template search comes down to two aggressive NMS (Non-Maximum Suppression) & spatial sectoring optimizations inside `findMultiTemplatesFastCascade`:

1. **Premature Sector Pruning**: In `findMultiTemplatesFastCascade`:
   ```kotlin
   val sectorsToScan = ... .take(if (findAllMatches) 24 else 10)
   ```
   If a macro step uses multiple templates (e.g. 5 templates), the screen is partitioned into `spatialSectors`. By taking only the top 10 or 24 global spots *before* iterating over the templates sequentially, the top spots for Template #1 might crowd out the correct locations for Template #5. If Template #5's correct location ranks 25th in the coarse scan (due to noise/downscaling), it gets discarded completely before fine evaluation.

2. **Downscale Factor Hardcoding Divergence**:
   The loop uses `val lastUsedDownscale = features.optimalDownscaleFactorFromMeta` which might enforce `downscaleFactor = 3` from previous global settings, whereas calibration dynamically recalibrates it.

## Proposed Changes

### TemplateMatchingEngine.kt
- [MODIFY] [TemplateMatchingEngine.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt)
  - In `findMultiTemplatesFastCascade`: Expand the sector pruning limit significantly for `findAllMatches` (from 24 to 64) and non-findAll (from 10 to 32) to ensure candidates for later templates in the multi-search array aren't starved out by the first template's false peaks.
  - Apply the exact same local L2 search expansion radius used in `findTemplateFastCascade` to `findMultiTemplatesFastCascade` to guarantee symmetry.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 174+ 1AInspector scripts.

### Manual Verification
- Execute a macro step with 5 image templates. Verify `CV_PROFILER` reports `1 целей` instead of `0 целей` and correctly clicks the match.
- Compare calibration score and macro execution score to ensure they yield identical or near-identical results.
