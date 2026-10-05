#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_game_focus_and_m3_tutorials.py — Инспектор фильтрации игровых пакетов и чистоты туториалов M3
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    aas_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/accessibility/AutoTapAccessibilityService.kt")
    ito_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/tutorial/InteractiveTutorialOverlay.kt")

    if not os.path.isfile(aas_path): return False, f"Файл не найден: {aas_path}"
    if not os.path.isfile(ito_path): return False, f"Файл не найден: {ito_path}"

    with open(aas_path, "r", encoding="utf-8", errors="replace") as f:
        aas_src = f.read()
        if "ignoredSystemPackages" not in aas_src:
            return False, "В AutoTapAccessibilityService.kt отсутствует фильтр системных пакетов ignoredSystemPackages"

    with open(ito_path, "r", encoding="utf-8", errors="replace") as f:
        ito_src = f.read()
        if "СЕТКА 9 ТИПОВ ДЕЙСТВИЯ" in ito_src:
            return False, "В туториале остался устаревший шаг 'СЕТКА 9 ТИПОВ ДЕЙСТВИЯ'"
        if "#00F5D4" in ito_src:
            return False, "В туториале остался кислотный цвет #00F5D4 в подсветке"

    return True, "Инспекция фильтрации игровых пакетов и актуальности туториалов пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
