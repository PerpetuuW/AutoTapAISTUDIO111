#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_calibration_collapse_and_recording_diagnostics.py — Инспектор кнопки сворачивания калибровки, вызова клавиатуры, отображения табло и логов записи жестов
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    calib_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")
    badge_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/badge/RunningBadgeOverlay.kt")
    rec_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/record/GestureRecorderOverlay.kt")

    if not os.path.isfile(calib_path): return False, f"Файл не найден: {calib_path}"
    if not os.path.isfile(badge_path): return False, f"Файл не найден: {badge_path}"
    if not os.path.isfile(rec_path): return False, f"Файл не найден: {rec_path}"

    with open(calib_path, "r", encoding="utf-8", errors="replace") as f:
        calib_src = f.read()
        if "isCardCollapsed" not in calib_src:
            return False, "в CalibrationOverlay.kt отсутствует кнопка сворачивания меню калибровки"
        if "FLAG_ALT_FOCUSABLE_IM" in calib_src.split("overlayWindowManager.addViewSafe(root, params)")[0].split("val params = WindowManager.LayoutParams(")[-1]:
            return False, "в CalibrationOverlay.kt обнаружен запрещенный FLAG_ALT_FOCUSABLE_IM, блокирующий вызов мягкой клавиатуры"
        if "Цель_" not in calib_src:
            return False, "в CalibrationOverlay.kt отсутствует генерация коротких инкрементальных имен по умолчанию"

    with open(badge_path, "r", encoding="utf-8", errors="replace") as f:
        badge_src = f.read()
        if "contentWidthDp = 78" not in badge_src and "contentWidthDp = 120" not in badge_src:
            return False, "в RunningBadgeOverlay.kt отсутствует ширина информационного табло"

    with open(rec_path, "r", encoding="utf-8", errors="replace") as f:
        rec_src = f.read()
        if "GESTURE_RECORDER" not in rec_src:
            return False, "в GestureRecorderOverlay.kt отсутствует детальное логирование записанных жестов"

    return True, "Инспекция кнопки сворачивания калибровки, вызова клавиатуры, табло и логов записи жестов пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
