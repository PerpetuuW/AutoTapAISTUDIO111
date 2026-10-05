#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_step_editor_ergonomics.py — Инспектор 3-вкладочной эргономичной верстки EditActionDialog
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    ead_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")

    if not os.path.isfile(ead_path):
        return False, f"Файл не найден: {ead_path}"

    with open(ead_path, "r", encoding="utf-8", errors="replace") as f:
        src = f.read()

    if "tabContainerParams" not in src or "tabContainerVision" not in src or "tabContainerLogic" not in src:
        return False, "В EditActionDialog.kt отсутствуют контейнеры 3-вкладочной архитектуры Material 3"

    if "setupTabBtn(btnTabParams, \"ПАРАМЕТРЫ\", 0)" not in src:
        return False, "Отсутствует сегментированный TabBar с переключением разделов"

    if "btnSaveFull" not in src:
        return False, "Отсутствует доминантная кнопка сохранения шага btnSaveFull"

    return True, "Инспекция эргономики редактора шагов пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
