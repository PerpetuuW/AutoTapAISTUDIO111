#!/usr/bin/env python3
import os
import sys

def run_check(root_dir="."):
    tmpl_engine_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/TemplateMatchingEngine.kt")
    if not os.path.exists(tmpl_engine_path):
        return False, f"Файл не найден: {tmpl_engine_path}"

    with open(tmpl_engine_path, "r", encoding="utf-8") as f:
        code = f.read()

    required_keys = ["calibratedX", "cropLeft", "originalX", "calibratedY", "cropTop", "originalY"]
    for key in required_keys:
        if key not in code:
            return False, f"TemplateMatchingEngine должен проверять метаданные позиций '{key}' для Tier 0 оптимизации!"

    if "anchorProbes" not in code:
        return False, "TemplateMatchingEngine должен формировать список опорных точек anchorProbes для TIER 0 отсечения!"

    return True, "[PASS] Инспекция оптимизации мультипоиска по метаданным шаблонов пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(".")
    print(msg)
    if not ok:
        sys.exit(1)
