# Task List: Serialization, WatchDog, Coil, DataStore, & Dependency Graph

- `[x]` Add dependencies for `kotlinx.serialization`, `ANR-WatchDog`, `Coil`, and `DataStore` to Gradle (`libs.versions.toml` & `build.gradle.kts`).
- `[x]` Initialize `ANR-WatchDog` in `AppLogger.kt`.
- `[ ]` Implement `TemplateMetadata` Serializable wrapper ensuring JSON parity in `TemplateRepositoryImpl.kt`.
- `[ ]` Migrate `BitmapFactory.decodeFile` to `Coil` image requests in UI carousels where applicable.
- `[ ]` Develop async `Preferences DataStore` and integrate it smoothly into `AutoTapOrchestrator` without blocking startup.
- `[x]` Create `1AInspector/generate_architecture_graph.py` to map codebase dependencies to a Mermaid diagram.
- `[x]` Build and verify compilation.
- `[x]` Run all 1AInspector scripts and create walkthrough.
