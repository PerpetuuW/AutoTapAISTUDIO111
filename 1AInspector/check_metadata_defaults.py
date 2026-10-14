#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_metadata_defaults.py — Инспектор применения метаданных шаблона по умолчанию
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    mee_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/engine/MacroExecutionEngine.kt")
    ead_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")

    for p in [mee_path, ead_path]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(mee_path, "r", encoding="utf-8") as f:
        m_src = f.read()
    if "bestMatchCand.clickOffsetX" not in m_src:
        return False, "В MacroExecutionEngine отсутствует учет смещений из метаданных победного шаблона"

    with open(ead_path, "r", encoding="utf-8") as f:
        e_src = f.read()
    if "templateRepository.getTemplateMetadata(" not in e_src:
        return False, "В EditActionDialog отсутствует считывание метаданных шаблона"

    return True, "Инспекция применения метаданных по умолчанию пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
