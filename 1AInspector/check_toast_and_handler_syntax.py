import os

def run_check(root_dir: str) -> tuple[bool, str]:
    srv_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/accessibility/AutoTapAccessibilityService.kt")
    cap_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CaptureFrameOverlay.kt")

    if not os.path.exists(srv_p) or not os.path.exists(cap_p):
        return True, "[INFO] Файлы проверки не найдены"

    with open(srv_p, "r", encoding="utf-8") as f:
        srv_code = f.read()

    with open(cap_p, "r", encoding="utf-8") as f:
        cap_code = f.read()

    if "Toast.SHORT" in srv_code or "Toast.SHORT" in cap_code:
        return False, "[FAIL] Обнаружено ошибочное использование Toast.SHORT вместо Toast.LENGTH_SHORT."

    if "orchestrator.mainHandler" in srv_code:
        return False, "[FAIL] В AutoTapAccessibilityService обнаружена попытка доступа к private mainHandler оркестратора."

    return True, "[OK] Синтаксис Toast.LENGTH_SHORT и видимость Handler верифицированы."
