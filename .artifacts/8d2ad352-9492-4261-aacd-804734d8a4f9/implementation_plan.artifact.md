# Update Existing Templates and Masks In-Place on Save/Recapture

## Problem
When users edit or recapture an existing template/mask (e.g. from target overlay manager or orchestrator recapture flow), `existingTemplatePath` was passed as `null`. This forced a "copy-on-write" / new file creation instead of updating the existing template file and metadata in-place, leaving old template files orphaned and failing to update references.

## Proposed Changes

### TargetOverlayManager.kt
- [MODIFY] [TargetOverlayManager.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/target/TargetOverlayManager.kt)
  - Pass `existingTemplatePath = targetAction.templatePath.takeIf { it.isNotBlank() }` when opening `CalibrationOverlay` for editing/recapturing an existing action.

### AutoTapOrchestrator.kt
- [MODIFY] [AutoTapOrchestrator.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt)
  - Pass `existingTemplatePath = effectiveAction.templatePath.takeIf { it.isNotBlank() }` and `initialAction?.templatePath?.takeIf { it.isNotBlank() }` when launching `CalibrationOverlay`.

## Verification Plan

### Automated Tests
- Run Gradle build: `./gradlew :app:compileDebugKotlin`
- Run existing inspection scripts in `1AInspector/`.

### Manual Verification
- Edit/recapture an existing action's template/mask and verify that the template file is updated in-place rather than creating a new file.
