import os

def run_check(root_dir: str) -> tuple[bool, str]:
    orch_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt")
    neural_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/NeuralVisualMatcher.kt")
    capture_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CaptureFrameOverlay.kt")
    calib_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")

    if not os.path.exists(orch_p) or not os.path.exists(neural_p) or not os.path.exists(capture_p) or not os.path.exists(calib_p):
        return False, "[FAIL] Компоненты системы не найдены"

    with open(orch_p, "r", encoding="utf-8") as f:
        orch_src = f.read()
    if "1000L else 100L" not in orch_src:
        return False, "[FAIL] В AutoTapOrchestrator не настроена дифференциация задержек: 1000мс для шаблонов и 100мс для обычных шагов."

    with open(neural_p, "r", encoding="utf-8") as f:
        neural_src = f.read()
    if "minSimilarityPercent + 5" not in neural_src:
        return False, "[FAIL] В NeuralVisualMatcher отсутствует защитный буфер сходимости AI в +5%."

    with open(capture_p, "r", encoding="utf-8") as f:
        cap_src = f.read()
    if "drawLine" not in cap_src or '"ROI"' not in cap_src:
        return False, "[FAIL] В CaptureFrameOverlay должна быть векторная пиктограмма прицела ROI [ · ]."

    with open(calib_p, "r", encoding="utf-8") as f:
        calib_src = f.read()
    if "TutorialMode.CALIBRATION" not in calib_src:
        return False, "[FAIL] В CalibrationOverlay отсутствует кнопка запуска туториала калибровки."

    return True, "[OK] Тайминги 1с/100мс, буфер AI +5%, векторная пиктограмма ROI и туториал калибровки верифицированы."
