# -*- coding: utf-8 -*-
"""
check_audit_logs_integrity.py
Валидация:
1. Физическое наличие 1AZapros.md и 1AZaprosFunc.md в корне проекта.
2. Отсутствие артефактов поврежденной кодировки.
3. Наличие обязательных структурных секций ## [YYYY-MM-DD ...].
"""

import os
import sys

def run_check(root_dir: str) -> tuple[bool, str]:
    z_bug = os.path.join(root_dir, "1AZapros.md")
    z_func = os.path.join(root_dir, "1AZaprosFunc.md")

    if not os.path.exists(z_bug) or not os.path.exists(z_func):
        return False, "[CRITICAL] Один из журналов аудита (1AZapros.md / 1AZaprosFunc.md) отсутствует"

    for path, name in [(z_bug, "1AZapros.md"), (z_func, "1AZaprosFunc.md")]:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            content = f.read()

        if len(content.strip()) < 100:
            return False, f"[FAIL] Журнал {name} пуст или поврежден"

        if "╨" in content or "╤" in content:
            return False, f"[FAIL] В журнале {name} обнаружены артефакты поврежденной кодировки"

        if "## [2026-" not in content:
            return False, f"[FAIL] В журнале {name} отсутствуют актуальные структурные записи 2026 года"

    return True, "[PASS] Целостность и чистота журналов аудита 1AZapros.md и 1AZaprosFunc.md подтверждены"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
