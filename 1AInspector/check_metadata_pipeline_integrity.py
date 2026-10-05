#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_metadata_pipeline_integrity.py — Инспектор целостности тракта метаданных мультишаблонов
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    mee_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/engine/MacroExecutionEngine.kt")
    tme_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")

    for p in [mee_path, tme_path]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(mee_path, "r", encoding="utf-8") as f:
        m_src = f.read()
    if "val realPath = matched?.first ?: path" not in m_src:
        return False, "В MacroExecutionEngine отсутствует нормализация путей для multiTemplatePaths"
    if "val isMulti = activeTemplates.size > 1" not in m_src:
        return False, "В MacroExecutionEngine отсутствует сегрегация смещений для мультипоиска"

    with open(tme_path, "r", encoding="utf-8") as f:
        t_src = f.read()
    if "metaLastMod" not in t_src:
        return False, "В TemplateMatchingEngine отсутствует инвалидация кэша по metaLastMod"

    return True, "Инспекция целостности тракта метаданных пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
