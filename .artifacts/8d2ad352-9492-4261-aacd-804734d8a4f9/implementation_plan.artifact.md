# Architectural Modernization Plan (Compose, LeakCanary, Detekt, StateFlow)

## Goal
Implement the top-tier industry standards to eliminate UI layout bugs, memory leaks, and architectural spaghetti code in AutoTap. We will integrate Jetpack Compose, LeakCanary, Detekt, and StateFlow infrastructure.

## Proposed Changes

### 1. Build Config & Dependencies (`libs.versions.toml`, `build.gradle.kts`)
- Add **Jetpack Compose** compiler plugin (Kotlin 2.2.10 requires `org.jetbrains.kotlin.plugin.compose`).
- Enable `buildFeatures { compose = true }` in `app/build.gradle.kts`.
- Add `androidx.compose.ui`, `material3`, and `ui-tooling`.
- Add **LeakCanary** to `debugImplementation` for automatic memory leak detection (OOM prevention).
- Add **Detekt** static analysis plugin to catch cognitive complexity, unused variables, and missing null-safety.
- Add `kotlinx-coroutines-android` and `lifecycle-viewmodel-compose` for **StateFlow/MVI** architecture.

### 2. Detekt Configuration
- Create a `detekt.yml` config or baseline script to automatically run static analysis and integrate it with your existing `1AInspector` suite.

### 3. StateFlow & Jetpack Compose Demonstration (Optional/Foundation)
- Create a foundational `AbstractComposeOverlay` class that allows rendering Jetpack Compose inside the `WindowManager`. This will serve as a bridge for you to incrementally migrate complex dialogs (like `EditActionDialog` or `GraphEditorOverlay`) to clean declarative UI without `LayoutParams` math.

## User Review Required

> [!IMPORTANT]
> **Scope of Migration**: Do you want me to *only* setup the infrastructure (add dependencies, configure plugins, and create base Compose classes) so you can use them gradually? Or would you also like me to completely rewrite a specific overlay (e.g., `RunningBadgeOverlay`) to demonstrate Jetpack Compose and StateFlow in action right now?

Please approve the plan or specify which part you want to focus on.