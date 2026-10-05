# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str):
    p_rec = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "record", "GestureRecorderOverlay.kt")
    if not os.path.exists(p_rec):
        return False, f"[CRITICAL] Файл не найден: {p_rec}"
        
    with open(p_rec, "r", encoding="utf-8") as f:
        src = f.read()
    if "AutoTapOrchestrator.INSTANCE" in src:
        return False, "[FAIL] В GestureRecorderOverlay осталось некорректное обращение к приватному свойству INSTANCE"
    if "AutoTapOrchestrator.getInstance(context)" not in src:
        return False, "[FAIL] В GestureRecorderOverlay отсутствует публичный вызов getInstance(context)"

    return True, "[PASS] Доступ к синглтону оркестратора полностью детерминирован"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
