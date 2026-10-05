#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_orchestrator_and_keyboard_fix.py — Инспектор компиляции AutoTapOrchestrator, мишени клика и клавиатуры
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    orch_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt")
    tov_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/target/TargetOverlayView.kt")
    ead_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")

    if not os.path.isfile(orch_path): return False, f"Файл не найден: {orch_path}"
    if not os.path.isfile(tov_path): return False, f"Файл не найден: {tov_path}"
    if not os.path.isfile(ead_path): return False, f"Файл не найден: {ead_path}"

    with open(orch_path, "r", encoding="utf-8", errors="replace") as f:
        orch_src = f.read()
        recapture_block = orch_src.split("fun startRecaptureForStep")[1].split("fun startCalibration")[0] if "fun startRecaptureForStep" in orch_src else ""
        if "safeX" in recapture_block:
            return False, "В startRecaptureForStep осталась неразрешенная переменная safeX"
        if "allActions = targetManager.getActions()" in recapture_block:
            return False, "В startRecaptureForStep остался недопустимый аргумент allActions"

    with open(tov_path, "r", encoding="utf-8", errors="replace") as f:
        tov_src = f.read()
        if "lp.topMargin = if (isBadge) dp(11) else 0" not in tov_src and "lp.topMargin = 0" not in tov_src:
            return False, "В TargetOverlayView.kt отсутствует динамический сброс topMargin для обычного клика"

    with open(ead_path, "r", encoding="utf-8", errors="replace") as f:
        ead_src = f.read()
        if "context.resources.displayMetrics.heightPixels" not in ead_src:
            return False, "В EditActionDialog.kt отсутствует абсолютный расчет высоты экрана для клавиатуры"

    return True, "Инспекция синтаксиса Orchestrator, мишени клика и клавиатуры пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
