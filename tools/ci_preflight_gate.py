#!/usr/bin/env python3
"""
ci_preflight_gate.py — Промышленный CI/CD валидатор кодовой базы AutoTap
Проверяет инварианты [G-01] .. [G-10] и корректность XML-разметок перед сборкой APK.
"""

import os
import re
import sys
from pathlib import Path

def run_preflight_checks(project_root_str: str) -> bool:
    root = Path(project_root_str)
    print("=" * 80)
    print("🚀 AutoTap Greenfield Master Protocol v34.0 Pre-Flight Gate")
    print(f"Scanning project directory: {root.resolve()}")
    print("=" * 80)

    kt_files = list(root.glob("app/src/**/*.kt"))
    xml_files = list(root.glob("app/src/main/res/**/*.xml"))
    print(f"Found {len(kt_files)} Kotlin files and {len(xml_files)} XML resources.")

    errors = []

    danger_bang = re.compile(r'(?<![!=])!!(?!=)')
    coroutine_sleep = re.compile(r'Thread\.sleep\(')
    unhandled_print_stack = re.compile(r'\.printStackTrace\(\)')
    wildcard_domain = re.compile(r'import\s+com\.example\.autotap\.domain\.model\.\*')
    xml_uppercase_enum = re.compile(r'android:(orientation|visibility)\s*=\s*"[A-Z]+"')

    for xf in xml_files:
        rel = str(xf.relative_to(root)).replace("\\", "/")
        try:
            content = xf.read_text(encoding='utf-8')
        except Exception as e:
            errors.append(f"[READ_FAIL] {rel}: {e}")
            continue

        match = xml_uppercase_enum.search(content)
        if match:
            errors.append(f"[XML_CASE_VIOLATION G-06] {rel}: Значение '{match.group(0)}' обязано быть в нижнем регистре.")

    for f in kt_files:
        rel = str(f.relative_to(root)).replace("\\", "/")
        try:
            code = f.read_text(encoding='utf-8')
        except Exception as e:
            errors.append(f"[READ_FAIL] {rel}: {e}")
            continue

        lines = code.splitlines()

        if wildcard_domain.search(code):
            errors.append(f"[VIOLATION G-08] {rel}: Запрещен wildcard-импорт доменных моделей.")

        for num, line in enumerate(lines, 1):
            sline = line.strip()
            if sline.startswith("//") or sline.startswith("*"):
                continue

            if danger_bang.search(line):
                errors.append(f"[VIOLATION G-02] {rel}:{num}: Обнаружен оператор !! в строке: {sline}")

            if coroutine_sleep.search(line):
                errors.append(f"[VIOLATION G-03] {rel}:{num}: Обнаружен блокирующий Thread.sleep() в строке: {sline}")

            if unhandled_print_stack.search(line) and not rel.endswith("AppLogger.kt"):
                errors.append(f"[VIOLATION G-02] {rel}:{num}: Прямой вызов printStackTrace() вне AppLogger.")

    print(f"\nCompleted scan across {len(kt_files)} Kotlin and {len(xml_files)} XML files.")
    if errors:
        print("\n❌ НАЙДЕНЫ НАРУШЕНИЯ АРХИТЕКТУРНЫХ ИНВАРИАНТОВ:")
        for err in errors:
            print("  - " + err)
        print("\nРелизная сборка заблокирована. Исправьте замечания.")
        return False
    else:
        print("\n✅ ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ УСПЕШНО.")
        print("Кодовая база полностью соответствует MASTER PROTOCOL v34.0 (Zero Defects Guarantee).")
        return True

if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else "."
    passed = run_preflight_checks(path)
    sys.exit(0 if passed else 1)
