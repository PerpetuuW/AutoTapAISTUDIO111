#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_graph_layout_and_aspect.py — Инспектор пропорционального превью в узлах графа
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    canvas_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphCanvasView.kt")
    overlay_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")

    for p in [canvas_path, overlay_path]:
        if not os.path.exists(p): return False, f"Файл не найден: {p}"

    with open(canvas_path, "r", encoding="utf-8", errors="replace") as f:
        if "targetFitRect" not in f.read():
            return False, "В GraphCanvasView.kt отсутствует расчет пропорционального прямоугольника targetFitRect (Aspect Fit)"

    return True, "Инспекция пропорционального превью графа пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
