import os

def run_check(root_dir: str) -> tuple[bool, str]:
    capture_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CaptureFrameOverlay.kt")
    license_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/core/license/LicenseManager.kt")
    settings_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/GlobalSettingsDialog.kt")

    if not os.path.exists(capture_p) or not os.path.exists(license_p) or not os.path.exists(settings_p):
        return False, "[FAIL] Компоненты помощника или видоискателя не найдены"

    with open(capture_p, "r", encoding="utf-8") as f:
        capture_code = f.read()

    with open(license_p, "r", encoding="utf-8") as f:
        lic_code = f.read()

    with open(settings_p, "r", encoding="utf-8") as f:
        set_code = f.read()

    if '"ROI"' not in capture_code:
        return False, "[FAIL] В CaptureFrameOverlay отсутствует кнопка ROI."

    if "isRewardAdSessionActive" not in lic_code:
        return False, "[FAIL] В LicenseManager отсутствует разграничение наградной сессии рекламы."

    if "Помощник закрытия рекламы" not in set_code:
        return False, "[FAIL] В GlobalSettingsDialog отсутствует настройка помощника рекламы."

    return True, "[OK] Ассистент закрытия рекламы и инструмент ROI верифицированы."
