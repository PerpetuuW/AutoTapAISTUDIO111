# Walkthrough - Root-Cause Template Fix, Single-Line Action Toolbar, & Dedicated 98% AI Threshold

## Root-Cause Discovery & Fixes

### 1. Template Repository (.png Extension Root-Cause)
- **Root Cause**: In `TemplateRepositoryImpl.kt` (`saveTemplateWithScreen`), when custom/default template names like `"Цель_1"` were provided, `cleanName` was formatted as `mask_Цель_1` **without** the `.png` extension. The saved file `mask_Цель_1` was then filtered out by `loadAllTemplates()` (`it.name.endsWith(".png")`), causing all created templates to vanish from disk queries.
- **Fix**: Enforced `.png` extension in `cleanName` formatting (`mask_Цель_1.png`). All templates are now correctly recognized and returned by `loadAllTemplates()`.

### 2. EditActionDialog.kt
- Removed `btnMergeTemplates` ("ОБЪЕДИНИТЬ") and its merge logic. The action toolbar buttons now fit on a single clean line without text wrapping or overflow.
- Updated `renderCarouselItems()` to construct `displayTemplates = (filteredTemplates + selectedMultiTemplates)`, ensuring ALL templates in `selectedMultiPaths` are displayed in the carousel regardless of subfolder.

### 3. NeuralVisualMatcher.kt
- Decoupled Neural Engine threshold from OpenCV. AI / Neural Engine mode now strictly uses its dedicated 98% convergence threshold (`minSimilarityPercent.coerceIn(98, 99)`).

## Verification Results

### Automated Tests
- Gradle Build: `:app:compileDebugKotlin` finished successfully.
- 1AInspector Suite: All `172 / 172` inspector modules passed 100% (`PASSED: 172 / 172`).
