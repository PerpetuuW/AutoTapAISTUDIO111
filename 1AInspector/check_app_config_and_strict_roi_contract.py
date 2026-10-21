#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_app_config_and_strict_roi_contract.py — Инспекция центрального AppConfig и режима "Только ROI шаблона / Весь экран".
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    cfg_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/core/config/AppConfig.kt")
    act_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/domain/model/MacroAction.kt")
    tme_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")
    ead_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")

    for p in [cfg_path, act_path, tme_path, ead_path]:
        if not os.path.isfile(p):
            return False, f"Файл не найден: {p}"

    with open(cfg_path, "r", encoding="utf-8") as f:
        cfg_src = f.read()

    for item in ["object Vision", "object Timing", "object ScreenLock", "object UI", "object Monetization"]:
        if item not in cfg_src:
            return False, f"В AppConfig.kt отсутствует категория '{item}'"

    with open(act_path, "r", encoding="utf-8") as f:
        act_src = f.read()

    if "isStrictRoiOnly: Boolean = false" not in act_src:
        return False, "В MacroAction.kt отсутствует флаг isStrictRoiOnly"

    with open(tme_path, "r", encoding="utf-8") as f:
        tme_src = f.read()

    if "isStrictRoiOnlyFromMeta" not in tme_src:
        return False, "В TemplateMatchingEngine.kt отсутствует интеграция isStrictRoiOnlyFromMeta"

    with open(ead_path, "r", encoding="utf-8") as f:
        ead_src = f.read()

    if "ОБЛАСТЬ ПОИСКА: ТОЛЬКО ROI ШАБЛОНА" not in ead_src:
        return False, "В EditActionDialog.kt отсутствует режим 'ОБЛАСТЬ ПОИСКА: ТОЛЬКО ROI ШАБЛОНА'"

    if "btnTestStep" in ead_src:
        return False, "В EditActionDialog.kt осталась устаревшая кнопка btnTestStep"

    return True, "Инспекция единого AppConfig и режима 'Только ROI шаблона / Весь экран' пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
