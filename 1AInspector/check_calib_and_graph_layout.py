#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_calib_and_graph_layout.py — Инспектор эргономики кнопок тулбара графа
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    graph_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")
    if not os.path.isfile(graph_path): return False, f"Файл не найден: {graph_path}"

    with open(graph_path, "r", encoding="utf-8", errors="replace") as f:
        if "dp(36)" not in f.read():
            return False, "В GraphEditorOverlay.kt не увеличена высота кнопок для предотвращения срезания текста"

    return True, "Инспекция верстки тулбара графа пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
