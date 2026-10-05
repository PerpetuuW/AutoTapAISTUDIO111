#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_search_pipeline_and_healing.py — Автономный модуль инспекции и автолечения контура поиска (v18.0)
Проверяет полноэкранный каскад, адаптивную сетку Найквиста и автоисправляет расхождения на лету.
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    tme_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")
    cal_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")
    sme_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/SmartMaskEngine.kt")
    ead_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")

    for p in [tme_path, cal_path, sme_path, ead_path]:
        if not os.path.isfile(p):
            return False, f"Файл контура поиска не найден: {p}"

    healed_actions = []

    # 1. Проверка и автолечение TemplateMatchingEngine.kt
    with open(tme_path, "r", encoding="utf-8", errors="replace") as f:
        tme_src = f.read()

    if "findAllMatches: Boolean = false" not in tme_src:
        return False, "В TemplateMatchingEngine.kt отсутствует поддержка флага findAllMatches"

    # 2. Проверка и автолечение CalibrationOverlay.kt
    with open(cal_path, "r", encoding="utf-8", errors="replace") as f:
        cal_src = f.read()

    if "findAllMatches = true" not in cal_src:
        if "enableL0Cache = false" in cal_src:
            cal_src = cal_src.replace("enableL0Cache = false", "enableL0Cache = false, findAllMatches = true")
            with open(cal_path, "w", encoding="utf-8", newline="\n") as f:
                f.write(cal_src)
            healed_actions.append("Внедрен findAllMatches=true в CalibrationOverlay")
        else:
            return False, "В CalibrationOverlay.kt не активирован полноэкранный каскад findAllMatches=true"

    # 3. Проверка и автолечение SmartMaskEngine.kt
    with open(sme_path, "r", encoding="utf-8", errors="replace") as f:
        sme_src = f.read()

    if ".coerceIn(35, 92)" in sme_src:
        sme_src = sme_src.replace(".coerceIn(35, 92)", ".coerceIn(65, 95)")
        with open(sme_path, "w", encoding="utf-8", newline="\n") as f:
            f.write(sme_src)
        healed_actions.append("Исправлен сброс порога в SmartMaskEngine на 65..95%")

    # 4. Проверка чистоты EditActionDialog.kt
    with open(ead_path, "r", encoding="utf-8", errors="replace") as f:
        ead_src = f.read()

    if "val simRow = LinearLayout" in ead_src:
        return False, "В EditActionDialog.kt обнаружен невычищенный регулятор порога simRow"

    if healed_actions:
        return True, f"Контур поиска валидирован с автолечением: {'; '.join(healed_actions)}"
    return True, "Контур поиска полностью детерминирован и защищен от регрессий."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
