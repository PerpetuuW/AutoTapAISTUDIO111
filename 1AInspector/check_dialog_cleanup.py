#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_dialog_cleanup.py — Инспектор чистоты параметров в EditActionDialog
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    ead_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")
    if not os.path.isfile(ead_path): return False, f"Файл не найден: {ead_path}"

    with open(ead_path, "r", encoding="utf-8", errors="replace") as f:
        src = f.read()

    if "РАЗРЕШЕНИЕ: " not in src:
        return False, "В EditActionDialog.kt отсутствует Read-Only вывод размеров шаблона в пикселях"

    return True, "Инспекция очистки диалога пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
