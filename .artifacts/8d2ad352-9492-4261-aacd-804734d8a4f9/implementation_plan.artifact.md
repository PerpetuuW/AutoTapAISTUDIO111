# Unified Material 3 Cyber-Glassmorphism Button Design System & Removal of Restricted Settings Button

## Goal
1. **Remove Unnecessary Button**: Completely remove the "РАЗРЕШИТЬ ОГРАНИЧЕННЫЕ НАСТРОЙКИ (ANDROID 13+)" button from `GlobalSettingsDialog.kt`.
2. **Unified Button Design System**: Standardize all button styles, colors, gradients, strokes, corner radiuses, and typography across `GlobalSettingsDialog.kt`, `ScriptsDialog.kt`, `LogViewerDialog.kt`, and `EditActionDialog.kt` into a single, cohesive **Material 3 Cyber-Glassmorphism** design system.

---

## Unified Button Design Tokens

| Button Type | Background Gradient | Stroke Border | Text Color & Font | Corner Radius |
| :--- | :--- | :--- | :--- | :---: |
| **Primary Action** (Сохранить, Применить) | `#162032` -> `#0F172A` | `#38BDF8` (Cyan) | `#38BDF8`, BOLD, 10.5sp | `8dp` |
| **Secondary Utility** (Отмена, Закрыть, Назад) | `#1F2937` -> `#111827` | `#374151` (Slate) | `#9CA3AF`, BOLD, 9.5sp | `8dp` |
| **Danger / Destructive** (Удалить, Очистить) | `#2A0E0C` -> `#1C0908` | `#7F1D1D` (Crimson) | `#F04438`, BOLD, 9.5sp | `8dp` |
| **Toggle Active** (Включено, Активно) | `#0D2E1E` -> `#061A10` | `#34D399` (Emerald) | `#86EFAC`, BOLD, 9sp | `6dp` |
| **Toggle Inactive** (Выключено) | `#21262D` -> `#161B22` | `#30363D` (Charcoal) | `#8B949E`, BOLD, 9sp | `6dp` |

---

## Proposed Changes

### 1. GlobalSettingsDialog.kt
- [MODIFY] [GlobalSettingsDialog.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/GlobalSettingsDialog.kt)
  - Remove "РАЗРЕШИТЬ ОГРАНИЧЕННЫЕ НАСТРОЙКИ (ANDROID 13+)" button.
  - Apply Unified Primary style to "СОХРАНИТЬ", Secondary to "ОТМЕНА", Danger to "СБРОСИТЬ", and Toggle style to "ВКЛЮЧЕНО / ВЫКЛЮЧЕНО".

### 2. ScriptsDialog.kt
- [MODIFY] [ScriptsDialog.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/ScriptsDialog.kt)
  - Apply Unified Primary style to "СОХРАНИТЬ СЦЕНАРИЙ", "ЭКСПОРТ ZIP", and Secondary style to "ГРАФ-РЕДАКТОР", "ЗАКРЫТЬ".

### 3. LogViewerDialog.kt
- [MODIFY] [LogViewerDialog.kt](file:///C:/Users/Stas/AndroidStudioProjects/AutoTapAISTUDIO111/app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/LogViewerDialog.kt)
  - Apply Unified Primary style to active tag chips, Secondary style to inactive tag chips, and Danger style to "ОЧИСТИТЬ".

## Verification Plan

### Automated Tests
- Gradle build: `./gradlew :app:compileDebugKotlin`
- Run all 177 1AInspector scripts.

### Manual Verification
- Open `GlobalSettingsDialog`: verify "Ограниченные настройки" button is gone and all buttons have unified Cyber-Glassmorphism styling.
- Open `ScriptsDialog` and `LogViewerDialog`: verify matching unified button styles.
