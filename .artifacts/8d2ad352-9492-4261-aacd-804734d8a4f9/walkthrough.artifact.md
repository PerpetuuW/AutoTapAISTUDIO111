# Walkthrough - Multi-Search Template Starvation Fix

## Changes

### TemplateMatchingEngine.kt
- **Template Starvation in Multi-Search (`findMultiTemplatesFastCascade`)**:
  - The previous multi-search engine prematurely restricted candidate sectors to the top `10` or `24` spatial sectors *before* sequentially testing individual templates against the screen. In a scenario with 5 templates, background noise or imperfect early clusters from Template #1 would completely monopolize the top 10 sectors, starving Template #5 from being evaluated at its true location (which might have ranked 15th overall in the raw coarse evaluation before fine tuning).
  - Increased `sectorsToScan` limits dynamically:
    - Standard multi-search: Top 10 -> **Top 32 sectors**.
    - Find All Matches mode: Top 24 -> **Top 64 sectors**.
  - This guarantees sufficient L1/L2 buffer capacity so later templates in the multi-search array are fairly and exhaustively evaluated at their proper coordinates without dropping true targets.

## Verification Results

### Automated Tests
- Gradle Build: `:app:compileDebugKotlin` finished successfully.
- 1AInspector Suite: All `174 / 174` inspector modules passed 100% (`PASSED: 174 / 174`).
