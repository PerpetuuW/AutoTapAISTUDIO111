# Walkthrough - In-Place Template and Mask Updates on Save/Recapture

## Changes

### TargetOverlayManager.kt
- Updated `CalibrationOverlay` invocation to pass `existingTemplatePath = targetAction.templatePath.takeIf { it.isNotBlank() }` instead of `null` (preventing orphaned files and enabling in-place template/mask updates).

### AutoTapOrchestrator.kt
- Updated recapture flows (`startRecaptureForStep` and related flows) to pass `existingTemplatePath = effectiveAction.templatePath.takeIf { it.isNotBlank() }` and `initialAction?.templatePath?.takeIf { it.isNotBlank() }` instead of `null`.

## Verification Results

### Automated Tests
- Gradle Build: `:app:compileDebugKotlin` finished successfully.
- Inspector Check: `python 1AInspector/check_orchestrator_and_keyboard_fix.py` passed with `[PASS]`.
