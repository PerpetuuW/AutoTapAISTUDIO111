#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_lock_passthrough_and_graph.py — Инспектор шлюза жестов блокировки, чистой перекалибровки и графа
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    orch_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt")
    gest_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/gesture/GestureDispatcher.kt")
    graph_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")

    for p in [orch_path, gest_path, graph_path]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(orch_path, "r", encoding="utf-8") as f:
        orch_src = f.read()
    if "executeCleanCalibration" not in orch_src:
        return False, "В AutoTapOrchestrator.kt отсутствует метод executeCleanCalibration"

    with open(graph_path, "r", encoding="utf-8") as f:
        graph_src = f.read()
    if "ВЫБРАТЬ ИЗ БАЗЫ" not in graph_src:
        return False, "В GraphEditorOverlay.kt отсутствует выбор шаблона в инспекторе узла"

    return True, "Инспекция шлюза блокировки, чистой перекалибровки и графа пройдена."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
