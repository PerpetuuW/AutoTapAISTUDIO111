#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_multi_template_search_cascade_integrity.py — Чекер каскадной целостности мультипоиска 13+ шаблонов.
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    tme_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")
    mee_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/engine/MacroExecutionEngine.kt")

    for p in [tme_path, mee_path]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(tme_path, "r", encoding="utf-8") as f:
        tme_src = f.read()

    if "contextsForGridScan = preparedContexts.filter { !matchedTemplates.contains(it.tPath) }" not in tme_src:
        return False, "В TemplateMatchingEngine.kt нарушено правило фильтрации contextsForGridScan для TIER 2"

    with open(mee_path, "r", encoding="utf-8") as f:
        mee_src = f.read()

    if "resolveSingle" not in mee_src:
        return False, "В MacroExecutionEngine.kt отсутствует точечный резолвинг resolveSingle"

    if "ocrQueries" not in mee_src:
        return False, "В MacroExecutionEngine.kt отсутствует поддержка ocrQueries в мультипоиске"

    return True, "Инспекция каскадной целостности мультипоиска пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
