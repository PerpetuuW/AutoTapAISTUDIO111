#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_template_search_robustness.py — Инспектор целостности тракта поиска шаблонов
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    tme_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")
    calib_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")
    mee_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/engine/MacroExecutionEngine.kt")

    for p in [tme_path, calib_path, mee_path]:
        if not os.path.exists(p): return False, f"Файл не найден: {p}"

    with open(tme_path, "r", encoding="utf-8") as f:
        if "features.tw / 4" not in f.read():
            return False, "В TemplateMatchingEngine.kt отсутствует плотный шаг TIER 2 (features.tw / 4)"

    with open(calib_path, "r", encoding="utf-8") as f:
        if "finalRawCropped" not in f.read():
            return False, "В CalibrationOverlay.kt отсутствует сохранение согласованного кропа finalRawCropped"

    with open(mee_path, "r", encoding="utf-8") as f:
        if "realPath" not in f.read():
            return False, "В MacroExecutionEngine.kt отсутствует сопоставление realPath для matched шаблонов"

    return True, "Инспекция целостности тракта шаблонов пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
