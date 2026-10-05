# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str) -> tuple[bool, str]:
    p_dialog = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "dialog", "EditActionDialog.kt")
    if not os.path.exists(p_dialog):
        return False, "[CRITICAL] EditActionDialog.kt не найден"

    with open(p_dialog, "r", encoding="utf-8") as f:
        content = f.read()

    if "btnMergeTemplates" not in content or "ОБЪЕДИНИТЬ" not in content:
        return False, "[FAIL] В multiControlRow отсутствует кнопка ОБЪЕДИНИТЬ"

    if "btnToggleAdvanced" in content:
        return False, "[FAIL] В диалоге остался избыточный пустой аккордеон btnToggleAdvanced"

    return True, "[PASS] Контракт кнопки ОБЪЕДИНИТЬ и чистой эргономики без спойлеров соблюден."

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
