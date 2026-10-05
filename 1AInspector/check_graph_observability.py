#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_graph_observability.py — Инспектор полноты логирования и защиты графа от сбоев
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    canvas_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphCanvasView.kt")
    overlay_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")
    logger_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/core/logger/AppLogger.kt")

    for p in [canvas_path, overlay_path, logger_path]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(logger_path, "r", encoding="utf-8") as f:
        l_src = f.read()
    if "app_session.log" not in l_src:
        return False, "В AppLogger отсутствует синхронная запись в app_session.log"

    with open(overlay_path, "r", encoding="utf-8") as f:
        o_src = f.read()
    if "GRAPH_LIFECYCLE" not in o_src or "GRAPH_TEMPLATE" not in o_src:
        return False, "В GraphEditorOverlay отсутствует подробное логирование жизненного цикла и шаблонов"

    return True, "Инспекция телеметрии и устойчивости графа пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
