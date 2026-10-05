#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_graph_fqcn_view.py — Инспектор FQCN для android.view.View в GraphEditorOverlay
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    graph_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")

    if not os.path.exists(graph_path):
        return False, f"Файл не найден: {graph_path}"

    with open(graph_path, "r", encoding="utf-8") as f:
        src = f.read()

    if "private var nodeSelectCard: View?" in src or "private var cascadeMenuCard: View?" in src or "private var templatePickerCard: View?" in src:
        return False, "В GraphEditorOverlay.kt остались неразрешенные короткие ссылки View вместо android.view.View"

    return True, "Инспекция FQCN пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
