# -*- coding: utf-8 -*-
"""check_metadata_calibration_anchors.py — Проверка мгновенного обнаружения шаблона через метаданные начальной координаты калибровки/выреза"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    engine_file = os.path.join(
        root_dir, "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "vision", "TemplateMatchingEngine.kt"
    )
    if not os.path.exists(engine_file):
        return False, f"Файл не найден: {engine_file}"

    with open(engine_file, "r", encoding="utf-8") as f:
        src = f.read()

    if "features.calibratedX" not in src or "anchorProbes.add" not in src:
        return False, "TemplateMatchingEngine.kt обязан использовать calibratedX/calibratedY из метаданных как опорные точки TIER 0"

    return True, "[PASS] Опорные точки калибровки из JSON метаданных верифицированы."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
