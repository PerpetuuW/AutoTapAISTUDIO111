#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check_gesture_recorder_meta.py — Инспектор чистоты метаданных в GestureRecorderOverlay
"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    rec_file = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/record/GestureRecorderOverlay.kt")
    if not os.path.isfile(rec_file): return False, f"Файл не найден: {rec_file}"

    with open(rec_file, "r", encoding="utf-8", errors="replace") as f:
        content = f.read()

    forbidden = ["finalSim", "finalShapeExp", "finalPadding"]
    detected = [t for t in forbidden if t in content]
    if detected:
        return False, f"Обнаружены неразрешенные идентификаторы: {', '.join(detected)}"

    return True, "Метаданные шаблонов в GestureRecorderOverlay валидны."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
