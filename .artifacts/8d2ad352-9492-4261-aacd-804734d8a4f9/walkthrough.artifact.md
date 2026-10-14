# Walkthrough - Kotlin Serialization, ANR-WatchDog & Architectural Dependency Graphing

## Changes

### 1. `kotlinx.serialization` (Data Layer Modernization)
- Replaced the brittle `org.json.JSONObject` manual parsing with type-safe `kotlinx.serialization` for template metadata.
- Created `@Serializable data class TemplateMetadata` which explicitly declares all 29 fields (e.g., `similarityPercent`, `clickOffsetX`, `roiZones`).
- **Backward Compatibility Engine**: Added a robust fallback layer inside `TemplateMetadata.fromJsonString()`. If strict serialization fails (e.g., reading a template from an older version of the app), the code automatically catches the error, parses the JSON using legacy `JSONObject`, and gracefully maps the fields using `.optInt()` and `.has()`. This ensures older exported packages or saved macros will never break.

### 2. ANR-WatchDog (Telemetry)
- Integrated `ANRWatchDog` directly into `AppLogger.kt`.
- The watcher operates on a 3-second heartbeat threshold (`3000ms`). If the main UI thread freezes due to expensive computer vision processing or deadlocks, the WatchDog forcefully interrupts, captures the full thread stack trace, and logs it under the `"ANR_FREEZE"` category. You can instantly view this inside your `LogViewerDialog` using the interactive tag filters.

### 3. Architecture Graph Generator (`1AInspector`)
- Developed a new Python module: `1AInspector/generate_architecture_graph.py`.
- This script recursively scans the entire Kotlin codebase (`app/src/main/java`), extracts packages, classes, and their `import` relationships.
- It automatically generates a Mermaid diagram (`architecture_graph.artifact.md`) detailing the exact topological dependencies of the project (e.g., `AutoTapOrchestrator` -> `MacroExecutionEngine`).

## Verification Results

### Automated Tests
- Gradle Build: `:app:compileDebugKotlin` finished successfully.
- 1AInspector Suite: All `174 / 174` inspector modules passed 100% (`PASSED: 174 / 174`).
