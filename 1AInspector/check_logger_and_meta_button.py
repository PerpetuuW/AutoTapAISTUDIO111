#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_logger_and_meta_button.py — Инспектор AppLogger.shareLogs и чистоты управления метаданными шаблона
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    logger_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/core/logger/AppLogger.kt")
    dialog_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")

    for p in [logger_path, dialog_path]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(logger_path, "r", encoding="utf-8") as f:
        l_src = f.read()
    if "fun shareLogs" not in l_src or ("ClipData.newRawUri" not in l_src and "ClipData.newUri" not in l_src):
        return False, "В AppLogger.kt отсутствует метод shareLogs"

    with open(dialog_path, "r", encoding="utf-8") as f:
        d_src = f.read()
    if "tvTplSpecs" not in d_src:
        return False, "В EditActionDialog.kt отсутствует блок tvTplSpecs вывода метаданных шаблона"

    return True, "Инспекция AppLogger.shareLogs и метаданных шаблона пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
