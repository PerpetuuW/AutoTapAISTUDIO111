#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_api35_and_zero_alloc_drawing.py — Инспектор безопасности API 35, zero-allocation в onDraw и манифеста
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    gro_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/record/GestureRecorderOverlay.kt")
    cal_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")
    man_path = os.path.join(root_dir, "app/src/main/AndroidManifest.xml")
    mee_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/engine/MacroExecutionEngine.kt")

    if not os.path.isfile(gro_path): return False, f"Файл не найден: {gro_path}"
    if not os.path.isfile(cal_path): return False, f"Файл не найден: {cal_path}"
    if not os.path.isfile(man_path): return False, f"Файл не найден: {man_path}"

    with open(gro_path, "r", encoding="utf-8", errors="replace") as f:
        if "removeLast()" in f.read():
            return False, "В GestureRecorderOverlay.kt остался вызов removeLast(), опасный на API 35"

    with open(cal_path, "r", encoding="utf-8", errors="replace") as f:
        src_cal = f.read()
        on_draw_part = src_cal.split("override fun onDraw(canvas: Canvas)")[1] if "override fun onDraw(canvas: Canvas)" in src_cal else ""
        if "val textScorePaint = Paint(" in on_draw_part or "val badgeRect = RectF(" in on_draw_part:
            return False, "В CalibrationOverlay.kt остались инлайн-аллокации в методе onDraw()"

    with open(man_path, "r", encoding="utf-8", errors="replace") as f:
        if "<queries>" not in f.read():
            return False, "В AndroidManifest.xml отсутствует тег <queries> для Package Visibility"

    if os.path.isfile(mee_path):
        with open(mee_path, "r", encoding="utf-8", errors="replace") as f:
            if "else -> return false" in f.read():
                return False, "В MacroExecutionEngine.kt остался redundant else в исчерпывающем when"

    return True, "Инспекция безопасности API 35, zero-allocation onDraw и манифеста пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
