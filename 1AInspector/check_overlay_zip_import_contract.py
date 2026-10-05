#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_overlay_zip_import_contract.py — Инспектор надежного импорта сценариев и обхода Scoped Storage
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    sd_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/ScriptsDialog.kt")
    ma_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/presentation/main/MainActivity.kt")

    if not os.path.isfile(sd_path): return False, f"Файл не найден: {sd_path}"
    if not os.path.isfile(ma_path): return False, f"Файл не найден: {ma_path}"

    with open(sd_path, "r", encoding="utf-8", errors="replace") as f:
        sd_src = f.read()
        if "Environment.getExternalStoragePublicDirectory" in sd_src:
            return False, "В ScriptsDialog.kt остался заблокированный Scoped Storage вызов getExternalStoragePublicDirectory"
        if "#00F5D4" in sd_src:
            return False, "В ScriptsDialog.kt обнаружен запрещенный кислотный цвет #00F5D4"

    with open(ma_path, "r", encoding="utf-8", errors="replace") as f:
        ma_src = f.read()
        if "ACTION_IMPORT_ZIP" not in ma_src:
            return False, "В MainActivity.kt отсутствует обработчик ACTION_IMPORT_ZIP"

    return True, "Инспекция тракта импорта сценариев пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
