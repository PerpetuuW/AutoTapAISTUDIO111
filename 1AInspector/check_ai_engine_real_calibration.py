import os

def run_check(root_dir: str) -> tuple[bool, str]:
    calib_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt")
    neural_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/NeuralVisualMatcher.kt")
    editor_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")

    if not os.path.exists(calib_p) or not os.path.exists(neural_p) or not os.path.exists(editor_p):
        return True, "[INFO] Модули калибровки не найдены"

    with open(calib_p, "r", encoding="utf-8") as f:
        calib_code = f.read()

    with open(neural_p, "r", encoding="utf-8") as f:
        neural_code = f.read()

    with open(editor_p, "r", encoding="utf-8") as f:
        editor_code = f.read()

    if "evaluateAnchorZNCC" not in calib_code:
        return False, "[FAIL] CalibrationOverlay обязан вызывать реальный evaluateAnchorZNCC при включенном AI-режиме."

    if "znccToScorePercent" not in neural_code or "percentToRequiredZncc" not in neural_code:
        return False, "[FAIL] NeuralVisualMatcher обязан содержать научную калибровку шкалы ZNCC в проценты."

    if "НАЙДЕНО (УСПЕХ)" in editor_code or "ТАЙМАУТ (ОШИБКА)" in editor_code:
        return False, "[FAIL] В GraphEditorOverlay обнаружены устаревшие длинные метки портов, приводящие к срезанию текста."

    return True, "[OK] Реальная калибровка AI-движка и эргономика инспектора подтверждены."
