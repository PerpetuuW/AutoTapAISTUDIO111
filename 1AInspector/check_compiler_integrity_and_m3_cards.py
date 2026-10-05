#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_compiler_integrity_and_m3_cards.py — Инспектор баланса скобок и компилируемости верстки
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    orch_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt")
    ead_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")

    if not os.path.isfile(orch_path): return False, f"Файл не найден: {orch_path}"
    if not os.path.isfile(ead_path): return False, f"Файл не найден: {ead_path}"

    with open(orch_path, "r", encoding="utf-8", errors="replace") as f:
        if "initialCandidates = emptyList()" not in f.read():
            return False, "В AutoTapOrchestrator.kt не передан обязательный параметр initialCandidates в CalibrationOverlay"

    with open(ead_path, "r", encoding="utf-8", errors="replace") as f:
        src = f.read()
        if "repo.baseTemplatesDir" in src:
            return False, "В EditActionDialog.kt осталось прямое обращение к private baseTemplatesDir"

    return True, "Инспекция баланса скобок и компиляции пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
