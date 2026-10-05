#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_micro_icon_sampling.py — Инспектор адаптивного шага сканирования микроиконок
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    engine_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")

    if not os.path.isfile(engine_path):
        return False, f"Файл не найден: {engine_path}"

    with open(engine_path, "r", encoding="utf-8", errors="replace") as f:
        content = f.read()

    if "val isSmall = features.isMicroIcon || features.isThinLine" not in content:
        return False, "Отсутствует флаг адаптивности isSmall в сеточном сканере TIER 2"

    if "(features.tw / 4).coerceIn(3, 6)" not in content:
        return False, "Отсутствует адаптивный плотный шаг сетки (3..6 px) для микроиконок"

    if "val isMicro = area <= 1600 || tw <= 36 || th <= 36" not in content:
        return False, "Классификатор isMicro не расширен до 36px / 1600 px^2"

    return True, "Инспекция плотного сканирования микроиконок пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
