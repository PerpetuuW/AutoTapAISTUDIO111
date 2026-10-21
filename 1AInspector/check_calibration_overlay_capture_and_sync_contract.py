#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_calibration_overlay_capture_and_sync_contract.py — Чекер асинхронного захвата кадров и синхронизации калибровки.
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    orch_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt")
    lock_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/lock/ScreenLockOverlay.kt")

    for p in [orch_path, lock_path]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(orch_path, "r", encoding="utf-8") as f:
        orch_src = f.read()

    if "captureScreenshotAsync" not in orch_src:
        return False, "В AutoTapOrchestrator.kt отсутствует асинхронный предЗахват скриншота captureScreenshotAsync"

    if "startTemplateCalibration" not in orch_src:
        return False, "В AutoTapOrchestrator.kt отсутствует точечный старт калибровки startTemplateCalibration"

    with open(lock_path, "r", encoding="utf-8") as f:
        lock_src = f.read()

    if "fun setTemporarilyTransparent" not in lock_src:
        return False, "В ScreenLockOverlay.kt отсутствует метод setTemporarilyTransparent"

    return True, "Инспекция контракта асинхронной калибровки пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
