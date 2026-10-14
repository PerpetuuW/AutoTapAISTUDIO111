#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_step_editor_clean_m3.py — Инспектор единой логики вкладок, тумблера OPENCV ⇄ AI и чистой палитры M3
"""
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    ead_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")

    if not os.path.isfile(ead_path):
        return False, f"Файл не найден: {ead_path}"

    with open(ead_path, "r", encoding="utf-8", errors="replace") as f:
        src = f.read()

    if "categorySelectorRow" in src:
        return False, "В EditActionDialog.kt остался дублирующий второй ряд категорий categorySelectorRow"

    # Проверка технического обозначения движков (OPENCV и AI / НЕЙРОСЕТЬ)
    has_engine_naming = any(k in src for k in ["OPENCV", "AI", "НЕЙРОСЕТЬ"])
    if not has_engine_naming:
        return False, "В тумблере движка отсутствует понятное обозначение движков (OPENCV / AI)"

    if "#00F5D4" in src:
        return False, "В EditActionDialog.kt обнаружен запрещенный кислотный цвет #00F5D4"

    return True, "Инспекция чистой палитры M3 и логики вкладок пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
