# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str) -> tuple[bool, str]:
    target_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "core", "license", "LicenseManager.kt")
    if not os.path.exists(target_file):
        return False, "[CRITICAL] LicenseManager.kt не найден"

    with open(target_file, "r", encoding="utf-8") as f:
        content = f.read()

    if "BuildConfig" in content:
        return False, "[FAIL] В LicenseManager.kt обнаружено обращение к BuildConfig вместо FLAG_DEBUGGABLE"

    if "FLAG_DEBUGGABLE" not in content:
        return False, "[FAIL] В LicenseManager.kt отсутствует нативная проверка FLAG_DEBUGGABLE"

    return True, "[PASS] Контракт нативной проверки отладки соблюден"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
