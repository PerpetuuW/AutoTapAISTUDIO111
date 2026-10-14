# Walkthrough - Tag Filtration in Logs & Detailed CV Execution Debugging

## Changes

### 1. LogViewerDialog.kt (Interactive Tag Filtration)
- **Tag Extraction**: `LogViewerDialog.kt` now automatically parses `error_log.txt` lines to extract unique categories (e.g., `[CV_EXECUTION]`, `[GRAPH_CV_EXECUTION]`, `[FATAL_CRASH]`).
- **Filter Buttons**: Added a horizontally scrollable row of tag buttons dynamically created above the log text.
- **Interactive Filtering**: Tapping a tag instantly filters the displayed logs to show ONLY lines matching that category (plus stack trace lines), making it incredibly easy to track down exact macro stopping causes. Tapping "ВСЕ ЛОГИ" restores full visibility.

### 2. MacroExecutionEngine.kt (Granular Diagnostics)
- **Linear Macros (`[CV_EXECUTION]`)**:
  - Added logs if search stops due to timeout without matching: `"ТАЙМАУТ ПОИСКА. Ни одна из целей не достигла порога X% за выделенное время."`
  - Added success logs containing exact percentage: `"УСПЕХ [MATCH_SUCCESS]. Найдена цель 'X' со сходимостью Y%"`
- **Graph Macros (`[GRAPH_CV_EXECUTION]`)**:
  - Added explicit logs on trigger fail: `"Триггер-шаблон 'X' НЕ НАЙДЕН. Порог: Y%, ROI: ЗАДАН"`
  - Added explicit logs on trigger success: `"Триггер 'X' УСПЕШНО НАЙДЕН на (x, y) со сходимостью Y%. Пул локаций: Z"`

## Verification Results

### Automated Tests
- Gradle Build: `:app:compileDebugKotlin` finished successfully.
- 1AInspector Suite: All `174 / 174` inspector modules passed 100% (`PASSED: 174 / 174`).
