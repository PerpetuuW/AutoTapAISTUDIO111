#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_template_calibration_trigger_type_contract.py — Чекер фиксации ActionType.TRIGGER при калибровке и накопления multiTemplatePaths.
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    calib_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")
    tme_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")

    for p in [calib_path, tme_path]:
        if not os.path.isfile(p):
            return False, f"Файл не найден: {p}"

    with open(calib_path, "r", encoding="utf-8") as f:
        calib_src = f.read()

    if "type = ActionType.TRIGGER" not in calib_src:
        return False, "В CalibrationOverlay.kt отсутствует фиксация type = ActionType.TRIGGER при сохранении шаблона"

    if "multiTemplatePaths = existingMulti.distinct()" not in calib_src:
        return False, "В CalibrationOverlay.kt отсутствует сохранение аккумулятора multiTemplatePaths"

    with open(tme_path, "r", encoding="utf-8") as f:
        tme_src = f.read()

    if "(isShapeOnly || currentNormColor < 0.55f)" not in tme_src:
        return False, "В TemplateMatchingEngine.kt отсутствует защита от ложного сброса скора по currentEdgeRatio при хорошем цвете"

    return True, "Инспекция фиксации типа TRIGGER и сбора multiTemplatePaths пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
