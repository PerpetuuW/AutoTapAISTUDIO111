# -*- coding: utf-8 -*-
"""
1AAutomation/error_parser.py
Интеллектуальный извлекатель критических ошибок:
- Ошибки компиляции Kotlin / Java (e: /path/...)
- Ошибки провала Unit-тестов (testDebugUnitTest / ComparisonFailure)
- Ошибки манифеста и ресурсов Android
"""

def extract_critical_error(build_output: str) -> str:
    lines = build_output.splitlines()
    critical_errors = []
    capture_test_failure = False

    for i, line in enumerate(lines):
        # 1. Ошибки компиляции Kotlin
        if line.strip().startswith("e:") or "error:" in line.lower():
            if not any(ignore in line for ignore in ["warning:", "w:"]):
                critical_errors.append(line.strip())
                # Захват 2 последующих строк контекста
                for offset in range(1, 3):
                    if i + offset < len(lines) and lines[i + offset].strip():
                        critical_errors.append("   " + lines[i + offset].strip())

        # 2. Провалы Unit-тестов
        if "FAILED" in line and ("Test" in line or "test" in line):
            critical_errors.append(line.strip())
        if "AssertionError" in line or "ComparisonFailure" in line:
            critical_errors.append(line.strip())
            capture_test_failure = True

        # 3. Фатальные сбои сборки Gradle
        if line.strip().startswith("FAILURE:") or line.strip().startswith("* What went wrong:"):
            critical_errors.append(line.strip())
            for offset in range(1, 4):
                if i + offset < len(lines):
                    critical_errors.append("   " + lines[i + offset].strip())

    if critical_errors:
        # Убираем дубликаты с сохранением порядка
        seen = set()
        unique = []
        for err in critical_errors:
            if err not in seen:
                seen.add(err)
                unique.append(err)
        return "\n".join(unique[:20])

    # Fallback: последние 15 строк вывода
    return "\n".join(lines[-15:])
