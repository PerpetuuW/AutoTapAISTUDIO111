#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_ui_clean_palette_and_parent_safety.py — Инспектор безопасности иерархии View, экспорта логов и чистой палитры
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    ead_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")
    logger_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/core/logger/AppLogger.kt")
    main_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/presentation/main/MainActivity.kt")

    if not os.path.isfile(ead_path):
        return False, f"Файл не найден: {ead_path}"
    if not os.path.isfile(logger_path):
        return False, f"Файл не найден: {logger_path}"

    with open(ead_path, "r", encoding="utf-8", errors="replace") as f:
        ead_src = f.read()
    with open(logger_path, "r", encoding="utf-8", errors="replace") as f:
        logger_src = f.read()

    if "contentLayout.addView(typeRow3)" in ead_src:
        return False, "В EditActionDialog.kt остался дублирующий вызов contentLayout.addView(typeRow3)"

    if "grantUriPermission" not in logger_src:
        return False, "В AppLogger.kt отсутствует явная выдача прав grantUriPermission"

    if os.path.isfile(main_path):
        with open(main_path, "r", encoding="utf-8", errors="replace") as f:
            main_src = f.read()
        if 'createBtn("ОТПРАВИТЬ", "#10B981", "#000000"' in main_src:
            return False, "В MainActivity.kt осталась кислотно-зеленая кнопка ОТПРАВИТЬ с черным текстом"

    return True, "Инспекция безопасности View, экспорта логов и палитры пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
