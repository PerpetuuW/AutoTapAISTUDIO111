#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_screen_lock_drag_and_collision_contract.py — Инспекция авто-размещения карточки замка от коллизий и ручки перетаскивания.
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    lock_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/lock/ScreenLockOverlay.kt")
    orch_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt")

    for p in [lock_path, orch_path]:
        if not os.path.isfile(p):
            return False, f"Файл не найден: {p}"

    with open(lock_path, "r", encoding="utf-8") as f:
        lock_src = f.read()

    if "headerDragBar" not in lock_src:
        return False, "В ScreenLockOverlay.kt отсутствует ручка свободного перетаскивания headerDragBar"

    if "pointsInBottom" not in lock_src:
        return False, "В ScreenLockOverlay.kt отсутствует алгоритм авто-анализа коллизий pointsInBottom"

    if "autotap_lock_prefs" not in lock_src:
        return False, "В ScreenLockOverlay.kt отсутствует сохранение позиции в autotap_lock_prefs"

    with open(orch_path, "r", encoding="utf-8") as f:
        orch_src = f.read()

    if "screenLockOverlay.show(targetManager.getActions())" not in orch_src:
        return False, "В AutoTapOrchestrator.kt экран замка вызывается без передачи списка мишеней targetManager.getActions()"

    return True, "Инспекция авто-размещения и ручного перетаскивания карточки замка пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
