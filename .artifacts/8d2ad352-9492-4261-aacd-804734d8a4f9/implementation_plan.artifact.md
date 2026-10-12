# Fix Rename Dialog Crash, Shorten Default Template Names, and Sync Multi-Search `multiTemplatePaths`

## Problem
1. **Rename Dialog Crash**: In `EditActionDialog.kt`, passing a raw `EditText` directly to `AlertDialog.Builder.setView()` without layout parameters or padding caused crashes when renaming templates.
2. **Long Default Template Names**: Default names like `цель_1712345678901` were too long and didn't fit on template previews.
3. **Multi-Search Calibration Bug**: Calibrating/updating a template in multi-search mode updated `templatePath` but left `multiTemplatePaths` out of sync.

## Proposed Changes

### EditActionDialog.kt
- [MODIFY] [EditActionDialog.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt)
  - Wrap the rename template `EditText` inside a padded `LinearLayout` container with proper `MATCH_PARENT` layout params to prevent crashes in `AlertDialog`.

### CalibrationOverlay.kt
- [MODIFY] [CalibrationOverlay.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt)
  - Shorten default template names to `Цель_<4-digit code>` (e.g., `Цель_4829`) so they fit nicely on template previews.
  - Synchronize `multiTemplatePaths` when saving/calibrating an action in multi-search mode.

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run inspection scripts.

### Manual Verification
- Test renaming template from action dialog (verify no crash).
- Test creating a new template (verify short default name).
- Test template calibration in multi-search mode (verify `multiTemplatePaths` sync).
