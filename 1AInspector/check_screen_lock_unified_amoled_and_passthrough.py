# -*- coding: utf-8 -*-
"""
Checker: check_screen_lock_unified_amoled_and_passthrough.py
Валидация единого AMOLED режима в ScreenLockOverlay и связки onGesturePassthroughToggle.
"""
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    lock_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "lock", "ScreenLockOverlay.kt")
    orch_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "orchestrator", "AutoTapOrchestrator.kt")
    svc_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "accessibility", "AutoTapAccessibilityService.kt")

    if not all(os.path.exists(f) for f in [lock_file, orch_file, svc_file]):
        return False, "Файлы экрана блокировки или сервиса не найдены"

    with open(lock_file, "r", encoding="utf-8") as f:
        l_code = f.read()
    with open(orch_file, "r", encoding="utf-8") as f:
        o_code = f.read()
    with open(svc_file, "r", encoding="utf-8") as f:
        s_code = f.read()

    if "AMOLED ECO" not in l_code or "btnMode" in l_code:
        return False, "ScreenLockOverlay обязан содержать единый умный ползунок без лишних кнопок режимов"

    if "#00F5D4" in l_code:
        return False, "В ScreenLockOverlay остался запрещенный кислотный цвет #00F5D4"

    if "onGesturePassthroughToggle" not in o_code and "onGesturePassthroughToggle" not in s_code:
        return False, "В оркестраторе/сервисе отсутствует подключение onGesturePassthroughToggle к screenLockOverlay"

    return True, "Единый AMOLED экран блокировки и безотказный шлюз жестов верифицированы"

if __name__ == "__main__":
    passed, msg = run_check(os.getcwd())
    print(f"[{'PASS' if passed else 'FAIL'}] {msg}")
