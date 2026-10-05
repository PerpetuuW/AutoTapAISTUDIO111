# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str) -> tuple[bool, str]:
    target = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "core", "math", "LinearToGraphMigrator.kt")
    if not os.path.exists(target):
        return False, "[CRITICAL] LinearToGraphMigrator.kt не найден."

    with open(target, "r", encoding="utf-8") as f:
        content = f.read()

    if "type = ActionType.DELAY" not in content or "holdDurationMs = 0L" not in content:
        return False, "[FAIL] Мигратор использует ActionType.CLICK вместо DELAY, ломая CV-полиморфизм."

    return True, "[PASS] Архитектура машинного зрения в Миграторе соблюдена (Zero-Regression)."

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
