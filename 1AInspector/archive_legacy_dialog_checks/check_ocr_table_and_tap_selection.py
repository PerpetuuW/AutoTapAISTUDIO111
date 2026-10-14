# -*- coding: utf-8 -*-
"""check_ocr_table_and_tap_selection.py — Проверка возможности выбора OCR совпадений как тапом по рамкам, так и из таблицы"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    picker_file = os.path.join(
        root_dir, "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "overlay", "dialog", "OcrCandidatePickerDialog.kt"
    )
    edit_file = os.path.join(
        root_dir, "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "overlay", "dialog", "EditActionDialog.kt"
    )

    if not os.path.exists(picker_file):
        return False, f"Файл не найден: {picker_file}"
    if not os.path.exists(edit_file):
        return False, f"Файл не найден: {edit_file}"

    with open(picker_file, "r", encoding="utf-8") as f:
        picker_code = f.read()

    with open(edit_file, "r", encoding="utf-8") as f:
        edit_code = f.read()

    if "ТАБЛИЦА СОВПАДЕНИЙ" not in picker_code:
        return False, "В OcrCandidatePickerDialog отсутствует заголовок таблицы совпадений"

    if "ПОДТВЕРДИТЬ ВЫБОР" not in picker_code:
        return False, "В OcrCandidatePickerDialog отсутствует кнопка подтверждения выбора"

    if "applySelection" not in picker_code:
        return False, "В OcrCandidatePickerDialog отсутствует метод applySelection для синхронизации тапа и таблицы"

    if "OcrCandidatePickerDialog" not in edit_code:
        return False, "В EditActionDialog не используется OcrCandidatePickerDialog"

    return True, "[PASS] Выбор OCR совпадений тапом и из таблицы верифицирован."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
