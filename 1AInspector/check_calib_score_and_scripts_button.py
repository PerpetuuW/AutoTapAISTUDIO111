#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_calib_score_and_scripts_button.py — Инспектор отображения кандидатов, микро-оффсета и кнопки шагов
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    calib_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")
    sd_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/ScriptsDialog.kt")
    mee_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/engine/MacroExecutionEngine.kt")

    for p in [calib_path, sd_path, mee_path]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(calib_path, "r", encoding="utf-8") as f:
        c_src = f.read()
    if "scoreBadgeText" not in c_src:
        return False, "В CalibrationOverlay отсутствует отрисовка бейджа процента сходимости scoreBadgeText над кандидатами"
    if "coerceAtLeast(60)" not in c_src:
        return False, "В CalibrationOverlay отсутствует системный нижний порог 60%"

    with open(sd_path, "r", encoding="utf-8") as f:
        s_src = f.read()
    if "btnLoadSteps" not in s_src:
        return False, "В ScriptsDialog отсутствует выделенная кнопка btnLoadSteps для вывода шагов"

    with open(mee_path, "r", encoding="utf-8") as f:
        m_src = f.read()
    if "[Поиск]" not in m_src:
        return False, "В MacroExecutionEngine не заменено [Search] на [Поиск]"

    return True, "Инспекция визуализации калибровки, порога 60% и кнопки шагов пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
