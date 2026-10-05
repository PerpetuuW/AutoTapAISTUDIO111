import os

def run_check(root_dir: str) -> tuple[bool, str]:
    calib_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")

    if not os.path.exists(calib_p):
        return True, "[INFO] CalibrationOverlay.kt не найден"

    with open(calib_p, "r", encoding="utf-8") as f:
        src = f.read()

    if "rawSourceBitmap" in src:
        return False, "[FAIL] В CalibrationOverlay обнаружена несуществующая ссылка rawSourceBitmap."

    if "initialMask = currentMask" not in src:
        return False, "[FAIL] В CalibrationOverlay пропущен обязательный параметр initialMask при создании MagicWandEditorOverlay."

    if "import com.example.autotap.infrastructure.overlay.tutorial.InteractiveTutorialOverlay" not in src:
        return False, "[FAIL] В CalibrationOverlay отсутствует импорт InteractiveTutorialOverlay."

    return True, "[OK] Сигнатура вызова MagicWandEditorOverlay и импорт туториала в калибровке верифицированы."
