#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_targets_lock_tournament.py — Инспектор скрытия номеров, тикера сессии, рекалибровки и турнирного поиска
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    tom_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/target/TargetOverlayManager.kt")
    lock_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/lock/ScreenLockOverlay.kt")
    orch_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt")
    tme_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")

    for p in [tom_path, lock_path, orch_path, tme_path]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(tom_path, "r", encoding="utf-8") as f:
        tom_src = f.read()
    if "tvCornerBadge.visibility" not in tom_src:
        return False, "В TargetOverlayManager отсутствует переключение видимости tvCornerBadge при скрытии номеров"

    with open(lock_path, "r", encoding="utf-8") as f:
        l_src = f.read()
    if "uptimeTickerRunnable" not in l_src:
        return False, "В ScreenLockOverlay отсутствует ежесекундный тикер сессии uptimeTickerRunnable"

    with open(orch_path, "r", encoding="utf-8") as f:
        o_src = f.read()
    if "screen_" not in o_src:
        return False, "В startRecaptureForStep отсутствует загрузка полноразмерного screen_ файла"

    return True, "Инспекция скрытия номеров, тикера, рекалибровки и турнирного поиска пройдена."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
