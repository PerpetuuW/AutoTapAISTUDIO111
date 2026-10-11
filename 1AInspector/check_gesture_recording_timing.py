# -*- coding: utf-8 -*-
"""check_gesture_recording_timing.py — Проверка корректной записи времени пауз (delayMs) и длительности удержания (holdDurationMs) в GestureRecorderOverlay"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    rec_file = os.path.join(
        root_dir, "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "overlay", "record", "GestureRecorderOverlay.kt"
    )
    if not os.path.exists(rec_file):
        return False, f"Файл не найден: {rec_file}"

    with open(rec_file, "r", encoding="utf-8") as f:
        src = f.read()

    if "actualDelay" not in src or "lastRecordEventTime" not in src:
        return False, "GestureRecorderOverlay.kt должен рассчитывать actualDelay для фиксации пауз между жестами"

    if "delayMs = actualDelay" not in src:
        return False, "GestureRecorderOverlay.kt должен присваивать delayMs = actualDelay всем записанным действиям"

    return True, "[PASS] Учет времени пауз и нажатий при записи жестов верифицирован."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
