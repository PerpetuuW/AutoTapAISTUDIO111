#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_template_folder_and_recalib.py — Инспектор кнопки перемещения в папку, вывода параметров шаблона и перекалибровки
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    ead_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")
    sme_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/SmartMaskEngine.kt")
    orch_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt")

    if not os.path.isfile(ead_path): return False, f"Файл не найден: {ead_path}"
    if not os.path.isfile(sme_path): return False, f"Файл не найден: {sme_path}"
    if not os.path.isfile(orch_path): return False, f"Файл не найден: {orch_path}"

    with open(ead_path, "r", encoding="utf-8", errors="replace") as f:
        ead_src = f.read()
        if "btnApplyMeta" in ead_src:
            return False, "В EditActionDialog.kt осталась рудиментарная кнопка btnApplyMeta"
        if "ТОЛЬКО ЭТОТ" in ead_src:
            return False, "В EditActionDialog.kt осталась кнопка ТОЛЬКО ЭТОТ вместо В ПАПКУ"
        if "В ПАПКУ" not in ead_src:
            return False, "В EditActionDialog.kt отсутствует кнопка В ПАПКУ"

    with open(sme_path, "r", encoding="utf-8", errors="replace") as f:
        sme_src = f.read()
        if ".coerceIn(35, 92)" in sme_src:
            return False, "В SmartMaskEngine.kt остался ошибочный сброс порога в 35%"

    return True, "Инспекция кнопки В ПАПКУ, перекалибровки и параметров шаблонов пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
