#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_calibration_all_candidates.py — Инспектор полноэкранного поиска всех кандидатов в калибровке
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    calib_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")

    if not os.path.isfile(calib_path):
        return False, f"Файл не найден: {calib_path}"

    with open(calib_path, "r", encoding="utf-8", errors="replace") as f:
        content = f.read()

    if "TemplateMatchingEngine.findTemplateFastCascade(" not in content:
        return False, "Отсутствует вызов findTemplateFastCascade в CalibrationOverlay.kt"

    if "minSimilarityPercent = 60" not in content:
        return False, "Порог полноэкранного поиска кандидатов не установлен в 60%"

    if "candidateList.sortByDescending { it.score }" not in content:
        return False, "Отсутствует сортировка пула кандидатов по убыванию очков"

    return True, "Инспекция вывода всех кандидатов калибровки пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
