# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str):
    target = os.path.join(
        root_dir, "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "overlay", "record", "GestureRecorderOverlay.kt"
    )
    if not os.path.exists(target):
        return False, "[FAIL] Файл GestureRecorderOverlay.kt не найден"

    with open(target, "r", encoding="utf-8") as f:
        content = f.read()

    if "val instantDuration = duration.coerceIn(100L, 2500L)" not in content:
        return False, "[FAIL] Отсутствует естественный расчет длительности воспроизведения жеста instantDuration"

    if "restoreTouchAfter(instantDuration + 60L)" not in content:
        return False, "[FAIL] restoreTouchAfter не синхронизирован с длительностью instantDuration"

    if "Live Motion Streaming" not in content:
        return False, "[FAIL] Отсутствует маркер Live Motion Streaming в блоке ACTION_MOVE"

    return True, "[PASS] Контракт гармонизированного воспроизведения жестов полностью соблюден"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
