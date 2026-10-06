#!/usr/bin/env python3
import os
import sys

def run_check(root_dir="."):
    engine_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/engine/MacroExecutionEngine.kt")
    if not os.path.exists(engine_path):
        return False, f"Файл не найден: {engine_path}"

    with open(engine_path, "r", encoding="utf-8") as f:
        code = f.read()

    if "TargetHighlightVisualizer.hideAllHighlights" not in code:
        return False, "MacroExecutionEngine должен принудительно очищать оверлеи подсветок перед снятием кадров/скриншотов!"

    return True, "[PASS] Инспекция безопасности оверлеев и защиты от самозахвата визуального дебага пройдена успешно."

if __name__ == "__main__":
    ok, msg = run_check(".")
    print(msg)
    if not ok:
        sys.exit(1)
