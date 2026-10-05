#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_threshold_val_resolved.py — Инспектор разрешения metaSim в бейджах карусели
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    ead_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")

    if not os.path.isfile(ead_path): return False, f"Файл не найден: {ead_path}"

    with open(ead_path, "r", encoding="utf-8", errors="replace") as f:
        src = f.read()
        if "thresholdVal" in src:
            return False, "В EditActionDialog.kt осталась неразрешенная переменная thresholdVal"
        if '"$metaSim%"' not in src:
            return False, "В бейдже карусели не используется переменная metaSim"

    return True, "Инспекция разрешения metaSim в карусели пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
