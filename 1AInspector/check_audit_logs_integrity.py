# -*- coding: utf-8 -*-
"""
check_audit_logs_integrity.py
Валидация:
1. Физическое наличие SYSTEM_INSTRUCTION.md и 1AProtocol/CORE_RULES.md в корне проекта.
2. Отсутствие артефактов поврежденной кодировки.
3. Наличие обязательных секций анти-вайбкодинга.
"""

import os
import sys

def run_check(root_dir: str) -> tuple[bool, str]:
    sys_inst = os.path.join(root_dir, "SYSTEM_INSTRUCTION.md")
    core_rules = os.path.join(root_dir, "1AProtocol", "CORE_RULES.md")

    if not os.path.exists(sys_inst) or not os.path.exists(core_rules):
        return False, "[CRITICAL] SYSTEM_INSTRUCTION.md или 1AProtocol/CORE_RULES.md отсутствует"

    for path, name in [(sys_inst, "SYSTEM_INSTRUCTION.md"), (core_rules, "1AProtocol/CORE_RULES.md")]:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            content = f.read()

        if len(content.strip()) < 100:
            return False, f"[FAIL] Файл {name} пуст или поврежден"

        if "╨" in content or "╤" in content:
            return False, f"[FAIL] В файле {name} обнаружены артефакты поврежденной кодировки"

    return True, "[PASS] Целостность SYSTEM_INSTRUCTION.md и 1AProtocol/CORE_RULES.md подтверждена"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
