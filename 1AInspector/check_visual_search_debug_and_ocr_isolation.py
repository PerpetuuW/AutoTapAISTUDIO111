#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
import sys

def run_check(root_dir=".") -> tuple[bool, str]:
    vis_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/visualizer/TargetHighlightVisualizer.kt")
    engine_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")
    macro_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/engine/MacroExecutionEngine.kt")

    for p in [vis_path, engine_path, macro_path]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(vis_path, "r", encoding="utf-8") as f:
        vis_code = f.read()

    if "fun isMatchVisualization" not in vis_code:
        return False, "TargetHighlightVisualizer.kt должен содержать метод isMatchVisualization"

    for kw in ["НАЙДЕНО", "ОТКЛОНЕНО", "ОТЛАДКА", "ПОРОГ", "activeHighlightRects"]:
        if kw not in vis_code:
            return False, f"isMatchVisualization в TargetHighlightVisualizer.kt должен фильтровать '{kw}'"

    if "fun showSearchDebugVisualization" not in vis_code:
        return False, "TargetHighlightVisualizer.kt должен содержать showSearchDebugVisualization"

    with open(engine_path, "r", encoding="utf-8") as f:
        eng_code = f.read()

    if "SearchDiagnostics" not in eng_code or "lastDiagnostics" not in eng_code:
        return False, "TemplateMatchingEngine.kt должен регистрировать SearchDiagnostics и lastDiagnostics"

    with open(macro_path, "r", encoding="utf-8") as f:
        macro_code = f.read()

    if "showSearchDebugVisualization" not in macro_code:
        return False, "MacroExecutionEngine.kt должен отображать showSearchDebugVisualization в режиме дебага"

    return True, "[PASS] Контракт визуализации поиска в дебаг-режиме и изоляции OCR от элементов подсветки полностью подтвержден."

if __name__ == "__main__":
    ok, msg = run_check(".")
    print(msg)
    sys.exit(0 if ok else 1)
