#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_graph_ergonomics_suite.py — Инспектор эргономики мобильного графа (Automate Benchmark)
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    canvas_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphCanvasView.kt")
    overlay_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")

    for p in [canvas_path, overlay_path]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(canvas_path, "r", encoding="utf-8") as f:
        c_src = f.read()
    if "onTemplateThumbnailClicked" not in c_src:
        return False, "В GraphCanvasView.kt отсутствует обработчик прямого тапа по превью onTemplateThumbnailClicked"
    if "dpF(48f)" not in c_src and "dpF(50f)" not in c_src:
        return False, "В GraphCanvasView.kt отсутствует увеличенная зона магнитной привязки (>= 48dp)"

    with open(overlay_path, "r", encoding="utf-8") as f:
        o_src = f.read()
    if "ОБЗОР" not in o_src:
        return False, "В GraphEditorOverlay.kt отсутствует кнопка быстрого центрирования ОБЗОР"

    return True, "Инспекция пакета эргономики нодового графа пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
