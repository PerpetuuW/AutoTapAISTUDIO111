#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_scripts_dialog_ergonomics_and_safety.py — Инспектор безопасности оверлея ScriptsDialog и защиты от BadTokenException
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    sd_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/ScriptsDialog.kt")

    if not os.path.isfile(sd_path): return False, f"Файл не найден: {sd_path}"

    with open(sd_path, "r", encoding="utf-8", errors="replace") as f:
        src = f.read()
        if "AlertDialog.Builder(" in src:
            return False, "В ScriptsDialog.kt обнаружен опасный вызов AlertDialog.Builder, вызывающий BadTokenException"
        if "ПЕРЕЗАПИСАТЬ" not in src:
            return False, "В ScriptsDialog.kt отсутствует функционал подтверждения перезаписи сценария"

    return True, "Инспекция безопасности оверлея сценариев пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
