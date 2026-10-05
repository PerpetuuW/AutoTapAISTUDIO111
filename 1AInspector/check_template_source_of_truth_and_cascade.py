#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_template_source_of_truth_and_cascade.py — Инспектор полноэкранного каскада и чистоты меню шагов
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    tme_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")
    ead_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")
    cal_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")

    if not os.path.isfile(tme_path): return False, f"Файл не найден: {tme_path}"
    if not os.path.isfile(ead_path): return False, f"Файл не найден: {ead_path}"
    if not os.path.isfile(cal_path): return False, f"Файл не найден: {cal_path}"

    with open(tme_path, "r", encoding="utf-8", errors="replace") as f:
        tme_src = f.read()
        if "findAllMatches: Boolean = false" not in tme_src:
            return False, "В TemplateMatchingEngine.kt отсутствует флаг findAllMatches"
        if "if (tier0Found && !findAllMatches) continue" not in tme_src:
            return False, "В TemplateMatchingEngine.kt остался безусловный ранний выход tier0Found"

    with open(cal_path, "r", encoding="utf-8", errors="replace") as f:
        cal_src = f.read()
        if "findAllMatches = true" not in cal_src:
            return False, "В CalibrationOverlay.kt не передан флаг findAllMatches = true"

    with open(ead_path, "r", encoding="utf-8", errors="replace") as f:
        ead_src = f.read()
        if "simRow" in ead_src:
            return False, "В EditActionDialog.kt остался регулятор порога simRow"
        if "btnToggleShape" in ead_src:
            return False, "В EditActionDialog.kt остался тумблер btnToggleShape"

    return True, "Инспекция чистоты меню шагов и полноэкранной калибровки пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
